package com.cadernoreceitas.parcial.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Testes puros de JVM (sem Android): regras do modelo e dos filtros. */
class RecipeFilterTest {

    private fun recipe(prepMinutes: Int, isFavorite: Boolean = false) = Recipe(
        id = 1,
        name = "Receita de teste",
        category = RecipeCategory.MAIN,
        prepMinutes = prepMinutes,
        servings = 2,
        difficulty = Difficulty.EASY,
        ingredients = listOf(Ingredient("Sal")),
        steps = listOf("Misture tudo."),
        isFavorite = isFavorite,
    )

    @Test
    fun `receita de ate 30 minutos e rapida`() {
        assertThat(recipe(prepMinutes = 1).isQuick).isTrue()
        assertThat(recipe(prepMinutes = Recipe.QUICK_LIMIT_MINUTES).isQuick).isTrue()
    }

    @Test
    fun `receita acima de 30 minutos nao e rapida`() {
        assertThat(recipe(prepMinutes = Recipe.QUICK_LIMIT_MINUTES + 1).isQuick).isFalse()
        assertThat(recipe(prepMinutes = 90).isQuick).isFalse()
    }

    @Test
    fun `filtro ALL aceita qualquer receita`() {
        assertThat(RecipeFilter.ALL.matches(recipe(prepMinutes = 90))).isTrue()
        assertThat(RecipeFilter.ALL.matches(recipe(prepMinutes = 5, isFavorite = true))).isTrue()
    }

    @Test
    fun `filtro QUICK aceita so receitas rapidas`() {
        assertThat(RecipeFilter.QUICK.matches(recipe(prepMinutes = 30))).isTrue()
        assertThat(RecipeFilter.QUICK.matches(recipe(prepMinutes = 31))).isFalse()
    }

    @Test
    fun `filtro FAVORITES aceita so receitas favoritas`() {
        assertThat(RecipeFilter.FAVORITES.matches(recipe(prepMinutes = 90, isFavorite = true))).isTrue()
        assertThat(RecipeFilter.FAVORITES.matches(recipe(prepMinutes = 5, isFavorite = false))).isFalse()
    }

    @Test
    fun `copy cria nova receita e nao altera a original`() {
        val original = recipe(prepMinutes = 10)
        val favorited = original.copy(isFavorite = true)

        assertThat(original.isFavorite).isFalse()
        assertThat(favorited.isFavorite).isTrue()
        assertThat(favorited).isNotEqualTo(original)
        assertThat(favorited.copy(isFavorite = false)).isEqualTo(original)
    }

    @Test
    fun `campos opcionais tem valor padrao nulo`() {
        val recipe = recipe(prepMinutes = 10)

        assertThat(recipe.tip).isNull()
        assertThat(recipe.videoUrl).isNull()
        assertThat(Ingredient("Sal").quantity).isNull()
    }
}
