package com.cadernoreceitas.ui.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.cadernoreceitas.domain.model.DataSource
import com.cadernoreceitas.domain.model.RecipeException
import com.cadernoreceitas.domain.model.UserPreferences
import com.cadernoreceitas.ui.components.UiError
import com.cadernoreceitas.uitesting.FakeRecipeRepository
import com.cadernoreceitas.uitesting.FakeRecipeRepository.CategoryCall
import com.cadernoreceitas.uitesting.FakeRecipeRepository.SearchCall
import com.cadernoreceitas.uitesting.FakeReminderScheduler
import com.cadernoreceitas.uitesting.FakeUserPreferencesRepository
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

/**
 * Testes do [ExploreViewModel] sem Android: `Dispatchers.Main` e trocado por um dispatcher de
 * teste (relogio virtual) e o repositorio/preferencias/agendador sao fakes.
 *
 * Convencao do relogio: `runCurrent()` executa o que esta pronto AGORA; `advanceTimeBy(x)` + `runCurrent()`
 * deixa passar `x` ms virtuais (o `advanceTimeBy` nao executa tarefas agendadas exatamente em "agora + x").
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeRecipeRepository().apply {
        seedSample()
        searchResults["chicken"] = listOf(SampleData.summary())
        searchResults["pasta"] = listOf(SampleData.arrabiata.toSummary())
        searchResults["soup"] = listOf(SampleData.crumble.toSummary())
        categoryResults["Seafood"] = listOf(SampleData.crumble.toSummary())
    }
    private val prefs = FakeUserPreferencesRepository()
    private val scheduler = FakeReminderScheduler()
    private val created = mutableListOf<ExploreViewModel>()

    @After
    fun tearDown() {
        // viewModelScope nao e filho do TestScope: cancela para nao deixar coroutines penduradas.
        created.forEach { it.viewModelScope.cancel() }
    }

    private fun TestScope.newViewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        debounceMillis: Long = ExploreViewModel.DEFAULT_DEBOUNCE_MILLIS,
        observe: Boolean = true,
    ): ExploreViewModel {
        val vm = ExploreViewModel(repo, prefs, scheduler, handle, debounceMillis)
        created += vm
        // uiState usa WhileSubscribed: sem coletor nada carrega.
        if (observe) backgroundScope.launch { vm.uiState.collect { } }
        return vm
    }

    private val ExploreViewModel.state: ExploreUiState get() = uiState.value

    private fun queries() = repo.searchCalls.map { it.query }

    // ------------------------------------------------------------------ carga inicial

    @Test
    fun `estado inicial e Loading e depois Success com os destaques`() = runTest {
        val vm = newViewModel(observe = false)

        vm.uiState.test {
            val initial = awaitItem()
            assertThat(initial.content).isEqualTo(ExploreContent.Loading)
            assertThat(initial.input).isEqualTo(SearchInput())

            val done = awaitUntil { it.content is ExploreContent.Success }
            assertThat(done.content)
                .isEqualTo(ExploreContent.Success(SampleData.featured, isOffline = false))
            cancelAndIgnoreRemainingEvents()
        }
        // Uma unica busca, sem forcar atualizacao.
        assertThat(repo.searchCalls).containsExactly(SearchCall("", false))
    }

    @Test
    fun `query vazia mostra os destaques tambem depois de uma busca`() = runTest {
        val vm = newViewModel()
        runCurrent()

        vm.onQueryChange("chicken")
        advanceTimeBy(400); runCurrent()
        assertThat(vm.state.content)
            .isEqualTo(ExploreContent.Success(listOf(SampleData.summary()), isOffline = false))

        // Apagar o texto reage na hora (sem esperar o debounce) e volta aos destaques.
        vm.onQueryChange("")
        runCurrent()
        assertThat(vm.state.input).isEqualTo(SearchInput())
        assertThat(vm.state.content)
            .isEqualTo(ExploreContent.Success(SampleData.featured, isOffline = false))
        assertThat(queries()).containsExactly("", "chicken", "").inOrder()
    }

    @Test
    fun `onClearSearch volta aos destaques`() = runTest {
        val vm = newViewModel()
        runCurrent()
        vm.onQueryChange("chicken")
        advanceTimeBy(400); runCurrent()

        vm.onClearSearch()
        runCurrent()

        assertThat(vm.state.input).isEqualTo(SearchInput())
        assertThat(vm.state.content).isEqualTo(ExploreContent.Success(SampleData.featured, false))
    }

    // ------------------------------------------------------------------ debounce / cancelamento

    @Test
    fun `digitar rapido dispara uma unica busca depois do debounce`() = runTest {
        val vm = newViewModel()
        runCurrent()
        assertThat(queries()).containsExactly("") // carga inicial

        // Teclas a cada 100 ms: nenhuma pausa chega a 400 ms.
        "chicken".fold("") { typed, char ->
            (typed + char).also {
                vm.onQueryChange(it)
                advanceTimeBy(100); runCurrent()
            }
        }
        // O campo ja mostra o texto, mas a lista ainda e a anterior (sem piscar).
        assertThat(vm.state.input.query).isEqualTo("chicken")
        assertThat(vm.state.content).isEqualTo(ExploreContent.Success(SampleData.featured, false))
        assertThat(queries()).containsExactly("")

        // Faltam 300 ms para completar 400 ms desde a ultima tecla.
        advanceTimeBy(299); runCurrent()
        assertThat(queries()).containsExactly("")
        advanceTimeBy(1); runCurrent()

        assertThat(queries()).containsExactly("", "chicken").inOrder() // UMA busca para a digitacao toda
        assertThat(vm.state.content)
            .isEqualTo(ExploreContent.Success(listOf(SampleData.summary()), false))
    }

    @Test
    fun `o debounce configurado no construtor e respeitado`() = runTest {
        val vm = newViewModel(debounceMillis = 1_000)
        runCurrent()

        vm.onQueryChange("pasta")
        advanceTimeBy(999); runCurrent()
        assertThat(queries()).containsExactly("")
        advanceTimeBy(1); runCurrent()
        assertThat(queries()).containsExactly("", "pasta").inOrder()
    }

    @Test
    fun `nova busca cancela a anterior que ainda estava em andamento`() = runTest {
        repo.searchDelayByQuery["slow"] = 2_000
        repo.searchResults["slow"] = listOf(SampleData.summary())
        repo.searchResults["fast"] = listOf(SampleData.arrabiata.toSummary())
        val vm = newViewModel()
        runCurrent()

        vm.onQueryChange("slow")
        advanceTimeBy(400); runCurrent()
        // "slow" comecou e esta suspensa no delay do fake.
        assertThat(queries().last()).isEqualTo("slow")
        assertThat(vm.state.content).isEqualTo(ExploreContent.Loading)
        advanceTimeBy(500); runCurrent()
        assertThat(repo.completedSearches).doesNotContain("slow")

        // O usuario muda o texto com a busca anterior ainda pendente.
        vm.onQueryChange("fast")
        advanceTimeBy(400); runCurrent()

        assertThat(repo.cancelledSearches).containsExactly("slow") // cancelada de verdade, nao so ignorada
        assertThat(vm.state.content)
            .isEqualTo(ExploreContent.Success(listOf(SampleData.arrabiata.toSummary()), false))

        // Mesmo depois do tempo que "slow" levaria, o resultado dela nunca aparece.
        advanceTimeBy(10_000); runCurrent()
        assertThat(repo.completedSearches).doesNotContain("slow")
        assertThat(vm.state.content)
            .isEqualTo(ExploreContent.Success(listOf(SampleData.arrabiata.toSummary()), false))
    }

    @Test
    fun `trocar para categoria tambem cancela a busca de texto em andamento`() = runTest {
        repo.searchDelayByQuery["slow"] = 5_000
        val vm = newViewModel()
        runCurrent()

        vm.onQueryChange("slow")
        advanceTimeBy(400); runCurrent()
        assertThat(vm.state.content).isEqualTo(ExploreContent.Loading)

        vm.onCategorySelected("Seafood")
        runCurrent()

        assertThat(repo.cancelledSearches).containsExactly("slow")
        assertThat(vm.state.content)
            .isEqualTo(ExploreContent.Success(listOf(SampleData.crumble.toSummary()), false))
    }

    // ------------------------------------------------------------------ categorias

    @Test
    fun `escolher categoria limpa o texto e tocar de novo desmarca`() = runTest {
        val handle = SavedStateHandle()
        val vm = newViewModel(handle)
        runCurrent()
        vm.onQueryChange("chicken")
        advanceTimeBy(400); runCurrent()

        vm.onCategorySelected("Seafood")
        runCurrent()
        assertThat(vm.state.input).isEqualTo(SearchInput(query = "", category = "Seafood"))
        assertThat(handle.get<String>("query")).isEqualTo("")
        assertThat(handle.get<String>("category")).isEqualTo("Seafood")
        assertThat(repo.categoryCalls).containsExactly(CategoryCall("Seafood", false))
        assertThat(vm.state.content)
            .isEqualTo(ExploreContent.Success(listOf(SampleData.crumble.toSummary()), false))

        // Mesmo chip de novo: desmarca e volta aos destaques.
        vm.onCategorySelected("Seafood")
        runCurrent()
        assertThat(vm.state.input).isEqualTo(SearchInput())
        assertThat(handle.get<String>("category")).isNull()
        assertThat(vm.state.content).isEqualTo(ExploreContent.Success(SampleData.featured, false))
    }

    @Test
    fun `digitar texto sai do modo categoria`() = runTest {
        val vm = newViewModel()
        runCurrent()
        vm.onCategorySelected("Seafood")
        runCurrent()

        vm.onQueryChange("pasta")
        advanceTimeBy(400); runCurrent()

        assertThat(vm.state.input).isEqualTo(SearchInput(query = "pasta", category = null))
        assertThat(queries().last()).isEqualTo("pasta")
    }

    @Test
    fun `trocar de categoria troca a busca sem passar pelo debounce`() = runTest {
        repo.categoryResults["Dessert"] = listOf(SampleData.crumble.toSummary())
        val vm = newViewModel()
        runCurrent()

        vm.onCategorySelected("Seafood")
        runCurrent()
        vm.onCategorySelected("Dessert")
        runCurrent()

        assertThat(repo.categoryCalls.map { it.category }).containsExactly("Seafood", "Dessert").inOrder()
        assertThat(vm.state.input.category).isEqualTo("Dessert")
    }

    // ------------------------------------------------------------------ vazio / erro / offline

    @Test
    fun `sem resultados vira Empty com a busca que nao achou nada`() = runTest {
        val vm = newViewModel()
        runCurrent()

        vm.onQueryChange("zzz")
        advanceTimeBy(400); runCurrent()

        assertThat(vm.state.content)
            .isEqualTo(ExploreContent.Empty(SearchInput(query = "zzz", category = null)))
    }

    @Test
    fun `categoria sem receitas tambem vira Empty`() = runTest {
        val vm = newViewModel()
        runCurrent()

        vm.onCategorySelected("Vegan")
        runCurrent()

        assertThat(vm.state.content)
            .isEqualTo(ExploreContent.Empty(SearchInput(query = "", category = "Vegan")))
    }

    @Test
    fun `falha de rede vira Error NETWORK e retry recarrega forcando atualizacao`() = runTest {
        repo.searchFailure = RecipeException(RecipeException.Kind.NETWORK)
        val vm = newViewModel()
        runCurrent()

        assertThat(vm.state.content).isEqualTo(ExploreContent.Error(UiError.NETWORK))
        assertThat(repo.searchCalls).containsExactly(SearchCall("", false))

        // A conexao voltou; o usuario toca em "Tentar novamente".
        repo.searchFailure = null
        vm.retry()
        runCurrent()

        assertThat(vm.state.content)
            .isEqualTo(ExploreContent.Success(SampleData.featured, isOffline = false))
        assertThat(repo.searchCalls)
            .containsExactly(SearchCall("", false), SearchCall("", true)).inOrder()
    }

    @Test
    fun `retry passa por Loading antes de voltar a Success`() = runTest {
        // Com um pequeno atraso o estado Loading dura tempo virtual suficiente para ser observado
        // (sem atraso, StateFlow conflaria Loading -> Success num unico passo).
        repo.searchDelayMillis = 100
        repo.searchFailure = RecipeException(RecipeException.Kind.SERVER)
        val vm = newViewModel(observe = false)

        vm.uiState.test {
            awaitUntil { it.content == ExploreContent.Error(UiError.SERVER) }
            repo.searchFailure = null
            vm.retry()
            val loading = awaitUntil { it.content == ExploreContent.Loading }
            assertThat(loading.content).isEqualTo(ExploreContent.Loading)
            val success = awaitUntil { it.content is ExploreContent.Success }
            assertThat((success.content as ExploreContent.Success).recipes).isEqualTo(SampleData.featured)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `falha do servidor vira Error SERVER`() = runTest {
        repo.searchFailure = RecipeException(RecipeException.Kind.SERVER)
        val vm = newViewModel()
        runCurrent()

        assertThat(vm.state.content).isEqualTo(ExploreContent.Error(UiError.SERVER))
    }

    @Test
    fun `excecao inesperada vira Error UNKNOWN sem derrubar o ViewModel`() = runTest {
        repo.searchFailure = IllegalStateException("bug qualquer")
        val vm = newViewModel()
        runCurrent()

        assertThat(vm.state.content).isEqualTo(ExploreContent.Error(UiError.UNKNOWN))

        // Continua funcional: o proximo texto busca normalmente.
        repo.searchFailure = null
        vm.onQueryChange("chicken")
        advanceTimeBy(400); runCurrent()
        assertThat(vm.state.content).isInstanceOf(ExploreContent.Success::class.java)
    }

    @Test
    fun `cache vencido liga o aviso offline`() = runTest {
        repo.source = DataSource.CACHE_STALE
        val vm = newViewModel()
        runCurrent()

        assertThat(vm.state.content)
            .isEqualTo(ExploreContent.Success(SampleData.featured, isOffline = true))
    }

    @Test
    fun `cache valido e resposta remota nao mostram aviso offline`() = runTest {
        repo.source = DataSource.CACHE_FRESH
        val vm = newViewModel()
        runCurrent()
        assertThat((vm.state.content as ExploreContent.Success).isOffline).isFalse()

        repo.source = DataSource.REMOTE
        vm.retry()
        runCurrent()
        assertThat((vm.state.content as ExploreContent.Success).isOffline).isFalse()
    }

    // ------------------------------------------------------------------ restauracao do estado

    @Test
    fun `restaura a ultima busca do DataStore uma unica vez sem busca duplicada`() = runTest {
        prefs.state.value = UserPreferences(lastQuery = "chicken")
        val vm = newViewModel()

        advanceTimeBy(400); runCurrent()
        advanceTimeBy(5_000); runCurrent()

        assertThat(vm.state.input).isEqualTo(SearchInput(query = "chicken", category = null))
        // Nada de buscar "" (destaques) antes: so a busca restaurada, e uma vez.
        assertThat(repo.searchCalls).containsExactly(SearchCall("chicken", false))
        assertThat(vm.state.content)
            .isEqualTo(ExploreContent.Success(listOf(SampleData.summary()), false))
    }

    @Test
    fun `restaura a ultima categoria do DataStore sem buscar destaques`() = runTest {
        prefs.state.value = UserPreferences(lastCategory = "Seafood")
        val vm = newViewModel()

        runCurrent()
        advanceTimeBy(5_000); runCurrent()

        assertThat(vm.state.input).isEqualTo(SearchInput(query = "", category = "Seafood"))
        assertThat(repo.searchCalls).isEmpty()
        assertThat(repo.categoryCalls).containsExactly(CategoryCall("Seafood", false))
    }

    @Test
    fun `estado vindo do SavedStateHandle tem prioridade sobre o DataStore`() = runTest {
        // Simula a recriacao do processo: o handle ja traz a busca que o usuario estava fazendo.
        prefs.state.value = UserPreferences(lastQuery = "chicken")
        val handle = SavedStateHandle(mapOf("query" to "pasta", "category" to null))
        val vm = newViewModel(handle)

        advanceTimeBy(400); runCurrent()
        advanceTimeBy(5_000); runCurrent()

        assertThat(vm.state.input).isEqualTo(SearchInput(query = "pasta", category = null))
        assertThat(queries()).containsExactly("pasta") // "chicken" (DataStore) nunca e buscada
        assertThat(vm.state.content)
            .isEqualTo(ExploreContent.Success(listOf(SampleData.arrabiata.toSummary()), false))
    }

    @Test
    fun `um novo ViewModel com o mesmo handle retoma a busca de onde parou`() = runTest {
        val handle = SavedStateHandle()
        val first = newViewModel(handle)
        runCurrent()
        first.onQueryChange("soup")
        advanceTimeBy(400); runCurrent()
        assertThat(handle.get<String>("query")).isEqualTo("soup")
        first.viewModelScope.cancel() // o "processo" morreu

        val second = newViewModel(handle)
        advanceTimeBy(400); runCurrent()

        assertThat(second.state.input.query).isEqualTo("soup")
        assertThat(second.state.content)
            .isEqualTo(ExploreContent.Success(listOf(SampleData.crumble.toSummary()), false))
    }

    @Test
    fun `a ultima busca e gravada no DataStore so depois de uma pausa`() = runTest {
        val vm = newViewModel()
        runCurrent()

        vm.onQueryChange("pasta")
        advanceTimeBy(799); runCurrent()
        assertThat(prefs.lastSearchCalls.map { it.first }).doesNotContain("pasta")

        advanceTimeBy(1); runCurrent()
        assertThat(prefs.lastSearchCalls.last()).isEqualTo("pasta" to null)
    }

    // ------------------------------------------------------------------ entrada do usuario

    @Test
    fun `onQueryChange remove caracteres de controle e limita o tamanho`() = runTest {
        val vm = newViewModel()
        runCurrent()

        vm.onQueryChange("pa\nst\u0007a")
        runCurrent()
        assertThat(vm.state.input.query).isEqualTo("pasta")

        vm.onQueryChange("x".repeat(200))
        runCurrent()
        assertThat(vm.state.input.query).hasLength(60)
    }

    // ------------------------------------------------------------------ lembrete diario

    @Test
    fun `ligar o lembrete sem permissao avisa e mantem tudo desligado`() = runTest {
        val vm = newViewModel()
        runCurrent()

        vm.onReminderToggled(enabled = true, permissionGranted = false)
        runCurrent()

        assertThat(vm.state.message).isEqualTo(ExploreMessage.PERMISSION_DENIED)
        assertThat(vm.state.reminderEnabled).isFalse()
        assertThat(prefs.reminderCalls).containsExactly(false)
        assertThat(scheduler.enableCount).isEqualTo(0)
        assertThat(scheduler.disableCount).isEqualTo(1)

        vm.onMessageShown()
        runCurrent()
        assertThat(vm.state.message).isNull()
    }

    @Test
    fun `ligar o lembrete com permissao grava a preferencia e agenda`() = runTest {
        val vm = newViewModel()
        runCurrent()

        vm.onReminderToggled(enabled = true, permissionGranted = true)
        runCurrent()

        assertThat(vm.state.message).isEqualTo(ExploreMessage.REMINDER_ON)
        assertThat(vm.state.reminderEnabled).isTrue() // reativo: vem do DataStore
        assertThat(prefs.reminderCalls).containsExactly(true)
        assertThat(scheduler.enableCount).isEqualTo(1)
        assertThat(scheduler.disableCount).isEqualTo(0)
        assertThat(scheduler.isEnabled).isTrue()
    }

    @Test
    fun `desligar o lembrete cancela o agendamento`() = runTest {
        prefs.state.value = UserPreferences(reminderEnabled = true)
        val vm = newViewModel()
        runCurrent()
        assertThat(vm.state.reminderEnabled).isTrue()

        vm.onReminderToggled(enabled = false)
        runCurrent()

        assertThat(vm.state.message).isEqualTo(ExploreMessage.REMINDER_OFF)
        assertThat(vm.state.reminderEnabled).isFalse()
        assertThat(prefs.reminderCalls).containsExactly(false)
        assertThat(scheduler.events).containsExactly("disable")
    }

    @Test
    fun `desligar nao pede permissao mesmo que ela tenha sido negada`() = runTest {
        val vm = newViewModel()
        runCurrent()

        vm.onReminderToggled(enabled = false, permissionGranted = false)
        runCurrent()

        assertThat(vm.state.message).isEqualTo(ExploreMessage.REMINDER_OFF)
    }

    // ------------------------------------------------------------------ categorias e receita do dia

    @Test
    fun `categorias e receita do dia carregam em paralelo com a busca`() = runTest {
        repo.categoriesDelayMillis = 1_000
        repo.dailyDelayMillis = 1_000
        val vm = newViewModel()
        runCurrent()

        // As tres chamadas ja comecaram: nenhuma esperou a outra terminar.
        assertThat(repo.searchCalls).hasSize(1)
        assertThat(repo.categoriesCalls).hasSize(1)
        assertThat(repo.dailyCalls.get()).isEqualTo(1)
        // A lista aparece sem esperar categorias/receita do dia.
        assertThat(vm.state.content).isInstanceOf(ExploreContent.Success::class.java)
        assertThat(vm.state.categories).isEmpty()
        assertThat(vm.state.daily).isEqualTo(DailyState.Loading)

        advanceTimeBy(999); runCurrent()
        assertThat(vm.state.daily).isEqualTo(DailyState.Loading)

        // Em sequencia seriam 2000 ms; em paralelo os dois terminam juntos aos 1000 ms.
        advanceTimeBy(1); runCurrent()
        assertThat(vm.state.categories).isEqualTo(SampleData.categories)
        assertThat(vm.state.daily).isEqualTo(DailyState.Ready(SampleData.arrabiata.toSummary()))
    }

    @Test
    fun `falha ao carregar categorias e receita do dia e silenciosa`() = runTest {
        repo.categoriesFailure = RecipeException(RecipeException.Kind.NETWORK)
        repo.dailyFailure = RecipeException(RecipeException.Kind.SERVER)
        val vm = newViewModel()
        runCurrent()

        assertThat(vm.state.categories).isEmpty()
        assertThat(vm.state.daily).isEqualTo(DailyState.Unavailable)
        // A lista principal nao e afetada e nenhuma mensagem de erro e emitida.
        assertThat(vm.state.content).isEqualTo(ExploreContent.Success(SampleData.featured, false))
        assertThat(vm.state.message).isNull()
    }

    @Test
    fun `excecao inesperada ao carregar a receita do dia tambem e silenciosa`() = runTest {
        repo.dailyFailure = IllegalStateException("json estranho")
        val vm = newViewModel()
        runCurrent()

        assertThat(vm.state.daily).isEqualTo(DailyState.Unavailable)
    }

    @Test
    fun `falha ao recarregar categorias mantem as que ja estavam na tela`() = runTest {
        val vm = newViewModel()
        runCurrent()
        assertThat(vm.state.categories).isEqualTo(SampleData.categories)

        repo.categoriesFailure = RecipeException(RecipeException.Kind.NETWORK)
        vm.retry()
        runCurrent()

        assertThat(vm.state.categories).isEqualTo(SampleData.categories)
    }

    @Test
    fun `retry recarrega tambem categorias e receita do dia`() = runTest {
        repo.categoriesFailure = RecipeException(RecipeException.Kind.NETWORK)
        repo.dailyFailure = RecipeException(RecipeException.Kind.NETWORK)
        val vm = newViewModel()
        runCurrent()
        assertThat(vm.state.daily).isEqualTo(DailyState.Unavailable)

        repo.categoriesFailure = null
        repo.dailyFailure = null
        vm.retry()
        runCurrent()

        assertThat(repo.categoriesCalls).hasSize(2)
        assertThat(repo.dailyCalls.get()).isEqualTo(2)
        assertThat(vm.state.categories).isEqualTo(SampleData.categories)
        assertThat(vm.state.daily).isEqualTo(DailyState.Ready(SampleData.arrabiata.toSummary()))
    }

    // ------------------------------------------------------------------ caderno

    @Test
    fun `savedIds acompanha o caderno em tempo real`() = runTest {
        val vm = newViewModel()
        runCurrent()
        assertThat(vm.state.savedIds).isEmpty()

        repo.savedFlow.value = listOf(SampleData.saved())
        runCurrent()
        assertThat(vm.state.savedIds).containsExactly(SampleData.TERIYAKI_ID)

        repo.savedFlow.value = emptyList()
        runCurrent()
        assertThat(vm.state.savedIds).isEmpty()
    }
}
