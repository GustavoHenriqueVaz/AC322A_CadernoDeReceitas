package com.cadernoreceitas.data.mapper

import com.cadernoreceitas.data.local.entity.CategoryEntity
import com.cadernoreceitas.data.local.entity.RecipeEntity
import com.cadernoreceitas.data.local.entity.SavedRecipeEntity
import com.cadernoreceitas.data.local.entity.SavedWithRecipe
import com.cadernoreceitas.data.remote.dto.CategoryDto
import com.cadernoreceitas.domain.model.Category
import com.cadernoreceitas.domain.model.Ingredient
import com.cadernoreceitas.domain.model.RecipeSummary
import com.cadernoreceitas.testutil.TestFixtures
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Mapeamentos DTO -> dominio -> entidade (e de volta). Testes JVM puros. */
class MappersTest {

    // ------------------------------------------------------------------ DTO -> Recipe

    @Test
    fun `receita real da API (lookup) vira Recipe completa`() {
        val dto = TestFixtures.lookupResponse().meals!!.single()

        val recipe = dto.toRecipe()!!

        assertThat(recipe.id).isEqualTo("52772")
        assertThat(recipe.name).isEqualTo("Teriyaki Chicken Casserole")
        assertThat(recipe.category).isEqualTo("Chicken")
        // strArea e strCountry preenchidos: vale a area ("Japanese"), nao o pais ("Japan")
        assertThat(recipe.area).isEqualTo("Japanese")
        assertThat(recipe.tags).containsExactly("Meat", "Casserole").inOrder()
        assertThat(recipe.thumbnailUrl).isEqualTo("https://www.themealdb.com/images/media/meals/wvpsxx1468256321.jpg")
        assertThat(recipe.youtubeUrl).isEqualTo("https://www.youtube.com/watch?v=4aZr5hZXP_s")
        assertThat(recipe.sourceUrl).isNull() // strSource veio null
        assertThat(recipe.ingredients).hasSize(9) // as posicoes 10..20 (vazias/nulas) sumiram
        assertThat(recipe.ingredients.first()).isEqualTo(Ingredient("soy sauce", "3/4 cup"))
        assertThat(recipe.ingredients.last()).isEqualTo(Ingredient("brown rice", "3 cups"))
        // caracteres especiais (° e ½) sobrevivem
        assertThat(recipe.instructions).startsWith("Preheat oven to 350° F.")
        assertThat(recipe.instructions).contains("½ cup water")
        assertThat(recipe.instructions).endsWith("Enjoy!")
    }

    @Test
    fun `instrucoes com CRLF sao normalizadas para LF e aparadas`() {
        val dto = TestFixtures.mealDto(instructions = "  Passo 1\r\nPasso 2\r\n\r\nPasso 3\r\n  ")

        val recipe = dto.toRecipe()!!

        assertThat(recipe.instructions).isEqualTo("Passo 1\nPasso 2\n\nPasso 3")
        assertThat(recipe.instructions).doesNotContain("\r")
    }

    @Test
    fun `instrucoes ausentes viram texto vazio (nunca null)`() {
        assertThat(TestFixtures.mealDto(instructions = null).toRecipe()!!.instructions).isEmpty()
        assertThat(TestFixtures.mealDto(instructions = "   ").toRecipe()!!.instructions).isEmpty()
    }

    @Test
    fun `strArea nulo com strCountry preenchido usa o pais (receita real Satee)`() {
        val satee = TestFixtures.searchResponse().meals!!.first { it.idMeal == "53443" }

        val recipe = satee.toRecipe()!!

        assertThat(satee.strArea).isNull()
        assertThat(recipe.area).isEqualTo("Aruba")
        assertThat(recipe.youtubeUrl).isNull() // strYoutube veio "" na API
        assertThat(recipe.tags).isEmpty() // strTags veio null
        assertThat(recipe.ingredients).hasSize(11)
        assertThat(recipe.ingredients[4]).isEqualTo(Ingredient("Soy Sauce", "1 cup"))
    }

