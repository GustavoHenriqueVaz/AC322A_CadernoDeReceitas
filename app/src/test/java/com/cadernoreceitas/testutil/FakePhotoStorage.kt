package com.cadernoreceitas.testutil

import android.net.Uri
import com.cadernoreceitas.data.photo.PhotoException
import com.cadernoreceitas.data.photo.PhotoStorage
import kotlinx.coroutines.delay
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Armazenamento de fotos falso: nao toca no disco, so registra o que foi pedido.
 *  - `importToTemp` devolve `importResult` (ou lanca `importFailure`); com `importDelayMillis`
 *    suspende antes (para testar cancelamento).
 *  - `commit` devolve um caminho previsivel: `committed/<recipeId>-<n>.jpg`.
 */
class FakePhotoStorage : PhotoStorage {

    var importResult: String = "/tmp/photo_tmp/fake.jpg"
    var importFailure: PhotoException? = null
    var importDelayMillis: Long = 0
    var commitFailure: PhotoException? = null

    val importedUris: MutableList<Uri> = CopyOnWriteArrayList()
    val commits: MutableList<Pair<String, String>> = CopyOnWriteArrayList() // (tempPath, recipeId)
    val deleted: MutableList<String?> = CopyOnWriteArrayList()
    val deletedTemp: MutableList<String?> = CopyOnWriteArrayList()
    val orphanCalls: MutableList<Set<String>> = CopyOnWriteArrayList()

    override suspend fun importToTemp(source: Uri): String {
        importedUris += source
        if (importDelayMillis > 0) delay(importDelayMillis)
        importFailure?.let { throw it }
        return importResult
    }

    override suspend fun commit(tempPath: String, recipeId: String): String {
        commitFailure?.let { throw it }
        commits += tempPath to recipeId
        return "committed/$recipeId-${commits.size}.jpg"
    }

    override suspend fun delete(path: String?) {
        deleted += path
    }

    override suspend fun deleteTemp(path: String?) {
        deletedTemp += path
    }

    override suspend fun deleteOrphans(referencedPaths: Set<String>) {
        orphanCalls += referencedPaths
    }
}
