package com.cadernoreceitas.testutil

import android.content.Context
import com.cadernoreceitas.data.local.AppDatabase
import com.cadernoreceitas.data.repository.CachePolicy
import com.cadernoreceitas.data.repository.DefaultRecipeRepository

/**
 * Monta um [DefaultRecipeRepository] REAL (Room em memoria) com a rede, as preferencias, as fotos
 * e o relogio falsos. Precisa de um `Context` (Robolectric). Chame [close] no `@After`.
 */
class RepositoryTestEnv(
    context: Context,
    policy: CachePolicy = CachePolicy(),
    val clock: MutableClock = MutableClock(),
    val remote: FakeRecipeRemoteDataSource = FakeRecipeRemoteDataSource(),
    val preferences: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
    val photos: FakePhotoStorage = FakePhotoStorage(),
) {
    val db: AppDatabase = AppDatabase.inMemory(context)

    val repository = DefaultRecipeRepository(
        remote = remote,
        db = db,
        preferences = preferences,
        photoStorage = photos,
        clock = clock,
        policy = policy,
    )

    fun close() {
        db.close()
    }
}
