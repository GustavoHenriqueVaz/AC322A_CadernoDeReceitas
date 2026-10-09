package com.cadernoreceitas.testutil

import com.cadernoreceitas.data.local.prefs.UserPreferencesRepository
import com.cadernoreceitas.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Preferencias em memoria. O estado fica em [state] (um `MutableStateFlow`): o teste pode
 * le-lo/altera-lo direto e observar [preferences] normalmente.
 */
class FakeUserPreferencesRepository(
    initial: UserPreferences = UserPreferences(),
) : UserPreferencesRepository {

    val state = MutableStateFlow(initial)
    override val preferences: Flow<UserPreferences> = state

    /** Historico das chamadas (para verificar o que o codigo de producao gravou). */
    val lastSearches = mutableListOf<Pair<String, String?>>()
    val dailyRecipes = mutableListOf<Pair<String, Long>>()
    val reminderChanges = mutableListOf<Boolean>()

    override suspend fun setLastSearch(query: String, category: String?) {
        lastSearches += query to category
        state.update { it.copy(lastQuery = query, lastCategory = category) }
    }

    override suspend fun setReminderEnabled(enabled: Boolean) {
        reminderChanges += enabled
        state.update { it.copy(reminderEnabled = enabled) }
    }

    override suspend fun setDailyRecipe(recipeId: String, epochDay: Long) {
        dailyRecipes += recipeId to epochDay
        state.update { it.copy(dailyRecipeId = recipeId, dailyRecipeEpochDay = epochDay) }
    }
}
