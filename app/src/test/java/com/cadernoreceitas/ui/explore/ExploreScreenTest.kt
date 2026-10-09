package com.cadernoreceitas.ui.explore

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadernoreceitas.R
import com.cadernoreceitas.domain.model.Category
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

/** [ExploreScreen] com estados construidos a mao (sem ViewModel). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [TEST_SDK], qualifiers = TEST_DEVICE, application = TestCadernoApp::class)
class ExploreScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class Calls {
        val queries = mutableListOf<String>()
        var cleared = 0
        val categories = mutableListOf<String?>()
        var retries = 0
        val recipeClicks = mutableListOf<String>()
        var messagesShown = 0
        val reminderToggles = mutableListOf<Boolean>()
    }

    private val calls = Calls()

    private val teriyaki = SampleData.teriyaki.toSummary()
    private val crumble = SampleData.crumble.toSummary()
    private val arrabiata = SampleData.arrabiata.toSummary()

    private fun state(
        content: ExploreContent = ExploreContent.Success(listOf(teriyaki, crumble), isOffline = false),
        input: SearchInput = SearchInput(),
        categories: List<Category> = emptyList(),
        savedIds: Set<String> = emptySet(),
        daily: DailyState = DailyState.Ready(arrabiata),
        reminderEnabled: Boolean = false,
        message: ExploreMessage? = null,
    ) = ExploreUiState(
        input = input,
        content = content,
        categories = categories,
        savedIds = savedIds,
        daily = daily,
        reminderEnabled = reminderEnabled,
        message = message,
    )

    private fun show(state: ExploreUiState, fontScale: Float = 1f) = showWith(fontScale) { state }

    /** Variante em que o estado e lido dentro da composicao (permite trocar o estado no meio do teste). */
    private fun showWith(fontScale: Float = 1f, stateProvider: () -> ExploreUiState) {
        rule.setCadernoContent(fontScale) {
            ExploreScreen(
                state = stateProvider(),
                onQueryChange = { calls.queries += it },
                onClearSearch = { calls.cleared++ },
                onCategorySelected = { calls.categories += it },
                onRetry = { calls.retries++ },
                onRecipeClick = { calls.recipeClicks += it },
                onMessageShown = { calls.messagesShown++ },
                onReminderToggle = { calls.reminderToggles += it },
            )
        }
    }

    private fun stateDescriptionIs(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    // ------------------------------------------------------------------ os quatro estados

    @Test
    fun estadoCarregandoMostraSoOIndicador() {
        show(state(content = ExploreContent.Loading))

        rule.onNodeWithText("Explorar receitas").assertIsDisplayed() // barra de titulo
        rule.onNodeWithTag(TestTags.LOADING).assertIsDisplayed()
        rule.onNodeWithTag(TestTags.RECIPE_LIST).assertDoesNotExist()
        rule.onNodeWithTag(TestTags.ERROR).assertDoesNotExist()
        rule.onNodeWithTag(TestTags.EMPTY).assertDoesNotExist()
    }

    @Test
    fun estadoComConteudoMostraListaTituloEContagem() {
        show(state())

        rule.onNodeWithTag(TestTags.RECIPE_LIST).assertIsDisplayed()
        rule.onNodeWithText("Receitas em destaque").assertIsDisplayed()
        rule.onNodeWithText("2 receitas").assertIsDisplayed()
        rule.onNodeWithText("Teriyaki Chicken Casserole").assertIsDisplayed()
        rule.onNodeWithText("Apple & Blackberry Crumble").assertIsDisplayed()
        rule.onNodeWithTag(TestTags.LOADING).assertDoesNotExist()
    }

    @Test
    fun contagemUsaOSingularQuandoHaUmaReceita() {
        show(state(content = ExploreContent.Success(listOf(teriyaki), isOffline = false)))

        rule.onNodeWithText("1 receita").assertIsDisplayed()
    }

    @Test
    fun estadoVazioExplicaEOfereceVoltarAosDestaques() {
        show(state(content = ExploreContent.Empty(SearchInput("zzz"))))

        rule.onNodeWithTag(TestTags.EMPTY).assertIsDisplayed()
        rule.onNodeWithText("Nenhuma receita encontrada").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.explore_empty_message)).assertIsDisplayed()

        rule.onNodeWithText("Ver receitas em destaque").performClick()

        assertThat(calls.cleared).isEqualTo(1)
    }

    @Test
    fun estadoDeErroDeRedeMostraMensagemEPermiteTentarDeNovo() {
        show(state(content = ExploreContent.Error(UiError.NETWORK)))

        rule.onNodeWithTag(TestTags.ERROR).assertIsDisplayed()
        rule.onNodeWithText("Algo deu errado").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.error_network)).assertIsDisplayed()

        rule.onNodeWithText("Tentar novamente").performClick()

        assertThat(calls.retries).isEqualTo(1)
    }

    @Test
    fun cadaTipoDeErroMostraASuaMensagem() {
        val messages = mapOf(
            UiError.NETWORK to R.string.error_network,
            UiError.SERVER to R.string.error_server,
            UiError.INVALID_DATA to R.string.error_invalid,
            UiError.NOT_FOUND to R.string.error_not_found,
            UiError.UNKNOWN to R.string.error_unknown,
        )
        var current by mutableStateOf(UiError.NETWORK)
        showWith { state(content = ExploreContent.Error(current)) }

        messages.forEach { (error, res) ->
            rule.runOnIdle { current = error }
            rule.onNodeWithText(context.getString(res)).assertIsDisplayed()
        }
    }

    @Test
    fun estadoComConteudoNaoMostraFaixaOfflineQuandoOnline() {
        show(state())

        rule.onNodeWithTag(TestTags.OFFLINE_BANNER).assertDoesNotExist()
    }

    @Test
    fun faixaOfflineApareceQuandoOsDadosVemDoCache() {
        show(state(content = ExploreContent.Success(listOf(teriyaki), isOffline = true)))

        rule.onNodeWithTag(TestTags.OFFLINE_BANNER).assertIsDisplayed()
        rule.onNodeWithText("Sem conexão. Mostrando dados salvos no aparelho.").assertIsDisplayed()
        // E a lista continua utilizavel.
        rule.onNodeWithText("Teriyaki Chicken Casserole").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ titulos conforme a busca

    @Test
    fun resultadosDeUmaBuscaTemTituloProprioESemReceitaDoDia() {
        show(state(input = SearchInput(query = "chicken")))

        rule.onNodeWithText("Resultados para “chicken”").assertIsDisplayed()
        rule.onNodeWithText("Receita do dia").assertDoesNotExist()
    }

    @Test
    fun resultadosDeUmaCategoriaTemTituloProprio() {
        show(state(input = SearchInput(category = "Seafood")))

        rule.onNodeWithText("Categoria: Seafood").assertIsDisplayed()
        rule.onNodeWithText("Receita do dia").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ campo de busca

    @Test
    fun digitarNoCampoEnviaOTexto() {
        show(state())

        rule.onNodeWithTag(TestTags.SEARCH_FIELD).performTextInput("pasta")

        assertThat(calls.queries).containsExactly("pasta")
    }

    @Test
    fun campoMostraOTextoDoEstado() {
        show(state(input = SearchInput(query = "pasta")))

        rule.onNodeWithTag(TestTags.SEARCH_FIELD).assertTextContains("pasta")
    }

    @Test
    fun botaoLimparSoApareceComTextoEFunciona() {
        show(state(input = SearchInput(query = "pasta")))

        rule.onNodeWithContentDescription("Limpar busca").assertIsDisplayed().performClick()

        assertThat(calls.cleared).isEqualTo(1)
    }

    @Test
    fun semTextoNaoHaBotaoLimpar() {
        show(state())

        rule.onNodeWithContentDescription("Limpar busca").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ categorias

    @Test
    fun categoriasAparecemComoChipsEEnviamAEscolha() {
        show(state(categories = SampleData.categories))

        rule.onNodeWithText("Chicken").assertIsDisplayed()
        rule.onNodeWithText("Dessert").assertIsDisplayed()

        rule.onNodeWithText("Seafood").performClick()

        assertThat(calls.categories).containsExactly("Seafood")
    }

    @Test
    fun soOChipDaCategoriaAtualFicaSelecionado() {
        show(state(categories = SampleData.categories, input = SearchInput(category = "Dessert")))

        rule.onNodeWithText("Dessert").assertIsSelected()
        rule.onNodeWithText("Chicken").assertIsNotSelected()
        rule.onNodeWithText("Seafood").assertIsNotSelected()
    }

    @Test
    fun semCategoriasNaoHaFaixaDeChips() {
        show(state(categories = emptyList()))

        rule.onNodeWithText("Seafood").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ cartoes

    @Test
    fun tocarNumaReceitaEnviaOId() {
        show(state())

        rule.onNodeWithText("Apple & Blackberry Crumble").performClick()

        assertThat(calls.recipeClicks).containsExactly(SampleData.CRUMBLE_ID)
    }

    @Test
    fun receitaSalvaTemEstadoSalvaNoCadernoENaoSalvaNao() {
        show(state(savedIds = setOf(SampleData.TERIYAKI_ID)))

        rule.onNodeWithText("Teriyaki Chicken Casserole").assert(stateDescriptionIs("Salva no caderno"))
        rule.onNodeWithText("Apple & Blackberry Crumble")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    // ------------------------------------------------------------------ receita do dia / lembrete

    @Test
    fun receitaDoDiaMostraOCartaoETocarAbreOId() {
        show(state(daily = DailyState.Ready(arrabiata)))

        rule.onNodeWithText("Receita do dia").assertIsDisplayed()
        rule.onNodeWithText("Spicy Arrabiata Penne").assertIsDisplayed().performClick()

        assertThat(calls.recipeClicks).containsExactly(SampleData.ARRABIATA_ID)
    }

    @Test
    fun receitaDoDiaCarregandoMostraAviso() {
        show(state(daily = DailyState.Loading))

        rule.onNodeWithText("Receita do dia").assertIsDisplayed()
        rule.onNodeWithText("Carregando…").assertIsDisplayed()
    }

    @Test
    fun receitaDoDiaIndisponivelMostraMensagemSemErroDeTela() {
        show(state(daily = DailyState.Unavailable))

        rule.onNodeWithText("Não foi possível carregar a sugestão de hoje.").assertIsDisplayed()
        rule.onNodeWithTag(TestTags.ERROR).assertDoesNotExist() // falha silenciosa: a lista segue normal
        rule.onNodeWithText("Teriyaki Chicken Casserole").assertIsDisplayed()
    }

    @Test
    fun linhaDoLembreteEUmInterruptorQueFalaLigadoEDesligado() {
        show(state(reminderEnabled = false))

        rule.onNode(isToggleable())
            .assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        rule.onNodeWithText("Lembrete diário").assertIsDisplayed()
        rule.onNodeWithText("Receba uma sugestão todos os dias às 9h").assertIsDisplayed()
    }

    @Test
    fun tocarNaLinhaLigaOLembrete() {
        show(state(reminderEnabled = false))

        rule.onNodeWithText("Lembrete diário").performClick() // a linha inteira e o alvo, nao so o Switch

        assertThat(calls.reminderToggles).containsExactly(true)
    }

    @Test
    fun lembreteLigadoApareceLigadoETocarDesliga() {
        show(state(reminderEnabled = true))

        rule.onNode(isToggleable()).assertIsOn()
        rule.onNodeWithText("Lembrete diário").performClick()

        assertThat(calls.reminderToggles).containsExactly(false)
    }

    // ------------------------------------------------------------------ mensagens (snackbar)

    @Test
    fun mensagemDePermissaoNegadaApareceEEDepoisConsumida() {
        show(state(message = ExploreMessage.PERMISSION_DENIED))

        rule.onNodeWithText(context.getString(R.string.daily_permission_denied)).assertIsDisplayed()
        assertThat(calls.messagesShown).isEqualTo(0)

        rule.mainClock.advanceTimeBy(10_000) // o snackbar some sozinho
        rule.waitForIdle()

        assertThat(calls.messagesShown).isEqualTo(1)
    }

    @Test
    fun mensagensDeLembreteLigadoEDesligadoTemTextoProprio() {
        show(state(message = ExploreMessage.REMINDER_ON))

        rule.onNodeWithText("Lembrete diário ativado").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ texto grande (fonte em 200%)

    @Test
    fun comTextoEmDoisXOsElementosPrincipaisContinuamPresentes() {
        show(
            state(
                input = SearchInput(query = "pasta"),
                categories = SampleData.categories,
                content = ExploreContent.Success(listOf(teriyaki, crumble), isOffline = false),
            ),
            fontScale = LARGE_FONT_SCALE,
        )

        rule.onNodeWithTag(TestTags.SEARCH_FIELD).assertIsDisplayed()
        rule.onNodeWithContentDescription("Limpar busca").assertIsDisplayed().assertTouchTargetAtLeast(rule)
        rule.onNodeWithText("Chicken").assertIsDisplayed().assertTouchTargetAtLeast(rule)
        rule.onNodeWithTag(TestTags.RECIPE_LIST).assertIsDisplayed()
        rule.onNodeWithText("Teriyaki Chicken Casserole").assertIsDisplayed().assertTouchTargetAtLeast(rule)
    }

    @Test
    fun comTextoEmDoisXAReceitaDoDiaELembreteSaoAlcancaveis() {
        show(state(daily = DailyState.Ready(arrabiata)), fontScale = LARGE_FONT_SCALE)

        rule.onNodeWithText("Receita do dia").assertIsDisplayed()
        rule.onNodeWithTag(TestTags.RECIPE_LIST).performScrollToNode(hasText("Lembrete diário"))
        rule.onNodeWithText("Lembrete diário").assertIsDisplayed()
        rule.onNode(isToggleable()).assertTouchTargetAtLeast(rule)
        // Todas as receitas continuam acessiveis rolando a lista.
        rule.onNodeWithTag(TestTags.RECIPE_LIST).performScrollToNode(hasText("Apple & Blackberry Crumble"))
        rule.onNodeWithText("Apple & Blackberry Crumble").assertIsDisplayed()
    }

    @Test
    fun comTextoEmDoisXOEstadoDeErroMantemOBotaoDeNovaTentativa() {
        show(state(content = ExploreContent.Error(UiError.NETWORK)), fontScale = LARGE_FONT_SCALE)

        rule.onNodeWithText("Algo deu errado").assertIsDisplayed()
        rule.onNodeWithText("Tentar novamente").assertIsDisplayed().assertTouchTargetAtLeast(rule).performClick()
        assertThat(calls.retries).isEqualTo(1)
    }

    @Test
    fun comTextoEmDoisXOEstadoVazioMantemOBotaoDeAcao() {
        show(state(content = ExploreContent.Empty(SearchInput("zzz"))), fontScale = LARGE_FONT_SCALE)

        rule.onNodeWithText("Nenhuma receita encontrada").assertIsDisplayed()
        rule.onNodeWithText("Ver receitas em destaque").assertIsDisplayed().assertTouchTargetAtLeast(rule)
    }

    @Test
    fun comTextoEmDoisXOEstadoDeCarregamentoAindaMostraAMensagem() {
        show(state(content = ExploreContent.Loading), fontScale = LARGE_FONT_SCALE)

        rule.onNodeWithTag(TestTags.LOADING).assertIsDisplayed()
        rule.onNodeWithText("Carregando…").assertIsDisplayed()
    }
}
