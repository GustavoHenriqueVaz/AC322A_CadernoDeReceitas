package com.cadernoreceitas.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cadernoreceitas.MainActivity
import com.cadernoreceitas.domain.model.DataSource
import com.cadernoreceitas.domain.model.RecipeException
import com.cadernoreceitas.domain.model.RecipeNote
import com.cadernoreceitas.ui.components.TestTags
import com.cadernoreceitas.uitesting.FakeAppContainer
import com.cadernoreceitas.uitesting.SampleData
import com.cadernoreceitas.uitesting.TEST_DEVICE
import com.cadernoreceitas.uitesting.TEST_SDK
import com.cadernoreceitas.uitesting.TestCadernoApp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Teste ponta a ponta (JVM + Robolectric): sobe a `MainActivity` de verdade com a Navigation 3,
 * ViewModels reais e o [FakeAppContainer] no lugar de rede/Room/DataStore/WorkManager.
 * Cobre o caminho principal do usuario, a recriacao da Activity (rotacao) e o deep link.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [TEST_SDK], qualifiers = TEST_DEVICE, application = TestCadernoApp::class)
class AppSmokeTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    private val app: TestCadernoApp get() = ApplicationProvider.getApplicationContext()
    private val fakes: FakeAppContainer get() = app.fakes
    private val repo get() = fakes.recipeRepository

    private val teriyaki = SampleData.teriyaki.name
    private val crumble = SampleData.crumble.name

    // Sem `scenario.close()` no @After: sob Robolectric (looper PAUSED) o close() bloqueia a thread
    // principal esperando um DESTROYED que so chegaria se ela processasse o looper (trava ~45 s por
    // teste). O proprio Robolectric destroi as Activities ao encerrar cada teste.

    // ------------------------------------------------------------------ utilitarios

    private fun intent(deepLink: String? = null): Intent {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return if (deepLink == null) {
            Intent(context, MainActivity::class.java)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).setClass(context, MainActivity::class.java)
        }
    }

    private fun launchApp(deepLink: String? = null) {
        scenario = ActivityScenario.launch(intent(deepLink))
        rule.waitForIdle()
    }

    /** Anda o relogio do Looper principal (usado pelo `delay` das coroutines em `Dispatchers.Main`). */
    private fun advanceMainClock(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
        rule.waitForIdle()
    }

    private fun waitForTag(tag: String) = rule.waitUntil(WAIT_MS) {
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForText(text: String) = rule.waitUntil(WAIT_MS) {
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForGone(text: String) = rule.waitUntil(WAIT_MS) {
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()
    }

    private fun waitForDetail() = waitForTag(TestTags.BUTTON_TOGGLE_SAVED)

    private fun waitForExplore() = waitForText("Explorar receitas")

    private fun stateDescriptionIs(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private fun openTeriyakiFromExplore() {
        rule.onNodeWithText(teriyaki).performClick()
        waitForDetail()
    }

    private fun onNote(block: (RecipeNote) -> Unit) = block(repo.saveNoteCalls.single().note)

    // ------------------------------------------------------------------ fluxo principal

    @Test
    fun appAbreEmExplorarEMostraOsDestaques() {
        launchApp()

        rule.onNodeWithText("Explorar receitas").assertIsDisplayed()
        rule.onNodeWithText("Receitas em destaque").assertIsDisplayed()
        rule.onNodeWithText(teriyaki).assertIsDisplayed()
        rule.onNodeWithText(crumble).assertIsDisplayed()
        // Receita do dia e categorias carregam em paralelo e tambem aparecem.
        rule.onNodeWithText("Receita do dia").assertIsDisplayed()
        rule.onNodeWithText("Spicy Arrabiata Penne").assertIsDisplayed()
        rule.onNodeWithText("Dessert").assertIsDisplayed()
        // Barra de navegacao com as duas abas.
        rule.onNodeWithText("Explorar").assertIsDisplayed()
        rule.onNodeWithText("Meu caderno").assertIsDisplayed()
        // Uma unica busca de destaques.
        assertThat(repo.searchCalls.map { it.query }).containsExactly("")
    }

    @Test
    fun fluxoCompletoExplorarDetalhesSalvarCadernoEAnotar() {
        launchApp()

        // 1) Explorar -> toca numa receita -> abre os detalhes.
        openTeriyakiFromExplore()
        rule.onNodeWithText("Ingredientes").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED).assert(stateDescriptionIs("Não salva"))
        assertThat(repo.recipeCalls.map { it.id }).containsExactly(SampleData.TERIYAKI_ID)

        // 2) Salvar no caderno.
        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED).performClick()
        waitForText("Receita salva no caderno")
        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED).assert(stateDescriptionIs("Salva no caderno"))
        assertThat(repo.saveRecipeCalls).containsExactly(SampleData.teriyaki)

        // 3) Voltar: a lista de Explorar ja mostra a receita como salva (estado reativo entre telas).
        rule.onNodeWithContentDescription("Voltar").performClick()
        waitForExplore()
        rule.onNodeWithText(teriyaki).assert(stateDescriptionIs("Salva no caderno"))

        // 4) Aba "Meu caderno" mostra a receita salva.
        rule.onNodeWithText("Meu caderno").performClick()
        waitForTag(TestTags.NOTEBOOK_LIST)
        rule.onNodeWithText(teriyaki).assertIsDisplayed()
        rule.onNodeWithText("Toque para ver e adicionar sua anotação").assertIsDisplayed()

        // 5) Abre a receita pelo caderno e escolhe "Anotar".
        rule.onNodeWithText(teriyaki).performClick()
        waitForDetail()
        rule.onNodeWithText("Anotar").performScrollTo().performClick()
        waitForTag(TestTags.FIELD_COMMENT)

        // 6) Formulario invalido: mostra os erros e NAO grava.
        rule.onNodeWithTag(TestTags.BUTTON_SAVE_NOTE).performScrollTo().performClick()
        waitForText("Escolha de 1 a 5 estrelas.")
        rule.onNodeWithText("Escreva uma observação sobre a receita.").assertIsDisplayed()
        assertThat(repo.saveNoteCalls).isEmpty()

        // 7) Preenche e salva: volta para os detalhes ja com a anotacao.
        rule.onNodeWithContentDescription("4 estrelas").performClick()
        rule.onNodeWithTag(TestTags.FIELD_COMMENT).performTextInput("  Ficou otimo, faria de novo!  ")
        rule.onNodeWithTag(TestTags.FIELD_MINUTES).performTextInput("45")
        rule.onNodeWithTag(TestTags.BUTTON_SAVE_NOTE).performScrollTo().performClick()
        waitForText("Editar anotação")

        onNote {
            assertThat(it).isEqualTo(RecipeNote(rating = 4, comment = "Ficou otimo, faria de novo!", prepMinutes = 45, photoPath = null))
        }
        rule.onNodeWithContentDescription("Nota 4 de 5").assertIsDisplayed()
        rule.onNodeWithText("45 min de preparo").assertIsDisplayed()
        rule.onNodeWithText("Ficou otimo, faria de novo!").assertIsDisplayed()

        // 8) Voltar leva ao caderno (a pilha era Caderno -> Detalhes), que mostra a nota.
        rule.onNodeWithContentDescription("Voltar").performClick()
        waitForTag(TestTags.NOTEBOOK_LIST)
        rule.onNodeWithContentDescription("Nota 4 de 5").assertIsDisplayed()
        rule.onNodeWithText("Ficou otimo, faria de novo!").assertIsDisplayed()
    }

    @Test
    fun cadernoVazioMostraAcaoQueLevaDeVoltaParaExplorar() {
        launchApp()

        rule.onNodeWithText("Meu caderno").performClick()
        waitForText("Seu caderno está vazio")
        rule.onNodeWithTag(TestTags.EMPTY).assertIsDisplayed()

        rule.onNodeWithText("Explorar receitas").performClick() // botao do estado vazio
        waitForText("Receitas em destaque")
        rule.onNodeWithText(teriyaki).assertIsDisplayed()
    }

    @Test
    fun removerDoCadernoPedeConfirmacaoEPermiteDesfazer() {
        repo.savedFlow.value = listOf(SampleData.saved(SampleData.teriyaki, rating = 5, comment = "Perfeita para o domingo"))
        launchApp()
        rule.onNodeWithText("Meu caderno").performClick()
        waitForTag(TestTags.NOTEBOOK_LIST)

        rule.onNodeWithContentDescription("Remover $teriyaki do caderno").performClick()
        rule.onNodeWithText("Remover do caderno?").assertIsDisplayed()
        rule.onNodeWithText("Remover").performClick()

        waitForText("Seu caderno está vazio")
        waitForText("Receita removida do caderno")
        assertThat(repo.removeCalls).containsExactly(SampleData.TERIYAKI_ID)

        rule.onNodeWithText("Desfazer").performClick()
        waitForTag(TestTags.NOTEBOOK_LIST)
        rule.onNodeWithText(teriyaki).assertIsDisplayed()
        rule.onNodeWithText("Perfeita para o domingo").assertIsDisplayed() // a anotacao volta junto
    }

    // ------------------------------------------------------------------ busca / erro / offline

    @Test
    fun buscarPorTextoEsperaODebounceEMostraOsResultados() {
        repo.searchResults["chicken"] = listOf(SampleData.teriyaki.toSummary())
        launchApp()

        rule.onNodeWithTag(TestTags.SEARCH_FIELD).performTextInput("chicken")
        // O campo reage na hora, mas a busca so sai depois da pausa de digitacao.
        rule.onNodeWithTag(TestTags.SEARCH_FIELD).assertTextContains("chicken")
        assertThat(repo.searchCalls.map { it.query }).containsExactly("")

        advanceMainClock(500)

        rule.waitUntil(WAIT_MS) { repo.searchCalls.map { it.query } == listOf("", "chicken") }
        waitForText("Resultados para “chicken”")
        rule.onNodeWithText(teriyaki).assertIsDisplayed()
        rule.onNodeWithText("Receita do dia").assertDoesNotExist()
    }

    @Test
    fun falhaDeRedeMostraErroETentarNovamenteRecarrega() {
        repo.searchFailure = RecipeException(RecipeException.Kind.NETWORK)
        launchApp()

        waitForTag(TestTags.ERROR)
        rule.onNodeWithText("Algo deu errado").assertIsDisplayed()

        repo.searchFailure = null
        rule.onNodeWithText("Tentar novamente").performClick()

        waitForTag(TestTags.RECIPE_LIST)
        rule.onNodeWithText(teriyaki).assertIsDisplayed()
        assertThat(repo.searchCalls.last().forceRefresh).isTrue()
    }

    @Test
    fun dadosDoCacheVencidoMostramAFaixaOffline() {
        repo.source = DataSource.CACHE_STALE
        launchApp()

        waitForTag(TestTags.OFFLINE_BANNER)
        rule.onNodeWithText(teriyaki).assertIsDisplayed()
    }

    @Test
    fun ligarOLembreteComPermissaoAgendaEAvisa() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        launchApp()

        rule.onNodeWithText("Lembrete diário").performScrollTo().performClick()

        waitForText("Lembrete diário ativado")
        assertThat(fakes.reminderScheduler.enableCount).isEqualTo(1)
        assertThat(fakes.userPreferences.reminderCalls).containsExactly(true)
    }

    // ------------------------------------------------------------------ recriacao da Activity (rotacao)

    @Test
    fun recriarAActivityMantemOsDetalhesEOEstadoSemRecarregar() {
        launchApp()
        openTeriyakiFromExplore()
        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED).performClick()
        waitForText("Receita salva no caderno")
        assertThat(repo.recipeCalls).hasSize(1)

        scenario!!.recreate()
        rule.waitForIdle()

        // Continua nos detalhes, ainda salva, e o ViewModel foi reaproveitado (nenhuma nova carga).
        waitForDetail()
        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED).assert(stateDescriptionIs("Salva no caderno"))
        rule.onNodeWithText("Ingredientes").performScrollTo().assertIsDisplayed()
        assertThat(repo.recipeCalls).hasSize(1)
        assertThat(repo.saveRecipeCalls).hasSize(1)

        // A pilha de navegacao tambem sobreviveu: Voltar leva para Explorar.
        rule.onNodeWithContentDescription("Voltar").performClick()
        waitForExplore()
        rule.onNodeWithText(teriyaki).assert(stateDescriptionIs("Salva no caderno"))
    }

    @Test
    fun recriarAActivityMantemOTextoDigitadoNaBusca() {
        launchApp()
        rule.onNodeWithTag(TestTags.SEARCH_FIELD).performTextInput("pasta")

        scenario!!.recreate()
        rule.waitForIdle()

        rule.onNodeWithTag(TestTags.SEARCH_FIELD).assertTextContains("pasta")
    }

    @Test
    fun recriarAActivityNoFormularioMantemOQueFoiDigitado() {
        launchApp()
        openTeriyakiFromExplore()
        rule.onNodeWithText("Anotar").performScrollTo().performClick()
        waitForTag(TestTags.FIELD_COMMENT)
        rule.onNodeWithContentDescription("5 estrelas").performClick()
        rule.onNodeWithTag(TestTags.FIELD_COMMENT).performTextInput("Texto em andamento")
        rule.onNodeWithTag(TestTags.FIELD_MINUTES).performTextInput("30")

        scenario!!.recreate()
        rule.waitForIdle()

        waitForTag(TestTags.FIELD_COMMENT)
        rule.onNodeWithTag(TestTags.FIELD_COMMENT).assertTextContains("Texto em andamento")
        rule.onNodeWithTag(TestTags.FIELD_MINUTES).assertTextContains("30")
        rule.onNodeWithContentDescription("5 estrelas").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        assertThat(repo.saveNoteCalls).isEmpty() // nada foi gravado so por girar a tela

        // O formulario ainda tem alteracoes nao salvas: voltar pergunta antes de descartar.
        rule.onNodeWithContentDescription("Voltar").performClick()
        rule.onNodeWithText("Descartar alterações?").assertIsDisplayed()
    }

    @Test
    fun recriarAActivityNoCadernoMantemAAbaSelecionada() {
        repo.savedFlow.value = listOf(SampleData.saved(SampleData.crumble, rating = 5))
        launchApp()
        rule.onNodeWithText("Meu caderno").performClick()
        waitForTag(TestTags.NOTEBOOK_LIST)

        scenario!!.recreate()
        rule.waitForIdle()

        waitForTag(TestTags.NOTEBOOK_LIST)
        rule.onNodeWithText(crumble).assertIsDisplayed()
    }

    // ------------------------------------------------------------------ deep link

    @Test
    fun deepLinkValidoAbreDiretoOsDetalhes() {
        launchApp("cadernoreceitas://receita/${SampleData.TERIYAKI_ID}")

        waitForDetail()
        rule.onNodeWithText("Ingredientes").performScrollTo().assertIsDisplayed()
        assertThat(repo.recipeCalls.map { it.id }).containsExactly(SampleData.TERIYAKI_ID)

        // Mesmo vindo de fora, Voltar leva a tela de Explorar (nao fecha o app).
        rule.onNodeWithContentDescription("Voltar").performClick()
        waitForExplore()
        rule.onNodeWithText("Receitas em destaque").assertIsDisplayed()
    }

    /** Abre o app com um deep link que NAO deve ser aceito: cai em Explorar e nenhuma receita e carregada. */
    private fun assertDeepLinkIgnored(uri: String) {
        launchApp(uri)

        waitForExplore()
        rule.onNodeWithText("Receitas em destaque").assertIsDisplayed()
        assertThat(repo.recipeCalls).isEmpty()
        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED).assertDoesNotExist()
    }

    @Test
    fun deepLinkComIdNaoNumericoEIgnorado() = assertDeepLinkIgnored("cadernoreceitas://receita/abc")

    @Test
    fun deepLinkComIdMisturadoEIgnorado() = assertDeepLinkIgnored("cadernoreceitas://receita/12abc")

    @Test
    fun deepLinkSemIdEIgnorado() = assertDeepLinkIgnored("cadernoreceitas://receita/")

    @Test
    fun deepLinkComCaminhoExtraEIgnorado() = assertDeepLinkIgnored("cadernoreceitas://receita/1/2")

    @Test
    fun deepLinkComHostDiferenteEIgnorado() = assertDeepLinkIgnored("cadernoreceitas://outro/52772")

    @Test
    fun deepLinkComEsquemaDiferenteEIgnorado() = assertDeepLinkIgnored("https://receita/52772")

    @Test
    fun deepLinkComOAppJaAbertoTrocaParaOsDetalhes() {
        launchApp()
        waitForExplore()

        val link = intent("cadernoreceitas://receita/${SampleData.CRUMBLE_ID}")
        scenario!!.onActivity { activity ->
            InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity, link)
        }
        rule.waitForIdle()

        waitForDetail()
        assertThat(repo.recipeCalls.map { it.id }).contains(SampleData.CRUMBLE_ID)
        rule.onAllNodesWithTag(TestTags.BUTTON_TOGGLE_SAVED).assertCountEquals(1)

        rule.onNodeWithContentDescription("Voltar").performClick()
        waitForExplore()
    }

    @Test
    fun deepLinkNaoReabreOsDetalhesDepoisDeRecriarAActivity() {
        launchApp("cadernoreceitas://receita/${SampleData.TERIYAKI_ID}")
        waitForDetail()
        rule.onNodeWithContentDescription("Voltar").performClick()
        waitForExplore()

        scenario!!.recreate()
        rule.waitForIdle()

        // O Intent original continua na Activity, mas so e interpretado na abertura "de verdade".
        waitForExplore()
        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED).assertDoesNotExist()
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