    @Test
    fun `area - prioridade de strArea, depois strCountry, depois null`() {
        assertThat(TestFixtures.mealDto(area = "Turkish", country = "Turkey").toRecipe()!!.area).isEqualTo("Turkish")
        assertThat(TestFixtures.mealDto(area = null, country = "Turkey").toRecipe()!!.area).isEqualTo("Turkey")
        assertThat(TestFixtures.mealDto(area = "", country = "Turkey").toRecipe()!!.area).isEqualTo("Turkey")
        assertThat(TestFixtures.mealDto(area = "  ", country = " Turkey ").toRecipe()!!.area).isEqualTo("Turkey")
        assertThat(TestFixtures.mealDto(area = null, country = null).toRecipe()!!.area).isNull()
        assertThat(TestFixtures.mealDto(area = "", country = "").toRecipe()!!.area).isNull()
    }

    @Test
    fun `categoria vazia vira null`() {
        assertThat(TestFixtures.mealDto(category = "").toRecipe()!!.category).isNull()
        assertThat(TestFixtures.mealDto(category = " Dessert ").toRecipe()!!.category).isEqualTo("Dessert")
    }

    @Test
    fun `ingredientes com nome vazio ou nulo sao descartados mesmo com medida`() {
        val dto = TestFixtures.mealDto(
            ingredients = listOf(
                "Flour" to "2 cups",
                "" to "1 tsp", // nome vazio, medida preenchida: sem nome nao ha ingrediente
                null to "3 g",
                "  " to null,
                "Egg" to "1",
            ),
        )

        val ingredients = dto.toRecipe()!!.ingredients

        assertThat(ingredients).containsExactly(Ingredient("Flour", "2 cups"), Ingredient("Egg", "1")).inOrder()
    }

    @Test
    fun `medida vazia, so espacos ou nula vira null e a medida e aparada`() {
        val dto = TestFixtures.mealDto(
            ingredients = listOf(
                "Salt" to "",
                "Pepper" to " ",
                "Oil" to null,
                "Butter" to "100g ", // a API manda espacos sobrando (ex.: "100g ")
            ),
        )

        val ingredients = dto.toRecipe()!!.ingredients

        assertThat(ingredients.map { it.measure }).containsExactly(null, null, null, "100g").inOrder()
        assertThat(ingredients.map { it.name }).containsExactly("Salt", "Pepper", "Oil", "Butter").inOrder()
    }

    @Test
    fun `ingrediente no meio de posicoes vazias e mantido na ordem original`() {
        // a API as vezes deixa buracos: posicao 2 vazia e a 3 preenchida
        val dto = TestFixtures.mealDto(ingredients = listOf("A" to "1", "" to "", "C" to "3"))

        assertThat(dto.toRecipe()!!.ingredients.map { it.name }).containsExactly("A", "C").inOrder()
    }

    @Test
    fun `as 20 posicoes de ingrediente sao lidas`() {
        val twenty = (1..20).map { "Item $it" to "$it g" }

        val ingredients = TestFixtures.mealDto(ingredients = twenty).toRecipe()!!.ingredients

        assertThat(ingredients).hasSize(20)
        assertThat(ingredients.last()).isEqualTo(Ingredient("Item 20", "20 g"))
    }

    @Test
    fun `tags separadas por virgula sao limpas e as vazias descartadas`() {
        assertThat(TestFixtures.mealDto(tags = "Meat, Casserole ,, ,Spicy").toRecipe()!!.tags)
            .containsExactly("Meat", "Casserole", "Spicy").inOrder()
        assertThat(TestFixtures.mealDto(tags = null).toRecipe()!!.tags).isEmpty()
        assertThat(TestFixtures.mealDto(tags = "").toRecipe()!!.tags).isEmpty()
        assertThat(TestFixtures.mealDto(tags = " , ").toRecipe()!!.tags).isEmpty()
    }

    @Test
    fun `id ou nome vazio, nulo ou so espacos invalidam a receita`() {
        assertThat(TestFixtures.mealDto(id = null).toRecipe()).isNull()
        assertThat(TestFixtures.mealDto(id = "").toRecipe()).isNull()
        assertThat(TestFixtures.mealDto(id = "  ").toRecipe()).isNull()
        assertThat(TestFixtures.mealDto(name = null).toRecipe()).isNull()
        assertThat(TestFixtures.mealDto(name = "").toRecipe()).isNull()
        assertThat(TestFixtures.mealDto(name = "   ").toRecipe()).isNull()
        // controle: com os dois preenchidos funciona
        assertThat(TestFixtures.mealDto(id = " 1 ", name = " Bolo ").toRecipe()).isNotNull()
        assertThat(TestFixtures.mealDto(id = " 1 ", name = " Bolo ").toRecipe()!!.id).isEqualTo("1")
        assertThat(TestFixtures.mealDto(id = " 1 ", name = " Bolo ").toRecipe()!!.name).isEqualTo("Bolo")
    }

