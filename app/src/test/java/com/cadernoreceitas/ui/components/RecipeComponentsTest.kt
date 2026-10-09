package com.cadernoreceitas.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadernoreceitas.uitesting.LARGE_FONT_SCALE
import com.cadernoreceitas.uitesting.SampleData
import com.cadernoreceitas.uitesting.TEST_DEVICE
import com.cadernoreceitas.uitesting.TEST_SDK
import com.cadernoreceitas.uitesting.TestCadernoApp
import com.cadernoreceitas.uitesting.assertLayoutSizeAtLeast
import com.cadernoreceitas.uitesting.assertTouchTargetAtLeast
import com.cadernoreceitas.uitesting.setCadernoContent
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Componentes compartilhados: [RecipeCard], [RatingDisplay] e [RatingInput] (semantica e toque). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [TEST_SDK], qualifiers = TEST_DEVICE, application = TestCadernoApp::class)
class RecipeComponentsTest {

    @get:Rule
    val rule = createComposeRule()

    private val summary = SampleData.summary()

    private fun stateDescriptionIs(expected: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected)

    // ------------------------------------------------------------------ RecipeCard

    @Test
    fun cartaoMostraNomeECategoriaEOrigem() {
        rule.setCadernoContent { RecipeCard(recipe = summary, isSaved = false, onClick = {}) }

        rule.onNodeWithText("Teriyaki Chicken Casserole").assertIsDisplayed()
        rule.onNodeWithText("Chicken · Japanese").assertIsDisplayed()
    }

    @Test
    fun cartaoSemCategoriaNemOrigemNaoMostraLinhaDeDetalhes() {
        rule.setCadernoContent {
            RecipeCard(recipe = SampleData.summary(name = "Sopa de cebola", category = null, area = null), isSaved = false, onClick = {})
        }

        rule.onNodeWithText("Sopa de cebola").assertIsDisplayed()
        rule.onNodeWithText("·", substring = true).assertDoesNotExist() // sem separador de detalhes
        rule.onNodeWithText("Chicken").assertDoesNotExist()
    }

    @Test
    fun cartaoSoComUmaDasDuasInformacoesMostraSoEla() {
        rule.setCadernoContent {
            RecipeCard(recipe = SampleData.summary(category = "Dessert", area = null), isSaved = false, onClick = {})
        }

        rule.onNodeWithText("Dessert").assertIsDisplayed()
    }

