package com.cadernoreceitas.data.repository

import com.cadernoreceitas.domain.model.Category
import com.cadernoreceitas.domain.model.Loaded
import com.cadernoreceitas.domain.model.Recipe
import com.cadernoreceitas.domain.model.RecipeNote
import com.cadernoreceitas.domain.model.RecipeSummary
import com.cadernoreceitas.domain.model.SavedRecipe
import kotlinx.coroutines.flow.Flow

/**
 * Unica porta de entrada de dados para a UI. Esconde de onde a informacao vem
 * (API, cache Room, DataStore) e permite trocar a implementacao por uma falsa nos testes.
 *
 * Convencao: leituras remotas devolvem [Loaded] (valor + origem) e lancam
 * [com.cadernoreceitas.domain.model.RecipeException] quando nao ha nem rede nem cache.
 * Leituras do caderno sao [Flow]s reativos (Room): a UI se atualiza sozinha.
 */
interface RecipeRepository {

    // ---- Caderno (dados 100% locais, reativos) ----
    fun observeSaved(): Flow<List<SavedRecipe>>
    fun observeSavedIds(): Flow<Set<String>>
    fun observeSavedRecipe(id: String): Flow<SavedRecipe?>

    // ---- Consultas (API + cache) ----
    /** [query] vazio devolve as receitas em destaque. */
    suspend fun search(query: String, forceRefresh: Boolean = false): Loaded<List<RecipeSummary>>
    suspend fun recipesByCategory(category: String, forceRefresh: Boolean = false): Loaded<List<RecipeSummary>>
    suspend fun categories(forceRefresh: Boolean = false): Loaded<List<Category>>
    suspend fun recipe(id: String, forceRefresh: Boolean = false): Loaded<Recipe>
    suspend fun recipeOfTheDay(): Loaded<Recipe>

    // ---- Escrita local ----
    /** Idempotente: salvar de novo uma receita ja salva nao altera nada. */
    suspend fun saveRecipe(recipe: Recipe)

    /** Usado pela acao da notificacao: busca a receita (cache/API) e salva. */
    suspend fun saveRecipeById(id: String)

    /** Salva (se preciso) e grava a anotacao pessoal. */
    suspend fun saveNote(recipe: Recipe, note: RecipeNote)

    /** Remove do caderno e devolve o item removido (para o usuario poder desfazer). */
    suspend fun removeSaved(id: String): SavedRecipe?
    suspend fun restoreSaved(saved: SavedRecipe)

    /** Manutencao: apaga cache vencido e fotos orfas. Nunca toca no caderno. */
    suspend fun purgeExpiredCache()
}
