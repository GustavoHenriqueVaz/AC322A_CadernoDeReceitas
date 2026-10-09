package com.cadernoreceitas.uitesting

import com.cadernoreceitas.data.repository.RecipeRepository
import com.cadernoreceitas.domain.model.Category
import com.cadernoreceitas.domain.model.DataSource
import com.cadernoreceitas.domain.model.Loaded
import com.cadernoreceitas.domain.model.Recipe
import com.cadernoreceitas.domain.model.RecipeException
import com.cadernoreceitas.domain.model.RecipeNote
import com.cadernoreceitas.domain.model.RecipeSummary
import com.cadernoreceitas.domain.model.SavedRecipe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * [RecipeRepository] falso, programavel e que registra tudo o que recebe.
 *
 *  - Caderno: [savedFlow] e a "tabela" (reativa). `observeSaved/observeSavedIds/observeSavedRecipe`
 *    derivam dela, entao a UI reage as escritas feitas pelo proprio fake (e pelo teste).
 *  - Consultas: respostas por texto ([searchResults]), por categoria ([categoryResults]) e por id
 *    ([recipes]); query vazia devolve [featured]. [source] define a origem (offline = CACHE_STALE).
 *  - Falhas: cada operacao tem um campo `...Failure` (qualquer [Throwable]); `null` desliga.
 *  - Atrasos: `...DelayMillis` usam `delay` (tempo virtual dentro de `runTest`), o que permite
 *    testar cancelamento: [cancelledSearches] registra as buscas encerradas por cancelamento.
 *  - Historico: listas/contadores seguros entre threads (o `CadernoApp` chama o repositorio
 *    tambem fora da thread principal).
 */
class FakeRecipeRepository(initialSaved: List<SavedRecipe> = emptyList()) : RecipeRepository {

    data class SearchCall(val query: String, val forceRefresh: Boolean)
    data class CategoryCall(val category: String, val forceRefresh: Boolean)
    data class RecipeCall(val id: String, val forceRefresh: Boolean)
    data class SaveNoteCall(val recipe: Recipe, val note: RecipeNote)

    // ------------------------------------------------------------------ caderno
    val savedFlow = MutableStateFlow(initialSaved)

    /** Se definido, `observeSaved()` falha ao ser coletado (simula erro de leitura do banco). */
    @Volatile var observeSavedError: Throwable? = null

    // ------------------------------------------------------------------ respostas
    @Volatile var featured: List<RecipeSummary> = emptyList()
    val searchResults: MutableMap<String, List<RecipeSummary>> = ConcurrentHashMap()
    val categoryResults: MutableMap<String, List<RecipeSummary>> = ConcurrentHashMap()
    @Volatile var categoryList: List<Category> = emptyList()
    val recipes: MutableMap<String, Recipe> = ConcurrentHashMap()
    @Volatile var dailyRecipe: Recipe? = null

    /** Origem devolvida por `search` e `recipesByCategory`. */
    @Volatile var source: DataSource = DataSource.REMOTE

    /** Origem devolvida por `recipe(id)`. */
    @Volatile var recipeSource: DataSource = DataSource.REMOTE

    // ------------------------------------------------------------------ falhas e atrasos
    @Volatile var searchFailure: Throwable? = null
    @Volatile var categoriesFailure: Throwable? = null
    @Volatile var recipeFailure: Throwable? = null
    @Volatile var dailyFailure: Throwable? = null
    @Volatile var saveRecipeFailure: Throwable? = null
    @Volatile var saveNoteFailure: Throwable? = null
    @Volatile var removeFailure: Throwable? = null
    @Volatile var restoreFailure: Throwable? = null

    @Volatile var searchDelayMillis: Long = 0
    val searchDelayByQuery: MutableMap<String, Long> = ConcurrentHashMap()
    @Volatile var categoriesDelayMillis: Long = 0
    @Volatile var recipeDelayMillis: Long = 0
    @Volatile var dailyDelayMillis: Long = 0
    @Volatile var saveNoteDelayMillis: Long = 0

    // ------------------------------------------------------------------ historico
    val searchCalls: MutableList<SearchCall> = CopyOnWriteArrayList()
    val categoryCalls: MutableList<CategoryCall> = CopyOnWriteArrayList()
    val categoriesCalls: MutableList<Boolean> = CopyOnWriteArrayList()
    val recipeCalls: MutableList<RecipeCall> = CopyOnWriteArrayList()
    val dailyCalls = AtomicInteger()
    val saveRecipeCalls: MutableList<Recipe> = CopyOnWriteArrayList()
    val saveNoteCalls: MutableList<SaveNoteCall> = CopyOnWriteArrayList()
    val removeCalls: MutableList<String> = CopyOnWriteArrayList()
    val restoreCalls: MutableList<SavedRecipe> = CopyOnWriteArrayList()
    val purgeCalls = AtomicInteger()

    /** Buscas que terminaram normalmente / que foram canceladas no meio (ordem de ocorrencia). */
    val completedSearches: MutableList<String> = CopyOnWriteArrayList()
    val cancelledSearches: MutableList<String> = CopyOnWriteArrayList()

    private val clock = AtomicLong(1_000L)
    private fun tick() = clock.incrementAndGet()

