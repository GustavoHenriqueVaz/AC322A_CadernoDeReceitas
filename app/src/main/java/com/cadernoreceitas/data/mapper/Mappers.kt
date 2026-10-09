package com.cadernoreceitas.data.mapper

import com.cadernoreceitas.data.local.entity.CategoryEntity
import com.cadernoreceitas.data.local.entity.RecipeEntity
import com.cadernoreceitas.data.local.entity.SavedWithRecipe
import com.cadernoreceitas.data.remote.dto.CategoryDto
import com.cadernoreceitas.data.remote.dto.MealDto
import com.cadernoreceitas.data.remote.dto.MealSummaryDto
import com.cadernoreceitas.domain.model.Category
import com.cadernoreceitas.domain.model.Ingredient
import com.cadernoreceitas.domain.model.Recipe
import com.cadernoreceitas.domain.model.RecipeSummary
import com.cadernoreceitas.domain.model.SavedRecipe
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/*
 * Mapeamentos entre as camadas:
 *   DTO (rede)  ->  Recipe / RecipeSummary / Category (dominio)  <->  Entity (Room)
 * Os modelos de UI sao derivados do dominio dentro de cada ViewModel.
 */

private val cacheJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class IngredientJson(val name: String, val measure: String? = null)

@Serializable
data class SummaryJson(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val category: String? = null,
    val area: String? = null,
)

// ---------------------------------------------------------------- DTO -> dominio

/** Devolve `null` quando o DTO nao tem o minimo necessario (id e nome). */
fun MealDto.toRecipe(): Recipe? {
    val recipeId = idMeal.cleanOrNull() ?: return null
    val recipeName = strMeal.cleanOrNull() ?: return null
    return Recipe(
        id = recipeId,
        name = recipeName,
        category = strCategory.cleanOrNull(),
        // A API as vezes manda strArea nulo mas strCountry preenchido (e vice-versa).
        area = strArea.cleanOrNull() ?: strCountry.cleanOrNull(),
        instructions = strInstructions.orEmpty().replace("\r\n", "\n").trim(),
        thumbnailUrl = strMealThumb.toHttpsUrlOrNull(),
        tags = strTags.orEmpty().split(',').mapNotNull { it.cleanOrNull() },
        youtubeUrl = strYoutube.toHttpsUrlOrNull(),
        sourceUrl = strSource.toHttpsUrlOrNull(),
        ingredients = rawIngredients().mapNotNull { (name, measure) ->
            name.cleanOrNull()?.let { Ingredient(name = it, measure = measure.cleanOrNull()) }
        },
    )
}

fun MealSummaryDto.toSummary(fallbackCategory: String? = null): RecipeSummary? {
    val recipeId = idMeal.cleanOrNull() ?: return null
    val recipeName = strMeal.cleanOrNull() ?: return null
    return RecipeSummary(
        id = recipeId,
        name = recipeName,
        thumbnailUrl = strMealThumb.toHttpsUrlOrNull(),
        category = fallbackCategory,
        area = strArea.cleanOrNull() ?: strCountry.cleanOrNull(),
    )
}

fun CategoryDto.toCategory(): Category? {
    val categoryName = strCategory.cleanOrNull() ?: return null
    return Category(
        name = categoryName,
        thumbnailUrl = strCategoryThumb.toHttpsUrlOrNull(),
        description = strCategoryDescription.cleanOrNull(),
    )
}

// ------------------------------------------------------------ dominio <-> Entity

fun Recipe.toEntity(fetchedAt: Long) = RecipeEntity(
    id = id,
    name = name,
    category = category,
    area = area,
    instructions = instructions,
    thumbnailUrl = thumbnailUrl,
    tags = tags.joinToString(","),
    youtubeUrl = youtubeUrl,
    sourceUrl = sourceUrl,
    ingredientsJson = cacheJson.encodeToString(
        ListSerializer(IngredientJson.serializer()),
        ingredients.map { IngredientJson(it.name, it.measure) },
    ),
    fetchedAt = fetchedAt,
)

fun RecipeEntity.toRecipe() = Recipe(
    id = id,
    name = name,
    category = category,
    area = area,
    instructions = instructions,
    thumbnailUrl = thumbnailUrl,
    tags = tags.split(',').mapNotNull { it.cleanOrNull() },
    youtubeUrl = youtubeUrl,
    sourceUrl = sourceUrl,
    ingredients = runCatching {
        cacheJson.decodeFromString(ListSerializer(IngredientJson.serializer()), ingredientsJson)
    }.getOrDefault(emptyList()).map { Ingredient(it.name, it.measure) },
)

/** `null` se a receita associada sumiu do cache (nao deveria acontecer). */
fun SavedWithRecipe.toSavedRecipe(): SavedRecipe? {
    val recipeEntity = recipe ?: return null
    return SavedRecipe(
        recipe = recipeEntity.toRecipe(),
        rating = saved.rating,
        comment = saved.comment,
        prepMinutes = saved.prepMinutes,
        photoPath = saved.photoPath,
        savedAt = saved.savedAt,
        updatedAt = saved.updatedAt,
    )
}

fun Category.toEntity(position: Int, fetchedAt: Long) = CategoryEntity(
    name = name,
    thumbnailUrl = thumbnailUrl,
    description = description,
    position = position,
    fetchedAt = fetchedAt,
)

fun CategoryEntity.toCategory() = Category(name, thumbnailUrl, description)

fun RecipeEntity.toSummary() = RecipeSummary(id, name, thumbnailUrl, category, area)

// ------------------------------------------------------------ listas em cache

fun List<RecipeSummary>.toCacheJson(): String = cacheJson.encodeToString(
    ListSerializer(SummaryJson.serializer()),
    map { SummaryJson(it.id, it.name, it.thumbnailUrl, it.category, it.area) },
)

/** Se o JSON estiver corrompido devolve `null` (o repositorio entao busca de novo na rede). */
fun String.fromCacheJson(): List<RecipeSummary>? = runCatching {
    cacheJson.decodeFromString(ListSerializer(SummaryJson.serializer()), this)
        .map { RecipeSummary(it.id, it.name, it.thumbnailUrl, it.category, it.area) }
}.getOrNull()

// ---------------------------------------------------------------- utilitarios

/** Remove espacos e trata string vazia como ausencia de valor (a API mistura "" e null). */
internal fun String?.cleanOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/**
 * O app so aceita HTTPS (trafego em texto puro e bloqueado em `network_security_config`).
 * URLs `http://` sao promovidas para `https://`; qualquer outro esquema e descartado.
 */
internal fun String?.toHttpsUrlOrNull(): String? {
    val value = cleanOrNull() ?: return null
    return when {
        value.startsWith("https://", ignoreCase = true) -> value
        value.startsWith("http://", ignoreCase = true) -> "https://" + value.substring(7)
        else -> null
    }
}
