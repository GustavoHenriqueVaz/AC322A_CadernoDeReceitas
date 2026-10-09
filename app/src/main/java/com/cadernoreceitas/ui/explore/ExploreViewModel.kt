package com.cadernoreceitas.ui.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadernoreceitas.data.local.prefs.UserPreferencesRepository
import com.cadernoreceitas.data.repository.DefaultRecipeRepository
import com.cadernoreceitas.data.repository.RecipeRepository
import com.cadernoreceitas.domain.model.Category
import com.cadernoreceitas.domain.model.RecipeSummary
import com.cadernoreceitas.ui.components.UiError
import com.cadernoreceitas.ui.components.toUiError
import com.cadernoreceitas.work.ReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** O que o usuario esta procurando: texto digitado OU categoria (um limpa o outro). */
data class SearchInput(val query: String = "", val category: String? = null)

/** Estados visuais da lista de resultados. */
sealed interface ExploreContent {
    data object Loading : ExploreContent
    data class Success(val recipes: List<RecipeSummary>, val isOffline: Boolean) : ExploreContent
    data class Empty(val input: SearchInput) : ExploreContent
    data class Error(val error: UiError) : ExploreContent
}

/** Receita do dia mostrada no topo (carregada em paralelo, falha silenciosa). */
sealed interface DailyState {
    data object Loading : DailyState
    data class Ready(val recipe: RecipeSummary) : DailyState
    data object Unavailable : DailyState
}

enum class ExploreMessage { REMINDER_ON, REMINDER_OFF, PERMISSION_DENIED }

data class ExploreUiState(
    val input: SearchInput = SearchInput(),
    val content: ExploreContent = ExploreContent.Loading,
    val categories: List<Category> = emptyList(),
    val savedIds: Set<String> = emptySet(),
    val daily: DailyState = DailyState.Loading,
    val reminderEnabled: Boolean = false,
    val message: ExploreMessage? = null,
)

