package com.cadernoreceitas.instrumented

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.cadernoreceitas.CadernoApp
import com.cadernoreceitas.data.local.prefs.UserPreferencesRepository
import com.cadernoreceitas.data.photo.PhotoStorage
import com.cadernoreceitas.data.repository.RecipeRepository
import com.cadernoreceitas.di.AppContainer
import com.cadernoreceitas.domain.model.Category
import com.cadernoreceitas.domain.model.DataSource
import com.cadernoreceitas.domain.model.Ingredient
import com.cadernoreceitas.domain.model.Loaded
import com.cadernoreceitas.domain.model.Recipe
import com.cadernoreceitas.domain.model.RecipeException
import com.cadernoreceitas.domain.model.RecipeNote
import com.cadernoreceitas.domain.model.RecipeSummary
import com.cadernoreceitas.domain.model.SavedRecipe
import com.cadernoreceitas.domain.model.UserPreferences
import com.cadernoreceitas.work.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/*
 * Fakes MINIMOS para os testes instrumentados (rodam no emulador/aparelho, sem rede nem banco).
 * Sao parecidos com os de src/test/.../uitesting, mas ficam aqui porque os dois source sets
 * (test e androidTest) nao compartilham codigo. Os testes JVM (Robolectric) continuam sendo a
 * suite principal; estes existem para validar os mesmos fluxos em um Android de verdade.
 */

object Sample {
    const val TERIYAKI_ID = "52772"
    const val CRUMBLE_ID = "52893"

    val teriyaki = Recipe(
        id = TERIYAKI_ID,
        name = "Teriyaki Chicken Casserole",
        category = "Chicken",
        area = "Japanese",
        instructions = "Misture tudo e asse por 35 minutos.",
        thumbnailUrl = null, // sem imagem: o Coil nao precisa de rede
        tags = listOf("Meat", "Casserole"),
        youtubeUrl = null,
        sourceUrl = null,
        ingredients = listOf(Ingredient("soy sauce", "3/4 cup"), Ingredient("water", "1/2 cup")),
    )

    val crumble = teriyaki.copy(
        id = CRUMBLE_ID,
        name = "Apple & Blackberry Crumble",
        category = "Dessert",
        area = "British",
        tags = emptyList(),
    )

    val daily = teriyaki.copy(id = "52771", name = "Spicy Arrabiata Penne", category = "Vegetarian", area = "Italian")
}

class FakeRepository : RecipeRepository {
    val all = listOf(Sample.teriyaki, Sample.crumble, Sample.daily)
    val saved = MutableStateFlow<List<SavedRecipe>>(emptyList())

    @Volatile var failSearch = false
    @Volatile var recipeLoads = 0

    override fun observeSaved(): Flow<List<SavedRecipe>> = saved
    override fun observeSavedIds(): Flow<Set<String>> = saved.map { list -> list.map { it.recipe.id }.toSet() }
    override fun observeSavedRecipe(id: String): Flow<SavedRecipe?> = saved.map { list -> list.firstOrNull { it.recipe.id == id } }

    override suspend fun search(query: String, forceRefresh: Boolean): Loaded<List<RecipeSummary>> {
        if (failSearch) throw RecipeException(RecipeException.Kind.NETWORK)
        val found = listOf(Sample.teriyaki, Sample.crumble).filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        return Loaded(found.map { it.toSummary() }, DataSource.REMOTE)
    }

    override suspend fun recipesByCategory(category: String, forceRefresh: Boolean): Loaded<List<RecipeSummary>> =
        Loaded(all.filter { it.category == category }.map { it.toSummary() }, DataSource.REMOTE)

    override suspend fun categories(forceRefresh: Boolean): Loaded<List<Category>> =
        Loaded(listOf(Category("Chicken", null, null), Category("Dessert", null, null)), DataSource.REMOTE)

    override suspend fun recipe(id: String, forceRefresh: Boolean): Loaded<Recipe> {
        recipeLoads++
        val found = all.firstOrNull { it.id == id } ?: throw RecipeException(RecipeException.Kind.NOT_FOUND)
        return Loaded(found, DataSource.REMOTE)
    }

    override suspend fun recipeOfTheDay(): Loaded<Recipe> = Loaded(Sample.daily, DataSource.REMOTE)

    override suspend fun saveRecipe(recipe: Recipe) {
        saved.update { list ->
            if (list.any { it.recipe.id == recipe.id }) list else list + SavedRecipe(recipe, null, null, null, null, 1L, 1L)
        }
    }

    override suspend fun saveRecipeById(id: String) = saveRecipe(recipe(id).value)

    override suspend fun saveNote(recipe: Recipe, note: RecipeNote) {
        saved.update { list ->
            val item = SavedRecipe(recipe, note.rating, note.comment, note.prepMinutes, note.photoPath, 1L, 2L)
            if (list.any { it.recipe.id == recipe.id }) list.map { if (it.recipe.id == recipe.id) item else it } else list + item
        }
    }

    override suspend fun removeSaved(id: String): SavedRecipe? {
        val removed = saved.value.firstOrNull { it.recipe.id == id }
        saved.update { list -> list.filterNot { it.recipe.id == id } }
        return removed
    }

    override suspend fun restoreSaved(saved: SavedRecipe) {
        this.saved.update { list -> if (list.any { it.recipe.id == saved.recipe.id }) list else list + saved }
    }

    override suspend fun purgeExpiredCache() = Unit
}

class FakePreferences : UserPreferencesRepository {
    private val state = MutableStateFlow(UserPreferences())
    override val preferences: Flow<UserPreferences> = state
    override suspend fun setLastSearch(query: String, category: String?) = state.update { it.copy(lastQuery = query, lastCategory = category) }
    override suspend fun setReminderEnabled(enabled: Boolean) = state.update { it.copy(reminderEnabled = enabled) }
    override suspend fun setDailyRecipe(recipeId: String, epochDay: Long) =
        state.update { it.copy(dailyRecipeId = recipeId, dailyRecipeEpochDay = epochDay) }
}

class FakePhotos : PhotoStorage {
    override suspend fun importToTemp(source: Uri): String = "/data/local/tmp/fake-photo.jpg"
    override suspend fun commit(tempPath: String, recipeId: String): String = tempPath
    override suspend fun delete(path: String?) = Unit
    override suspend fun deleteTemp(path: String?) = Unit
    override suspend fun deleteOrphans(referencedPaths: Set<String>) = Unit
}

class FakeScheduler : ReminderScheduler {
    @Volatile var enabled = false
    override fun enable() { enabled = true }
    override fun disable() { enabled = false }
}

class FakeContainer(
    override val recipeRepository: FakeRepository = FakeRepository(),
    override val userPreferences: UserPreferencesRepository = FakePreferences(),
    override val photoStorage: PhotoStorage = FakePhotos(),
    override val reminderScheduler: FakeScheduler = FakeScheduler(),
) : AppContainer {

    companion object {
        /**
         * Troca o container da Application que ja esta rodando no aparelho (o runner usa a
         * `CadernoApp` real). `container` e `lateinit` com setter privado, entao usamos
         * reflexao: so existe para testes e evita mexer no codigo de producao.
         */
        fun install(container: AppContainer) {
            val app = ApplicationProvider.getApplicationContext<CadernoApp>()
            val field = CadernoApp::class.java.getDeclaredField("container")
            field.isAccessible = true
            field.set(app, container)
        }
    }
}
