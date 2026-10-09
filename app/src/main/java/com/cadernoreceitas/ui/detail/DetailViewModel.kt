package com.cadernoreceitas.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadernoreceitas.data.repository.RecipeRepository
import com.cadernoreceitas.domain.model.Recipe
import com.cadernoreceitas.domain.model.SavedRecipe
import com.cadernoreceitas.ui.components.UiError
import com.cadernoreceitas.ui.components.toUiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DetailContent {
    data object Loading : DetailContent
    data class Success(val recipe: Recipe, val isOffline: Boolean) : DetailContent
    data class Error(val error: UiError) : DetailContent
}

/** Mensagem efemera (snackbar). `Removed` carrega o item para permitir "Desfazer". */
sealed interface DetailMessage {
    data object Saved : DetailMessage
    data class Removed(val item: SavedRecipe) : DetailMessage
    data object Failed : DetailMessage
}

data class DetailUiState(
    val content: DetailContent = DetailContent.Loading,
    /** Nao nulo quando a receita esta no caderno (inclui a anotacao pessoal). */
    val saved: SavedRecipe? = null,
    val message: DetailMessage? = null,
) {
    val isSaved: Boolean get() = saved != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel(
    private val recipeId: String,
    private val repository: RecipeRepository,
) : ViewModel() {

    private val reloadToken = MutableStateFlow(0)
    private val message = MutableStateFlow<DetailMessage?>(null)

    private val content: StateFlow<DetailContent> = reloadToken
        .flatMapLatest {
            flow {
                emit(DetailContent.Loading)
                emit(load())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DetailContent.Loading)

    val uiState: StateFlow<DetailUiState> = combine(
        content,
        repository.observeSavedRecipe(recipeId),
        message,
    ) { c, saved, msg -> DetailUiState(content = c, saved = saved, message = msg) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DetailUiState())

    private suspend fun load(): DetailContent = try {
        val loaded = repository.recipe(recipeId)
        DetailContent.Success(loaded.value, loaded.isOffline)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DetailContent.Error(e.toUiError())
    }

    fun retry() = reloadToken.update { it + 1 }

    /** Coracao: salva a receita no caderno ou remove (com opcao de desfazer). */
    fun toggleSaved() {
        viewModelScope.launch {
            try {
                val current = uiState.value.saved
                if (current != null) {
                    repository.removeSaved(recipeId)?.let { message.value = DetailMessage.Removed(it) }
                } else {
                    val recipe = (uiState.value.content as? DetailContent.Success)?.recipe ?: return@launch
                    repository.saveRecipe(recipe)
                    message.value = DetailMessage.Saved
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = DetailMessage.Failed
            }
        }
    }

    fun undoRemoval(item: SavedRecipe) {
        viewModelScope.launch {
            try {
                repository.restoreSaved(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = DetailMessage.Failed
            }
        }
    }

    fun onMessageShown() {
        message.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
