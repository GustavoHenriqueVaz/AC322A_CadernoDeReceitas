package com.cadernoreceitas.data.remote.dto

import com.cadernoreceitas.testutil.TestFixtures
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Desserializacao dos JSONs REAIS da TheMealDB (fixtures reduzidas) com a mesma configuracao do
 * `DefaultAppContainer`: `ignoreUnknownKeys` e `coerceInputValues`.
 */
class DtoDeserializationTest {

    private val json = TestFixtures.json

    @Test
    fun `lookup real - todos os campos relevantes chegam, nulos e vazios preservados`() {
        val response = json.decodeFromString<MealsResponseDto>(TestFixtures.resourceText("meal_lookup.json"))

        val meal = response.meals!!.single()
        assertThat(meal.idMeal).isEqualTo("52772")
        assertThat(meal.strMeal).isEqualTo("Teriyaki Chicken Casserole")
        assertThat(meal.strCategory).isEqualTo("Chicken")
        assertThat(meal.strArea).isEqualTo("Japanese")
        assertThat(meal.strCountry).isEqualTo("Japan")
        assertThat(meal.strTags).isEqualTo("Meat,Casserole")
        assertThat(meal.strMealThumb).isEqualTo("https://www.themealdb.com/images/media/meals/wvpsxx1468256321.jpg")
        assertThat(meal.strInstructions).contains("\r\n") // o DTO guarda o texto CRU (o mapper normaliza)
        assertThat(meal.strInstructions).contains("½ cup") // escape ½ decodificado
        assertThat(meal.strIngredient1).isEqualTo("soy sauce")
        assertThat(meal.strMeasure1).isEqualTo("3/4 cup") // "\/" do JSON vira "/"
        assertThat(meal.strIngredient10).isEmpty() // a API manda "" ...
        assertThat(meal.strIngredient16).isNull() // ... e tambem null
        assertThat(meal.strSource).isNull()
        // campos que o DTO nao conhece (strMealAlternate, dateModified...) sao ignorados
    }

    @Test
    fun `rawIngredients devolve as 20 posicoes em ordem`() {
        val meal = TestFixtures.lookupResponse().meals!!.single()

        val raw = meal.rawIngredients()

        assertThat(raw).hasSize(20)
        assertThat(raw[0]).isEqualTo("soy sauce" to "3/4 cup")
        assertThat(raw[8]).isEqualTo("brown rice" to "3 cups")
        assertThat(raw[9]).isEqualTo("" to "")
        assertThat(raw[19]).isEqualTo(null to null)
    }

    @Test
    fun `busca real - varias receitas, com strArea nulo e strCountry preenchido`() {
        val response = json.decodeFromString<MealsResponseDto>(TestFixtures.resourceText("meals_search.json"))

        val meals = response.meals!!
        assertThat(meals.map { it.strMeal }).containsExactly("Satee", "Kumpir", "Flan").inOrder()
        assertThat(meals[0].strArea).isNull()
        assertThat(meals[0].strCountry).isEqualTo("Aruba")
        assertThat(meals[0].strYoutube).isEmpty()
        assertThat(meals[1].strSource).isEqualTo("http://www.turkeysforlife.com/2013/10/firinda-kumpir-turkish-street-food.html")
        assertThat(meals[1].strMeasure4).isEqualTo(" ") // medida "so espaco" como na API real
        assertThat(meals[2].strMeasure1).isEqualTo("100g ")
    }

    @Test
    fun `random real desserializa como MealsResponseDto`() {
        val meal = TestFixtures.randomResponse().meals!!.single()

        assertThat(meal.idMeal).isEqualTo("53318")
        assertThat(meal.strMeal).isEqualTo("Torta de fiambre")
        assertThat(meal.strTags).isNull()
    }

