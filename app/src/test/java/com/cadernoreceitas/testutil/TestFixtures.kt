package com.cadernoreceitas.testutil

import com.cadernoreceitas.data.remote.dto.CategoriesResponseDto
import com.cadernoreceitas.data.remote.dto.CategoryDto
import com.cadernoreceitas.data.remote.dto.MealDto
import com.cadernoreceitas.data.remote.dto.MealSummariesResponseDto
import com.cadernoreceitas.data.remote.dto.MealSummaryDto
import com.cadernoreceitas.data.remote.dto.MealsResponseDto
import com.cadernoreceitas.domain.model.Category
import com.cadernoreceitas.domain.model.Ingredient
import com.cadernoreceitas.domain.model.Recipe
import com.cadernoreceitas.domain.model.RecipeSummary
import com.cadernoreceitas.domain.model.SavedRecipe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Dados de exemplo compartilhados pelos testes.
 *  - `recipe/summary/category/savedRecipe`: objetos de DOMINIO prontos (parametros com padrao).
 *  - `mealDto/mealSummaryDto/categoryDto`: DTOs de REDE (o formato cru da TheMealDB).
 *  - `resourceText/...Fixture`: JSONs reais (reduzidos) de `src/test/resources/fixtures`.
 */
object TestFixtures {

    /** Mesma configuracao do `DefaultAppContainer` (a API pode ganhar campos novos). */
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // ------------------------------------------------------------------ dominio

    fun recipe(
        id: String = "52772",
        name: String = "Teriyaki Chicken Casserole",
        category: String? = "Chicken",
        area: String? = "Japanese",
        instructions: String = "Mix everything and bake for 35 minutes.",
        thumbnailUrl: String? = "https://www.themealdb.com/images/media/meals/$id.jpg",
        tags: List<String> = listOf("Meat", "Casserole"),
        youtubeUrl: String? = null,
        sourceUrl: String? = null,
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
        thumbnailUrl = thumbnailUrl,
        tags = tags,
        youtubeUrl = youtubeUrl,
        sourceUrl = sourceUrl,
        ingredients = ingredients,
    )

    fun summary(
        id: String = "52772",
        name: String = "Teriyaki Chicken Casserole",
        thumbnailUrl: String? = "https://www.themealdb.com/images/media/meals/$id.jpg",
        category: String? = "Chicken",
        area: String? = "Japanese",
    ) = RecipeSummary(id, name, thumbnailUrl, category, area)

    fun category(
        name: String = "Chicken",
        thumbnailUrl: String? = "https://www.themealdb.com/images/category/chicken.png",
        description: String? = "Chicken is a type of domesticated fowl.",
    ) = Category(name, thumbnailUrl, description)

    fun savedRecipe(
        recipe: Recipe = recipe(),
        rating: Int? = null,
        comment: String? = null,
        prepMinutes: Int? = null,
        photoPath: String? = null,
        savedAt: Long = 1_000L,
        updatedAt: Long = savedAt,
    ) = SavedRecipe(recipe, rating, comment, prepMinutes, photoPath, savedAt, updatedAt)

    // --------------------------------------------------------------------- DTOs

    /**
     * Monta um [MealDto] como a API o entrega: ate 20 pares (ingrediente, medida); as posicoes
     * que sobram ficam `null` (a API tambem manda `""`, passe `"" to " "` para simular).
     */
    fun mealDto(
        id: String? = "52772",
        name: String? = "Teriyaki Chicken Casserole",
        category: String? = "Chicken",
        area: String? = "Japanese",
        country: String? = null,
        instructions: String? = "Mix everything and bake for 35 minutes.",
        thumbnail: String? = "https://www.themealdb.com/images/media/meals/wvpsxx1468256321.jpg",
        tags: String? = null,
        youtube: String? = null,
        source: String? = null,
        ingredients: List<Pair<String?, String?>> = listOf("soy sauce" to "3/4 cup", "water" to "1/2 cup"),
    ): MealDto {
        require(ingredients.size <= 20) { "A API so tem 20 posicoes de ingrediente" }
        val element = buildJsonObject {
            put("idMeal", id)
            put("strMeal", name)
            put("strCategory", category)
            put("strArea", area)
            put("strCountry", country)
            put("strInstructions", instructions)
            put("strMealThumb", thumbnail)
            put("strTags", tags)
            put("strYoutube", youtube)
            put("strSource", source)
            for (slot in 1..20) {
                val pair = ingredients.getOrNull(slot - 1)
                put("strIngredient$slot", pair?.first)
                put("strMeasure$slot", pair?.second)
            }
        }
        return json.decodeFromJsonElement(MealDto.serializer(), element)
    }

    fun mealSummaryDto(
        id: String? = "53483",
        name: String? = "Acaraje",
        thumbnail: String? = "https://www.themealdb.com/images/media/meals/acaraje.jpg",
        area: String? = null,
        country: String? = "Brazil",
    ) = MealSummaryDto(
        idMeal = id,
        strMeal = name,
        strMealThumb = thumbnail,
        strArea = area,
        strCountry = country,
    )

    fun categoryDto(
        id: String? = "2",
        name: String? = "Chicken",
        thumbnail: String? = "https://www.themealdb.com/images/category/chicken.png",
        description: String? = "Chicken is a type of domesticated fowl.",
    ) = CategoryDto(id, name, thumbnail, description)

    // ----------------------------------------------------------- fixtures reais

    fun resourceText(name: String): String {
        val stream = checkNotNull(TestFixtures::class.java.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Fixture nao encontrada: fixtures/$name"
        }
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** `lookup.php?i=52772` (Teriyaki Chicken Casserole). */
    fun lookupResponse(): MealsResponseDto =
        json.decodeFromString(MealsResponseDto.serializer(), resourceText("meal_lookup.json"))

    /** `search.php?s=` reduzido: Satee (sem strArea), Kumpir (fonte http) e Flan. */
    fun searchResponse(): MealsResponseDto =
        json.decodeFromString(MealsResponseDto.serializer(), resourceText("meals_search.json"))

    fun randomResponse(): MealsResponseDto =
        json.decodeFromString(MealsResponseDto.serializer(), resourceText("random.json"))

    fun categoriesResponse(): CategoriesResponseDto =
        json.decodeFromString(CategoriesResponseDto.serializer(), resourceText("categories.json"))

    fun filterResponse(): MealSummariesResponseDto =
        json.decodeFromString(MealSummariesResponseDto.serializer(), resourceText("filter.json"))
}
