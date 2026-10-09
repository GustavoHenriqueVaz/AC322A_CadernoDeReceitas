package com.cadernoreceitas.data.photo

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

/**
 * `FilePhotoStorage` com arquivos reais nas pastas do Robolectric (cacheDir/filesDir).
 * Foco: protecao contra path traversal, limpeza de orfaos respeitando a idade minima e a
 * conta de `inSampleSize`.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // decodificacao/compressao de imagem de verdade (no modo LEGACY e tudo de mentira)
class FilePhotoStorageTest {

    private lateinit var context: Context
    private lateinit var storage: FilePhotoStorage
    private lateinit var tempDir: File
    private lateinit var photosDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = FilePhotoStorage(context, io = Dispatchers.Unconfined)
        tempDir = File(context.cacheDir, FilePhotoStorage.TEMP_DIR).apply { mkdirs() }
        photosDir = File(context.filesDir, FilePhotoStorage.PHOTOS_DIR).apply { mkdirs() }
    }

    private fun newFile(dir: File, name: String, content: String = "conteudo $name", ageMs: Long = 0L): File {
        dir.mkdirs()
        return File(dir, name).apply {
            writeText(content)
            if (ageMs > 0) setLastModified(System.currentTimeMillis() - ageMs)
        }
    }

    private val hour = 60 * 60 * 1000L

    private suspend fun expectPhotoError(block: suspend () -> Unit): PhotoException {
        val error = runCatching { block() }.exceptionOrNull()
        assertThat(error).isInstanceOf(PhotoException::class.java)
        return error as PhotoException
    }

    // ================================================================== sampleSizeFor

    @Test
    fun `imagens pequenas nao sao reduzidas`() {
        assertThat(FilePhotoStorage.sampleSizeFor(100, 100)).isEqualTo(1)
        assertThat(FilePhotoStorage.sampleSizeFor(1600, 1600)).isEqualTo(1)
        assertThat(FilePhotoStorage.sampleSizeFor(3199, 3199)).isEqualTo(1) // metade (1599) < 1600
        assertThat(FilePhotoStorage.sampleSizeFor(1, 1)).isEqualTo(1)
    }

    @Test
    fun `a reducao e potencia de 2 e cresce com o tamanho`() {
        assertThat(FilePhotoStorage.sampleSizeFor(3200, 100)).isEqualTo(2)
        assertThat(FilePhotoStorage.sampleSizeFor(100, 3200)).isEqualTo(2) // vale o maior lado
        assertThat(FilePhotoStorage.sampleSizeFor(4000, 3000)).isEqualTo(2)
        assertThat(FilePhotoStorage.sampleSizeFor(6400, 4800)).isEqualTo(4)
        assertThat(FilePhotoStorage.sampleSizeFor(12_800, 9_600)).isEqualTo(8)
    }

    @Test
    fun `sampleSizeFor sempre devolve potencia de 2 e a imagem reduzida nao fica menor que o limite`() {
        val sizes = listOf(1 to 1, 800 to 600, 1600 to 1200, 4032 to 3024, 8000 to 6000, 12_000 to 9_000, 20_000 to 100)
        for ((w, h) in sizes) {
            val sample = FilePhotoStorage.sampleSizeFor(w, h)

            assertThat(sample and (sample - 1)).isEqualTo(0) // potencia de 2
            assertThat(sample).isAtLeast(1)
            val largestSide = maxOf(w, h) / sample
            if (maxOf(w, h) >= FilePhotoStorage.MAX_SIDE) {
                // abordagem padrao do Android: reduz enquanto a METADE ainda alcanca o limite
                assertThat(largestSide).isAtLeast(FilePhotoStorage.MAX_SIDE)
                assertThat(largestSide).isLessThan(FilePhotoStorage.MAX_SIDE * 2)
            } else {
                assertThat(sample).isEqualTo(1)
            }
        }
    }

    @Test
    fun `maxSide customizado e respeitado`() {
        assertThat(FilePhotoStorage.sampleSizeFor(1000, 1000, maxSide = 500)).isEqualTo(2)
        assertThat(FilePhotoStorage.sampleSizeFor(1000, 1000, maxSide = 1000)).isEqualTo(1)
    }

    // ================================================================== commit

    @Test
    fun `commit move a foto temporaria para a pasta de fotos com nome baseado no id`() = runTest {
        val temp = newFile(tempDir, "abc.jpg", content = "bytes-da-foto")

        val committed = File(storage.commit(temp.absolutePath, "52772"))

        assertThat(committed.parentFile!!.canonicalFile).isEqualTo(photosDir.canonicalFile)
        assertThat(committed.name).matches("52772-\\d+\\.jpg")
        assertThat(committed.readText()).isEqualTo("bytes-da-foto")
        assertThat(temp.exists()).isFalse() // a temporaria foi consumida
    }

    @Test
    fun `commit limpa o id (so letras e digitos, no maximo 20) para montar o nome do arquivo`() = runTest {
        val temp1 = newFile(tempDir, "a.jpg")
        val temp2 = newFile(tempDir, "b.jpg")

        val nasty = File(storage.commit(temp1.absolutePath, "../../etc/passwd"))
        val long = File(storage.commit(temp2.absolutePath, "1".repeat(50)))

        assertThat(nasty.parentFile!!.canonicalFile).isEqualTo(photosDir.canonicalFile)
        assertThat(nasty.name).startsWith("etcpasswd-")
        assertThat(long.name).startsWith("1".repeat(20) + "-")
        assertThat(long.name).doesNotContain("1".repeat(21))
    }

    @Test
    fun `commit recusa caminho fora da pasta temporaria e preserva o arquivo`() = runTest {
        val outside = newFile(context.cacheDir, "segredo.txt")
        val inFiles = newFile(context.filesDir, "banco.db")

        expectPhotoError { storage.commit(outside.absolutePath, "1") }
        expectPhotoError { storage.commit(inFiles.absolutePath, "1") }

        assertThat(outside.exists()).isTrue()
        assertThat(inFiles.exists()).isTrue()
        assertThat(photosDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `commit recusa path traversal com ponto-ponto que escapa da pasta temporaria`() = runTest {
        val outside = newFile(context.cacheDir, "segredo.txt")
        val traversal = File(tempDir, "../segredo.txt").path // aparentemente dentro de photo_tmp

        expectPhotoError { storage.commit(traversal, "1") }

        assertThat(outside.exists()).isTrue()
        assertThat(photosDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `commit recusa subpastas da pasta temporaria`() = runTest {
        val nested = newFile(File(tempDir, "sub"), "a.jpg")

        expectPhotoError { storage.commit(nested.absolutePath, "1") }

        assertThat(nested.exists()).isTrue()
    }

    @Test
    fun `commit aceita caminho com ponto-ponto que continua dentro da pasta temporaria`() = runTest {
        val temp = newFile(tempDir, "a.jpg")
        val roundabout = File(tempDir, "../${FilePhotoStorage.TEMP_DIR}/a.jpg").path

        val committed = File(storage.commit(roundabout, "9"))

        assertThat(committed.exists()).isTrue()
        assertThat(temp.exists()).isFalse()
    }

    @Test
    fun `commit de arquivo temporario que nao existe falha com PhotoException`() = runTest {
        val missing = File(tempDir, "nao-existe.jpg").absolutePath

        expectPhotoError { storage.commit(missing, "1") }
    }

    // ================================================================== delete / deleteTemp

    @Test
    fun `delete remove foto da pasta de fotos`() = runTest {
        val photo = newFile(photosDir, "1-100.jpg")

        storage.delete(photo.absolutePath)

        assertThat(photo.exists()).isFalse()
    }

    @Test
    fun `delete com null ou arquivo inexistente nao faz nada nem falha`() = runTest {
        storage.delete(null)
        storage.delete(File(photosDir, "nao-existe.jpg").absolutePath)
        storage.deleteTemp(null)
        storage.deleteTemp(File(tempDir, "nao-existe.jpg").absolutePath)
    }

    @Test
    fun `delete nunca apaga arquivos fora da pasta de fotos`() = runTest {
        val inTemp = newFile(tempDir, "t.jpg")
        val inFiles = newFile(context.filesDir, "importante.db")
        val traversal = File(photosDir, "../importante.db").path

        storage.delete(inTemp.absolutePath)
        storage.delete(inFiles.absolutePath)
        storage.delete(traversal)
        storage.delete("../../importante.db")

        assertThat(inTemp.exists()).isTrue()
        assertThat(inFiles.exists()).isTrue()
    }

    @Test
    fun `deleteTemp remove so da pasta temporaria`() = runTest {
        val temp = newFile(tempDir, "t.jpg")
        val photo = newFile(photosDir, "p.jpg")
        val outside = newFile(context.filesDir, "importante.db")

        storage.deleteTemp(photo.absolutePath)
        storage.deleteTemp(outside.absolutePath)
        storage.deleteTemp(File(tempDir, "../importante.db").path)
        assertThat(photo.exists()).isTrue()
        assertThat(outside.exists()).isTrue()
        assertThat(temp.exists()).isTrue()

        storage.deleteTemp(temp.absolutePath)
        assertThat(temp.exists()).isFalse()
    }

    // ================================================================== deleteOrphans

    @Test
    fun `deleteOrphans apaga fotos antigas nao referenciadas e preserva as demais`() = runTest {
        val referencedOld = newFile(photosDir, "keep.jpg", ageMs = 5 * hour)
        val orphanOld = newFile(photosDir, "orphan-old.jpg", ageMs = 5 * hour)
        val orphanYoung = newFile(photosDir, "orphan-young.jpg", ageMs = 10 * 60 * 1000L)
        val orphanBorderline = newFile(photosDir, "orphan-59min.jpg", ageMs = 59 * 60 * 1000L)
        val orphanJustOld = newFile(photosDir, "orphan-61min.jpg", ageMs = 61 * 60 * 1000L)

        storage.deleteOrphans(setOf(referencedOld.absolutePath))

        assertThat(referencedOld.exists()).isTrue() // referenciada pelo caderno: nunca some
        assertThat(orphanOld.exists()).isFalse()
        assertThat(orphanYoung.exists()).isTrue() // pode ser uma foto recem-salva ainda nao referenciada
        assertThat(orphanBorderline.exists()).isTrue()
        assertThat(orphanJustOld.exists()).isFalse()
    }

    @Test
    fun `deleteOrphans reconhece o caminho devolvido pelo commit como referenciado`() = runTest {
        val temp = newFile(tempDir, "novo.jpg")
        val committedPath = storage.commit(temp.absolutePath, "52772")
        File(committedPath).setLastModified(System.currentTimeMillis() - 10 * hour)

        storage.deleteOrphans(setOf(committedPath))
        assertThat(File(committedPath).exists()).isTrue()

        storage.deleteOrphans(emptySet())
        assertThat(File(committedPath).exists()).isFalse() // sem referencia e velha: orfa
    }

    @Test
    fun `deleteOrphans limpa temporarios antigos e mantem os recentes`() = runTest {
        val oldTemp = newFile(tempDir, "old.jpg", ageMs = 3 * hour)
        val youngTemp = newFile(tempDir, "young.jpg", ageMs = 5 * 60 * 1000L)

        storage.deleteOrphans(emptySet())

        assertThat(oldTemp.exists()).isFalse()
        assertThat(youngTemp.exists()).isTrue()
    }

    @Test
    fun `deleteOrphans nao mexe em subpastas nem em arquivos fora das duas pastas`() = runTest {
        val subDir = File(photosDir, "subpasta").apply { mkdirs() }
        val inSub = newFile(subDir, "x.jpg", ageMs = 9 * hour)
        val unrelated = newFile(context.filesDir, "outro.txt", ageMs = 9 * hour)
        val cacheFile = newFile(context.cacheDir, "cache.bin", ageMs = 9 * hour)

        storage.deleteOrphans(emptySet())

        assertThat(subDir.exists()).isTrue()
        assertThat(inSub.exists()).isTrue()
        assertThat(unrelated.exists()).isTrue()
        assertThat(cacheFile.exists()).isTrue()
    }

    @Test
    fun `deleteOrphans funciona quando as pastas ainda nao existem`() = runTest {
        photosDir.deleteRecursively()
        tempDir.deleteRecursively()

        storage.deleteOrphans(setOf("/qualquer/coisa.jpg"))
    }

    // ================================================================== importToTemp (validacoes)

    /** Provedor de conteudo falso: tipo MIME e bytes configurados por URI. */
    class FakeImageProvider : ContentProvider() {
        override fun onCreate() = true
        override fun getType(uri: Uri): String? = types[uri.toString()]
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
            val bytes = contents[uri.toString()] ?: return null
            val file = File.createTempFile("fake-image", ".bin").apply {
                deleteOnExit()
                writeBytes(bytes)
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

        companion object {
            val types = mutableMapOf<String, String>()
            val contents = mutableMapOf<String, ByteArray>()
        }
    }

    private fun registerProvider() {
        FakeImageProvider.types.clear()
        FakeImageProvider.contents.clear()
        Robolectric.buildContentProvider(FakeImageProvider::class.java).create("com.cadernoreceitas.test.images")
    }

    @Test
    fun `importToTemp rejeita URI sem tipo MIME conhecido`() = runTest {
        val uri = Uri.parse("content://com.cadernoreceitas.test.inexistente/foto/1")

        val error = expectPhotoError { storage.importToTemp(uri) }

        assertThat(error).hasMessageThat().contains("Tipo de arquivo nao suportado")
        assertThat(tempDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `importToTemp rejeita arquivos que nao sao imagem`() = runTest {
        registerProvider()
        val uri = Uri.parse("content://com.cadernoreceitas.test.images/doc/1")
        FakeImageProvider.types[uri.toString()] = "application/pdf"
        FakeImageProvider.contents[uri.toString()] = "%PDF-1.4".toByteArray()

        val error = expectPhotoError { storage.importToTemp(uri) }

        assertThat(error).hasMessageThat().contains("application/pdf")
        assertThat(tempDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `importToTemp rejeita MIME de imagem com conteudo que nao e imagem`() = runTest {
        registerProvider()
        val uri = Uri.parse("content://com.cadernoreceitas.test.images/img/2")
        FakeImageProvider.types[uri.toString()] = "image/png"
        FakeImageProvider.contents[uri.toString()] = "isto nao e uma imagem de verdade".toByteArray()

        val error = expectPhotoError { storage.importToTemp(uri) }

        assertThat(error).hasMessageThat().isEqualTo("Imagem invalida") // falhou ao ler as dimensoes, nao antes
        assertThat(tempDir.listFiles().orEmpty()).isEmpty() // nenhum arquivo pela metade
    }

    @Test
    fun `importToTemp de imagem valida grava um jpg na pasta temporaria`() = runTest {
        registerProvider()
        val uri = Uri.parse("content://com.cadernoreceitas.test.images/img/3")
        FakeImageProvider.types[uri.toString()] = "image/png"
        FakeImageProvider.contents[uri.toString()] = Base64.getDecoder().decode(ONE_PIXEL_PNG)

        val path = storage.importToTemp(uri)

        val file = File(path)
        assertThat(file.isFile).isTrue()
        assertThat(file.name).endsWith(".jpg")
        assertThat(file.parentFile!!.canonicalFile).isEqualTo(tempDir.canonicalFile)
        // e e um JPEG de verdade (assinatura FF D8)
        val header = file.readBytes().take(2).map { it.toInt() and 0xFF }
        assertThat(header).containsExactly(0xFF, 0xD8).inOrder()
        // o caminho devolvido e aceito por commit (esta mesmo dentro da pasta permitida)
        val committed = File(storage.commit(path, "1"))
        assertThat(committed.exists()).isTrue()
    }

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(156, 63, 27))
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun decodedSize(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth to options.outHeight
    }

    @Test
    fun `importToTemp reduz fotos grandes (maior lado acima de 3200 cai pela metade)`() = runTest {
        registerProvider()
        val uri = Uri.parse("content://com.cadernoreceitas.test.images/img/4")
        FakeImageProvider.types[uri.toString()] = "image/png"
        FakeImageProvider.contents[uri.toString()] = pngBytes(3300, 200)

        val file = File(storage.importToTemp(uri))

        assertThat(decodedSize(file)).isEqualTo(1650 to 100) // inSampleSize = 2
    }

    @Test
    fun `importToTemp nao mexe no tamanho de fotos pequenas`() = runTest {
        registerProvider()
        val uri = Uri.parse("content://com.cadernoreceitas.test.images/img/5")
        FakeImageProvider.types[uri.toString()] = "image/png"
        FakeImageProvider.contents[uri.toString()] = pngBytes(640, 480)

        val file = File(storage.importToTemp(uri))

        assertThat(decodedSize(file)).isEqualTo(640 to 480)
    }

    @Test
    fun `importToTemp gera arquivos distintos a cada importacao`() = runTest {
        registerProvider()
        val uri = Uri.parse("content://com.cadernoreceitas.test.images/img/6")
        FakeImageProvider.types[uri.toString()] = "image/png"
        FakeImageProvider.contents[uri.toString()] = pngBytes(32, 32)

        val first = storage.importToTemp(uri)
        val second = storage.importToTemp(uri)

        assertThat(first).isNotEqualTo(second)
        assertThat(tempDir.listFiles().orEmpty()).hasLength(2)
    }

    private companion object {
        /** PNG 1x1 valido. */
        const val ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    }
}
