package com.cadernoreceitas.testutil

import com.cadernoreceitas.data.local.prefs.UserPreferencesRepository
import com.cadernoreceitas.data.photo.PhotoStorage
import com.cadernoreceitas.data.repository.RecipeRepository
import com.cadernoreceitas.di.AppContainer
import com.cadernoreceitas.domain.model.Category
import com.cadernoreceitas.domain.model.Loaded
import com.cadernoreceitas.domain.model.Recipe
import com.cadernoreceitas.domain.model.RecipeException
import com.cadernoreceitas.domain.model.RecipeNote
import com.cadernoreceitas.domain.model.RecipeSummary
import com.cadernoreceitas.domain.model.SavedRecipe
import com.cadernoreceitas.work.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Container de dependencias para testes: cada item pode ser trocado em tempo de execucao
 * (propriedades `var`), o que permite a um teste Robolectric obter o container ja criado pelo
 * `TestCadernoApp` e injetar o repositorio que quiser:
 *
 *     val app = ApplicationProvider.getApplicationContext<TestCadernoApp>()
 *     (app.container as FakeAppContainer).recipeRepository = meuRepositorio
 */
class FakeAppContainer(
    repository: RecipeRepository = NoOpRecipeRepository,
    override var userPreferences: UserPreferencesRepository = FakeUserPreferencesRepository(),
    override var photoStorage: PhotoStorage = FakePhotoStorage(),
    override var reminderScheduler: ReminderScheduler = FakeReminderScheduler(),
) : AppContainer {
    override var recipeRepository: RecipeRepository = repository
}

/** Agendador falso: so conta as chamadas. */
class FakeReminderScheduler : ReminderScheduler {
    var enableCalls = 0
        private set
    var disableCalls = 0
        private set

    /** Estado atual: `true` apos `enable()`, `false` apos `disable()`. */
    var enabled = false
        private set

    override fun enable() {
        enableCalls++
        enabled = true
    }

    override fun disable() {
        disableCalls++
        enabled = false
    }
}

/**
 * Repositorio inerte (o padrao do [FakeAppContainer]): caderno vazio e consultas que falham
 * com [RecipeException] de rede. Existe so para o `CadernoApp.onCreate` (manutencao) nao quebrar.
 */
object NoOpRecipeRepository : RecipeRepository {
    override fun observeSaved(): Flow<List<SavedRecipe>> = flowOf(emptyList())
    override fun observeSavedIds(): Flow<Set<String>> = flowOf(emptySet())
    override fun observeSavedRecipe(id: String): Flow<SavedRecipe?> = flowOf(null)

    override suspend fun search(query: String, forceRefresh: Boolean): Loaded<List<RecipeSummary>> = offline()
    override suspend fun recipesByCategory(category: String, forceRefresh: Boolean): Loaded<List<RecipeSummary>> = offline()
    override suspend fun categories(forceRefresh: Boolean): Loaded<List<Category>> = offline()
    override suspend fun recipe(id: String, forceRefresh: Boolean): Loaded<Recipe> = offline()
    override suspend fun recipeOfTheDay(): Loaded<Recipe> = offline()

    override suspend fun saveRecipe(recipe: Recipe) = Unit
    override suspend fun saveRecipeById(id: String) = offline()
    override suspend fun saveNote(recipe: Recipe, note: RecipeNote) = Unit
    override suspend fun removeSaved(id: String): SavedRecipe? = null
    override suspend fun restoreSaved(saved: SavedRecipe) = Unit
    override suspend fun purgeExpiredCache() = Unit

    private fun offline(): Nothing = throw RecipeException(RecipeException.Kind.NETWORK)
}
