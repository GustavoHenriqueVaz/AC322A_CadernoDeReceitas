package com.cadernoreceitas.ui.editnote

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadernoreceitas.data.photo.PhotoException
import com.cadernoreceitas.domain.model.RecipeException
import com.cadernoreceitas.domain.model.RecipeNote
import com.cadernoreceitas.ui.components.UiError
import com.cadernoreceitas.uitesting.FakePhotoStorage
import com.cadernoreceitas.uitesting.FakeRecipeRepository
import com.cadernoreceitas.uitesting.FakeRecipeRepository.SaveNoteCall
import com.cadernoreceitas.uitesting.MainDispatcherRule
import com.cadernoreceitas.uitesting.SampleData
import com.cadernoreceitas.uitesting.TEST_SDK
import com.cadernoreceitas.uitesting.TestCadernoApp
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Testes do [EditNoteViewModel]. Rodam sob Robolectric so porque `Uri` (foto escolhida no Photo
 * Picker) e uma classe do Android; o resto e coroutines de teste com relogio virtual.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [TEST_SDK], application = TestCadernoApp::class)
@OptIn(ExperimentalCoroutinesApi::class)
class EditNoteViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeRecipeRepository().seedSample()
    private val photos = FakePhotoStorage()
    private val created = mutableListOf<EditNoteViewModel>()
    private val recipe = SampleData.teriyaki

    private val validComment = "Ficou otimo, faria de novo!"
    private val oldPhoto = "/files/photos/old.jpg"
    private fun uri(n: Int): Uri = Uri.parse("content://media/picker/0/$n")

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
    }

    private fun newViewModel(handle: SavedStateHandle = SavedStateHandle()): EditNoteViewModel {
        val vm = EditNoteViewModel(SampleData.TERIYAKI_ID, repo, photos, handle)
        created += vm
        return vm
    }

    /** ViewModel ja carregado (apos o primeiro `runCurrent`). */
    private fun TestScope.loadedViewModel(handle: SavedStateHandle = SavedStateHandle()): EditNoteViewModel {
        val vm = newViewModel(handle)
        runCurrent()
        return vm
    }

    private val EditNoteViewModel.state: EditNoteUiState get() = uiState.value

    private fun existingNote(photo: String? = null) {
        repo.savedFlow.value = listOf(
            SampleData.saved(recipe, rating = 4, comment = "Muito bom, repetir", prepMinutes = 45, photoPath = photo),
        )
    }

    private fun EditNoteViewModel.fillValidForm(comment: String = validComment) {
        onRatingChange(5)
        onCommentChange(comment)
    }

    // ------------------------------------------------------------------ carga

    @Test
    fun `carrega a receita e comeca com o formulario vazio`() = runTest {
        val vm = newViewModel()
        assertThat(vm.state.isLoading).isTrue() // antes do primeiro ciclo

        runCurrent()

        assertThat(vm.state.isLoading).isFalse()
        assertThat(vm.state.loadError).isNull()
        assertThat(vm.state.recipeName).isEqualTo(recipe.name)
        assertThat(vm.state.fields).isEqualTo(NoteFields())
        assertThat(vm.state.isDirty).isFalse()
        assertThat(vm.state.hasVisibleErrors).isFalse() // nada de erro antes de o usuario interagir
    }

    @Test
    fun `carrega a anotacao existente nos campos`() = runTest {
        existingNote(photo = oldPhoto)
        val vm = loadedViewModel()

        assertThat(vm.state.fields)
            .isEqualTo(NoteFields(rating = 4, comment = "Muito bom, repetir", minutes = "45", photoPath = oldPhoto))
        assertThat(vm.state.isDirty).isFalse()
    }

    @Test
    fun `falha ao carregar mostra loadError e load tenta de novo`() = runTest {
        repo.recipeFailure = RecipeException(RecipeException.Kind.NETWORK)
        val vm = loadedViewModel()

        assertThat(vm.state.isLoading).isFalse()
        assertThat(vm.state.loadError).isEqualTo(UiError.NETWORK)

        repo.recipeFailure = null
        vm.load()
        assertThat(vm.state.isLoading).isTrue()
        assertThat(vm.state.loadError).isNull()
        runCurrent()

        assertThat(vm.state.isLoading).isFalse()
        assertThat(vm.state.loadError).isNull()
        assertThat(vm.state.recipeName).isEqualTo(recipe.name)
    }

    // ------------------------------------------------------------------ validacao

    @Test
    fun `erro de observacao so aparece depois do blur`() = runTest {
        val vm = loadedViewModel()

        vm.onCommentChange("abc")
        assertThat(vm.state.commentError).isNull() // ainda digitando

        vm.onFieldBlurred(NoteField.COMMENT)
        assertThat(vm.state.commentError).isEqualTo(FieldError.TooShort(min = 10, missing = 7))
        assertThat(vm.state.hasVisibleErrors).isTrue()

        // Depois de "tocado" o campo revalida a cada tecla.
        vm.onCommentChange("a".repeat(10))
        assertThat(vm.state.commentError).isNull()
        vm.onCommentChange("")
        assertThat(vm.state.commentError).isEqualTo(FieldError.Required)
    }

    @Test
    fun `observacao so com espacos e obrigatoria e acima do limite e longa demais`() = runTest {
        val vm = loadedViewModel()

        vm.onCommentChange(" ".repeat(12))
        vm.onFieldBlurred(NoteField.COMMENT)
        assertThat(vm.state.commentError).isEqualTo(FieldError.Required)

        vm.onCommentChange("x".repeat(600))
        // O ViewModel ja corta o texto (500 + folga); o erro continua valendo.
        assertThat(vm.state.fields.comment.length).isEqualTo(NoteValidator.COMMENT_MAX + 50)
        assertThat(vm.state.commentError).isEqualTo(FieldError.TooLong(NoteValidator.COMMENT_MAX))
    }

    @Test
    fun `tempo de preparo e opcional e so aceita digitos ate 3 posicoes`() = runTest {
        val vm = loadedViewModel()

        vm.onFieldBlurred(NoteField.MINUTES)
        assertThat(vm.state.minutesError).isNull() // vazio e valido

        vm.onMinutesChange("12abc4")
        assertThat(vm.state.fields.minutes).isEqualTo("124")
        assertThat(vm.state.minutesError).isNull()

        vm.onMinutesChange("0")
        assertThat(vm.state.minutesError).isEqualTo(FieldError.OutOfRange(1, 600))
        vm.onMinutesChange("601")
        assertThat(vm.state.minutesError).isEqualTo(FieldError.OutOfRange(1, 600))
        vm.onMinutesChange("600")
        assertThat(vm.state.minutesError).isNull()
    }

    @Test
    fun `erro de minutos so aparece depois do blur`() = runTest {
        val vm = loadedViewModel()

        vm.onMinutesChange("999")
        assertThat(vm.state.minutesError).isNull()

        vm.onFieldBlurred(NoteField.MINUTES)
        assertThat(vm.state.minutesError).isEqualTo(FieldError.OutOfRange(1, 600))
    }

    @Test
    fun `a nota e validada na hora porque estrelas nao tem foco`() = runTest {
        val vm = loadedViewModel()
        assertThat(vm.state.ratingError).isNull()

        vm.onRatingChange(0)
        assertThat(vm.state.ratingError).isEqualTo(FieldError.Required)

        vm.onRatingChange(3)
        assertThat(vm.state.ratingError).isNull()
        assertThat(vm.state.fields.rating).isEqualTo(3)
    }

    // ------------------------------------------------------------------ salvar

    @Test
    fun `onSave invalido nao chama o repositorio e mostra todos os erros`() = runTest {
        val vm = loadedViewModel()

        vm.onSave()
        runCurrent()

        assertThat(repo.saveNoteCalls).isEmpty()
        assertThat(vm.state.saved).isFalse()
        assertThat(vm.state.isSaving).isFalse()
        assertThat(vm.state.ratingError).isEqualTo(FieldError.Required)
        assertThat(vm.state.commentError).isEqualTo(FieldError.Required)
        assertThat(vm.state.minutesError).isNull() // opcional
        assertThat(vm.state.hasVisibleErrors).isTrue()
    }

    @Test
    fun `onSave com minutos invalidos nao grava`() = runTest {
        val vm = loadedViewModel()
        vm.fillValidForm()
        vm.onMinutesChange("700")

        vm.onSave()
        runCurrent()

        assertThat(repo.saveNoteCalls).isEmpty()
        assertThat(vm.state.minutesError).isEqualTo(FieldError.OutOfRange(1, 600))
    }

    @Test
    fun `onSave valido grava com os dados aparados e marca saved`() = runTest {
        val vm = loadedViewModel()
        vm.onRatingChange(5)
        vm.onCommentChange("   $validComment   \n")
        vm.onMinutesChange(" 45")

        vm.onSave()
        runCurrent()

        assertThat(repo.saveNoteCalls)
            .containsExactly(SaveNoteCall(recipe, RecipeNote(5, validComment, 45, null)))
        assertThat(vm.state.saved).isTrue()
        assertThat(vm.state.isSaving).isFalse()
        assertThat(vm.state.saveFailed).isFalse()
        // A receita entrou no caderno com a anotacao.
        assertThat(repo.savedFlow.value.single().rating).isEqualTo(5)
    }

    @Test
    fun `onSave sem tempo de preparo grava prepMinutes nulo`() = runTest {
        val vm = loadedViewModel()
        vm.fillValidForm()

        vm.onSave()
        runCurrent()

        assertThat(repo.saveNoteCalls.single().note.prepMinutes).isNull()
        assertThat(vm.state.saved).isTrue()
    }

    @Test
    fun `durante o salvamento isSaving fica ligado e um segundo toque e ignorado`() = runTest {
        repo.saveNoteDelayMillis = 1_000
        val vm = loadedViewModel()
        vm.fillValidForm()

        vm.onSave()
        runCurrent()
        assertThat(vm.state.isSaving).isTrue()
        assertThat(vm.state.saved).isFalse()

        vm.onSave() // duplo toque
        runCurrent()
        advanceTimeBy(1_000); runCurrent()

        assertThat(repo.saveNoteCalls).hasSize(1)
        assertThat(vm.state.isSaving).isFalse()
        assertThat(vm.state.saved).isTrue()
    }

    @Test
    fun `falha ao salvar liga saveFailed e permite tentar de novo`() = runTest {
        repo.saveNoteFailure = IOException("banco cheio")
        val vm = loadedViewModel()
        vm.fillValidForm()

        vm.onSave()
        runCurrent()

        assertThat(vm.state.saveFailed).isTrue()
        assertThat(vm.state.isSaving).isFalse()
        assertThat(vm.state.saved).isFalse()
        // Os campos digitados continuam la.
        assertThat(vm.state.fields.comment).isEqualTo(validComment)

        vm.onSaveFailedShown()
        assertThat(vm.state.saveFailed).isFalse()

        repo.saveNoteFailure = null
        vm.onSave()
        runCurrent()
        assertThat(vm.state.saved).isTrue()
        assertThat(vm.state.saveFailed).isFalse()
    }

    // ------------------------------------------------------------------ alteracoes nao salvas

    @Test
    fun `isDirty acompanha diferencas em relacao ao que foi carregado`() = runTest {
        existingNote()
        val vm = loadedViewModel()
        assertThat(vm.state.isDirty).isFalse()

        vm.onCommentChange("Muito bom, repetir!")
        assertThat(vm.state.isDirty).isTrue()
        vm.onCommentChange("Muito bom, repetir") // voltou ao original
        assertThat(vm.state.isDirty).isFalse()

        vm.onRatingChange(5)
        assertThat(vm.state.isDirty).isTrue()
        vm.onRatingChange(4)
        assertThat(vm.state.isDirty).isFalse()

        vm.onMinutesChange("46")
        assertThat(vm.state.isDirty).isTrue()
    }

    @Test
    fun `formulario novo fica sujo ao digitar qualquer coisa`() = runTest {
        val vm = loadedViewModel()
        assertThat(vm.state.isDirty).isFalse()

        vm.onCommentChange("a")
        assertThat(vm.state.isDirty).isTrue()
    }

    // ------------------------------------------------------------------ morte do processo

    @Test
    fun `restaura os campos digitados quando o processo e recriado`() = runTest {
        val handle = SavedStateHandle()
        val first = loadedViewModel(handle)
        first.onRatingChange(2)
        first.onCommentChange("Texto digitado antes de o processo morrer")
        first.onMinutesChange("30")
        first.viewModelScope.cancel() // o processo morreu

        val second = loadedViewModel(handle)

        assertThat(second.state.isLoading).isFalse()
        assertThat(second.state.fields).isEqualTo(
            NoteFields(rating = 2, comment = "Texto digitado antes de o processo morrer", minutes = "30"),
        )
        assertThat(second.state.isDirty).isTrue() // ainda ha alteracao nao salva
        assertThat(second.state.hasVisibleErrors).isFalse() // erros so voltam quando o usuario interagir
    }

    @Test
    fun `os campos restaurados vencem a anotacao gravada`() = runTest {
        existingNote()
        val handle = SavedStateHandle()
        val first = loadedViewModel(handle)
        first.onCommentChange("Reescrevi tudo e nao salvei ainda")
        first.viewModelScope.cancel()

        val second = loadedViewModel(handle)

        assertThat(second.state.fields.comment).isEqualTo("Reescrevi tudo e nao salvei ainda")
        assertThat(second.state.fields.rating).isEqualTo(4)
        assertThat(second.state.isDirty).isTrue()
    }

    @Test
    fun `sem edicao previa o handle nao interfere na anotacao gravada`() = runTest {
        existingNote()
        val handle = SavedStateHandle()
        val first = loadedViewModel(handle)
        first.viewModelScope.cancel() // fechou sem editar nada

        val second = loadedViewModel(handle)

        assertThat(second.state.fields.comment).isEqualTo("Muito bom, repetir")
        assertThat(second.state.isDirty).isFalse()
    }

    // ------------------------------------------------------------------ foto

    @Test
    fun `cancelar o Photo Picker (uri nula) nao faz nada`() = runTest {
        val vm = loadedViewModel()

        vm.onPhotoPicked(null)
        runCurrent()

        assertThat(photos.importCalls).isEmpty()
        assertThat(vm.state.fields.photoPath).isNull()
        assertThat(vm.state.isImportingPhoto).isFalse()
        assertThat(vm.state.photoFailed).isFalse()
        assertThat(vm.state.isDirty).isFalse()
    }

    @Test
    fun `importar a foto mostra progresso e guarda o caminho temporario`() = runTest {
        photos.importDelayMillis = 500
        val vm = loadedViewModel()

        vm.onPhotoPicked(uri(1))
        runCurrent()
        assertThat(vm.state.isImportingPhoto).isTrue()
        assertThat(vm.state.fields.photoPath).isNull()

        advanceTimeBy(500); runCurrent()
        assertThat(vm.state.isImportingPhoto).isFalse()
        assertThat(vm.state.fields.photoPath).isEqualTo("/cache/photo_tmp/tmp-1.jpg")
        assertThat(vm.state.photoFailed).isFalse()
        assertThat(vm.state.isDirty).isTrue()
        assertThat(photos.importCalls).containsExactly(uri(1))
    }

    @Test
    fun `falha na importacao liga photoFailed e mantem a foto anterior`() = runTest {
        val vm = loadedViewModel()
        vm.onPhotoPicked(uri(1))
        runCurrent()
        val first = vm.state.fields.photoPath
        assertThat(first).isNotNull()

        photos.importFailure = PhotoException("Imagem invalida")
        vm.onPhotoPicked(uri(2))
        runCurrent()

        assertThat(vm.state.photoFailed).isTrue()
        assertThat(vm.state.isImportingPhoto).isFalse()
        assertThat(vm.state.fields.photoPath).isEqualTo(first)

        vm.onPhotoErrorShown()
        assertThat(vm.state.photoFailed).isFalse()
    }

    @Test
    fun `uma nova importacao cancela a que ainda estava em andamento`() = runTest {
        photos.importDelayMillis = 1_000
        val vm = loadedViewModel()

        vm.onPhotoPicked(uri(1))
        advanceTimeBy(500); runCurrent()
        assertThat(vm.state.isImportingPhoto).isTrue()

        vm.onPhotoPicked(uri(2))
        runCurrent()
        assertThat(photos.importCancellations.get()).isEqualTo(1) // a primeira foi cancelada de verdade
        assertThat(vm.state.isImportingPhoto).isTrue() // a segunda continua

        advanceTimeBy(1_000); runCurrent()
        assertThat(photos.importCalls).containsExactly(uri(1), uri(2)).inOrder()
        assertThat(vm.state.isImportingPhoto).isFalse()
        assertThat(vm.state.fields.photoPath).isEqualTo("/cache/photo_tmp/tmp-1.jpg") // so a 2a gerou arquivo
    }

    @Test
    fun `trocar a foto apaga a temporaria anterior e nao grava nada ainda`() = runTest {
        val vm = loadedViewModel()
        vm.onPhotoPicked(uri(1))
        runCurrent()
        vm.onPhotoPicked(uri(2))
        runCurrent()

        assertThat(vm.state.fields.photoPath).isEqualTo("/cache/photo_tmp/tmp-2.jpg")
        assertThat(photos.deleteTempCalls.filterNotNull()).containsExactly("/cache/photo_tmp/tmp-1.jpg")
        // Nada foi movido para o armazenamento definitivo antes de salvar.
        assertThat(photos.commitCalls).isEmpty()
        assertThat(photos.deleteCalls).isEmpty()
    }

    @Test
    fun `a foto temporaria so e confirmada ao salvar`() = runTest {
        val vm = loadedViewModel()
        vm.onPhotoPicked(uri(1))
        runCurrent()
        vm.fillValidForm()
        assertThat(photos.commitCalls).isEmpty()

        vm.onSave()
        runCurrent()

        assertThat(photos.commitCalls).containsExactly("/cache/photo_tmp/tmp-1.jpg" to SampleData.TERIYAKI_ID)
        assertThat(repo.saveNoteCalls.single().note.photoPath).isEqualTo("/files/photos/52772-1.jpg")
        assertThat(vm.state.saved).isTrue()
    }

    @Test
    fun `trocar uma foto ja salva apaga a antiga so depois de gravar`() = runTest {
        existingNote(photo = oldPhoto)
        val vm = loadedViewModel()
        vm.onPhotoPicked(uri(1))
        runCurrent()
        // A foto antiga NAO e temporaria: nao pode ser apagada ao trocar.
        assertThat(photos.deleteTempCalls.filterNotNull()).isEmpty()
        assertThat(photos.deleteCalls).isEmpty()

        vm.onSave()
        runCurrent()

        assertThat(repo.saveNoteCalls.single().note.photoPath).isEqualTo("/files/photos/52772-1.jpg")
        assertThat(photos.deleteCalls).containsExactly(oldPhoto)
    }

    @Test
    fun `se a gravacao falha a foto antiga nao e apagada`() = runTest {
        existingNote(photo = oldPhoto)
        repo.saveNoteFailure = IOException("sem espaco")
        val vm = loadedViewModel()
        vm.onPhotoPicked(uri(1))
        runCurrent()

        vm.onSave()
        runCurrent()

        assertThat(vm.state.saveFailed).isTrue()
        assertThat(photos.deleteCalls).isEmpty() // o usuario nao perde a foto que ja tinha
    }

    @Test
    fun `salvar sem trocar a foto nao confirma nem apaga nada`() = runTest {
        existingNote(photo = oldPhoto)
        val vm = loadedViewModel()
        vm.onCommentChange("Muito bom, repetir sempre")

        vm.onSave()
        runCurrent()

        assertThat(photos.commitCalls).isEmpty()
        assertThat(photos.deleteCalls).isEmpty()
        assertThat(repo.saveNoteCalls.single().note.photoPath).isEqualTo(oldPhoto)
    }

    @Test
    fun `remover uma foto temporaria apaga o arquivo na hora`() = runTest {
        val vm = loadedViewModel()
        vm.onPhotoPicked(uri(1))
        runCurrent()

        vm.onPhotoRemoved()
        runCurrent()

        assertThat(vm.state.fields.photoPath).isNull()
        assertThat(photos.deleteTempCalls.filterNotNull()).containsExactly("/cache/photo_tmp/tmp-1.jpg")
    }

    @Test
    fun `remover a foto ja salva so a apaga ao gravar`() = runTest {
        existingNote(photo = oldPhoto)
        val vm = loadedViewModel()

        vm.onPhotoRemoved()
        runCurrent()
        assertThat(vm.state.fields.photoPath).isNull()
        assertThat(vm.state.isDirty).isTrue()
        assertThat(photos.deleteCalls).isEmpty() // ainda da para descartar a alteracao

        vm.onSave()
        runCurrent()

        assertThat(repo.saveNoteCalls.single().note.photoPath).isNull()
        assertThat(photos.deleteCalls).containsExactly(oldPhoto)
    }

    @Test
    fun `a foto escolhida tambem sobrevive a morte do processo`() = runTest {
        val handle = SavedStateHandle()
        val first = loadedViewModel(handle)
        first.onPhotoPicked(uri(1))
        runCurrent()
        first.viewModelScope.cancel()

        val second = loadedViewModel(handle)

        assertThat(second.state.fields.photoPath).isEqualTo("/cache/photo_tmp/tmp-1.jpg")
    }
}