    // ------------------------------------------------------------------ URLs (HTTPS)

    @Test
    fun `fonte http da API (Kumpir) e promovida para https`() {
        val kumpir = TestFixtures.searchResponse().meals!!.first { it.idMeal == "52978" }

        assertThat(kumpir.strSource).startsWith("http://") // como veio da API
        assertThat(kumpir.toRecipe()!!.sourceUrl)
            .isEqualTo("https://www.turkeysforlife.com/2013/10/firinda-kumpir-turkish-street-food.html")
    }

    @Test
    fun `http vira https em miniatura, youtube e fonte`() {
        val recipe = TestFixtures.mealDto(
            thumbnail = "http://img.example.com/a.jpg",
            youtube = "http://www.youtube.com/watch?v=abc",
            source = "HTTP://Example.com/Receita",
        ).toRecipe()!!

        assertThat(recipe.thumbnailUrl).isEqualTo("https://img.example.com/a.jpg")
        assertThat(recipe.youtubeUrl).isEqualTo("https://www.youtube.com/watch?v=abc")
        assertThat(recipe.sourceUrl).isEqualTo("https://Example.com/Receita") // so o esquema e normalizado
    }

    @Test
    fun `esquemas invalidos ou perigosos sao descartados`() {
        val invalid = listOf(
            "ftp://example.com/a.jpg",
            "javascript:alert(1)",
            "file:///etc/passwd",
            "data:text/html;base64,AAAA",
            "intent://scan/#Intent;scheme=zxing;end",
            "//example.com/a.jpg", // relativa ao protocolo
            "www.example.com/a.jpg", // sem esquema
            "example.com",
            "",
            "   ",
        )
        for (url in invalid) {
            val recipe = TestFixtures.mealDto(thumbnail = url, youtube = url, source = url).toRecipe()!!
            assertThat(recipe.thumbnailUrl).isNull()
            assertThat(recipe.youtubeUrl).isNull()
            assertThat(recipe.sourceUrl).isNull()
        }
    }

    @Test
    fun `url https valida e mantida e aparada`() {
        val recipe = TestFixtures.mealDto(thumbnail = "  https://www.themealdb.com/a.jpg  ").toRecipe()!!

        assertThat(recipe.thumbnailUrl).isEqualTo("https://www.themealdb.com/a.jpg")
    }

    @Test
    fun `toHttpsUrlOrNull - casos diretos`() {
        assertThat(null.toHttpsUrlOrNull()).isNull()
        assertThat("https://a.com".toHttpsUrlOrNull()).isEqualTo("https://a.com")
        assertThat("HTTPS://a.com".toHttpsUrlOrNull()).isEqualTo("HTTPS://a.com")
        assertThat("http://a.com".toHttpsUrlOrNull()).isEqualTo("https://a.com")
        assertThat("httpx://a.com".toHttpsUrlOrNull()).isNull()
    }

    // ------------------------------------------------------------------ resumos e categorias

    @Test
    fun `resumos reais de filter - pais substitui area nula e a categoria vem do parametro`() {
        val dtos = TestFixtures.filterResponse().meals!!

        val summaries = dtos.map { it.toSummary(fallbackCategory = "Seafood") }

        assertThat(summaries).hasSize(3)
        assertThat(summaries[0]).isEqualTo(
            RecipeSummary(
                id = "53483",
                name = "Acaraje black-eyed pea fritters with shrimp filling",
                thumbnailUrl = "https://www.themealdb.com/images/media/meals/dxs5t71782678369.jpg",
                category = "Seafood",
                area = "Brazil", // strArea null -> strCountry
            ),
        )
        assertThat(summaries[1]!!.name).isEqualTo("Amok Trey – Cambodian Fish Curry")
        assertThat(summaries[2]!!.area).isEqualTo("Spanish") // strArea vence strCountry ("Spain")
    }

    @Test
    fun `resumo sem categoria de apoio fica com category nula`() {
        val summary = TestFixtures.mealSummaryDto().toSummary()!!

        assertThat(summary.category).isNull()
    }

