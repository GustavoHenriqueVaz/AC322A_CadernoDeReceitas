package com.cadernoreceitas.parcial.data

import com.cadernoreceitas.parcial.model.RecipeFilter
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Garante que os dados simulados cobrem os casos que a interface precisa tratar. */
class MockRecipesTest {

    @Test
    fun `existem 8 receitas com ids unicos`() {
        val ids = MockRecipes.all.map { it.id }

        assertThat(ids).hasSize(8)
        assertThat(ids.toSet()).hasSize(ids.size)
    }

    @Test
    fun `byId encontra receita existente e devolve nulo para id inexistente`() {
        assertThat(MockRecipes.byId(1)).isEqualTo(MockRecipes.all.first { it.id == 1 })
        assertThat(MockRecipes.byId(999)).isNull()
    }

    @Test
    fun `existem receitas com dica e video nulos e tambem preenchidos`() {
        assertThat(MockRecipes.all.any { it.tip == null }).isTrue()
        assertThat(MockRecipes.all.any { it.tip != null }).isTrue()
        assertThat(MockRecipes.all.any { it.videoUrl == null }).isTrue()
        assertThat(MockRecipes.all.any { it.videoUrl != null }).isTrue()
    }

    @Test
    fun `existem ingredientes sem quantidade`() {
        val ingredients = MockRecipes.all.flatMap { it.ingredients }

        assertThat(ingredients.any { it.quantity == null }).isTrue()
        assertThat(ingredients.any { it.quantity != null }).isTrue()
    }

    @Test
    fun `toda receita tem ingredientes e passos`() {
        MockRecipes.all.forEach { recipe ->
            assertThat(recipe.ingredients).isNotEmpty()
            assertThat(recipe.steps).isNotEmpty()
            assertThat(recipe.name).isNotEmpty()
        }
    }

    @Test
    fun `filtros dividem as receitas como esperado`() {
        val quick = MockRecipes.all.filter { RecipeFilter.QUICK.matches(it) }
        val favorites = MockRecipes.all.filter { RecipeFilter.FAVORITES.matches(it) }

        assertThat(quick).hasSize(5)
        assertThat(quick.all { it.prepMinutes <= 30 }).isTrue()
        // nenhuma receita comeca favorita: o usuario e quem favorita
        assertThat(favorites).isEmpty()
    }
}
