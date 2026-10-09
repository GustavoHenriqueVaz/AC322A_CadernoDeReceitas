package com.cadernoreceitas.data.repository

import androidx.room.withTransaction
import com.cadernoreceitas.data.local.AppDatabase
import com.cadernoreceitas.data.local.entity.ListCacheEntity
import com.cadernoreceitas.data.local.entity.SavedRecipeEntity
import com.cadernoreceitas.data.local.prefs.UserPreferencesRepository
import com.cadernoreceitas.data.mapper.fromCacheJson
import com.cadernoreceitas.data.mapper.toCacheJson
import com.cadernoreceitas.data.mapper.toCategory
import com.cadernoreceitas.data.mapper.toEntity
import com.cadernoreceitas.data.mapper.toRecipe
import com.cadernoreceitas.data.mapper.toSavedRecipe
import com.cadernoreceitas.data.mapper.toSummary
import com.cadernoreceitas.data.photo.PhotoStorage
import com.cadernoreceitas.data.remote.RecipeRemoteDataSource
import com.cadernoreceitas.domain.model.Category
import com.cadernoreceitas.domain.model.DataSource
import com.cadernoreceitas.domain.model.Loaded
import com.cadernoreceitas.domain.model.Recipe
import com.cadernoreceitas.domain.model.RecipeException
import com.cadernoreceitas.domain.model.RecipeNote
import com.cadernoreceitas.domain.model.RecipeSummary
import com.cadernoreceitas.domain.model.SavedRecipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Politica de cache (documentada no README):
 *  - listas (busca/categoria/destaques): validas por [listTtl]
 *  - detalhe de receita: valido por [recipeTtl]
 *  - categorias: validas por [categoriesTtl]
 *  - limpeza: receitas nao salvas e nao vistas ha [purgeAfter] sao apagadas
 * Dentro do TTL o app nem chama a rede; vencido, tenta a rede e, se falhar, usa o cache
 * vencido marcando o resultado como [DataSource.CACHE_STALE] (modo offline).
 */
data class CachePolicy(
    val listTtl: Duration = 6.hours,
    val recipeTtl: Duration = 24.hours,
    val categoriesTtl: Duration = 7.days,
    val purgeAfter: Duration = 30.days,
)

