package com.cadernoreceitas.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * DTOs: refletem o JSON da TheMealDB exatamente como ele chega (nomes `strXxx`).
 * A API mistura `null` e `""` para campos vazios, entao tudo e anulavel com padrao nulo.
 * A traducao para os modelos de dominio fica em `data/mapper`.
 */

@Serializable
data class MealsResponseDto(val meals: List<MealDto>? = null)

@Serializable
data class MealSummariesResponseDto(val meals: List<MealSummaryDto>? = null)

@Serializable
data class CategoriesResponseDto(val categories: List<CategoryDto>? = null)

@Serializable
data class CategoryDto(
    val idCategory: String? = null,
    val strCategory: String? = null,
    val strCategoryThumb: String? = null,
    val strCategoryDescription: String? = null,
)

/** Retorno de `filter.php`: so traz o basico (sem instrucoes nem ingredientes). */
@Serializable
data class MealSummaryDto(
    val idMeal: String? = null,
    val strMeal: String? = null,
    val strMealThumb: String? = null,
    val strArea: String? = null,
    val strCountry: String? = null,
)

@Serializable
data class MealDto(
    val idMeal: String? = null,
    val strMeal: String? = null,
    val strCategory: String? = null,
    val strArea: String? = null,
    val strCountry: String? = null,
    val strInstructions: String? = null,
    val strMealThumb: String? = null,
    val strTags: String? = null,
    val strYoutube: String? = null,
    val strSource: String? = null,
    val strIngredient1: String? = null,
    val strIngredient2: String? = null,
    val strIngredient3: String? = null,
    val strIngredient4: String? = null,
    val strIngredient5: String? = null,
    val strIngredient6: String? = null,
    val strIngredient7: String? = null,
    val strIngredient8: String? = null,
    val strIngredient9: String? = null,
    val strIngredient10: String? = null,
    val strIngredient11: String? = null,
    val strIngredient12: String? = null,
    val strIngredient13: String? = null,
    val strIngredient14: String? = null,
    val strIngredient15: String? = null,
    val strIngredient16: String? = null,
    val strIngredient17: String? = null,
    val strIngredient18: String? = null,
    val strIngredient19: String? = null,
    val strIngredient20: String? = null,
    val strMeasure1: String? = null,
    val strMeasure2: String? = null,
    val strMeasure3: String? = null,
    val strMeasure4: String? = null,
    val strMeasure5: String? = null,
    val strMeasure6: String? = null,
    val strMeasure7: String? = null,
    val strMeasure8: String? = null,
    val strMeasure9: String? = null,
    val strMeasure10: String? = null,
    val strMeasure11: String? = null,
    val strMeasure12: String? = null,
    val strMeasure13: String? = null,
    val strMeasure14: String? = null,
    val strMeasure15: String? = null,
    val strMeasure16: String? = null,
    val strMeasure17: String? = null,
    val strMeasure18: String? = null,
    val strMeasure19: String? = null,
    val strMeasure20: String? = null,
) {
    /** Pares (ingrediente, medida) na ordem original, ainda crus (podem ser nulos/vazios). */
    fun rawIngredients(): List<Pair<String?, String?>> = listOf(
        strIngredient1 to strMeasure1,
        strIngredient2 to strMeasure2,
        strIngredient3 to strMeasure3,
        strIngredient4 to strMeasure4,
        strIngredient5 to strMeasure5,
        strIngredient6 to strMeasure6,
        strIngredient7 to strMeasure7,
        strIngredient8 to strMeasure8,
        strIngredient9 to strMeasure9,
        strIngredient10 to strMeasure10,
        strIngredient11 to strMeasure11,
        strIngredient12 to strMeasure12,
        strIngredient13 to strMeasure13,
        strIngredient14 to strMeasure14,
        strIngredient15 to strMeasure15,
        strIngredient16 to strMeasure16,
        strIngredient17 to strMeasure17,
        strIngredient18 to strMeasure18,
        strIngredient19 to strMeasure19,
        strIngredient20 to strMeasure20,
    )
}