    @Test
    fun `resumo com id ou nome invalido e descartado e miniatura http vira https`() {
        assertThat(TestFixtures.mealSummaryDto(id = "").toSummary()).isNull()
        assertThat(TestFixtures.mealSummaryDto(id = null).toSummary()).isNull()
        assertThat(TestFixtures.mealSummaryDto(name = " ").toSummary()).isNull()
        assertThat(TestFixtures.mealSummaryDto(thumbnail = "http://a.com/x.jpg").toSummary()!!.thumbnailUrl)
            .isEqualTo("https://a.com/x.jpg")
        assertThat(TestFixtures.mealSummaryDto(thumbnail = "ftp://a.com/x.jpg").toSummary()!!.thumbnailUrl).isNull()
    }

    @Test
    fun `categorias reais da API viram Category`() {
        val categories = TestFixtures.categoriesResponse().categories!!.map { it.toCategory() }

        assertThat(categories.map { it!!.name }).containsExactly("Beef", "Chicken", "Dessert").inOrder()
        assertThat(categories[0]!!.thumbnailUrl).isEqualTo("https://www.themealdb.com/images/category/beef.png")
        assertThat(categories[0]!!.description).startsWith("Beef is the culinary name")
    }

    @Test
    fun `categoria sem nome e descartada e campos vazios viram null`() {
        assertThat(CategoryDto(strCategory = null).toCategory()).isNull()
        assertThat(CategoryDto(strCategory = "  ").toCategory()).isNull()

        val category = CategoryDto(
            strCategory = " Vegan ",
            strCategoryThumb = "http://x.com/v.png",
            strCategoryDescription = "",
        ).toCategory()!!

        assertThat(category).isEqualTo(Category("Vegan", "https://x.com/v.png", null))
    }

    // ------------------------------------------------------------------ dominio <-> entidade

    @Test
    fun `Recipe vai para a entidade e volta identica`() {
        val recipe = TestFixtures.recipe(
            tags = listOf("Meat", "Casserole"),
            youtubeUrl = "https://www.youtube.com/watch?v=4aZr5hZXP_s",
            sourceUrl = "https://example.com/r",
            ingredients = listOf(
                Ingredient("soy sauce", "3/4 cup"),
                Ingredient("salt", null),
                Ingredient("crème fraîche \"extra\"", "1\\2 cup\nfresh"), // acentos, aspas, barra e quebra
            ),
        )

        val entity = recipe.toEntity(fetchedAt = 1234L)

        assertThat(entity.fetchedAt).isEqualTo(1234L)
        assertThat(entity.tags).isEqualTo("Meat,Casserole")
        assertThat(entity.toRecipe()).isEqualTo(recipe)
    }

    @Test
    fun `Recipe com campos opcionais nulos tambem faz a viagem de ida e volta`() {
        val recipe = TestFixtures.recipe(
            category = null,
            area = null,
            thumbnailUrl = null,
            tags = emptyList(),
            ingredients = emptyList(),
        )

        assertThat(recipe.toEntity(1L).toRecipe()).isEqualTo(recipe)
    }

    @Test
    fun `receita real passa por entidade sem perder dados`() {
        val real = TestFixtures.lookupResponse().meals!!.single().toRecipe()!!

        assertThat(real.toEntity(99L).toRecipe()).isEqualTo(real)
    }

    @Test
    fun `JSON de ingredientes corrompido vira lista vazia, sem excecao`() {
        val base = TestFixtures.recipe().toEntity(1L)
        val corrupted = listOf(
            "",
            "not json",
            "{\"name\":\"x\"}", // objeto no lugar de lista
            "[{\"measure\":\"1 cup\"}]", // falta o campo obrigatorio `name`
            "[{\"name\":",
            "null",
        )
        for (json in corrupted) {
            val recipe = base.copy(ingredientsJson = json).toRecipe()

            assertThat(recipe.ingredients).isEmpty()
            assertThat(recipe.name).isEqualTo(base.name) // o resto da receita continua legivel
        }
    }

    @Test
    fun `JSON de ingredientes com chaves desconhecidas e tolerado`() {
        val entity = TestFixtures.recipe().toEntity(1L)
            .copy(ingredientsJson = "[{\"name\":\"Salt\",\"measure\":\"1 tsp\",\"novoCampo\":123}]")

        assertThat(entity.toRecipe().ingredients).containsExactly(Ingredient("Salt", "1 tsp"))
    }