    /** Preenche destaques, categorias, receitas e receita do dia com os dados de [SampleData]. */
    fun seedSample(): FakeRecipeRepository = apply {
        featured = SampleData.featured
        categoryList = SampleData.categories
        listOf(SampleData.teriyaki, SampleData.crumble, SampleData.arrabiata).forEach { recipes[it.id] = it }
        dailyRecipe = SampleData.arrabiata
    }

    // ------------------------------------------------------------------ caderno (leitura)
    override fun observeSaved(): Flow<List<SavedRecipe>> = flow {
        observeSavedError?.let { throw it }
        emitAll(savedFlow)
    }

    override fun observeSavedIds(): Flow<Set<String>> =
        savedFlow.map { list -> list.map { it.recipe.id }.toSet() }

    override fun observeSavedRecipe(id: String): Flow<SavedRecipe?> =
        savedFlow.map { list -> list.firstOrNull { it.recipe.id == id } }

    // ------------------------------------------------------------------ consultas
    override suspend fun search(query: String, forceRefresh: Boolean): Loaded<List<RecipeSummary>> {
        searchCalls += SearchCall(query, forceRefresh)
        try {
            val wait = searchDelayByQuery[query] ?: searchDelayMillis
            if (wait > 0) delay(wait)
            searchFailure?.let { throw it }
            val list = if (query.isEmpty()) featured else searchResults[query].orEmpty()
            completedSearches += query
            return Loaded(list, source)
        } catch (e: CancellationException) {
            cancelledSearches += query
            throw e
        }
    }

    override suspend fun recipesByCategory(category: String, forceRefresh: Boolean): Loaded<List<RecipeSummary>> {
        categoryCalls += CategoryCall(category, forceRefresh)
        if (searchDelayMillis > 0) delay(searchDelayMillis)
        searchFailure?.let { throw it }
        return Loaded(categoryResults[category].orEmpty(), source)
    }

    override suspend fun categories(forceRefresh: Boolean): Loaded<List<Category>> {
        categoriesCalls += forceRefresh
        if (categoriesDelayMillis > 0) delay(categoriesDelayMillis)
        categoriesFailure?.let { throw it }
        return Loaded(categoryList, DataSource.REMOTE)
    }

    override suspend fun recipe(id: String, forceRefresh: Boolean): Loaded<Recipe> {
        recipeCalls += RecipeCall(id, forceRefresh)
        if (recipeDelayMillis > 0) delay(recipeDelayMillis)
        recipeFailure?.let { throw it }
        val found = recipes[id] ?: throw RecipeException(RecipeException.Kind.NOT_FOUND)
        return Loaded(found, recipeSource)
    }

    override suspend fun recipeOfTheDay(): Loaded<Recipe> {
        dailyCalls.incrementAndGet()
        if (dailyDelayMillis > 0) delay(dailyDelayMillis)
        dailyFailure?.let { throw it }
        val found = dailyRecipe ?: throw RecipeException(RecipeException.Kind.NETWORK)
        return Loaded(found, DataSource.REMOTE)
    }

    // ------------------------------------------------------------------ escrita local
    override suspend fun saveRecipe(recipe: Recipe) {
        saveRecipeCalls += recipe
        saveRecipeFailure?.let { throw it }
        savedFlow.update { list ->
            if (list.any { it.recipe.id == recipe.id }) {
                list // idempotente, como o repositorio de verdade
            } else {
                val now = tick()
                list + SavedRecipe(recipe, null, null, null, null, savedAt = now, updatedAt = now)
            }
        }
    }

    override suspend fun saveRecipeById(id: String) {
        val found = recipes[id] ?: throw RecipeException(RecipeException.Kind.NOT_FOUND)
        saveRecipe(found)
    }

    override suspend fun saveNote(recipe: Recipe, note: RecipeNote) {
        saveNoteCalls += SaveNoteCall(recipe, note)
        if (saveNoteDelayMillis > 0) delay(saveNoteDelayMillis)
        saveNoteFailure?.let { throw it }
        savedFlow.update { list ->
            val existing = list.firstOrNull { it.recipe.id == recipe.id }
            val updated = SavedRecipe(
                recipe = recipe,
                rating = note.rating,
                comment = note.comment,
                prepMinutes = note.prepMinutes,
                photoPath = note.photoPath,
                savedAt = existing?.savedAt ?: tick(),
                updatedAt = tick(),
            )
            if (existing == null) list + updated else list.map { if (it.recipe.id == recipe.id) updated else it }
        }
    }

    override suspend fun removeSaved(id: String): SavedRecipe? {
        removeCalls += id
        removeFailure?.let { throw it }
        var removed: SavedRecipe? = null
        savedFlow.update { list ->
            removed = list.firstOrNull { it.recipe.id == id }
            list.filterNot { it.recipe.id == id }
        }
        return removed
    }

    override suspend fun restoreSaved(saved: SavedRecipe) {
        restoreCalls += saved
        restoreFailure?.let { throw it }
        savedFlow.update { list -> if (list.any { it.recipe.id == saved.recipe.id }) list else list + saved }
    }

    override suspend fun purgeExpiredCache() {
        purgeCalls.incrementAndGet()
    }
}