class DefaultRecipeRepository(
    private val remote: RecipeRemoteDataSource,
    private val db: AppDatabase,
    private val preferences: UserPreferencesRepository,
    private val photoStorage: PhotoStorage,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val policy: CachePolicy = CachePolicy(),
) : RecipeRepository {

    private val recipeDao = db.recipeDao()
    private val savedDao = db.savedRecipeDao()
    private val listCacheDao = db.listCacheDao()
    private val categoryDao = db.categoryDao()

    private fun now(): Long = clock.millis()

    private fun isExpired(fetchedAt: Long, ttl: Duration): Boolean =
        now() - fetchedAt >= ttl.inWholeMilliseconds

    // ------------------------------------------------------------------ caderno

    override fun observeSaved(): Flow<List<SavedRecipe>> =
        savedDao.observeAll().map { list -> list.mapNotNull { it.toSavedRecipe() } }

    override fun observeSavedIds(): Flow<Set<String>> =
        savedDao.observeIds().map { it.toSet() }

    override fun observeSavedRecipe(id: String): Flow<SavedRecipe?> =
        savedDao.observeById(id).map { it?.toSavedRecipe() }

    override suspend fun saveRecipe(recipe: Recipe) {
        db.withTransaction {
            recipeDao.upsert(recipe.toEntity(fetchedAt = now()))
            savedDao.insertIfAbsent(newSavedEntity(recipe.id))
        }
    }

    override suspend fun saveRecipeById(id: String) {
        saveRecipe(recipe(id).value)
    }

    override suspend fun saveNote(recipe: Recipe, note: RecipeNote) {
        db.withTransaction {
            recipeDao.upsert(recipe.toEntity(fetchedAt = now()))
            savedDao.insertIfAbsent(newSavedEntity(recipe.id))
            savedDao.updateNote(
                id = recipe.id,
                rating = note.rating,
                comment = note.comment,
                prepMinutes = note.prepMinutes,
                photoPath = note.photoPath,
                now = now(),
            )
        }
    }

    override suspend fun removeSaved(id: String): SavedRecipe? = db.withTransaction {
        val existing = savedDao.getWithRecipe(id)?.toSavedRecipe()
        savedDao.delete(id)
        existing
    }

    override suspend fun restoreSaved(saved: SavedRecipe) {
        db.withTransaction {
            recipeDao.upsert(saved.recipe.toEntity(fetchedAt = now()))
            savedDao.upsert(
                SavedRecipeEntity(
                    recipeId = saved.recipe.id,
                    rating = saved.rating,
                    comment = saved.comment,
                    prepMinutes = saved.prepMinutes,
                    photoPath = saved.photoPath,
                    savedAt = saved.savedAt,
                    updatedAt = saved.updatedAt,
                ),
            )
        }
    }

    private fun newSavedEntity(recipeId: String) = SavedRecipeEntity(
        recipeId = recipeId,
        rating = null,
        comment = null,
        prepMinutes = null,
        photoPath = null,
        savedAt = now(),
        updatedAt = now(),
    )

    // ---------------------------------------------------------------- consultas

    override suspend fun search(query: String, forceRefresh: Boolean): Loaded<List<RecipeSummary>> {
        val normalized = normalizeQuery(query)
        val key = "search:${normalized.lowercase(Locale.ROOT)}"
        return cachedList(
            key = key,
            forceRefresh = forceRefresh,
            fetch = {
                val recipes = remote.search(normalized).mapNotNull { it.toRecipe() }
                val fetchedAt = now()
                db.withTransaction {
                    // A busca ja traz o detalhe completo: guardamos tudo para uso offline.
                    recipeDao.upsertAll(recipes.map { it.toEntity(fetchedAt) })
                }
                recipes.map { it.toSummary() }
            },
            fallback = {
                val local = if (normalized.isEmpty()) {
                    recipeDao.getMostRecent()
                } else {
                    recipeDao.searchByName("%${escapeLike(normalized)}%")
                }
                local.map { it.toSummary() }.takeIf { it.isNotEmpty() }
            },
        )
    }

    override suspend fun recipesByCategory(
        category: String,
        forceRefresh: Boolean,
    ): Loaded<List<RecipeSummary>> {
        val name = category.trim()
        return cachedList(
            key = "category:${name.lowercase(Locale.ROOT)}",
            forceRefresh = forceRefresh,
            fetch = { remote.byCategory(name).mapNotNull { it.toSummary(fallbackCategory = name) } },
            fallback = {
                recipeDao.getByCategory(name).map { it.toSummary() }.takeIf { it.isNotEmpty() }
            },
        )
    }

    /** Estrategia comum das listas: cache valido -> rede -> cache vencido -> erro. */
    private suspend fun cachedList(
        key: String,
        forceRefresh: Boolean,
        fetch: suspend () -> List<RecipeSummary>,
        fallback: suspend () -> List<RecipeSummary>?,
    ): Loaded<List<RecipeSummary>> {
        val cachedEntity = listCacheDao.get(key)
        val cachedList = cachedEntity?.payload?.fromCacheJson()
        if (cachedEntity != null && cachedList != null && !forceRefresh &&
            !isExpired(cachedEntity.fetchedAt, policy.listTtl)
        ) {
            return Loaded(cachedList, DataSource.CACHE_FRESH)
        }
        return try {
            val fresh = fetch()
            listCacheDao.put(ListCacheEntity(key, fresh.toCacheJson(), now()))
            Loaded(fresh, DataSource.REMOTE)
        } catch (e: RecipeException) {
            val stale = cachedList ?: fallback()
            if (stale != null) Loaded(stale, DataSource.CACHE_STALE) else throw e
        }
    }

    override suspend fun categories(forceRefresh: Boolean): Loaded<List<Category>> {
        val cached = categoryDao.getAll()
        val cachedAt = cached.firstOrNull()?.fetchedAt
        if (cached.isNotEmpty() && cachedAt != null && !forceRefresh &&
            !isExpired(cachedAt, policy.categoriesTtl)
        ) {
            return Loaded(cached.map { it.toCategory() }, DataSource.CACHE_FRESH)
        }
        return try {
            val fresh = remote.categories().mapNotNull { it.toCategory() }
            if (fresh.isEmpty()) throw RecipeException(RecipeException.Kind.INVALID_DATA)
            val fetchedAt = now()
            categoryDao.replaceAll(fresh.mapIndexed { index, c -> c.toEntity(index, fetchedAt) })
            Loaded(fresh, DataSource.REMOTE)
        } catch (e: RecipeException) {
            if (cached.isNotEmpty()) Loaded(cached.map { it.toCategory() }, DataSource.CACHE_STALE) else throw e
        }
    }

    override suspend fun recipe(id: String, forceRefresh: Boolean): Loaded<Recipe> {
        val cached = recipeDao.getById(id)
        if (cached != null && !forceRefresh && !isExpired(cached.fetchedAt, policy.recipeTtl)) {
            return Loaded(cached.toRecipe(), DataSource.CACHE_FRESH)
        }
        return try {
            val fresh = remote.lookup(id)?.toRecipe()
                ?: throw RecipeException(RecipeException.Kind.NOT_FOUND)
            recipeDao.upsert(fresh.toEntity(now()))
            Loaded(fresh, DataSource.REMOTE)
        } catch (e: RecipeException) {
            if (cached != null) Loaded(cached.toRecipe(), DataSource.CACHE_STALE) else throw e
        }
    }

    override suspend fun recipeOfTheDay(): Loaded<Recipe> {
        val prefs = preferences.preferences.first()
        val today = LocalDate.now(clock).toEpochDay()
        val savedId = prefs.dailyRecipeId
        val cachedToday = if (prefs.dailyRecipeEpochDay == today && savedId != null) {
            recipeDao.getById(savedId)
        } else {
            null
        }
        if (cachedToday != null) return Loaded(cachedToday.toRecipe(), DataSource.CACHE_FRESH)

        return try {
            val fresh = remote.random()?.toRecipe()
                ?: throw RecipeException(RecipeException.Kind.INVALID_DATA)
            recipeDao.upsert(fresh.toEntity(now()))
            preferences.setDailyRecipe(fresh.id, today)
            Loaded(fresh, DataSource.REMOTE)
        } catch (e: RecipeException) {
            // Sem rede: reaproveita a ultima sugestao conhecida (se houver).
            val last = savedId?.let { recipeDao.getById(it) }
            if (last != null) Loaded(last.toRecipe(), DataSource.CACHE_STALE) else throw e
        }
    }

    // --------------------------------------------------------------- manutencao

    override suspend fun purgeExpiredCache() {
        val cutoff = now() - policy.purgeAfter.inWholeMilliseconds
        recipeDao.purgeOlderThan(cutoff)
        listCacheDao.purgeOlderThan(cutoff)
        val referenced = savedDao.getAllPhotoPaths().mapNotNull { it.photoPath }.toSet()
        photoStorage.deleteOrphans(referenced)
    }

    // ---------------------------------------------------------------- utilitarios

    internal companion object {
        const val MAX_QUERY_LENGTH = 60

        /** Normaliza o texto digitado: sem controles/espacos extras e com tamanho limitado. */
        fun normalizeQuery(raw: String): String =
            // Controles (tab, quebra de linha...) viram espaco: "chicken\tsoup" nao pode virar "chickensoup".
            raw.map { if (it.isISOControl()) ' ' else it }
                .joinToString("")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(MAX_QUERY_LENGTH)
                .trimEnd() // o corte pode deixar um espaco no fim

        /** Escapa curingas do LIKE para que "50%" seja buscado literalmente. */
        fun escapeLike(text: String): String =
            text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
