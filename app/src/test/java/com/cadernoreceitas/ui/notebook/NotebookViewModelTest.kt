package com.cadernoreceitas.ui.notebook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.cadernoreceitas.domain.model.RecipeException
import com.cadernoreceitas.domain.model.SavedRecipe
import com.cadernoreceitas.ui.components.UiError
import com.cadernoreceitas.uitesting.FakeRecipeRepository
import com.cadernoreceitas.uitesting.MainDispatcherRule
import com.cadernoreceitas.uitesting.SampleData
import com.cadernoreceitas.uitesting.awaitUntil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NotebookViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    // Quatro situacoes diferentes de anotacao, para exercitar os filtros.
    private val plain = SampleData.saved(SampleData.teriyaki) // sem nota nenhuma
    private val top = SampleData.saved(SampleData.crumble, rating = 5, comment = "Excelente, perfeita!")
    private val mid = SampleData.saved(SampleData.arrabiata, rating = 3) // tem nota, mas nao e "top"
    private val commentOnly = SampleData.saved(
        SampleData.recipe(id = "1", name = "Sopa de cebola"),
        comment = "Faltou sal na primeira vez",
    )
    private val all = listOf(plain, top, mid, commentOnly)

    private val repo = FakeRecipeRepository(all)
    private val created = mutableListOf<NotebookViewModel>()

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
    }

    private fun TestScope.newViewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        observe: Boolean = true,
    ): NotebookViewModel {
        val vm = NotebookViewModel(repo, handle)
        created += vm
        if (observe) backgroundScope.launch { vm.uiState.collect { } }
        return vm
    }

    private val NotebookViewModel.state: NotebookUiState get() = uiState.value

    private fun NotebookUiState.items(): List<SavedRecipe> = (content as NotebookContent.Success).items

    // ------------------------------------------------------------------ matches (regra pura)

    @Test
    fun `ALL aceita tudo`() {
        assertThat(all.all { NotebookFilter.ALL.matches(it) }).isTrue()
    }

    @Test
    fun `WITH_NOTE aceita quem tem nota ou observacao`() {
        assertThat(NotebookFilter.WITH_NOTE.matches(plain)).isFalse()
        assertThat(NotebookFilter.WITH_NOTE.matches(top)).isTrue()
        assertThat(NotebookFilter.WITH_NOTE.matches(mid)).isTrue() // so a nota ja conta
        assertThat(NotebookFilter.WITH_NOTE.matches(commentOnly)).isTrue() // so o comentario ja conta
        // Comentario em branco nao e anotacao.
        assertThat(NotebookFilter.WITH_NOTE.matches(SampleData.saved(comment = "   "))).isFalse()
    }

    @Test
    fun `TOP_RATED aceita 4 estrelas ou mais`() {
        assertThat(NotebookFilter.TOP_RATED.matches(SampleData.saved(rating = 5))).isTrue()
        assertThat(NotebookFilter.TOP_RATED.matches(SampleData.saved(rating = 4))).isTrue() // fronteira
        assertThat(NotebookFilter.TOP_RATED.matches(SampleData.saved(rating = 3))).isFalse()
        assertThat(NotebookFilter.TOP_RATED.matches(commentOnly)).isFalse() // sem nota = 0
        assertThat(NotebookFilter.TOP_RATED.matches(plain)).isFalse()
    }

    // ------------------------------------------------------------------ estados

    @Test
    fun `comeca em Loading e termina em Success com os itens`() = runTest {
        val vm = newViewModel(observe = false)

        vm.uiState.test {
            val initial = awaitItem()
            assertThat(initial.content).isEqualTo(NotebookContent.Loading)
            assertThat(initial.filter).isEqualTo(NotebookFilter.ALL)
            assertThat(initial.totalSaved).isEqualTo(0)

            val done = awaitUntil { it.content is NotebookContent.Success }
            assertThat(done.content).isEqualTo(NotebookContent.Success(all))
            assertThat(done.totalSaved).isEqualTo(4)
            assertThat(done.lastRemoved).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `caderno vazio vira Empty sem filtro`() = runTest {
        repo.savedFlow.value = emptyList()
        val vm = newViewModel()
        runCurrent()

        assertThat(vm.state.content).isEqualTo(NotebookContent.Empty(filtered = false))
        assertThat(vm.state.totalSaved).isEqualTo(0)
    }

    @Test
    fun `filtro sem correspondencia vira Empty filtrado e mantem o total`() = runTest {
        repo.savedFlow.value = listOf(plain) // nenhuma anotacao
        val vm = newViewModel()
        runCurrent()

        vm.onFilterSelected(NotebookFilter.WITH_NOTE)
        runCurrent()

        assertThat(vm.state.content).isEqualTo(NotebookContent.Empty(filtered = true))
        assertThat(vm.state.totalSaved).isEqualTo(1) // a UI usa isto para continuar mostrando os filtros
        assertThat(vm.state.filter).isEqualTo(NotebookFilter.WITH_NOTE)

        vm.onFilterSelected(NotebookFilter.ALL)
        runCurrent()
        assertThat(vm.state.content).isEqualTo(NotebookContent.Success(listOf(plain)))
    }

    @Test
    fun `cada filtro mostra so os itens esperados`() = runTest {
        val vm = newViewModel()
        runCurrent()
        assertThat(vm.state.items()).containsExactlyElementsIn(all).inOrder()

        vm.onFilterSelected(NotebookFilter.WITH_NOTE)
        runCurrent()
        assertThat(vm.state.items()).containsExactly(top, mid, commentOnly).inOrder()

        vm.onFilterSelected(NotebookFilter.TOP_RATED)
        runCurrent()
        assertThat(vm.state.items()).containsExactly(top)
        assertThat(vm.state.totalSaved).isEqualTo(4)
    }

    @Test
    fun `a lista reage a receitas salvas em outras telas`() = runTest {
        repo.savedFlow.value = emptyList()
        val vm = newViewModel()
        runCurrent()
        assertThat(vm.state.content).isEqualTo(NotebookContent.Empty(filtered = false))

        repo.savedFlow.value = listOf(top)
        runCurrent()
        assertThat(vm.state.content).isEqualTo(NotebookContent.Success(listOf(top)))
    }

    // ------------------------------------------------------------------ remover / desfazer

    @Test
    fun `remove tira o item da lista e guarda para desfazer`() = runTest {
        val vm = newViewModel()
        runCurrent()

        vm.remove(top.recipe.id)
        runCurrent()

        assertThat(repo.removeCalls).containsExactly(top.recipe.id)
        assertThat(vm.state.items()).containsExactly(plain, mid, commentOnly).inOrder()
        assertThat(vm.state.totalSaved).isEqualTo(3)
        assertThat(vm.state.lastRemoved).isEqualTo(top)
    }

    @Test
    fun `undoRemove devolve o item e limpa lastRemoved`() = runTest {
        val vm = newViewModel()
        runCurrent()
        vm.remove(top.recipe.id)
        runCurrent()

        vm.undoRemove()
        runCurrent()

        assertThat(repo.restoreCalls).containsExactly(top)
        assertThat(vm.state.lastRemoved).isNull()
        assertThat(vm.state.items().map { it.recipe.id }).contains(top.recipe.id)
        assertThat(vm.state.totalSaved).isEqualTo(4)
        // A anotacao volta junto.
        assertThat(vm.state.items().first { it.recipe.id == top.recipe.id }.rating).isEqualTo(5)
    }

    @Test
    fun `undoRemove sem remocao previa nao faz nada`() = runTest {
        val vm = newViewModel()
        runCurrent()

        vm.undoRemove()
        runCurrent()

        assertThat(repo.restoreCalls).isEmpty()
        assertThat(vm.state.totalSaved).isEqualTo(4)
    }

    @Test
    fun `onRemovalMessageShown limpa o aviso sem restaurar`() = runTest {
        val vm = newViewModel()
        runCurrent()
        vm.remove(plain.recipe.id)
        runCurrent()
        assertThat(vm.state.lastRemoved).isEqualTo(plain)

        vm.onRemovalMessageShown()
        runCurrent()

        assertThat(vm.state.lastRemoved).isNull()
        assertThat(repo.restoreCalls).isEmpty()
        assertThat(vm.state.totalSaved).isEqualTo(3) // continua removido
    }

    @Test
    fun `falha ao remover nao quebra a tela nem cria aviso de desfazer`() = runTest {
        repo.removeFailure = IOException("banco travado")
        val vm = newViewModel()
        runCurrent()

        vm.remove(top.recipe.id)
        runCurrent()

        assertThat(vm.state.lastRemoved).isNull()
        assertThat(vm.state.content).isEqualTo(NotebookContent.Success(all))
    }

    @Test
    fun `remover o ultimo item do caderno mostra o estado vazio e permite desfazer`() = runTest {
        repo.savedFlow.value = listOf(top)
        val vm = newViewModel()
        runCurrent()

        vm.remove(top.recipe.id)
        runCurrent()
        assertThat(vm.state.content).isEqualTo(NotebookContent.Empty(filtered = false))
        assertThat(vm.state.lastRemoved).isEqualTo(top)

        vm.undoRemove()
        runCurrent()
        assertThat(vm.state.content).isEqualTo(NotebookContent.Success(listOf(top)))
    }

    // ------------------------------------------------------------------ SavedStateHandle

    @Test
    fun `o filtro escolhido e guardado no SavedStateHandle`() = runTest {
        val handle = SavedStateHandle()
        val vm = newViewModel(handle)
        runCurrent()

        vm.onFilterSelected(NotebookFilter.TOP_RATED)

        assertThat(handle.get<String>("filter")).isEqualTo("TOP_RATED")
    }

    @Test
    fun `um novo ViewModel com o mesmo handle comeca no filtro que estava`() = runTest {
        val handle = SavedStateHandle()
        val first = newViewModel(handle)
        runCurrent()
        first.onFilterSelected(NotebookFilter.WITH_NOTE)
        first.viewModelScope.cancel() // processo morto

        val second = newViewModel(handle)
        runCurrent()

        assertThat(second.state.filter).isEqualTo(NotebookFilter.WITH_NOTE)
        assertThat(second.state.items()).containsExactly(top, mid, commentOnly).inOrder()
    }

    @Test
    fun `filtro invalido no handle cai em ALL`() = runTest {
        val vm = newViewModel(SavedStateHandle(mapOf("filter" to "NAO_EXISTE")))
        runCurrent()

        assertThat(vm.state.filter).isEqualTo(NotebookFilter.ALL)
    }

    // ------------------------------------------------------------------ erro de leitura

    @Test
    fun `erro no Flow do banco vira Error e retry reassina o Flow`() = runTest {
        repo.observeSavedError = IOException("banco corrompido")
        val vm = newViewModel()
        runCurrent()

        assertThat(vm.state.content).isEqualTo(NotebookContent.Error(UiError.UNKNOWN))
        assertThat(vm.state.totalSaved).isEqualTo(0)

        repo.observeSavedError = null
        vm.retry()
        runCurrent()

        assertThat(vm.state.content).isEqualTo(NotebookContent.Success(all))
        assertThat(vm.state.totalSaved).isEqualTo(4)
    }

    @Test
    fun `RecipeException no Flow e traduzida para o tipo de erro`() = runTest {
        repo.observeSavedError = RecipeException(RecipeException.Kind.INVALID_DATA)
        val vm = newViewModel()
        runCurrent()

        assertThat(vm.state.content).isEqualTo(NotebookContent.Error(UiError.INVALID_DATA))
    }
}