    @Test
    fun `tags da entidade com virgulas soltas sao limpas`() {
        val entity = TestFixtures.recipe().toEntity(1L).copy(tags = ",Meat, ,Casserole,")

        assertThat(entity.toRecipe().tags).containsExactly("Meat", "Casserole").inOrder()
        assertThat(entity.copy(tags = "").toRecipe().tags).isEmpty()
    }

    @Test
    fun `SavedWithRecipe vira SavedRecipe com a anotacao`() {
        val recipeEntity = TestFixtures.recipe().toEntity(1L)
        val joined = SavedWithRecipe(
            saved = SavedRecipeEntity(
                recipeId = recipeEntity.id,
                rating = 4,
                comment = "Ficou otimo",
                prepMinutes = 45,
                photoPath = "/files/photos/a.jpg",
                savedAt = 100L,
                updatedAt = 200L,
            ),
            recipe = recipeEntity,
        )

        val saved = joined.toSavedRecipe()!!

        assertThat(saved.recipe).isEqualTo(TestFixtures.recipe())
        assertThat(saved.rating).isEqualTo(4)
        assertThat(saved.comment).isEqualTo("Ficou otimo")
        assertThat(saved.prepMinutes).isEqualTo(45)
        assertThat(saved.photoPath).isEqualTo("/files/photos/a.jpg")
        assertThat(saved.savedAt).isEqualTo(100L)
        assertThat(saved.updatedAt).isEqualTo(200L)
    }

    @Test
    fun `SavedWithRecipe sem a receita associada vira null`() {
        val joined = SavedWithRecipe(
            saved = SavedRecipeEntity("1", null, null, null, null, 1L, 1L),
            recipe = null,
        )

        assertThat(joined.toSavedRecipe()).isNull()
    }

    @Test
    fun `Category vai para a entidade com posicao e data e volta`() {
        val category = Category("Chicken", "https://x.com/c.png", "Frango")

        val entity = category.toEntity(position = 3, fetchedAt = 777L)

        assertThat(entity).isEqualTo(CategoryEntity("Chicken", "https://x.com/c.png", "Frango", 3, 777L))
        assertThat(entity.toCategory()).isEqualTo(category)
    }

    @Test
    fun `RecipeEntity vira RecipeSummary`() {
        val entity: RecipeEntity = TestFixtures.recipe(id = "7", name = "Bolo", category = "Dessert", area = "Italian")
            .toEntity(1L)

        assertThat(entity.toSummary()).isEqualTo(
            RecipeSummary("7", "Bolo", "https://www.themealdb.com/images/media/meals/7.jpg", "Dessert", "Italian"),
        )
    }

    // ------------------------------------------------------------------ cache de listas

    @Test
    fun `lista de resumos faz a viagem de ida e volta pelo JSON do cache`() {
        val list = listOf(
            TestFixtures.summary(id = "1", name = "Bolo de cenoura ç"),
            TestFixtures.summary(id = "2", name = "Sopa", thumbnailUrl = null, category = null, area = null),
        )

        val json = list.toCacheJson()

        assertThat(json.fromCacheJson()).isEqualTo(list)
    }

    @Test
    fun `lista vazia no cache volta como lista vazia (e nao como null)`() {
        val json = emptyList<RecipeSummary>().toCacheJson()

        assertThat(json).isEqualTo("[]")
        assertThat(json.fromCacheJson()).isEmpty()
    }

    @Test
    fun `JSON de cache corrompido devolve null`() {
        val corrupted = listOf(
            "",
            "not json",
            "{",
            "[{\"id\":\"1\"}]", // falta `name`
            "{\"id\":\"1\",\"name\":\"x\"}", // objeto no lugar de lista
            "[1,2,3]",
            "null",
        )
        for (json in corrupted) {
            assertThat(json.fromCacheJson()).isNull()
        }
    }

    @Test
    fun `JSON de cache com chaves desconhecidas e tolerado`() {
        val json = "[{\"id\":\"1\",\"name\":\"Bolo\",\"extra\":true}]"

        assertThat(json.fromCacheJson()).containsExactly(RecipeSummary("1", "Bolo", null, null, null))
    }

    // ------------------------------------------------------------------ utilitarios

    @Test
    fun `cleanOrNull apara e trata vazio como ausencia`() {
        assertThat(null.cleanOrNull()).isNull()
        assertThat("".cleanOrNull()).isNull()
        assertThat(" \t\n ".cleanOrNull()).isNull()
        assertThat("  oi ".cleanOrNull()).isEqualTo("oi")
    }
}
