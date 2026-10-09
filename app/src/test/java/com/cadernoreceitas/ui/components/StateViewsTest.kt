package com.cadernoreceitas.ui.components

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadernoreceitas.R
import com.cadernoreceitas.domain.model.RecipeException
import com.cadernoreceitas.uitesting.LARGE_FONT_SCALE
import com.cadernoreceitas.uitesting.TEST_DEVICE
import com.cadernoreceitas.uitesting.TEST_SDK
import com.cadernoreceitas.uitesting.TestCadernoApp
import com.cadernoreceitas.uitesting.assertTouchTargetAtLeast
import com.cadernoreceitas.uitesting.setCadernoContent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

/** Os quatro estados visuais (carregando / vazio / erro + faixa offline) e a traducao de erros. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [TEST_SDK], qualifiers = TEST_DEVICE, application = TestCadernoApp::class)
class StateViewsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun liveRegionPolite() = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
    private fun isHeading() = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    // ------------------------------------------------------------------ carregando

    @Test
    fun carregandoMostraProgressoEMensagemPadrao() {
        rule.setCadernoContent { LoadingState() }

        rule.onNodeWithTag(TestTags.LOADING).assertIsDisplayed()
        rule.onNodeWithText("Carregando…").assertIsDisplayed()
    }

    @Test
    fun carregandoAceitaMensagemPropria() {
        rule.setCadernoContent { LoadingState(message = "Buscando receitas") }

        rule.onNodeWithText("Buscando receitas").assertIsDisplayed()
    }

    @Test
    fun carregandoEAnunciadoPeloLeitorDeTela() {
        rule.setCadernoContent { LoadingState() }

        rule.onNodeWithTag(TestTags.LOADING).assert(liveRegionPolite())
    }

    // ------------------------------------------------------------------ vazio

    @Test
    fun vazioMostraTituloMensagemEAcao() {
        var clicks = 0
        rule.setCadernoContent {
            EmptyState(
                icon = Icons.Outlined.AutoStories,
                title = "Nada por aqui",
                message = "Volte mais tarde.",
                actionLabel = "Recarregar",
                onAction = { clicks++ },
            )
        }

        rule.onNodeWithTag(TestTags.EMPTY).assertIsDisplayed()
        rule.onNodeWithText("Nada por aqui").assertIsDisplayed().assert(isHeading())
        rule.onNodeWithText("Volte mais tarde.").assertIsDisplayed()
        rule.onNodeWithText("Recarregar").assertIsDisplayed().performClick()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun vazioSemAcaoNaoDesenhaBotao() {
        rule.setCadernoContent {
            EmptyState(icon = Icons.Outlined.AutoStories, title = "Nada por aqui", message = "Volte mais tarde.")
        }

        rule.onNodeWithTag(TestTags.EMPTY).assertIsDisplayed()
        rule.onNodeWithText("Recarregar").assertDoesNotExist()
    }

    @Test
    fun vazioComRotuloMasSemCallbackNaoDesenhaBotao() {
        rule.setCadernoContent {
            EmptyState(icon = Icons.Outlined.AutoStories, title = "Nada", message = "msg", actionLabel = "Acao")
        }

        rule.onNodeWithText("Acao").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ erro

    @Test
    fun erroMostraTituloMensagemETentarNovamente() {
        var retries = 0
        rule.setCadernoContent { ErrorState(message = "Sem internet", onRetry = { retries++ }) }

        rule.onNodeWithTag(TestTags.ERROR).assertIsDisplayed()
        rule.onNodeWithText("Algo deu errado").assertIsDisplayed().assert(isHeading())
        rule.onNodeWithText("Sem internet").assertIsDisplayed()
        rule.onNodeWithText("Tentar novamente").assertIsDisplayed().performClick()
        assertThat(retries).isEqualTo(1)
    }

    @Test
    fun erroEAnunciadoPeloLeitorDeTela() {
        rule.setCadernoContent { ErrorState(message = "Falhou", onRetry = {}) }

        rule.onNodeWithTag(TestTags.ERROR).assert(liveRegionPolite())
    }

    @Test
    fun botaoTentarNovamenteTemAlvoDeToqueDe48dp() {
        rule.setCadernoContent { ErrorState(message = "Falhou", onRetry = {}) }

        rule.onNodeWithText("Tentar novamente").assertTouchTargetAtLeast(rule)
    }

    // ------------------------------------------------------------------ offline

    @Test
    fun faixaOfflineExplicaQueOsDadosSaoDoAparelho() {
        rule.setCadernoContent { OfflineBanner() }

        rule.onNodeWithTag(TestTags.OFFLINE_BANNER).assertIsDisplayed()
        rule.onNodeWithText("Sem conexão. Mostrando dados salvos no aparelho.").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ texto grande

    @Test
    fun escalaDeFonteRealmenteAumentaOTexto() {
        var scale by mutableFloatStateOf(1f)
        rule.setContent {
            com.cadernoreceitas.uitesting.CadernoTestContent(fontScale = scale) {
                androidx.compose.material3.Text("Texto de teste")
            }
        }
        val normal = rule.onNodeWithText("Texto de teste").getBoundsInRoot().height

        rule.runOnIdle { scale = LARGE_FONT_SCALE }
        rule.waitForIdle()
        val large = rule.onNodeWithText("Texto de teste").getBoundsInRoot().height

        assertThat(large.value).isGreaterThan(normal.value * 1.5f)
    }

    @Test
    fun estadosContinuamUsaveisComTextoEmDoisX() {
        rule.setCadernoContent(fontScale = LARGE_FONT_SCALE) {
            ErrorState(message = context.getString(R.string.error_network), onRetry = {})
        }

        rule.onNodeWithText("Algo deu errado").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.error_network)).assertIsDisplayed()
        rule.onNodeWithText("Tentar novamente").assertIsDisplayed().assertTouchTargetAtLeast(rule)
    }

    @Test
    fun faixaOfflineQuebraEmVariasLinhasComTextoEmDoisX() {
        rule.setCadernoContent(fontScale = LARGE_FONT_SCALE) { OfflineBanner() }

        // O texto inteiro continua visivel (a faixa cresce em altura em vez de cortar).
        rule.onNodeWithText("Sem conexão. Mostrando dados salvos no aparelho.").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ traducao de erros

    @Test
    fun cadaTipoDeRecipeExceptionViraSeuUiError() {
        assertThat(RecipeException(RecipeException.Kind.NETWORK).toUiError()).isEqualTo(UiError.NETWORK)
        assertThat(RecipeException(RecipeException.Kind.SERVER).toUiError()).isEqualTo(UiError.SERVER)
        assertThat(RecipeException(RecipeException.Kind.INVALID_DATA).toUiError()).isEqualTo(UiError.INVALID_DATA)
        assertThat(RecipeException(RecipeException.Kind.NOT_FOUND).toUiError()).isEqualTo(UiError.NOT_FOUND)
    }

    @Test
    fun excecoesDesconhecidasViramUnknown() {
        assertThat(IOException("disco").toUiError()).isEqualTo(UiError.UNKNOWN)
        assertThat(IllegalStateException("bug").toUiError()).isEqualTo(UiError.UNKNOWN)
    }

    @Test
    fun cancelamentoNuncaViraErroDeTela() {
        // Transformar CancellationException em erro quebraria a cooperacao das coroutines.
        assertThrows(CancellationException::class.java) { CancellationException("cancelada").toUiError() }
    }

    @Test
    fun cadaUiErrorTemMensagemPropria() {
        val shown = mutableMapOf<UiError, String>()
        rule.setCadernoContent {
            UiError.entries.forEach { error -> shown[error] = error.messageText() }
        }
        rule.waitForIdle()

        assertThat(shown.keys).containsExactlyElementsIn(UiError.entries)
        assertThat(shown[UiError.NETWORK]).isEqualTo(context.getString(R.string.error_network))
        assertThat(shown[UiError.SERVER]).isEqualTo(context.getString(R.string.error_server))
        assertThat(shown[UiError.INVALID_DATA]).isEqualTo(context.getString(R.string.error_invalid))
        assertThat(shown[UiError.NOT_FOUND]).isEqualTo(context.getString(R.string.error_not_found))
        assertThat(shown[UiError.UNKNOWN]).isEqualTo(context.getString(R.string.error_unknown))
        // Nenhuma mensagem repetida: o usuario sabe diferenciar os erros.
        assertThat(shown.values.toSet()).hasSize(UiError.entries.size)
        // E nenhuma expoe termos tecnicos.
        shown.values.forEach { assertThat(it.lowercase()).doesNotContain("exception") }
    }

    @Composable
    private fun UiError.messageText(): String = message()
}
