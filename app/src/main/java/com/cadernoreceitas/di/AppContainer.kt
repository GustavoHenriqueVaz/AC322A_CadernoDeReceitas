package com.cadernoreceitas.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.cadernoreceitas.BuildConfig
import com.cadernoreceitas.data.local.AppDatabase
import com.cadernoreceitas.data.local.prefs.DataStoreUserPreferencesRepository
import com.cadernoreceitas.data.local.prefs.UserPreferencesRepository
import com.cadernoreceitas.data.photo.FilePhotoStorage
import com.cadernoreceitas.data.photo.PhotoStorage
import com.cadernoreceitas.data.remote.MealApi
import com.cadernoreceitas.data.remote.RetrofitRecipeRemoteDataSource
import com.cadernoreceitas.data.repository.DefaultRecipeRepository
import com.cadernoreceitas.data.repository.RecipeRepository
import com.cadernoreceitas.work.ReminderScheduler
import com.cadernoreceitas.work.WorkManagerReminderScheduler
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Injecao de dependencias MANUAL (sem Hilt/Koin): o `Application` guarda um [AppContainer]
 * e as telas o consultam para montar seus ViewModels. Nos testes basta trocar o container
 * por outro com implementacoes falsas.
 */
interface AppContainer {
    val recipeRepository: RecipeRepository
    val userPreferences: UserPreferencesRepository
    val photoStorage: PhotoStorage
    val reminderScheduler: ReminderScheduler
}

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")

class DefaultAppContainer(private val context: Context) : AppContainer {

    private val json = Json {
        ignoreUnknownKeys = true // a API pode ganhar campos novos sem quebrar o app
        coerceInputValues = true
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val mealApi: MealApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.MEALDB_BASE_URL)
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()
            .create(MealApi::class.java)
    }

    private val database: AppDatabase by lazy { AppDatabase.create(context) }

    override val userPreferences: UserPreferencesRepository by lazy {
        DataStoreUserPreferencesRepository(context.applicationContext.userPreferencesDataStore)
    }

    override val photoStorage: PhotoStorage by lazy { FilePhotoStorage(context) }

    override val reminderScheduler: ReminderScheduler by lazy { WorkManagerReminderScheduler(context) }

    override val recipeRepository: RecipeRepository by lazy {
        DefaultRecipeRepository(
            remote = RetrofitRecipeRemoteDataSource(mealApi),
            db = database,
            preferences = userPreferences,
            photoStorage = photoStorage,
        )
    }
}
