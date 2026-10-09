package com.cadernoreceitas.uitesting

import com.cadernoreceitas.data.local.prefs.UserPreferencesRepository
import com.cadernoreceitas.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.CopyOnWriteArrayList

/** Preferencias em memoria (sem DataStore) que registram cada gravacao recebida. */
class FakeUserPreferencesRepository(initial: UserPreferences = UserPreferences()) : UserPreferencesRepository {

    /** Valor atual; o teste pode alterar direto (ex.: `state.value = ...`). */
    val state = MutableStateFlow(initial)

    override val preferences: Flow<UserPreferences> = state

    /** Cada chamada de `setLastSearch` como par (texto, categoria). */
    val lastSearchCalls: MutableList<Pair<String, String?>> = CopyOnWriteArrayList()
    val reminderCalls: MutableList<Boolean> = CopyOnWriteArrayList()
    val dailyRecipeCalls: MutableList<Pair<String, Long>> = CopyOnWriteArrayList()

    override suspend fun setLastSearch(query: String, category: String?) {
        lastSearchCalls += query to category
        state.update { it.copy(lastQuery = query, lastCategory = category) }
    }

    override suspend fun setReminderEnabled(enabled: Boolean) {
        reminderCalls += enabled
        state.update { it.copy(reminderEnabled = enabled) }
    }

    override suspend fun setDailyRecipe(recipeId: String, epochDay: Long) {
        dailyRecipeCalls += recipeId to epochDay
        state.update { it.copy(dailyRecipeId = recipeId, dailyRecipeEpochDay = epochDay) }
    }
}
