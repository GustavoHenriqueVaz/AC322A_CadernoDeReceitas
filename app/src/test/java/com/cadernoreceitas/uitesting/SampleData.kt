package com.cadernoreceitas.uitesting

import com.cadernoreceitas.domain.model.Category
import com.cadernoreceitas.domain.model.Ingredient
import com.cadernoreceitas.domain.model.Recipe
import com.cadernoreceitas.domain.model.RecipeSummary
import com.cadernoreceitas.domain.model.SavedRecipe

/**
 * Dados de exemplo dos testes de UI. Todas as miniaturas sao `null` de proposito: assim o Coil
 * nunca tenta abrir rede/disco durante os testes (a UI mostra o icone de "sem foto").
 */
object SampleData {

    const val TERIYAKI_ID = "52772"
    const val CRUMBLE_ID = "52893"
    const val ARRABIATA_ID = "52771"

    fun recipe(
        id: String = TERIYAKI_ID,
        name: String = "Teriyaki Chicken Casserole",
        category: String? = "Chicken",
        area: String? = "Japanese",
        instructions: String = "Misture tudo e asse por 35 minutos.",
        tags: List<String> = listOf("Meat", "Casserole"),
        youtubeUrl: String? = null,
        ingredients: List<Ingredient> = listOf(
            Ingredient("soy sauce", "3/4 cup"),
            Ingredient("water", "1/2 cup"),
            Ingredient("salt", null),
        ),
    ) = Recipe(
        id = id,
        name = name,
        category = category,
        area = area,
        instructions = instructions,
        thumbnailUrl = null,
        tags = tags,
        youtubeUrl = youtubeUrl,
        sourceUrl = null,
        ingredients = ingredients,
    )

    fun summary(
        id: String = TERIYAKI_ID,
        name: String = "Teriyaki Chicken Casserole",
        category: String? = "Chicken",
        area: String? = "Japanese",
    ) = RecipeSummary(id = id, name = name, thumbnailUrl = null, category = category, area = area)

    fun category(name: String) = Category(name = name, thumbnailUrl = null, description = null)

    fun saved(
        recipe: Recipe = recipe(),
        rating: Int? = null,
        comment: String? = null,
        prepMinutes: Int? = null,
        photoPath: String? = null,
        savedAt: Long = 1_000L,
    ) = SavedRecipe(
        recipe = recipe,
        rating = rating,
        comment = comment,
        prepMinutes = prepMinutes,
        photoPath = photoPath,
        savedAt = savedAt,
        updatedAt = savedAt,
    )

    val teriyaki: Recipe = recipe()
    val crumble: Recipe = recipe(
        id = CRUMBLE_ID,
        name = "Apple & Blackberry Crumble",
        category = "Dessert",
        area = "British",
        tags = emptyList(),
    )
    val arrabiata: Recipe = recipe(
        id = ARRABIATA_ID,
        name = "Spicy Arrabiata Penne",
        category = "Vegetarian",
        area = "Italian",
        tags = emptyList(),
    )

    /** Destaques da tela inicial (query vazia). */
    val featured: List<RecipeSummary> = listOf(teriyaki.toSummary(), crumble.toSummary())

    val categories: List<Category> = listOf(category("Chicken"), category("Dessert"), category("Seafood"))
}
