package com.cadernoreceitas.testutil

import com.cadernoreceitas.data.remote.RecipeRemoteDataSource
import com.cadernoreceitas.data.remote.dto.CategoryDto
import com.cadernoreceitas.data.remote.dto.MealDto
import com.cadernoreceitas.data.remote.dto.MealSummaryDto
import com.cadernoreceitas.domain.model.RecipeException
import kotlinx.coroutines.delay
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fonte remota falsa e programavel (nada de rede).
 *
 *  - Respostas: cada endpoint tem um valor padrao (`searchResponse`, `randomResponse`...) e, para
 *    `search`/`random`, uma fila de respostas em sequencia (`enqueueSearch`/`enqueueRandom`; a fila
 *    tem prioridade e, esvaziada, volta ao padrao). `search` tambem aceita resposta por texto
 *    (`searchByQuery`), `lookup` por id (`lookups`) e `byCategory` por nome (`byCategoryResults`).
 *  - Falha: `failWith(kind)` faz TODAS as chamadas lancarem [RecipeException]; `recover()` desfaz.
 *    A chamada e contada mesmo quando falha.
 *  - Atraso: `delayMillis` suspende cada chamada com `delay` (tempo virtual no `runTest`), para
 *    testar cancelamento.
 *  - Contadores/historico: `searchCalls`, `searchQueries`, `lookupCalls`, `lookupIds`... seguros
 *    entre threads (os workers rodam fora da thread do teste).
 */
class FakeRecipeRemoteDataSource : RecipeRemoteDataSource {

    // ---- respostas
    @Volatile var searchResponse: List<MealDto> = emptyList()
    val searchByQuery: MutableMap<String, List<MealDto>> = java.util.concurrent.ConcurrentHashMap()
    val lookups: MutableMap<String, MealDto> = java.util.concurrent.ConcurrentHashMap()
    @Volatile var randomResponse: MealDto? = null
    @Volatile var categoriesResponse: List<CategoryDto> = emptyList()
    val byCategoryResults: MutableMap<String, List<MealSummaryDto>> = java.util.concurrent.ConcurrentHashMap()

    private val searchQueue = ArrayDeque<List<MealDto>>()
    private val randomQueue = ArrayDeque<MealDto?>()

    fun enqueueSearch(vararg responses: List<MealDto>) {
        synchronized(searchQueue) { responses.forEach { searchQueue.addLast(it) } }
    }

    fun enqueueRandom(vararg responses: MealDto?) {
        synchronized(randomQueue) { responses.forEach { randomQueue.addLast(it) } }
    }

    /** Atalho: `lookup(id)` passa a devolver [meal] (usa o `idMeal` do DTO). */
    fun putLookup(meal: MealDto) {
        lookups[checkNotNull(meal.idMeal)] = meal
    }

    // ---- falha e atraso
    @Volatile var failure: RecipeException? = null
    @Volatile var delayMillis: Long = 0

    fun failWith(kind: RecipeException.Kind, cause: Throwable? = null) {
        failure = RecipeException(kind, cause)
    }

    fun recover() {
        failure = null
    }

    // ---- contadores e historico
    private val searchCounter = AtomicInteger()
    private val lookupCounter = AtomicInteger()
    private val randomCounter = AtomicInteger()
    private val categoriesCounter = AtomicInteger()
    private val byCategoryCounter = AtomicInteger()

    val searchCalls: Int get() = searchCounter.get()
    val lookupCalls: Int get() = lookupCounter.get()
    val randomCalls: Int get() = randomCounter.get()
    val categoriesCalls: Int get() = categoriesCounter.get()
    val byCategoryCalls: Int get() = byCategoryCounter.get()
    val totalCalls: Int get() = searchCalls + lookupCalls + randomCalls + categoriesCalls + byCategoryCalls

    val searchQueries: MutableList<String> = CopyOnWriteArrayList()
    val lookupIds: MutableList<String> = CopyOnWriteArrayList()
    val byCategoryNames: MutableList<String> = CopyOnWriteArrayList()

    fun resetCounters() {
        listOf(searchCounter, lookupCounter, randomCounter, categoriesCounter, byCategoryCounter)
            .forEach { it.set(0) }
        searchQueries.clear()
        lookupIds.clear()
        byCategoryNames.clear()
    }

    // ---- RecipeRemoteDataSource
    override suspend fun search(query: String): List<MealDto> {
        searchCounter.incrementAndGet()
        searchQueries += query
        gate()
        val queued = synchronized(searchQueue) { searchQueue.removeFirstOrNull() }
        return queued ?: searchByQuery[query] ?: searchResponse
    }

    override suspend fun lookup(id: String): MealDto? {
        lookupCounter.incrementAndGet()
        lookupIds += id
        gate()
        return lookups[id]
    }

    override suspend fun random(): MealDto? {
        randomCounter.incrementAndGet()
        gate()
        val hasQueued = synchronized(randomQueue) { randomQueue.isNotEmpty() }
        return if (hasQueued) synchronized(randomQueue) { randomQueue.removeFirst() } else randomResponse
    }

    override suspend fun categories(): List<CategoryDto> {
        categoriesCounter.incrementAndGet()
        gate()
        return categoriesResponse
    }

    override suspend fun byCategory(category: String): List<MealSummaryDto> {
        byCategoryCounter.incrementAndGet()
        byCategoryNames += category
        gate()
        return byCategoryResults[category].orEmpty()
    }

    /** Atraso (cancelavel) e depois a falha configurada, se houver. */
    private suspend fun gate() {
        val wait = delayMillis
        if (wait > 0) delay(wait)
        failure?.let { throw it }
    }
}
