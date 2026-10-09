package com.cadernoreceitas.data.remote

import com.cadernoreceitas.data.remote.dto.CategoriesResponseDto
import com.cadernoreceitas.data.remote.dto.MealSummariesResponseDto
import com.cadernoreceitas.data.remote.dto.MealsResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Contrato HTTP da TheMealDB (https://www.themealdb.com/api.php).
 * A chave de API ja faz parte da URL base (ver `BuildConfig.MEALDB_BASE_URL`).
 * As funcoes `suspend` sao cancelaveis: se a coroutine for cancelada, a chamada HTTP e abortada.
 */
interface MealApi {

    /** Busca por nome. Com `s=""` a API devolve uma lista de receitas em destaque. */
    @GET("search.php")
    suspend fun searchMeals(@Query("s") query: String): MealsResponseDto

    @GET("lookup.php")
    suspend fun lookupMeal(@Query("i") id: String): MealsResponseDto

    @GET("random.php")
    suspend fun randomMeal(): MealsResponseDto

    @GET("categories.php")
    suspend fun categories(): CategoriesResponseDto

    @GET("filter.php")
    suspend fun filterByCategory(@Query("c") category: String): MealSummariesResponseDto
}