    @Test
    fun tocarNoCartaoDisparaOnClick() {
        var clicks = 0
        rule.setCadernoContent { RecipeCard(recipe = summary, isSaved = false, onClick = { clicks++ }) }

        rule.onNodeWithText("Teriyaki Chicken Casserole").performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun cartaoEUmUnicoItemClicavelParaOLeitorDeTela() {
        rule.setCadernoContent { RecipeCard(recipe = summary, isSaved = false, onClick = {}) }

        // Nome e detalhes ficam fundidos no mesmo no, que e o proprio alvo de clique.
        rule.onNode(hasClickAction() and hasText("Teriyaki Chicken Casserole"))
            .assertIsDisplayed()
            .assert(hasText("Chicken · Japanese"))
    }

    @Test
    fun cartaoSalvoExpoeOEstadoSalvaNoCaderno() {
        rule.setCadernoContent { RecipeCard(recipe = summary, isSaved = true, onClick = {}) }

        rule.onNodeWithText("Teriyaki Chicken Casserole").assert(stateDescriptionIs("Salva no caderno"))
    }

    @Test
    fun cartaoNaoSalvoNaoExpoeEstado() {
        rule.setCadernoContent { RecipeCard(recipe = summary, isSaved = false, onClick = {}) }

        rule.onNodeWithText("Teriyaki Chicken Casserole")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun cartaoTemAlturaMinimaConfortavelParaOToque() {
        rule.setCadernoContent { RecipeCard(recipe = summary, isSaved = false, onClick = {}) }

        rule.onNodeWithText("Teriyaki Chicken Casserole").assertTouchTargetAtLeast(rule)
    }

    @Test
    fun metaLineIgnoraPartesAusentes() {
        assertThat(metaLine("Chicken", "Japanese")).isEqualTo("Chicken · Japanese")
        assertThat(metaLine("Chicken", null)).isEqualTo("Chicken")
        assertThat(metaLine(null, "Japanese")).isEqualTo("Japanese")
        assertThat(metaLine(null, null)).isNull()
    }

    // ------------------------------------------------------------------ RatingDisplay

    @Test
    fun notaSomenteLeituraDescreveONumeroDeEstrelas() {
        rule.setCadernoContent { RatingDisplay(rating = 4) }

        rule.onNodeWithContentDescription("Nota 4 de 5").assertIsDisplayed()
    }

    @Test
    fun semNotaDizSemNota() {
        rule.setCadernoContent { RatingDisplay(rating = null) }

        rule.onNodeWithContentDescription("Sem nota").assertIsDisplayed()
    }

    @Test
    fun asCincoEstrelasDoDisplayFormamUmUnicoElementoDeAcessibilidade() {
        rule.setCadernoContent { RatingDisplay(rating = 2) }

        // So um no com descricao (as estrelas individuais sao decorativas).
        rule.onAllNodesWithContentDescription("Nota 2 de 5").assertCountEquals(1)
    }

    // ------------------------------------------------------------------ RatingInput

    @Test
    fun seletorTemCincoOpcoesComDescricaoNoSingularEPlural() {
        rule.setCadernoContent { RatingInput(rating = 0, onRatingChange = {}) }

        rule.onNodeWithContentDescription("1 estrela").assertIsDisplayed()
        listOf("2 estrelas", "3 estrelas", "4 estrelas", "5 estrelas").forEach {
            rule.onNodeWithContentDescription(it).assertIsDisplayed()
        }
    }

    @Test
    fun seletorExpoeCadaEstrelaComoBotaoDeRadio() {
        rule.setCadernoContent { RatingInput(rating = 3, onRatingChange = {}) }

        (1..5).forEach { value ->
            val label = if (value == 1) "1 estrela" else "$value estrelas"
            rule.onNodeWithContentDescription(label)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
    }

    @Test
    fun soAEstrelaDaNotaAtualFicaSelecionada() {
        rule.setCadernoContent { RatingInput(rating = 3, onRatingChange = {}) }

        rule.onNodeWithContentDescription("3 estrelas").assertIsSelected()
        listOf("1 estrela", "2 estrelas", "4 estrelas", "5 estrelas").forEach {
            rule.onNodeWithContentDescription(it).assertIsNotSelected()
        }
    }

    @Test
    fun semNotaNenhumaEstrelaFicaSelecionada() {
        rule.setCadernoContent { RatingInput(rating = 0, onRatingChange = {}) }

        listOf("1 estrela", "2 estrelas", "3 estrelas", "4 estrelas", "5 estrelas").forEach {
            rule.onNodeWithContentDescription(it).assertIsNotSelected()
        }
    }

    @Test
    fun tocarNaEstrelaEnviaOValorCorreto() {
        val received = mutableListOf<Int>()
        rule.setCadernoContent { RatingInput(rating = 0, onRatingChange = { received += it }) }

        rule.onNodeWithContentDescription("4 estrelas").performClick()
        rule.onNodeWithContentDescription("1 estrela").performClick()

        assertThat(received).containsExactly(4, 1).inOrder()
    }

    @Test
    fun seletorInterativoMoveASelecaoEntreAsEstrelas() {
        rule.setCadernoContent {
            var rating by remember { mutableIntStateOf(0) }
            RatingInput(rating = rating, onRatingChange = { rating = it })
        }

        rule.onNodeWithContentDescription("5 estrelas").performClick()
        rule.onNodeWithContentDescription("5 estrelas").assertIsSelected()

        rule.onNodeWithContentDescription("2 estrelas").performClick()
        rule.onNodeWithContentDescription("2 estrelas").assertIsSelected()
        rule.onNodeWithContentDescription("5 estrelas").assertIsNotSelected()
    }

    @Test
    fun cadaEstrelaDoSeletorTemPeloMenos48dp() {
        rule.setCadernoContent { RatingInput(rating = 2, onRatingChange = {}) }

        listOf("1 estrela", "2 estrelas", "3 estrelas", "4 estrelas", "5 estrelas").forEach {
            rule.onNodeWithContentDescription(it).assertLayoutSizeAtLeast()
            rule.onNodeWithContentDescription(it).assertTouchTargetAtLeast(rule)
        }
    }

    @Test
    fun rotuloDoSeletorNaoDependeDaCor() {
        // A selecao e anunciada por "selected" (nao so pela cor da estrela): cobre daltonismo e leitor de tela.
        rule.setCadernoContent { RatingInput(rating = 5, onRatingChange = {}, isError = true) }

        rule.onNodeWithContentDescription("5 estrelas").assertIsSelected()
        rule.onNodeWithContentDescription("4 estrelas").assertIsNotSelected()
    }

    @Test
    fun seletorEmEstadoDeErroContinuaInterativo() {
        var last = 0
        rule.setCadernoContent { RatingInput(rating = 0, onRatingChange = { last = it }, isError = true) }

        rule.onNodeWithContentDescription("3 estrelas").performClick()

        assertThat(last).isEqualTo(3)
    }

    @Test
    fun cartaoComTextoEmDoisXContinuaLegivelEClicavel() {
        var clicks = 0
        rule.setCadernoContent(fontScale = LARGE_FONT_SCALE) {
            RecipeCard(recipe = summary, isSaved = true, onClick = { clicks++ })
        }

        // Nome e categoria continuam visiveis (o cartao cresce em altura em vez de cortar o texto).
        rule.onNodeWithText("Teriyaki Chicken Casserole")
            .assertIsDisplayed()
            .assertTextContains("Chicken · Japanese", substring = true)
            .assertTouchTargetAtLeast(rule)
            .performClick()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun seletorDeNotaComTextoEmDoisXNaoPerdeOsAlvosDeToque() {
        rule.setCadernoContent(fontScale = LARGE_FONT_SCALE) { RatingInput(rating = 1, onRatingChange = {}) }

        // Os alvos sao em dp (nao dependem da escala de fonte): continuam com 48 dp.
        (1..5).forEach { value ->
            val label = if (value == 1) "1 estrela" else "$value estrelas"
            rule.onNodeWithContentDescription(label).assertIsDisplayed().assertLayoutSizeAtLeast()
        }
    }
}
