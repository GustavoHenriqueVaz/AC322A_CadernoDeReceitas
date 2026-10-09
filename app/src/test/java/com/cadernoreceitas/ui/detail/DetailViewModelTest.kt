package com.cadernoreceitas.ui.detail

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.cadernoreceitas.domain.model.DataSource
import com.cadernoreceitas.domain.model.RecipeException
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeRecipeRepository().seedSample()
    private val created = mutableListOf<DetailViewModel>()
    private val recipe = SampleData.teriyaki

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
    }

    private fun TestScope.newViewModel(observe: Boolean = true): DetailViewModel {
        val vm = DetailViewModel(SampleData.TERIYAKI_ID, repo)
        created += vm
        if (observe) backgroundScope.launch { vm.uiState.collect { } }
        return vm
    }

    private val DetailViewModel.state: DetailUiState get() = uiState.value

    // ------------------------------------------------------------------ carga

    @Test
    fun `comeca em Loading e termina em Success com a receita`() = runTest {
        repo.recipeDelayMillis = 500
        val vm = newViewModel(observe = false)

        vm.uiState.test {
            val initial = awaitItem()
            assertThat(initial.content).isEqualTo(DetailContent.Loading)
            assertThat(initial.saved).isNull()
            assertThat(initial.isSaved).isFalse()

            val done = awaitUntil { it.content is DetailContent.Success }
            assertThat(done.content).isEqualTo(DetailContent.Success(recipe, isOffline = false))
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repo.recipeCalls).containsExactly(FakeRecipeRepository.RecipeCall(SampleData.TERIYAKI_ID, false))
    }

    @Test
    fun `permanece em Loading enquanto a receita nao chega`() = runTest {
        repo.recipeDelayMillis = 1_000
        val vm = newViewModel()
        runCurrent()
        advanceTimeBy(999); runCurrent()
        assertThat(vm.state.content).isEqualTo(DetailContent.Loading)

        advanceTimeBy(1); runCurrent()
        assertThat(vm.state.content).isInstanceOf(DetailContent.Success::class.java)
    }

    @Test
    fun `cache vencido marca a receita como offline`() = runTest {
        repo.recipeSource = DataSource.CACHE_STALE
        val vm = newViewModel()
        runCurrent()

        assertThat(vm.state.content).isEqualTo(DetailContent.Success(recipe, isOffline = true))
    }

    @Test
    fun `falha de rede vira Error e retry carrega a receita`() = runTest {
        repo.recipeFailure = RecipeException(RecipeException.Kind.NETWORK)
        val vm = newViewModel()
        runCurrent()
        assertThat(vm.state.content).isEqualTo(DetailContent.Error(UiError.NETWORK))

        repo.recipeFailure = null
        vm.retry()
        runCurrent()

        assertThat(vm.state.content).isEqualTo(DetailContent.Success(recipe, isOffline = false))
        assertThat(repo.recipeCalls).hasSize(2)
    }

    @Test
    fun `receita inexistente vira Error NOT_FOUND`() = runTest {
        repo.recipes.remove(SampleData.TERIYAKI_ID)
        val vm = newViewModel()
        runCurrent()

        assertThat(vm.state.content).isEqualTo(DetailContent.Error(UiError.NOT_FOUND))
    }

    @Test
    fun `erro do servidor e erro inesperado sao traduzidos`() = runTest {
        repo.recipeFailure = RecipeException(RecipeException.Kind.SERVER)
        val vm = newViewModel()
        runCurrent()
        assertThat(vm.state.content).isEqualTo(DetailContent.Error(UiError.SERVER))

        repo.recipeFailure = IOException("disco")
        vm.retry()
        runCurrent()
        assertThat(vm.state.content).isEqualTo(DetailContent.Error(UiError.UNKNOWN))
    }

    // ------------------------------------------------------------------ salvar / remover / desfazer

    @Test
    fun `toggleSaved salva a receita e avisa com Saved`() = runTest {
        val vm = newViewModel()
        runCurrent()
        assertThat(vm.state.isSaved).isFalse()

        vm.toggleSaved()
        runCurrent()

        assertThat(repo.saveRecipeCalls).containsExactly(recipe)
        assertThat(vm.state.isSaved).isTrue()
        assertThat(vm.state.saved?.recipe).isEqualTo(recipe)
        assertThat(vm.state.message).isEqualTo(DetailMessage.Saved)

        vm.onMessageShown()
        runCurrent()
        assertThat(vm.state.message).isNull()
    }

    @Test
    fun `toggleSaved numa receita salva remove e entrega o item para desfazer`() = runTest {
        val item = SampleData.saved(recipe, rating = 5, comment = "Muito bom, repetir sempre", prepMinutes = 40)
        repo.savedFlow.value = listOf(item)
        val vm = newViewModel()
        runCurrent()
        assertThat(vm.state.isSaved).isTrue()
        assertThat(vm.state.saved).isEqualTo(item)

        vm.toggleSaved()
        runCurrent()

        assertThat(repo.removeCalls).containsExactly(SampleData.TERIYAKI_ID)
        assertThat(vm.state.isSaved).isFalse()
        // O item removido (com a anotacao!) vai na mensagem para o "Desfazer".
        assertThat(vm.state.message).isEqualTo(DetailMessage.Removed(item))
    }

    @Test
    fun `undoRemoval restaura a receita com a anotacao`() = runTest {
        val item = SampleData.saved(recipe, rating = 4, comment = "Quase perfeito, menos sal")
        repo.savedFlow.value = listOf(item)
        val vm = newViewModel()
        runCurrent()
        vm.toggleSaved()
        runCurrent()
        assertThat(vm.state.isSaved).isFalse()

        val removed = (vm.state.message as DetailMessage.Removed).item
        vm.undoRemoval(removed)
        runCurrent()

        assertThat(repo.restoreCalls).containsExactly(item)
        assertThat(vm.state.isSaved).isTrue()
        assertThat(vm.state.saved?.rating).isEqualTo(4)
        assertThat(vm.state.saved?.comment).isEqualTo("Quase perfeito, menos sal")
    }

    @Test
    fun `isSaved reage a mudancas feitas fora da tela`() = runTest {
        val vm = newViewModel()
        runCurrent()
        assertThat(vm.state.isSaved).isFalse()

        // Outra tela (ou a notificacao) salva a receita.
        repo.savedFlow.value = listOf(SampleData.saved(recipe))
        runCurrent()
        assertThat(vm.state.isSaved).isTrue()

        // E outra remove.
        repo.savedFlow.value = emptyList()
        runCurrent()
        assertThat(vm.state.isSaved).isFalse()
    }

    @Test
    fun `toggleSaved e ignorado enquanto a receita ainda nao carregou`() = runTest {
        repo.recipeDelayMillis = 1_000
        val vm = newViewModel()
        runCurrent()
        assertThat(vm.state.content).isEqualTo(DetailContent.Loading)

        vm.toggleSaved()
        runCurrent()

        assertThat(repo.saveRecipeCalls).isEmpty()
        assertThat(vm.state.isSaved).isFalse()
        assertThat(vm.state.message).isNull()
    }

    @Test
    fun `toggleSaved e ignorado quando a receita falhou ao carregar`() = runTest {
        repo.recipeFailure = RecipeException(RecipeException.Kind.NETWORK)
        val vm = newViewModel()
        runCurrent()

        vm.toggleSaved()
        runCurrent()

        assertThat(repo.saveRecipeCalls).isEmpty()
        assertThat(vm.state.message).isNull()
    }

    @Test
    fun `falha ao salvar vira a mensagem Failed e nao marca como salva`() = runTest {
        repo.saveRecipeFailure = IOException("banco cheio")
        val vm = newViewModel()
        runCurrent()

        vm.toggleSaved()
        runCurrent()

        assertThat(vm.state.message).isEqualTo(DetailMessage.Failed)
        assertThat(vm.state.isSaved).isFalse()
    }

    @Test
    fun `falha ao remover e ao restaurar tambem viram Failed`() = runTest {
        repo.savedFlow.value = listOf(SampleData.saved(recipe))
        repo.removeFailure = IOException("travado")
        val vm = newViewModel()
        runCurrent()

        vm.toggleSaved()
        runCurrent()
        assertThat(vm.state.message).isEqualTo(DetailMessage.Failed)
        assertThat(vm.state.isSaved).isTrue() // nada mudou no caderno

        vm.onMessageShown()
        repo.restoreFailure = IOException("travado")
        vm.undoRemoval(SampleData.saved(recipe))
        runCurrent()
        assertThat(vm.state.message).isEqualTo(DetailMessage.Failed)
    }

    @Test
    fun `anotacao existente aparece em saved`() = runTest {
        repo.savedFlow.value = listOf(
            SampleData.saved(recipe, rating = 3, comment = "Ficou bom mas demorou", prepMinutes = 90),
        )
        val vm = newViewModel()
        runCurrent()

        val saved = vm.state.saved
        assertThat(saved?.hasNote).isTrue()
        assertThat(saved?.rating).isEqualTo(3)
        assertThat(saved?.prepMinutes).isEqualTo(90)
    }
}