    @Test
    fun `categorias reais`() {
        val response = json.decodeFromString<CategoriesResponseDto>(TestFixtures.resourceText("categories.json"))

        val categories = response.categories!!
        assertThat(categories).hasSize(3)
        assertThat(categories[0].idCategory).isEqualTo("1")
        assertThat(categories[0].strCategory).isEqualTo("Beef")
        assertThat(categories[0].strCategoryThumb).isEqualTo("https://www.themealdb.com/images/category/beef.png")
        assertThat(categories[0].strCategoryDescription).contains("prehistoric times.[1]")
    }

    @Test
    fun `filter real - resumos com a chave strMeal antes do id`() {
        val response = json.decodeFromString<MealSummariesResponseDto>(TestFixtures.resourceText("filter.json"))

        val meals = response.meals!!
        assertThat(meals).hasSize(3)
        assertThat(meals[0].idMeal).isEqualTo("53483")
        assertThat(meals[0].strArea).isNull()
        assertThat(meals[0].strCountry).isEqualTo("Brazil")
        assertThat(meals[1].strMeal).isEqualTo("Amok Trey – Cambodian Fish Curry")
        assertThat(meals[2].strArea).isEqualTo("Spanish")
    }

    @Test
    fun `meals null (nada encontrado) desserializa para lista nula`() {
        val response = json.decodeFromString<MealsResponseDto>(TestFixtures.resourceText("meals_null.json"))

        assertThat(response.meals).isNull()
        assertThat(json.decodeFromString<MealSummariesResponseDto>("{\"meals\":null}").meals).isNull()
    }

    @Test
    fun `corpo vazio ou sem a chave principal desserializa com valores padrao`() {
        assertThat(json.decodeFromString<MealsResponseDto>("{}").meals).isNull()
        assertThat(json.decodeFromString<CategoriesResponseDto>("{}").categories).isNull()
        assertThat(json.decodeFromString<MealsResponseDto>("{\"meals\":[]}").meals).isEmpty()
    }

    @Test
    fun `campos novos que a API venha a ganhar sao ignorados`() {
        val text = "{\"meals\":[{\"idMeal\":\"1\",\"strMeal\":\"Bolo\",\"strNovidade\":\"x\",\"extra\":{\"a\":[1,2]}}],\"outra\":1}"

        val meal = json.decodeFromString<MealsResponseDto>(text).meals!!.single()

        assertThat(meal.idMeal).isEqualTo("1")
        assertThat(meal.strMeal).isEqualTo("Bolo")
    }

    @Test
    fun `sem ignoreUnknownKeys os campos desconhecidos da API real quebrariam (por isso a config existe)`() {
        val strict = Json // sem ignoreUnknownKeys
        assertThrows(SerializationException::class.java) {
            strict.decodeFromString<MealsResponseDto>(TestFixtures.resourceText("meal_lookup.json"))
        }
    }

    @Test
    fun `coerceInputValues - null em campo nao anulavel nao ocorre porque todos os campos sao anulaveis`() {
        // todos os campos dos DTOs sao `String?`: "strMeal": null e valido
        val meal = json.decodeFromString<MealsResponseDto>("{\"meals\":[{\"idMeal\":null,\"strMeal\":null}]}")
            .meals!!.single()

        assertThat(meal.idMeal).isNull()
        assertThat(meal.strMeal).isNull()
    }

    @Test
    fun `meals como texto (resposta Invalid ID da API) falha com SerializationException`() {
        // lookup.php?i=abc devolve {"meals":"Invalid ID"}: o app converte isso em INVALID_DATA
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<MealsResponseDto>(TestFixtures.resourceText("meals_invalid_id.json"))
        }
    }

    @Test
    fun `JSON malformado ou HTML falham com SerializationException`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<MealsResponseDto>("<html><body>502 Bad Gateway</body></html>")
        }
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<MealsResponseDto>("{\"meals\":[{\"idMeal\":\"1\"")
        }
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<MealsResponseDto>("")
        }
    }
}