/**
 * Fluxo unidirecional: a UI envia eventos (onQueryChange, onCategorySelected, retry...) e
 * observa um unico [ExploreUiState]. O estado da busca fica no [SavedStateHandle], entao
 * sobrevive a rotacao E a morte do processo.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ExploreViewModel(
    private val repository: RecipeRepository,
    private val preferences: UserPreferencesRepository,
    private val reminderScheduler: ReminderScheduler,
    private val savedStateHandle: SavedStateHandle,
    private val searchDebounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) : ViewModel() {

    private val hasStateFromHandle = savedStateHandle.contains(KEY_QUERY)
    private val input = MutableStateFlow(
        SearchInput(
            query = savedStateHandle[KEY_QUERY] ?: "",
            category = savedStateHandle[KEY_CATEGORY],
        ),
    )

    /** So comeca a buscar depois de restaurar a ultima busca (DataStore) para nao buscar 2x. */
    private val restored = MutableStateFlow(hasStateFromHandle)
    private val reloadToken = MutableStateFlow(0)
    private var forceRefreshNext = false

    private val categories = MutableStateFlow<List<Category>>(emptyList())
    private val daily = MutableStateFlow<DailyState>(DailyState.Loading)
    private val message = MutableStateFlow<ExploreMessage?>(null)

    private val content: StateFlow<ExploreContent> = restored
        .filter { it }
        .flatMapLatest {
            combine(
                // Sem texto -> reage na hora (categoria/limpar); digitando -> espera uma pausa.
                input.debounce { if (it.query.isEmpty()) 0L else searchDebounceMillis }.distinctUntilChanged(),
                reloadToken,
            ) { searchInput, _ -> searchInput }
        }
        // flatMapLatest CANCELA a busca anterior (e a chamada HTTP dela) quando chega uma nova.
        .flatMapLatest { searchInput ->
            flow {
                emit(ExploreContent.Loading)
                val force = forceRefreshNext
                forceRefreshNext = false
                emit(load(searchInput, force))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ExploreContent.Loading)

    val uiState: StateFlow<ExploreUiState> = combine(
        combine(input, content, categories) { i, c, cats -> Triple(i, c, cats) },
        combine(repository.observeSavedIds(), daily, preferences.preferences.map { it.reminderEnabled }, message) {
                saved, d, reminder, msg ->
            Quad(saved, d, reminder, msg)
        },
    ) { (i, c, cats), (saved, d, reminder, msg) ->
        ExploreUiState(
            input = i,
            content = c,
            categories = cats,
            savedIds = saved,
            daily = d,
            reminderEnabled = reminder,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ExploreUiState())

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    init {
        viewModelScope.launch {
            if (!hasStateFromHandle) {
                val last = preferences.preferences.first()
                setInput(SearchInput(last.lastQuery, last.lastCategory))
            }
            restored.value = true
        }
        // Guarda a ultima busca (DataStore) para reabrir o app no mesmo ponto.
        viewModelScope.launch {
            restored.filter { it }.first()
            input.debounce(PERSIST_DEBOUNCE_MILLIS).distinctUntilChanged().onEach {
                preferences.setLastSearch(it.query, it.category)
            }.collect {}
        }
        loadCategories()
        loadDaily()
    }

    // ------------------------------------------------------------------ eventos

    fun onQueryChange(text: String) {
        val clean = text.filterNot { it.isISOControl() }.take(DefaultRecipeRepository.MAX_QUERY_LENGTH)
        // Digitar sai do modo "categoria".
        setInput(SearchInput(query = clean, category = if (clean.isEmpty()) input.value.category else null))
    }

    fun onCategorySelected(category: String?) {
        // Escolher categoria limpa o texto. O mesmo chip de novo desmarca.
        val next = if (category == input.value.category) null else category
        setInput(SearchInput(query = "", category = next))
    }

    fun onClearSearch() = setInput(SearchInput())

    fun retry() {
        forceRefreshNext = true
        reloadToken.update { it + 1 }
        loadCategories()
        loadDaily()
    }

    fun onMessageShown() {
        message.value = null
    }

    fun onReminderToggled(enabled: Boolean, permissionGranted: Boolean = true) {
        viewModelScope.launch {
            if (enabled && !permissionGranted) {
                preferences.setReminderEnabled(false)
                reminderScheduler.disable()
                message.value = ExploreMessage.PERMISSION_DENIED
                return@launch
            }
            preferences.setReminderEnabled(enabled)
            if (enabled) reminderScheduler.enable() else reminderScheduler.disable()
            message.value = if (enabled) ExploreMessage.REMINDER_ON else ExploreMessage.REMINDER_OFF
        }
    }

    // ------------------------------------------------------------------ internos

    private fun setInput(value: SearchInput) {
        input.value = value
        savedStateHandle[KEY_QUERY] = value.query
        savedStateHandle[KEY_CATEGORY] = value.category
    }

    private suspend fun load(searchInput: SearchInput, forceRefresh: Boolean): ExploreContent = try {
        val loaded = when {
            searchInput.query.isNotBlank() -> repository.search(searchInput.query, forceRefresh)
            searchInput.category != null -> repository.recipesByCategory(searchInput.category, forceRefresh)
            else -> repository.search("", forceRefresh)
        }
        if (loaded.value.isEmpty()) {
            ExploreContent.Empty(searchInput)
        } else {
            ExploreContent.Success(loaded.value, loaded.isOffline)
        }
    } catch (e: CancellationException) {
        throw e // cancelamento NAO e erro de tela: precisa propagar para a coroutine encerrar
    } catch (e: Exception) {
        ExploreContent.Error(e.toUiError())
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categories.value = try {
                repository.categories().value
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                categories.value // erro nao-critico: mantem o que ja tinha (os chips so somem)
            }
        }
    }

    private fun loadDaily() {
        viewModelScope.launch {
            daily.value = try {
                DailyState.Ready(repository.recipeOfTheDay().value.toSummary())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DailyState.Unavailable
            }
        }
    }

    companion object {
        const val KEY_QUERY = "query"
        const val KEY_CATEGORY = "category"
        const val DEFAULT_DEBOUNCE_MILLIS = 400L
        private const val PERSIST_DEBOUNCE_MILLIS = 800L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
