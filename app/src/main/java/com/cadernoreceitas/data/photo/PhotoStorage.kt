package com.cadernoreceitas.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** Falha ao importar a foto escolhida (arquivo invalido, grande demais, sem leitura...). */
class PhotoException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Guarda a foto "do meu prato" escolhida no Photo Picker.
 * Fluxo: [importToTemp] (reduz e salva em cache) -> [commit] ao gravar a anotacao
 * (move para o armazenamento interno do app) -> [deleteOrphans] na manutencao.
 */
interface PhotoStorage {
    suspend fun importToTemp(source: Uri): String
    suspend fun commit(tempPath: String, recipeId: String): String
    suspend fun delete(path: String?)
    suspend fun deleteTemp(path: String?)
    suspend fun deleteOrphans(referencedPaths: Set<String>)
}

class FilePhotoStorage(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : PhotoStorage {

    private val appContext = context.applicationContext
    private val tempDir get() = File(appContext.cacheDir, TEMP_DIR)
    private val photosDir get() = File(appContext.filesDir, PHOTOS_DIR)

    override suspend fun importToTemp(source: Uri): String = withContext(io) {
        val resolver = appContext.contentResolver
        val mime = resolver.getType(source)
        if (mime == null || !mime.startsWith("image/")) {
            throw PhotoException("Tipo de arquivo nao suportado: $mime")
        }

        // 1) Le so as dimensoes para decidir quanto reduzir (evita estourar a memoria).
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Com inJustDecodeBounds o decodeStream SEMPRE devolve null (so preenche `bounds`):
        // por isso o bloco devolve Unit, senao toda foto seria rejeitada como "nao abriu".
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds); Unit }
            ?: throw PhotoException("Nao foi possivel abrir a imagem")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw PhotoException("Imagem invalida")
        coroutineContext.ensureActive()

        // 2) Decodifica reduzida (maior lado <= MAX_SIDE).
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw PhotoException("Nao foi possivel decodificar a imagem")
        try {
            coroutineContext.ensureActive()
            tempDir.mkdirs()
            val target = File(tempDir, "${UUID.randomUUID()}.jpg")
            try {
                target.outputStream().use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                        throw PhotoException("Falha ao comprimir a imagem")
                    }
                }
                coroutineContext.ensureActive()
            } catch (e: Throwable) {
                target.delete() // nao deixa arquivo pela metade se cancelado/falhou
                throw e
            }
            target.absolutePath
        } finally {
            bitmap.recycle()
        }
    }

    override suspend fun commit(tempPath: String, recipeId: String): String = withContext(io) {
        val temp = requireInside(File(tempPath), tempDir)
        if (!temp.isFile) throw PhotoException("Foto temporaria nao encontrada")
        photosDir.mkdirs()
        val safeId = recipeId.filter { it.isLetterOrDigit() }.take(20)
        val target = File(photosDir, "$safeId-${System.currentTimeMillis()}.jpg")
        temp.copyTo(target, overwrite = true)
        temp.delete()
        target.absolutePath
    }

    override suspend fun delete(path: String?) {
        if (path == null) return
        withContext(io) {
            runCatching { requireInside(File(path), photosDir).delete() }
        }
    }

    override suspend fun deleteTemp(path: String?) {
        if (path == null) return
        withContext(io) {
            runCatching { requireInside(File(path), tempDir).delete() }
        }
    }

    override suspend fun deleteOrphans(referencedPaths: Set<String>) = withContext(io) {
        val referenced = referencedPaths.map { File(it).absolutePath }.toSet()
        val limit = System.currentTimeMillis() - ORPHAN_MIN_AGE_MS
        photosDir.listFiles().orEmpty()
            .filter { it.isFile && it.absolutePath !in referenced && it.lastModified() < limit }
            .forEach { it.delete() }
        tempDir.listFiles().orEmpty()
            .filter { it.isFile && it.lastModified() < limit }
            .forEach { it.delete() }
    }

    /** Protege contra caminhos como "../../x" vindos de estado salvo. */
    private fun requireInside(file: File, dir: File): File {
        val canonical = file.canonicalFile
        val base = dir.canonicalFile
        if (canonical.parentFile != base) throw PhotoException("Caminho fora da pasta permitida")
        return canonical
    }

    internal companion object {
        const val TEMP_DIR = "photo_tmp"
        const val PHOTOS_DIR = "photos"
        const val MAX_SIDE = 1600
        const val JPEG_QUALITY = 85
        const val ORPHAN_MIN_AGE_MS = 60 * 60 * 1000L

        fun sampleSizeFor(width: Int, height: Int, maxSide: Int = MAX_SIDE): Int {
            var sample = 1
            var w = width
            var h = height
            while (w / 2 >= maxSide || h / 2 >= maxSide) {
                sample *= 2
                w /= 2
                h /= 2
            }
            return sample
        }
    }
}
