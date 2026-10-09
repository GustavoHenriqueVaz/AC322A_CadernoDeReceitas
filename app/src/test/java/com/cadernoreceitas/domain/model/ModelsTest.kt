package com.cadernoreceitas.domain.model

import com.cadernoreceitas.testutil.TestFixtures
import com.cadernoreceitas.ui.components.UiError
import com.cadernoreceitas.ui.components.toUiError
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

/** Regras simples embutidas nos modelos de dominio e a traducao de erros para a UI. */
class ModelsTest {

    // ------------------------------------------------------------------ Loaded / DataSource

    @Test
    fun `Loaded so e offline quando o dado vem do cache vencido`() {
        assertThat(Loaded("x", DataSource.REMOTE).isOffline).isFalse()
        assertThat(Loaded("x", DataSource.CACHE_FRESH).isOffline).isFalse()
        assertThat(Loaded("x", DataSource.CACHE_STALE).isOffline).isTrue()
    }

    // ------------------------------------------------------------------ SavedRecipe.hasNote

    @Test
    fun `sem nota e sem comentario nao ha anotacao`() {
        assertThat(TestFixtures.savedRecipe().hasNote).isFalse()
        // tempo de preparo e foto sozinhos NAO contam como anotacao
        assertThat(TestFixtures.savedRecipe(prepMinutes = 30, photoPath = "/a.jpg").hasNote).isFalse()
    }

    @Test
    fun `nota sozinha ou comentario nao vazio caracterizam anotacao`() {
        assertThat(TestFixtures.savedRecipe(rating = 3).hasNote).isTrue()
        assertThat(TestFixtures.savedRecipe(comment = "Muito bom").hasNote).isTrue()
        assertThat(TestFixtures.savedRecipe(rating = 1, comment = "ok ok ok ok").hasNote).isTrue()
    }

    @Test
    fun `comentario em branco nao conta como anotacao`() {
        assertThat(TestFixtures.savedRecipe(comment = "").hasNote).isFalse()
        assertThat(TestFixtures.savedRecipe(comment = "   \n ").hasNote).isFalse()
        assertThat(TestFixtures.savedRecipe(comment = "  ", rating = 5).hasNote).isTrue() // a nota basta
    }

    // ------------------------------------------------------------------ Recipe.toSummary

    @Test
    fun `Recipe toSummary copia so os campos da lista`() {
        val recipe = TestFixtures.recipe(id = "9", name = "Bolo", category = "Dessert", area = "Italian")

        assertThat(recipe.toSummary()).isEqualTo(
            RecipeSummary(
                id = "9",
                name = "Bolo",
                thumbnailUrl = "https://www.themealdb.com/images/media/meals/9.jpg",
                category = "Dessert",
                area = "Italian",
            ),
        )
    }

    // ------------------------------------------------------------------ UserPreferences

    @Test
    fun `preferencias padrao`() {
        val prefs = UserPreferences()

        assertThat(prefs.lastQuery).isEmpty()
        assertThat(prefs.lastCategory).isNull()
        assertThat(prefs.reminderEnabled).isFalse()
        assertThat(prefs.dailyRecipeId).isNull()
        assertThat(prefs.dailyRecipeEpochDay).isNull()
    }

    // ------------------------------------------------------------------ RecipeException

    @Test
    fun `RecipeException guarda o tipo, o nome do tipo como mensagem e a causa`() {
        val cause = IOException("sem rede")

        val exception = RecipeException(RecipeException.Kind.NETWORK, cause)

        assertThat(exception.kind).isEqualTo(RecipeException.Kind.NETWORK)
        assertThat(exception.message).isEqualTo("NETWORK")
        assertThat(exception.cause).isSameInstanceAs(cause)
        assertThat(RecipeException(RecipeException.Kind.NOT_FOUND).cause).isNull()
    }

    // ------------------------------------------------------------------ Throwable.toUiError

    @Test
    fun `cada tipo de RecipeException vira o UiError correspondente`() {
        assertThat(RecipeException(RecipeException.Kind.NETWORK).toUiError()).isEqualTo(UiError.NETWORK)
        assertThat(RecipeException(RecipeException.Kind.SERVER).toUiError()).isEqualTo(UiError.SERVER)
        assertThat(RecipeException(RecipeException.Kind.INVALID_DATA).toUiError()).isEqualTo(UiError.INVALID_DATA)
        assertThat(RecipeException(RecipeException.Kind.NOT_FOUND).toUiError()).isEqualTo(UiError.NOT_FOUND)
    }

    @Test
    fun `todo Kind tem um UiError (nenhum cai em UNKNOWN)`() {
        for (kind in RecipeException.Kind.entries) {
            assertThat(RecipeException(kind).toUiError()).isNotEqualTo(UiError.UNKNOWN)
        }
    }

    @Test
    fun `excecoes desconhecidas viram UNKNOWN sem expor detalhes`() {
        assertThat(IllegalStateException("boom").toUiError()).isEqualTo(UiError.UNKNOWN)
        assertThat(IOException("io").toUiError()).isEqualTo(UiError.UNKNOWN)
        assertThat(NullPointerException().toUiError()).isEqualTo(UiError.UNKNOWN)
    }

    @Test
    fun `cancelamento nunca vira erro de tela - e relancado`() {
        val cancellation = CancellationException("cancelado")

        val thrown = assertThrows(CancellationException::class.java) { cancellation.toUiError() }

        assertThat(thrown).isSameInstanceAs(cancellation)
    }
}
