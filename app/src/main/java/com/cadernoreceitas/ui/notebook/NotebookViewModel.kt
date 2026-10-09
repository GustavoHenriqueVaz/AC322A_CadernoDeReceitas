package com.cadernoreceitas.ui.notebook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadernoreceitas.data.repository.RecipeRepository
import com.cadernoreceitas.domain.model.SavedRecipe
import com.cadernoreceitas.ui.components.UiError
import com.cadernoreceitas.ui.components.toUiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NotebookFilter {
    ALL,

    /** So as que tem nota ou observacao do usuario. */
    WITH_NOTE,

    /** Avaliadas com 4 estrelas ou mais. */
    TOP_RATED,
}

fun NotebookFilter.matches(item: SavedRecipe): Boolean = when (this) {
    NotebookFilter.ALL -> true
    NotebookFilter.WITH_NOTE -> item.hasNote
    NotebookFilter.TOP_RATED -> (item.rating ?: 0) >= 4
}

sealed interface NotebookContent {
    data object Loading : NotebookContent
    data class Success(val items: List<SavedRecipe>) : NotebookContent

    /** [filtered] = true quando existem receitas salvas, mas nenhuma passa pelo filtro. */
    data class Empty(val filtered: Boolean) : NotebookContent
    data class Error(val error: UiError) : NotebookContent
}

data class NotebookUiState(
    val content: NotebookContent = NotebookContent.Loading,
    val filter: NotebookFilter = NotebookFilter.ALL,
    val totalSaved: Int = 0,
    /** Ultimo item removido: a UI mostra "Desfazer" enquanto este valor existir. */
    val lastRemoved: SavedRecipe? = null,
)

/**
 * O caderno e 100% local: observa o Room (Flow) e a UI se atualiza sozinha quando algo
 * e salvo, anotado ou removido em qualquer outra tela.
 */
class NotebookViewModel(
    private val repository: RecipeRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val filter = MutableStateFlow(
        savedStateHandle.get<String>(KEY_FILTER)
            ?.let { name -> NotebookFilter.entries.firstOrNull { it.name == name } }
            ?: NotebookFilter.ALL,
    )
    private val lastRemoved = MutableStateFlow<SavedRecipe?>(null)

    private val reloadToken = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val source = reloadToken.flatMapLatest {
        repository.observeSaved()
            .map<List<SavedRecipe>, Result<List<SavedRecipe>>> { Result.success(it) }
            .catch { emit(Result.failure(it)) }
    }

    val uiState: StateFlow<NotebookUiState> = combine(source, filter, lastRemoved) { result, f, removed ->
        val items = result.getOrNull()
        val content = when {
            items == null -> NotebookContent.Error(result.exceptionOrNull()?.toUiError() ?: UiError.UNKNOWN)
            items.isEmpty() -> NotebookContent.Empty(filtered = false)
            else -> items.filter(f::matches).let { shown ->
                if (shown.isEmpty()) NotebookContent.Empty(filtered = true) else NotebookContent.Success(shown)
            }
        }
        NotebookUiState(content, f, items?.size ?: 0, removed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), NotebookUiState())

    /** Reassina o Flow do banco depois de uma falha de leitura. */
    fun retry() = reloadToken.update { it + 1 }

    fun onFilterSelected(value: NotebookFilter) {
        filter.value = value
        savedStateHandle[KEY_FILTER] = value.name
    }

    fun remove(recipeId: String) {
        viewModelScope.launch {
            try {
                lastRemoved.value = repository.removeSaved(recipeId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Falha local improvavel; o Flow continua mostrando o estado real do banco.
            }
        }
    }

    fun undoRemove() {
        val item = lastRemoved.value ?: return
        lastRemoved.value = null
        viewModelScope.launch {
            try {
                repository.restoreSaved(item)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // ignora: o item simplesmente nao volta
            }
        }
    }

    fun onRemovalMessageShown() {
        lastRemoved.value = null
    }

    private companion object {
        const val KEY_FILTER = "filter"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
