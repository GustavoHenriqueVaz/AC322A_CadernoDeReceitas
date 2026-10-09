package com.cadernoreceitas.ui.notebook

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadernoreceitas.R
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

/** [NotebookScreen] com estados construidos a mao (sem ViewModel). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [TEST_SDK], qualifiers = TEST_DEVICE, application = TestCadernoApp::class)
class NotebookScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val noted = SampleData.saved(
        SampleData.crumble,
        rating = 5,
        comment = "Excelente, perfeita para o domingo",
        prepMinutes = 40,
    )
    private val plain = SampleData.saved(SampleData.teriyaki)

    private class Calls {
        val filters = mutableListOf<NotebookFilter>()
        val clicks = mutableListOf<String>()
        val removed = mutableListOf<String>()
        var undo = 0
        var removalShown = 0
        var retries = 0
        var explore = 0
    }

    private val calls = Calls()

    private fun success(vararg items: SavedRecipe, filter: NotebookFilter = NotebookFilter.ALL) =
        NotebookUiState(
            content = NotebookContent.Success(items.toList()),
            filter = filter,
            totalSaved = items.size,
        )

    private fun show(state: NotebookUiState, fontScale: Float = 1f) {
        rule.setCadernoContent(fontScale) {
            NotebookScreen(
                state = state,
                onFilterSelected = { calls.filters += it },
                onRecipeClick = { calls.clicks += it },
                onRemove = { calls.removed += it },
                onUndo = { calls.undo++ },
                onRemovalShown = { calls.removalShown++ },
                onRetry = { calls.retries++ },
                onExplore = { calls.explore++ },
            )
        }
    }

    // ------------------------------------------------------------------ os quatro estados

    @Test
    fun carregandoMostraIndicadorESemFiltros() {
        show(NotebookUiState(content = NotebookContent.Loading))

        rule.onNodeWithText("Meu caderno").assertIsDisplayed()
        rule.onNodeWithTag(TestTags.LOADING).assertIsDisplayed()
        rule.onNodeWithText("Todas").assertDoesNotExist()
    }

    @Test
    fun comConteudoMostraAsReceitasComNotaTempoEObservacao() {
        show(success(noted, plain))

        rule.onNodeWithTag(TestTags.NOTEBOOK_LIST).assertIsDisplayed()
        rule.onNodeWithText("Apple & Blackberry Crumble").assertIsDisplayed()
        rule.onNodeWithContentDescription("Nota 5 de 5").assertIsDisplayed()
        rule.onNodeWithText("40 min de preparo").assertIsDisplayed()
        rule.onNodeWithText("Excelente, perfeita para o domingo").assertIsDisplayed()
    }

    @Test
    fun receitaSemAnotacaoMostraSemNotaEConvite() {
        show(success(plain))

        rule.onNodeWithText("Teriyaki Chicken Casserole").assertIsDisplayed()
        rule.onNodeWithContentDescription("Sem nota").assertIsDisplayed()
        rule.onNodeWithText("Toque para ver e adicionar sua anotação").assertIsDisplayed()
    }

    @Test
    fun cadernoVazioMostraEstadoVazioComAcaoParaExplorar() {
        show(NotebookUiState(content = NotebookContent.Empty(filtered = false), totalSaved = 0))

        rule.onNodeWithTag(TestTags.EMPTY).assertIsDisplayed()
        rule.onNodeWithText("Seu caderno está vazio").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.notebook_empty_message)).assertIsDisplayed()
        rule.onNodeWithText("Todas").assertDoesNotExist() // sem receitas nao ha o que filtrar

        rule.onNodeWithText("Explorar receitas").performClick()

        assertThat(calls.explore).isEqualTo(1)
    }

    @Test
    fun filtroSemResultadosMostraOutraMensagemEVoltaParaTodas() {
        show(
            NotebookUiState(
                content = NotebookContent.Empty(filtered = true),
                filter = NotebookFilter.TOP_RATED,
                totalSaved = 3,
            ),
        )

        rule.onNodeWithTag(TestTags.EMPTY).assertIsDisplayed()
        rule.onNodeWithText("Nada neste filtro").assertIsDisplayed()
        rule.onNodeWithText("Explorar receitas").assertDoesNotExist()
        // Os filtros continuam na tela (ha receitas salvas), entao "Todas" existe duas vezes: chip e acao.
        rule.onNode(hasText("Todas") and hasAnyAncestor(hasTestTag(TestTags.EMPTY))).performClick()

        assertThat(calls.filters).containsExactly(NotebookFilter.ALL)
    }

    @Test
    fun erroMostraMensagemETentarNovamente() {
        show(NotebookUiState(content = NotebookContent.Error(UiError.UNKNOWN)))

        rule.onNodeWithTag(TestTags.ERROR).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.error_unknown)).assertIsDisplayed()

        rule.onNodeWithText("Tentar novamente").performClick()

        assertThat(calls.retries).isEqualTo(1)
    }

    // ------------------------------------------------------------------ filtros

    @Test
    fun chipsDeFiltroApareceQuandoHaReceitasEOAtualFicaSelecionado() {
        show(success(noted, plain, filter = NotebookFilter.WITH_NOTE))

        rule.onNodeWithText("Todas").assertIsDisplayed().assertIsNotSelected()
        rule.onNodeWithText("Com anotação").assertIsDisplayed().assertIsSelected()
        rule.onNodeWithText("4 estrelas ou mais").assertIsDisplayed().assertIsNotSelected()
    }

    @Test
    fun tocarNumChipEnviaOFiltro() {
        show(success(noted, plain))

        rule.onNodeWithText("4 estrelas ou mais").performClick()
        rule.onNodeWithText("Com anotação").performClick()

        assertThat(calls.filters).containsExactly(NotebookFilter.TOP_RATED, NotebookFilter.WITH_NOTE).inOrder()
    }

    // ------------------------------------------------------------------ abrir e remover

    @Test
    fun tocarNaReceitaEnviaOId() {
        show(success(noted, plain))

        rule.onNodeWithText("Teriyaki Chicken Casserole").performClick()

        assertThat(calls.clicks).containsExactly(SampleData.TERIYAKI_ID)
    }

    @Test
    fun botaoRemoverTemDescricaoComONomeEAlvoDe48dp() {
        show(success(noted))

        rule.onNodeWithContentDescription("Remover Apple & Blackberry Crumble do caderno")
            .assertIsDisplayed()
            .assertTouchTargetAtLeast(rule)
    }

    @Test
    fun removerPedeConfirmacaoAntesDeApagar() {
        show(success(noted, plain))

        rule.onNodeWithContentDescription("Remover Apple & Blackberry Crumble do caderno").performClick()

        rule.onNodeWithText("Remover do caderno?").assertIsDisplayed()
        rule.onNodeWithText("“Apple & Blackberry Crumble” e a sua anotação serão removidas do caderno.")
            .assertIsDisplayed()
        assertThat(calls.removed).isEmpty() // ainda nao removeu nada
    }

    @Test
    fun confirmarARemocaoChamaOnRemoveComOIdEFechaODialogo() {
        show(success(noted, plain))
        rule.onNodeWithContentDescription("Remover Apple & Blackberry Crumble do caderno").performClick()

        rule.onNodeWithText("Remover").performClick()

        assertThat(calls.removed).containsExactly(SampleData.CRUMBLE_ID)
        rule.onNodeWithText("Remover do caderno?").assertDoesNotExist()
    }

    @Test
    fun cancelarARemocaoNaoRemoveENemFechaOCadernoMantemOItem() {
        show(success(noted, plain))
        rule.onNodeWithContentDescription("Remover Apple & Blackberry Crumble do caderno").performClick()

        rule.onNodeWithText("Cancelar").performClick()

        assertThat(calls.removed).isEmpty()
        rule.onNodeWithText("Remover do caderno?").assertDoesNotExist()
        rule.onNodeWithText("Apple & Blackberry Crumble").assertIsDisplayed()
    }

    @Test
    fun botoesDoDialogoTemAlvoDe48dp() {
        show(success(noted))
        rule.onNodeWithContentDescription("Remover Apple & Blackberry Crumble do caderno").performClick()

        rule.onNodeWithText("Remover").assertTouchTargetAtLeast(rule)
        rule.onNodeWithText("Cancelar").assertTouchTargetAtLeast(rule)
    }

    @Test
    fun aposRemoverMostraAvisoComDesfazer() {
        show(success(plain).copy(lastRemoved = noted))

        rule.onNodeWithText("Receita removida do caderno").assertIsDisplayed()
        rule.onNodeWithText("Desfazer").assertIsDisplayed().performClick()
        rule.waitForIdle()

        assertThat(calls.undo).isEqualTo(1)
        assertThat(calls.removalShown).isEqualTo(0) // desfazer nao conta como "aviso consumido"
    }

    // ------------------------------------------------------------------ texto grande

    @Test
    fun comTextoEmDoisXFiltrosListaEBotoesContinuamAcessiveis() {
        show(success(noted, plain), LARGE_FONT_SCALE)

        rule.onNodeWithText("Todas").assertIsDisplayed().assertTouchTargetAtLeast(rule)
        rule.onNodeWithText("Com anotação").assertIsDisplayed().assertTouchTargetAtLeast(rule)
        rule.onNodeWithTag(TestTags.NOTEBOOK_LIST).assertIsDisplayed()
        rule.onNodeWithText("Apple & Blackberry Crumble").assertIsDisplayed().assertTouchTargetAtLeast(rule)
        rule.onNodeWithContentDescription("Remover Apple & Blackberry Crumble do caderno")
            .assertIsDisplayed()
            .assertTouchTargetAtLeast(rule)
        // A segunda receita fica alcancavel rolando a lista.
        rule.onNodeWithTag(TestTags.NOTEBOOK_LIST).performScrollToNode(hasText("Teriyaki Chicken Casserole"))
        rule.onNodeWithText("Teriyaki Chicken Casserole").assertIsDisplayed()
    }

    @Test
    fun comTextoEmDoisXODialogoDeRemocaoMostraTextoEBotoesCompletos() {
        show(success(noted), LARGE_FONT_SCALE)
        rule.onNodeWithContentDescription("Remover Apple & Blackberry Crumble do caderno").performClick()

        rule.onNodeWithText("Remover do caderno?").assertIsDisplayed()
        rule.onNodeWithText("Remover").assertIsDisplayed().assertTouchTargetAtLeast(rule)
        rule.onNodeWithText("Cancelar").assertIsDisplayed().assertTouchTargetAtLeast(rule)
    }

    @Test
    fun comTextoEmDoisXOEstadoVazioMantemAAcao() {
        show(NotebookUiState(content = NotebookContent.Empty(filtered = false)), LARGE_FONT_SCALE)

        rule.onNodeWithText("Seu caderno está vazio").assertIsDisplayed()
        rule.onNodeWithText("Explorar receitas").assertIsDisplayed().assertTouchTargetAtLeast(rule)
    }

    @Test
    fun comTextoEmDoisXOEstadoDeErroMantemOBotao() {
        show(NotebookUiState(content = NotebookContent.Error(UiError.NETWORK)), LARGE_FONT_SCALE)

        rule.onNodeWithText("Tentar novamente").assertIsDisplayed().assertTouchTargetAtLeast(rule)
    }
}
