package com.cadernoreceitas.data.remote

import com.cadernoreceitas.data.remote.dto.CategoryDto
import com.cadernoreceitas.data.remote.dto.MealDto
import com.cadernoreceitas.data.remote.dto.MealSummaryDto
import com.cadernoreceitas.domain.model.RecipeException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Fonte remota de dados. E uma interface para que os testes usem uma implementacao falsa
 * (`FakeRecipeRemoteDataSource`) sem precisar de rede.
 * Todas as falhas chegam ao repositorio como [RecipeException].
 */
interface RecipeRemoteDataSource {
    suspend fun search(query: String): List<MealDto>
    suspend fun lookup(id: String): MealDto?
    suspend fun random(): MealDto?
    suspend fun categories(): List<CategoryDto>
    suspend fun byCategory(category: String): List<MealSummaryDto>
}

class RetrofitRecipeRemoteDataSource(private val api: MealApi) : RecipeRemoteDataSource {

    override suspend fun search(query: String): List<MealDto> =
        call { api.searchMeals(query).meals.orEmpty() }

    override suspend fun lookup(id: String): MealDto? =
        call { api.lookupMeal(id).meals?.firstOrNull() }

    override suspend fun random(): MealDto? =
        call { api.randomMeal().meals?.firstOrNull() }

    override suspend fun categories(): List<CategoryDto> =
        call { api.categories().categories.orEmpty() }

    override suspend fun byCategory(category: String): List<MealSummaryDto> =
        call { api.filterByCategory(category).meals.orEmpty() }

    /**
     * Traduz as excecoes de rede/parse para [RecipeException].
     * [CancellationException] e sempre repassada: engolir o cancelamento quebraria
     * a cooperacao das coroutines (o `viewModelScope` nao conseguiria cancelar a busca).
     */
    private suspend inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        throw RecipeException(RecipeException.Kind.SERVER, e)
    } catch (e: IOException) {
        throw RecipeException(RecipeException.Kind.NETWORK, e)
    } catch (e: SerializationException) {
        throw RecipeException(RecipeException.Kind.INVALID_DATA, e)
    } catch (e: IllegalArgumentException) {
        throw RecipeException(RecipeException.Kind.INVALID_DATA, e)
    }
}
