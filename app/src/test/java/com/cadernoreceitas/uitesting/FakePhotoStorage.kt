package com.cadernoreceitas.uitesting

import android.net.Uri
import com.cadernoreceitas.data.photo.PhotoStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * [PhotoStorage] sem arquivos: gera caminhos "de mentira" e registra cada operacao.
 * `importToTemp` pode demorar ([importDelayMillis]) ou falhar ([importFailure]); cancelamentos
 * no meio da importacao ficam contados em [importCancellations].
 */
class FakePhotoStorage : PhotoStorage {

    val importCalls: MutableList<Uri> = CopyOnWriteArrayList()
    val importCancellations = AtomicInteger()

    /** Cada `commit` como par (caminho temporario, id da receita). */
    val commitCalls: MutableList<Pair<String, String>> = CopyOnWriteArrayList()

    /** Argumentos de `deleteTemp` / `delete`, na ordem (inclui `null`, como o chamador mandou). */
    val deleteTempCalls: MutableList<String?> = CopyOnWriteArrayList()
    val deleteCalls: MutableList<String?> = CopyOnWriteArrayList()

    @Volatile var importFailure: Throwable? = null
    @Volatile var importDelayMillis: Long = 0

    private val tempCounter = AtomicInteger()
    private val finalCounter = AtomicInteger()

    override suspend fun importToTemp(source: Uri): String {
        importCalls += source
        try {
            if (importDelayMillis > 0) delay(importDelayMillis)
            importFailure?.let { throw it }
            return "/cache/photo_tmp/tmp-${tempCounter.incrementAndGet()}.jpg"
        } catch (e: CancellationException) {
            importCancellations.incrementAndGet()
            throw e
        }
    }

    override suspend fun commit(tempPath: String, recipeId: String): String {
        commitCalls += tempPath to recipeId
        return "/files/photos/$recipeId-${finalCounter.incrementAndGet()}.jpg"
    }

    override suspend fun delete(path: String?) {
        deleteCalls += path
    }

    override suspend fun deleteTemp(path: String?) {
        deleteTempCalls += path
    }

    override suspend fun deleteOrphans(referencedPaths: Set<String>) = Unit
}
