package com.cadernoreceitas.ui.editnote

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadernoreceitas.R
import com.cadernoreceitas.ui.components.TestTags
import com.cadernoreceitas.ui.components.UiError
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

/** [EditNoteScreen] (formulario de anotacao) com estados construidos a mao (sem ViewModel). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [TEST_SDK], qualifiers = TEST_DEVICE, application = TestCadernoApp::class)
class EditNoteScreenTest {

    // Precisa da Activity para simular o botao "voltar" do sistema.
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val recipeName = SampleData.teriyaki.name

    private class Calls {
        var back = 0
        val ratings = mutableListOf<Int>()
        val comments = mutableListOf<String>()
        val minutes = mutableListOf<String>()
        val blurred = mutableListOf<NoteField>()
        val photos = mutableListOf<Uri?>()
        var photoRemoved = 0
        var photoErrorShown = 0
        var saveFailedShown = 0
        var save = 0
        var retries = 0
    }

    private val calls = Calls()

    private fun state(
        fields: NoteFields = NoteFields(),
        isLoading: Boolean = false,
        loadError: UiError? = null,
        ratingError: FieldError? = null,
        commentError: FieldError? = null,
        minutesError: FieldError? = null,
        isImportingPhoto: Boolean = false,
        photoFailed: Boolean = false,
        isSaving: Boolean = false,
        saveFailed: Boolean = false,
        saved: Boolean = false,
        isDirty: Boolean = false,
    ) = EditNoteUiState(
        isLoading = isLoading,
        loadError = loadError,
        recipeName = recipeName,
        fields = fields,
        ratingError = ratingError,
        commentError = commentError,
        minutesError = minutesError,
        isImportingPhoto = isImportingPhoto,
        photoFailed = photoFailed,
        isSaving = isSaving,
        saveFailed = saveFailed,
        saved = saved,
        isDirty = isDirty,
    )

    private fun show(state: EditNoteUiState, fontScale: Float = 1f) {
        rule.setCadernoContent(fontScale) {
            EditNoteScreen(
                state = state,
                onBack = { calls.back++ },
                onRatingChange = { calls.ratings += it },
                onCommentChange = { calls.comments += it },
                onMinutesChange = { calls.minutes += it },
                onFieldBlurred = { calls.blurred += it },
                onPhotoPicked = { calls.photos += it },
                onPhotoRemoved = { calls.photoRemoved++ },
                onPhotoErrorShown = { calls.photoErrorShown++ },
                onSaveFailedShown = { calls.saveFailedShown++ },
                onSave = { calls.save++ },
                onRetry = { calls.retries++ },
            )
        }
    }

    private fun isHeading() = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    // ------------------------------------------------------------------ estados

    @Test
    fun carregandoMostraIndicadorESemFormulario() {
        show(state(isLoading = true))

        rule.onNodeWithTag(TestTags.LOADING).assertIsDisplayed()
        rule.onNodeWithTag(TestTags.FIELD_COMMENT).assertDoesNotExist()
        rule.onNodeWithTag(TestTags.BUTTON_SAVE_NOTE).assertDoesNotExist()
        rule.onNodeWithContentDescription("Voltar").assertIsDisplayed()
    }

    @Test
    fun erroDeCarregamentoMostraMensagemETentarNovamente() {
        show(state(loadError = UiError.NETWORK))

        rule.onNodeWithTag(TestTags.ERROR).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.error_network)).assertIsDisplayed()
        rule.onNodeWithTag(TestTags.FIELD_COMMENT).assertDoesNotExist()

        rule.onNodeWithText("Tentar novamente").performClick()

        assertThat(calls.retries).isEqualTo(1)
    }

    @Test
    fun formularioMostraTodosOsCamposComRotulo() {
        show(state())

        rule.onNodeWithText(recipeName).assertIsDisplayed().assert(isHeading())
        rule.onNodeWithText("Sua nota").assertIsDisplayed()
        rule.onNodeWithText("Observações").assertIsDisplayed()
        rule.onNodeWithText("Tempo de preparo (minutos)").assertIsDisplayed()
        rule.onNodeWithText("Foto do meu prato").assertIsDisplayed()
        rule.onNodeWithTag(TestTags.BUTTON_SAVE_NOTE).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Salvar anotação").assertIsDisplayed()
    }

    @Test
    fun textosDeAjudaOrientamAntesDeQualquerErro() {
        show(state())

        rule.onNodeWithText("Entre 10 e 500 caracteres").assertIsDisplayed()
        rule.onNodeWithText("Opcional. De 1 a 600 minutos.").assertIsDisplayed()
        rule.onNodeWithText("Corrija os campos destacados.").assertDoesNotExist()
        rule.onNodeWithText("Escolha de 1 a 5 estrelas.").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ nota (estrelas)

    @Test
    fun estrelasMostramANotaAtualEEnviamANova() {
        show(state(fields = NoteFields(rating = 4)))

        rule.onNodeWithContentDescription("4 estrelas").assertIsSelected()

        rule.onNodeWithContentDescription("2 estrelas").performClick()

        assertThat(calls.ratings).containsExactly(2)
    }

    @Test
    fun cadaEstrelaTemPeloMenos48dp() {
        show(state())

        listOf("1 estrela", "2 estrelas", "3 estrelas", "4 estrelas", "5 estrelas").forEach {
            rule.onNodeWithContentDescription(it).assertLayoutSizeAtLeast().assertTouchTargetAtLeast(rule)
        }
    }

    // ------------------------------------------------------------------ erros de validacao

    @Test
    fun erroDeNotaApareceComTextoNaTela() {
        show(state(ratingError = FieldError.Required))

        rule.onNodeWithText("Escolha de 1 a 5 estrelas.").assertIsDisplayed()
        rule.onNodeWithText("Corrija os campos destacados.").assertIsDisplayed()
    }

    @Test
    fun erroDeObservacaoCurtaMostraQuantosCaracteresFaltam() {
        show(state(fields = NoteFields(comment = "abc"), commentError = FieldError.TooShort(min = 10, missing = 7)))

        rule.onNodeWithText("Escreva pelo menos 10 caracteres (faltam 7).").assertIsDisplayed()
        rule.onNodeWithText("Entre 10 e 500 caracteres").assertDoesNotExist() // o erro substitui a dica
    }

    @Test
    fun erroDeObservacaoObrigatoriaELonga() {
        show(state(commentError = FieldError.Required))
        rule.onNodeWithText("Escreva uma observação sobre a receita.").assertIsDisplayed()
    }

    @Test
    fun erroDeObservacaoLongaMostraOLimite() {
        show(state(fields = NoteFields(comment = "x".repeat(520)), commentError = FieldError.TooLong(500)))

        rule.onNodeWithText("O máximo é de 500 caracteres.").assertIsDisplayed()
        rule.onNodeWithText("520/500").assertIsDisplayed()
    }

    @Test
    fun erroDeMinutosForaDaFaixaEDeNaoNumero() {
        show(state(fields = NoteFields(minutes = "999"), minutesError = FieldError.OutOfRange(1, 600)))

        rule.onNodeWithText("Informe um valor entre 1 e 600 minutos.").assertIsDisplayed()
        rule.onNodeWithText("Corrija os campos destacados.").assertIsDisplayed()
    }

    @Test
    fun erroDeMinutosNaoNumericoTemMensagemPropria() {
        show(state(minutesError = FieldError.NotANumber))

        rule.onNodeWithText("Digite apenas números.").assertIsDisplayed()
    }

    @Test
    fun contadorDeCaracteresIgnoraEspacosNasPontas() {
        show(state(fields = NoteFields(comment = "   Ficou otimo   ")))

        rule.onNodeWithText("11/500").assertIsDisplayed()
    }

    @Test
    fun contadorComecaEmZero() {
        show(state())

        rule.onNodeWithText("0/500").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ digitacao e foco

    @Test
    fun digitarNaObservacaoEnviaOTexto() {
        show(state())

        rule.onNodeWithTag(TestTags.FIELD_COMMENT).performTextInput("Ficou otimo")

        assertThat(calls.comments).containsExactly("Ficou otimo")
    }

    @Test
    fun digitarNosMinutosEnviaOTexto() {
        show(state())

        rule.onNodeWithTag(TestTags.FIELD_MINUTES).performTextInput("45")

        assertThat(calls.minutes).containsExactly("45")
    }

    @Test
    fun camposMostramOQueEstaNoEstado() {
        show(state(fields = NoteFields(comment = "Muito bom", minutes = "30")))

        rule.onNodeWithTag(TestTags.FIELD_COMMENT).assertTextContains("Muito bom")
        rule.onNodeWithTag(TestTags.FIELD_MINUTES).assertTextContains("30")
    }

    @Test
    fun perderOFocoAvisaQualCampoFoiTocado() {
        show(state())
        assertThat(calls.blurred).isEmpty() // nenhum "blur" falso ao compor a tela

        rule.onNodeWithTag(TestTags.FIELD_COMMENT).performClick() // foca
        assertThat(calls.blurred).isEmpty()

        rule.onNodeWithTag(TestTags.FIELD_MINUTES).performClick() // tira o foco da observacao
        assertThat(calls.blurred).containsExactly(NoteField.COMMENT)

        rule.onNodeWithTag(TestTags.FIELD_COMMENT).performClick() // tira o foco dos minutos
        assertThat(calls.blurred).containsExactly(NoteField.COMMENT, NoteField.MINUTES).inOrder()
    }

    // ------------------------------------------------------------------ salvar

    @Test
    fun botaoSalvarEstaHabilitadoEEnviaOnSave() {
        show(state())

        rule.onNodeWithTag(TestTags.BUTTON_SAVE_NOTE).performScrollTo().assertIsEnabled().performClick()

        assertThat(calls.save).isEqualTo(1)
    }

    @Test
    fun durantePSalvamentoOBotaoFicaDesabilitadoEMostraSalvando() {
        show(state(isSaving = true))

        rule.onNodeWithTag(TestTags.BUTTON_SAVE_NOTE).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Salvando…").assertIsDisplayed()
        rule.onNodeWithText("Salvar anotação").assertDoesNotExist()
    }

    @Test
    fun botaoSalvarTemPeloMenos48dpDeAltura() {
        show(state())

        rule.onNodeWithTag(TestTags.BUTTON_SAVE_NOTE).performScrollTo().assertTouchTargetAtLeast(rule)
    }

    @Test
    fun saveFailedMostraAvisoEPedeParaConsumi() {
        show(state(saveFailed = true))

        rule.onNodeWithText("Não foi possível salvar. Tente novamente.").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(10_000)
        rule.waitForIdle()
        assertThat(calls.saveFailedShown).isEqualTo(1)
    }

    // ------------------------------------------------------------------ foto

    @Test
    fun semFotoOfereceAdicionar() {
        show(state())

        rule.onNodeWithText("Adicionar foto").performScrollTo().assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithText("Trocar foto").assertDoesNotExist()
        rule.onNodeWithContentDescription("Remover foto").assertDoesNotExist()
    }

    @Test
    fun comFotoOfereceTrocarERemover() {
        show(state(fields = NoteFields(photoPath = "/files/photos/prato.jpg")))

        rule.onNodeWithText("Trocar foto").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Adicionar foto").assertDoesNotExist()

        rule.onNodeWithContentDescription("Remover foto").performScrollTo().assertTouchTargetAtLeast(rule).performClick()

        assertThat(calls.photoRemoved).isEqualTo(1)
    }

    @Test
    fun enquantoImportaAFotoNaoSePodeEscolherOutra() {
        show(state(isImportingPhoto = true))

        rule.onNodeWithText("Adicionar foto").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun falhaNaFotoMostraAvisoEPedeParaConsumi() {
        show(state(photoFailed = true))

        rule.onNodeWithText("Não foi possível usar essa imagem. Escolha outra.").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(10_000)
        rule.waitForIdle()
        assertThat(calls.photoErrorShown).isEqualTo(1)
    }

    // ------------------------------------------------------------------ sair do formulario

    @Test
    fun voltarSemAlteracoesSaiDireto() {
        show(state(isDirty = false))

        rule.onNodeWithContentDescription("Voltar").performClick()

        assertThat(calls.back).isEqualTo(1)
        rule.onNodeWithText("Descartar alterações?").assertDoesNotExist()
    }

    @Test
    fun voltarComAlteracoesPedeConfirmacao() {
        show(state(isDirty = true))

        rule.onNodeWithContentDescription("Voltar").performClick()

        rule.onNodeWithText("Descartar alterações?").assertIsDisplayed()
        rule.onNodeWithText("Você tem alterações que ainda não foram salvas.").assertIsDisplayed()
        assertThat(calls.back).isEqualTo(0)
    }

    @Test
    fun continuarEditandoFechaODialogoSemSair() {
        show(state(isDirty = true))
        rule.onNodeWithContentDescription("Voltar").performClick()

        rule.onNodeWithText("Continuar editando").performClick()

        rule.onNodeWithText("Descartar alterações?").assertDoesNotExist()
        assertThat(calls.back).isEqualTo(0)
        rule.onNodeWithTag(TestTags.FIELD_COMMENT).assertIsDisplayed() // continua no formulario
    }

    @Test
    fun descartarSaiDoFormulario() {
        show(state(isDirty = true))
        rule.onNodeWithContentDescription("Voltar").performClick()

        rule.onNodeWithText("Descartar").performClick()

        assertThat(calls.back).isEqualTo(1)
        rule.onNodeWithText("Descartar alterações?").assertDoesNotExist()
    }

    @Test
    fun botaoVoltarDoSistemaComAlteracoesTambemPedeConfirmacao() {
        show(state(isDirty = true))

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        rule.onNodeWithText("Descartar alterações?").assertIsDisplayed()
        assertThat(calls.back).isEqualTo(0)
    }

    @Test
    fun aoSalvarComSucessoVoltaSozinho() {
        show(state(saved = true, isDirty = true))
        rule.waitForIdle()

        assertThat(calls.back).isEqualTo(1)
        rule.onNodeWithText("Descartar alterações?").assertDoesNotExist() // salvo nao pergunta nada
    }

    @Test
    fun botoesDoDialogoDeDescarteTemAlvoDe48dp() {
        show(state(isDirty = true))
        rule.onNodeWithContentDescription("Voltar").performClick()

        rule.onNodeWithText("Descartar").assertTouchTargetAtLeast(rule)
        rule.onNodeWithText("Continuar editando").assertTouchTargetAtLeast(rule)
    }

    // ------------------------------------------------------------------ texto grande

    @Test
    fun comTextoEmDoisXOFormularioContinuaAlcancavelRolando() {
        show(state(fields = NoteFields(rating = 3, comment = "Ficou otimo e rapido", minutes = "20")), LARGE_FONT_SCALE)

        rule.onNodeWithContentDescription("Voltar").assertIsDisplayed().assertTouchTargetAtLeast(rule)
        rule.onNodeWithText("Sua nota").assertIsDisplayed()
        rule.onNodeWithContentDescription("3 estrelas").assertIsDisplayed().assertIsSelected()
        rule.onNodeWithTag(TestTags.FIELD_COMMENT).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(TestTags.FIELD_MINUTES).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Adicionar foto").performScrollTo().assertIsDisplayed().assertTouchTargetAtLeast(rule)
        rule.onNodeWithTag(TestTags.BUTTON_SAVE_NOTE).performScrollTo().assertIsDisplayed().assertTouchTargetAtLeast(rule)
    }

    @Test
    fun comTextoEmDoisXOsErrosContinuamLegiveis() {
        show(
            state(
                ratingError = FieldError.Required,
                commentError = FieldError.TooShort(10, 7),
                minutesError = FieldError.OutOfRange(1, 600),
            ),
            LARGE_FONT_SCALE,
        )

        rule.onNodeWithText("Escolha de 1 a 5 estrelas.").assertIsDisplayed()
        rule.onNodeWithText("Escreva pelo menos 10 caracteres (faltam 7).").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Informe um valor entre 1 e 600 minutos.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Corrija os campos destacados.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun comTextoEmDoisXODialogoDeDescarteMostraTudo() {
        show(state(isDirty = true), LARGE_FONT_SCALE)
        rule.onNodeWithContentDescription("Voltar").performClick()

        rule.onNodeWithText("Descartar alterações?").assertIsDisplayed()
        rule.onNodeWithText("Descartar").assertIsDisplayed()
        rule.onNodeWithText("Continuar editando").assertIsDisplayed()
    }

    @Test
    fun comTextoEmDoisXOEstadoDeErroDeCarregamentoMantemOBotao() {
        show(state(loadError = UiError.SERVER), LARGE_FONT_SCALE)

        rule.onNodeWithText("Tentar novamente").assertIsDisplayed().assertTouchTargetAtLeast(rule)
    }
}
