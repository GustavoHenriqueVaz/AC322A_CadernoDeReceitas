package com.cadernoreceitas.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cadernoreceitas.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Preferencias do usuario (ultima busca, lembrete diario...). Interface para permitir fakes. */
interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setLastSearch(query: String, category: String?)
    suspend fun setReminderEnabled(enabled: Boolean)
    suspend fun setDailyRecipe(recipeId: String, epochDay: Long)
}

class DataStoreUserPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    private object Keys {
        val LAST_QUERY = stringPreferencesKey("last_query")
        val LAST_CATEGORY = stringPreferencesKey("last_category")
        val REMINDER = booleanPreferencesKey("reminder_enabled")
        val DAILY_ID = stringPreferencesKey("daily_recipe_id")
        val DAILY_DAY = longPreferencesKey("daily_recipe_epoch_day")
    }

    override val preferences: Flow<UserPreferences> = dataStore.data
        // Arquivo ilegivel nao pode derrubar o app: cai nos valores padrao.
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs ->
            UserPreferences(
                lastQuery = prefs[Keys.LAST_QUERY].orEmpty(),
                lastCategory = prefs[Keys.LAST_CATEGORY],
                reminderEnabled = prefs[Keys.REMINDER] ?: false,
                dailyRecipeId = prefs[Keys.DAILY_ID],
                dailyRecipeEpochDay = prefs[Keys.DAILY_DAY],
            )
        }

    override suspend fun setLastSearch(query: String, category: String?) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_QUERY] = query
            if (category == null) prefs.remove(Keys.LAST_CATEGORY) else prefs[Keys.LAST_CATEGORY] = category
        }
    }

    override suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.REMINDER] = enabled }
    }

    override suspend fun setDailyRecipe(recipeId: String, epochDay: Long) {
        dataStore.edit {
            it[Keys.DAILY_ID] = recipeId
            it[Keys.DAILY_DAY] = epochDay
        }
    }
}
