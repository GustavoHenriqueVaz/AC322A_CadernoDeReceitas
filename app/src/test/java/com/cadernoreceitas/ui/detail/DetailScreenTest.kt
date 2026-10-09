package com.cadernoreceitas.ui.detail

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadernoreceitas.R
import com.cadernoreceitas.domain.model.Recipe
import com.cadernoreceitas.domain.model.SavedRecipe
import com.cadernoreceitas.ui.components.TestTags
import com.cadernoreceitas.ui.components.UiError
import com.cadernoreceitas.uitesting.LARGE_FONT_SCALE
import com.cadernoreceitas.uitesting.SampleData
import com.cadernoreceitas.uitesting.TEST_DEVICE
import com.cadernoreceitas.uitesting.TEST_SDK
import com.cadernoreceitas.uitesting.TestCadernoApp
import com.cadernoreceitas.uitesting.assertTouchTargetAtLeast
import com.cadernoreceitas.uitesting.setCadernoContent
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** [DetailScreen] com estados construidos a mao (sem ViewModel). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [TEST_SDK], qualifiers = TEST_DEVICE, application = TestCadernoApp::class)
class DetailScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val recipe = SampleData.teriyaki
    private val name = recipe.name

    private class Calls {
        var back = 0
        var toggles = 0
        val edits = mutableListOf<String>()
        var retries = 0
        val undone = mutableListOf<SavedRecipe>()
        var messagesShown = 0
        val shared = mutableListOf<Recipe>()
        val videos = mutableListOf<String>()
    }

    private val calls = Calls()

    private fun state(
        content: DetailContent = DetailContent.Success(recipe, isOffline = false),
        saved: SavedRecipe? = null,
        message: DetailMessage? = null,
    ) = DetailUiState(content = content, saved = saved, message = message)

    private fun show(state: DetailUiState, fontScale: Float = 1f) {
        rule.setCadernoContent(fontScale) {
            DetailScreen(
                state = state,
                onBack = { calls.back++ },
                onToggleSaved = { calls.toggles++ },
                onEditNote = { calls.edits += it },
                onRetry = { calls.retries++ },
                onUndo = { calls.undone += it },
                onMessageShown = { calls.messagesShown++ },
                onShare = { calls.shared += it },
                onOpenVideo = { calls.videos += it },
            )
        }
    }

    private fun isHeading() = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private fun stateDescriptionIs(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    // ------------------------------------------------------------------ os estados

    @Test
    fun carregandoMostraIndicadorETituloGenericoSemAcoesDeReceita() {
        show(state(content = DetailContent.Loading))

        rule.onNodeWithTag(TestTags.LOADING).assertIsDisplayed()
        rule.onNodeWithText("Receita").assertIsDisplayed() // titulo padrao da barra
        rule.onNodeWithContentDescription("Voltar").assertIsDisplayed()
        // Sem receita ainda nao faz sentido compartilhar nem salvar.
        rule.onNodeWithContentDescription("Compartilhar").assertDoesNotExist()
        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED).assertDoesNotExist()
    }

    @Test
    fun erroMostraMensagemETentarNovamente() {
        show(state(content = DetailContent.Error(UiError.NOT_FOUND)))

        rule.onNodeWithTag(TestTags.ERROR).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.error_not_found)).assertIsDisplayed()
        rule.onNodeWithContentDescription("Compartilhar").assertDoesNotExist()

        rule.onNodeWithText("Tentar novamente").performClick()

        assertThat(calls.retries).isEqualTo(1)
    }

    @Test
    fun conteudoMostraNomeCategoriaTagsIngredientesEModoDePreparo() {
        show(state())

        // O nome aparece na barra de titulo e como titulo (heading) do corpo.
        rule.onAllNodesWithText(name).assertCountEquals(2)
        rule.onNode(hasText(name) and isHeading()).assertIsDisplayed()
        rule.onNodeWithText("Chicken · Japanese").assertIsDisplayed()
        rule.onNodeWithText("#Meat  #Casserole").assertIsDisplayed()
        rule.onNodeWithText("Ingredientes").assertIsDisplayed().assert(isHeading())
        rule.onNodeWithText("3/4 cup soy sauce").assertIsDisplayed()
        rule.onNodeWithText("1/2 cup water").assertIsDisplayed()
        rule.onNodeWithText("Modo de preparo").performScrollTo().assertIsDisplayed().assert(isHeading())
        rule.onNodeWithText("Misture tudo e asse por 35 minutos.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun ingredienteSemMedidaMostraSoONome() {
        show(state())

        rule.onNodeWithText("salt").assertIsDisplayed()
    }

    @Test
    fun receitaSemIngredientesEInstrucoesMostraAvisos() {
        show(state(content = DetailContent.Success(SampleData.recipe(ingredients = emptyList(), instructions = ""), false)))

        rule.onNodeWithText("Ingredientes não informados.").assertIsDisplayed()
        rule.onNodeWithText("Esta receita não tem instruções cadastradas.").assertIsDisplayed()
    }

    @Test
    fun receitaSemCategoriaAreaETagsNaoMostraEssasLinhas() {
        show(
            state(
                content = DetailContent.Success(
                    SampleData.recipe(name = "Sopa de cebola", category = null, area = null, tags = emptyList()),
                    isOffline = false,
                ),
            ),
        )

        rule.onNodeWithText("Chicken", substring = true).assertDoesNotExist()
        rule.onNodeWithText("#", substring = true).assertDoesNotExist()
    }

    @Test
    fun faixaOfflineAparecePeloModoOffline() {
        show(state(content = DetailContent.Success(recipe, isOffline = true)))

        rule.onNodeWithTag(TestTags.OFFLINE_BANNER).assertIsDisplayed()
    }

    @Test
    fun semOfflineNaoMostraAFaixa() {
        show(state())

        rule.onNodeWithTag(TestTags.OFFLINE_BANNER).assertDoesNotExist()
    }

    // ------------------------------------------------------------------ ingredientes marcaveis

    @Test
    fun ingredientesSaoCaixasDeMarcacaoQueMudamDeEstadoAoTocar() {
        show(state())

        rule.onNodeWithText("3/4 cup soy sauce").assertIsOff().performClick()
        rule.onNodeWithText("3/4 cup soy sauce").assertIsOn()
        rule.onNodeWithText("1/2 cup water").assertIsOff() // os outros nao mudam

        rule.onNodeWithText("3/4 cup soy sauce").performClick()
        rule.onNodeWithText("3/4 cup soy sauce").assertIsOff()
    }

    @Test
    fun linhaDoIngredienteTemAlvoDeToqueDe48dp() {
        show(state())

        rule.onNodeWithText("salt").assertTouchTargetAtLeast(rule)
    }

    // ------------------------------------------------------------------ salvar / compartilhar / voltar

    @Test
    fun botaoSalvarAnunciaQueAReceitaAindaNaoEstaSalva() {
        show(state())

        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED)
            .assertIsDisplayed()
            .assert(stateDescriptionIs("Não salva"))
        rule.onNodeWithContentDescription("Salvar no caderno").assertIsDisplayed()
        rule.onNodeWithContentDescription("Remover do caderno").assertDoesNotExist()
    }

    @Test
    fun botaoSalvarAnunciaQueAReceitaEstaSalvaEOfereceRemover() {
        show(state(saved = SampleData.saved(recipe)))

        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED)
            .assertIsDisplayed()
            .assert(stateDescriptionIs("Salva no caderno"))
        rule.onNodeWithContentDescription("Remover do caderno").assertIsDisplayed()
        rule.onNodeWithContentDescription("Salvar no caderno").assertDoesNotExist()
    }

    @Test
    fun tocarNoBotaoSalvarDisparaOToggle() {
        show(state())

        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED).performClick()

        assertThat(calls.toggles).isEqualTo(1)
    }

    @Test
    fun botaoVoltarEChamado() {
        show(state())

        rule.onNodeWithContentDescription("Voltar").performClick()

        assertThat(calls.back).isEqualTo(1)
    }

    @Test
    fun botaoCompartilharEnviaAReceita() {
        show(state())

        rule.onNodeWithContentDescription("Compartilhar").performClick()

        assertThat(calls.shared).containsExactly(recipe)
    }

    @Test
    fun botoesDaBarraTemAlvoDeToqueDe48dp() {
        show(state())

        rule.onNodeWithContentDescription("Voltar").assertTouchTargetAtLeast(rule)
        rule.onNodeWithContentDescription("Compartilhar").assertTouchTargetAtLeast(rule)
        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED).assertTouchTargetAtLeast(rule)
    }

    // ------------------------------------------------------------------ minha anotacao

    @Test
    fun semAnotacaoMostraAvisoEBotaoAnotar() {
        show(state())

        rule.onNodeWithText("Minha anotação").assertIsDisplayed().assert(isHeading())
        rule.onNodeWithText(context.getString(R.string.my_note_empty)).assertIsDisplayed()

        rule.onNodeWithText("Anotar").performClick()

        assertThat(calls.edits).containsExactly(SampleData.TERIYAKI_ID)
    }

    @Test
    fun receitaSalvaMasSemAnotacaoAindaOfereceAnotar() {
        show(state(saved = SampleData.saved(recipe))) // salva, sem nota

        rule.onNodeWithText("Anotar").assertIsDisplayed()
        rule.onNodeWithText("Editar anotação").assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.my_note_empty)).assertIsDisplayed()
    }

    @Test
    fun comAnotacaoMostraNotaTempoEObservacaoEBotaoEditar() {
        show(
            state(
                saved = SampleData.saved(
                    recipe,
                    rating = 4,
                    comment = "Ficou otimo, menos sal na proxima vez",
                    prepMinutes = 45,
                ),
            ),
        )

        rule.onNodeWithContentDescription("Nota 4 de 5").assertIsDisplayed()
        rule.onNodeWithText("45 min de preparo").assertIsDisplayed()
        rule.onNodeWithText("Ficou otimo, menos sal na proxima vez").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.my_note_empty)).assertDoesNotExist()

        rule.onNodeWithText("Editar anotação").performClick()

        assertThat(calls.edits).containsExactly(SampleData.TERIYAKI_ID)
    }

    @Test
    fun botaoDeAnotacaoTemAlvoDeToqueDe48dp() {
        show(state())

        rule.onNodeWithText("Anotar").performScrollTo().assertTouchTargetAtLeast(rule)
    }

    // ------------------------------------------------------------------ video / instrucoes longas

    @Test
    fun botaoDeVideoSoApareceQuandoHaLinkEEnviaAUrl() {
        val url = "https://www.youtube.com/watch?v=4aZr5hZXP_s"
        show(state(content = DetailContent.Success(SampleData.recipe(youtubeUrl = url), false)))

        rule.onNodeWithText("Ver vídeo").performScrollTo().assertIsDisplayed().performClick()

        assertThat(calls.videos).containsExactly(url)
    }

    @Test
    fun semLinkNaoHaBotaoDeVideo() {
        show(state())

        rule.onNodeWithText("Ver vídeo").assertDoesNotExist()
    }

    @Test
    fun instrucoesLongasComecamRecolhidasEExpandem() {
        val long = "Passo detalhado da receita. ".repeat(60) // bem acima do limite de 700 caracteres
        show(state(content = DetailContent.Success(SampleData.recipe(instructions = long), false)))

        rule.onNodeWithText("Ler mais").performScrollTo().assertIsDisplayed().performClick()

        rule.onNodeWithText("Mostrar menos").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithText("Ler mais").assertIsDisplayed()
    }

    @Test
    fun instrucoesCurtasNaoTemBotaoLerMais() {
        show(state())

        rule.onNodeWithText("Ler mais").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ mensagens (snackbar)

    @Test
    fun mensagemSalvaAparecePedeParaSerConsumida() {
        show(state(message = DetailMessage.Saved))

        rule.onNodeWithText("Receita salva no caderno").assertIsDisplayed()

        rule.mainClock.advanceTimeBy(10_000)
        rule.waitForIdle()
        assertThat(calls.messagesShown).isEqualTo(1)
    }

    @Test
    fun mensagemDeFalhaApareceNaTela() {
        show(state(message = DetailMessage.Failed))

        rule.onNodeWithText("Não foi possível salvar. Tente novamente.").assertIsDisplayed()
    }

    @Test
    fun mensagemDeRemocaoOferecaDesfazerEEsteRestauraOItem() {
        val item = SampleData.saved(recipe, rating = 5, comment = "Perfeita para o domingo")
        show(state(message = DetailMessage.Removed(item)))

        rule.onNodeWithText("Receita removida do caderno").assertIsDisplayed()
        rule.onNodeWithText("Desfazer").assertIsDisplayed().performClick()
        rule.waitForIdle()

        assertThat(calls.undone).containsExactly(item)
        assertThat(calls.messagesShown).isEqualTo(1)
    }

    // ------------------------------------------------------------------ texto grande

    @Test
    fun comTextoEmDoisXABarraEAsAcoesPrincipaisContinuamVisiveis() {
        show(state(saved = SampleData.saved(recipe, rating = 3, comment = "Boa, mas demorada")), LARGE_FONT_SCALE)

        rule.onNodeWithContentDescription("Voltar").assertIsDisplayed().assertTouchTargetAtLeast(rule)
        rule.onNodeWithContentDescription("Compartilhar").assertIsDisplayed().assertTouchTargetAtLeast(rule)
        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED).assertIsDisplayed().assertTouchTargetAtLeast(rule)
    }

    @Test
    fun comTextoEmDoisXTodasAsSecoesSaoAlcancaveisRolando() {
        show(state(content = DetailContent.Success(SampleData.recipe(youtubeUrl = "https://youtu.be/x"), false)), LARGE_FONT_SCALE)

        rule.onNodeWithText("Editar anotação").assertDoesNotExist()
        rule.onNodeWithText("Anotar").performScrollTo().assertIsDisplayed().assertTouchTargetAtLeast(rule)
        rule.onNodeWithText("3/4 cup soy sauce").performScrollTo().assertIsDisplayed().assertTouchTargetAtLeast(rule)
        rule.onNodeWithText("Modo de preparo").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Ver vídeo").performScrollTo().assertIsDisplayed().assertTouchTargetAtLeast(rule)
    }

    @Test
    fun comTextoEmDoisXOEstadoDeErroContinuaUtilizavel() {
        show(state(content = DetailContent.Error(UiError.NETWORK)), LARGE_FONT_SCALE)

        rule.onNodeWithTag(TestTags.ERROR).assertIsDisplayed()
        rule.onNodeWithText("Tentar novamente").assertIsDisplayed().assertTouchTargetAtLeast(rule)
    }

    // ------------------------------------------------------------------ tela larga

    @Test
    @Config(qualifiers = "w900dp-h600dp-normal-long-notround-any-mdpi-keyshidden-nonav")
    fun emTelaLargaAsDuasColunasMostramIngredientesEModoDePreparoLadoALado() {
        show(state())

        // Sem rolar: ingredientes na coluna da esquerda e instrucoes na da direita.
        rule.onNodeWithText("Ingredientes").assertIsDisplayed()
        rule.onNodeWithText("Modo de preparo").assertIsDisplayed()
        rule.onNodeWithText("Anotar").assertIsDisplayed()
    }
}
