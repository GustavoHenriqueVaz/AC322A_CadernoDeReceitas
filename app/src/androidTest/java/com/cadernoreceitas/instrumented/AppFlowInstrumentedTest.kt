package com.cadernoreceitas.instrumented

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
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
import com.cadernoreceitas.MainActivity
import com.cadernoreceitas.ui.components.TestTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fluxos principais em um Android de verdade (emulador ou aparelho):
 *   ./gradlew :app:connectedDebugAndroidTest
 * Usa a `MainActivity` real com um [FakeContainer] (sem internet), entao e deterministico.
 * Equivalente instrumentado de `AppSmokeTest` (src/test, Robolectric).
 */
@RunWith(AndroidJUnit4::class)
class AppFlowInstrumentedTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private lateinit var fakes: FakeContainer
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun installFakes() {
        fakes = FakeContainer()
        FakeContainer.install(fakes)
    }

    @After
    fun closeActivity() {
        scenario?.close()
    }

    private fun launch(deepLink: String? = null) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = if (deepLink == null) {
            Intent(context, MainActivity::class.java)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).setClass(context, MainActivity::class.java)
        }
        scenario = ActivityScenario.launch(intent)
    }

    private fun waitForTag(tag: String) = rule.waitUntil(10_000) {
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForText(text: String) = rule.waitUntil(10_000) {
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun stateDescriptionIs(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test
    fun appOpensOnExploreWithFeaturedRecipes() {
        launch()

        waitForText("Receitas em destaque")
        rule.onNodeWithText("Explorar receitas").assertIsDisplayed()
        rule.onNodeWithText(Sample.teriyaki.name).assertIsDisplayed()
        rule.onNodeWithText(Sample.crumble.name).assertIsDisplayed()
    }

    @Test
    fun exploreDetailSaveNotebookAndAnnotateFlow() {
        launch()
        waitForText(Sample.teriyaki.name)

        // Explorar -> detalhes -> salvar.
        rule.onNodeWithText(Sample.teriyaki.name).performClick()
        waitForTag(TestTags.BUTTON_TOGGLE_SAVED)
        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED).performClick()
        waitForText("Receita salva no caderno")
        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED).assert(stateDescriptionIs("Salva no caderno"))

        // Voltar e abrir a aba "Meu caderno".
        rule.onNodeWithContentDescription("Voltar").performClick()
        waitForText("Explorar receitas")
        rule.onNodeWithText("Meu caderno").performClick()
        waitForTag(TestTags.NOTEBOOK_LIST)
        rule.onNodeWithText(Sample.teriyaki.name).assertIsDisplayed()

        // Anotar: formulario valido volta para os detalhes ja com a anotacao.
        rule.onNodeWithText(Sample.teriyaki.name).performClick()
        waitForTag(TestTags.BUTTON_TOGGLE_SAVED)
        rule.onNodeWithText("Anotar").performScrollTo().performClick()
        waitForTag(TestTags.FIELD_COMMENT)
        rule.onNodeWithContentDescription("4 estrelas").performClick()
        rule.onNodeWithTag(TestTags.FIELD_COMMENT).performTextInput("Ficou otimo, faria de novo!")
        rule.onNodeWithTag(TestTags.FIELD_MINUTES).performTextInput("45")
        rule.onNodeWithTag(TestTags.BUTTON_SAVE_NOTE).performScrollTo().performClick()

        waitForText("Editar anotação")
        rule.onNodeWithContentDescription("Nota 4 de 5").assertIsDisplayed()
        rule.onNodeWithText("45 min de preparo").assertIsDisplayed()
        assertEquals(4, fakes.recipeRepository.saved.value.single().rating)
    }

    @Test
    fun invalidNoteFormShowsErrorsAndDoesNotSave() {
        launch()
        waitForText(Sample.teriyaki.name)
        rule.onNodeWithText(Sample.teriyaki.name).performClick()
        waitForTag(TestTags.BUTTON_TOGGLE_SAVED)
        rule.onNodeWithText("Anotar").performScrollTo().performClick()
        waitForTag(TestTags.BUTTON_SAVE_NOTE)

        rule.onNodeWithTag(TestTags.BUTTON_SAVE_NOTE).performScrollTo().performClick()

        waitForText("Escolha de 1 a 5 estrelas.")
        rule.onNodeWithText("Escreva uma observação sobre a receita.").assertIsDisplayed()
        assertTrue(fakes.recipeRepository.saved.value.isEmpty())
    }

    @Test
    fun recreatingTheActivityKeepsScreenAndState() {
        launch()
        waitForText(Sample.teriyaki.name)
        rule.onNodeWithText(Sample.teriyaki.name).performClick()
        waitForTag(TestTags.BUTTON_TOGGLE_SAVED)
        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED).performClick()
        waitForText("Receita salva no caderno")
        val loadsBefore = fakes.recipeRepository.recipeLoads

        scenario!!.recreate() // equivale a girar o aparelho

        waitForTag(TestTags.BUTTON_TOGGLE_SAVED)
        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED).assert(stateDescriptionIs("Salva no caderno"))
        assertEquals(loadsBefore, fakes.recipeRepository.recipeLoads) // ViewModel reaproveitado

        rule.onNodeWithContentDescription("Voltar").performClick()
        waitForText("Explorar receitas") // a pilha de navegacao tambem sobreviveu
    }

    @Test
    fun validDeepLinkOpensTheRecipeDirectly() {
        launch("cadernoreceitas://receita/${Sample.TERIYAKI_ID}")

        waitForTag(TestTags.BUTTON_TOGGLE_SAVED)
        rule.onNodeWithText("Ingredientes").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun invalidDeepLinkIsIgnored() {
        launch("cadernoreceitas://receita/abc")

        waitForText("Receitas em destaque")
        rule.onNodeWithTag(TestTags.BUTTON_TOGGLE_SAVED).assertDoesNotExist()
        assertEquals(0, fakes.recipeRepository.recipeLoads)
    }

    @Test
    fun networkFailureShowsErrorAndRetryRecovers() {
        fakes.recipeRepository.failSearch = true
        launch()

        waitForTag(TestTags.ERROR)
        fakes.recipeRepository.failSearch = false
        rule.onNodeWithText("Tentar novamente").performClick()

        waitForTag(TestTags.RECIPE_LIST)
        rule.onNodeWithText(Sample.teriyaki.name).assertIsDisplayed()
    }
}
