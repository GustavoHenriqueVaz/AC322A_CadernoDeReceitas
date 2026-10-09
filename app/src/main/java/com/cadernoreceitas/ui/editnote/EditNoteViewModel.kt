package com.cadernoreceitas.ui.editnote

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadernoreceitas.data.photo.PhotoStorage
import com.cadernoreceitas.data.repository.RecipeRepository
import com.cadernoreceitas.domain.model.Recipe
import com.cadernoreceitas.domain.model.RecipeNote
import com.cadernoreceitas.ui.components.UiError
import com.cadernoreceitas.ui.components.toUiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NoteField { RATING, COMMENT, MINUTES }

/** O que o usuario digitou (texto cru). Salvo no SavedStateHandle para sobreviver a morte do processo. */
data class NoteFields(
    val rating: Int = 0,
    val comment: String = "",
    val minutes: String = "",
    /** Foto atual: caminho em cache (recem escolhida) ou em filesDir (ja salva). */
    val photoPath: String? = null,
)

data class EditNoteUiState(
    val isLoading: Boolean = true,
    val loadError: UiError? = null,
    val recipeName: String = "",
    val fields: NoteFields = NoteFields(),
    /** Erros ja filtrados: so aparecem para campos "tocados" ou apos tentar salvar. */
    val ratingError: FieldError? = null,
    val commentError: FieldError? = null,
    val minutesError: FieldError? = null,
    val isImportingPhoto: Boolean = false,
    val photoFailed: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    /** Verdadeiro depois de gravar: a tela observa isto e volta para a anterior. */
    val saved: Boolean = false,
    val isDirty: Boolean = false,
) {
    val hasVisibleErrors: Boolean
        get() = ratingError != null || commentError != null || minutesError != null
}

class EditNoteViewModel(
    private val recipeId: String,
    private val repository: RecipeRepository,
    private val photoStorage: PhotoStorage,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(EditNoteUiState())
    val uiState: StateFlow<EditNoteUiState> = _state.asStateFlow()

    private var recipe: Recipe? = null
    private var initialFields = NoteFields()
    private var originalPhotoPath: String? = null
    private var touched: Set<NoteField> = emptySet()
    private var submitAttempted = false
    private var photoJob: Job? = null

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val loaded = repository.recipe(recipeId).value
                recipe = loaded
                val existing = repository.observeSavedRecipe(recipeId).first()
                originalPhotoPath = existing?.photoPath
                initialFields = NoteFields(
                    rating = existing?.rating ?: 0,
                    comment = existing?.comment.orEmpty(),
                    minutes = existing?.prepMinutes?.toString().orEmpty(),
                    photoPath = existing?.photoPath,
                )
                // Se o processo foi recriado, os valores digitados (SavedStateHandle) vencem.
                val restored = restoreFromHandle() ?: initialFields
                _state.value = EditNoteUiState(
                    isLoading = false,
                    recipeName = loaded.name,
                    fields = restored,
                ).recomputed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, loadError = e.toUiError()) }
            }
        }
    }

    // ------------------------------------------------------------------ eventos

    fun onRatingChange(value: Int) = updateFields(NoteField.RATING) { it.copy(rating = value) }

    fun onCommentChange(value: String) =
        updateFields(null) { it.copy(comment = NoteValidator.sanitizeComment(value)) }

    fun onMinutesChange(value: String) =
        updateFields(null) { it.copy(minutes = NoteValidator.sanitizeMinutes(value)) }

    /** Chamado quando o campo perde o foco: a partir dai o erro passa a ser exibido. */
    fun onFieldBlurred(field: NoteField) {
        touched = touched + field
        _state.update { it.recomputed() }
    }

    fun onPhotoPicked(uri: Uri?) {
        if (uri == null) return // usuario cancelou o Photo Picker
        photoJob?.cancel() // so uma importacao por vez; a anterior e cancelada de verdade
        photoJob = viewModelScope.launch {
            _state.update { it.copy(isImportingPhoto = true, photoFailed = false) }
            try {
                val tempPath = photoStorage.importToTemp(uri)
                val previousTemp = _state.value.fields.photoPath.takeIf { it != originalPhotoPath }
                photoStorage.deleteTemp(previousTemp)
                updateFields(null) { it.copy(photoPath = tempPath) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(photoFailed = true) }
            } finally {
                _state.update { it.copy(isImportingPhoto = false) }
            }
        }
    }

    fun onPhotoRemoved() {
        val current = _state.value.fields.photoPath
        if (current != null && current != originalPhotoPath) {
            viewModelScope.launch { photoStorage.deleteTemp(current) }
        }
        updateFields(null) { it.copy(photoPath = null) }
    }

    fun onPhotoErrorShown() = _state.update { it.copy(photoFailed = false) }

    fun onSaveFailedShown() = _state.update { it.copy(saveFailed = false) }

    fun onSave() {
        submitAttempted = true
        _state.update { it.recomputed() }
        val current = _state.value
        if (current.isSaving || current.hasVisibleErrors) return
        val loadedRecipe = recipe ?: return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveFailed = false) }
            try {
                val fields = _state.value.fields
                // 1) Foto nova -> move do cache para o armazenamento interno do app.
                val photoToStore = fields.photoPath?.let { path ->
                    if (path == originalPhotoPath) path else photoStorage.commit(path, recipeId)
                }
                // 2) Grava no Room (salva a receita no caderno, se ainda nao estava).
                repository.saveNote(
                    recipe = loadedRecipe,
                    note = RecipeNote(
                        rating = fields.rating,
                        comment = fields.comment.trim(),
                        prepMinutes = fields.minutes.trim().toIntOrNull(),
                        photoPath = photoToStore,
                    ),
                )
                // 3) So depois de gravar apaga a foto antiga (se foi trocada ou removida).
                if (originalPhotoPath != null && originalPhotoPath != photoToStore) {
                    photoStorage.delete(originalPhotoPath)
                }
                _state.update { it.copy(isSaving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    // ------------------------------------------------------------------ internos

    /**
     * [touchedField]: campo que passa a mostrar erro imediatamente. A nota (estrelas) nao tem
     * "foco", entao valida ja ao mudar; os campos de texto so validam ao perder o foco.
     */
    private fun updateFields(touchedField: NoteField?, transform: (NoteFields) -> NoteFields) {
        if (touchedField != null) touched = touched + touchedField
        _state.update { state ->
            val next = transform(state.fields)
            persistToHandle(next)
            state.copy(fields = next).recomputed()
        }
    }

    /** Recalcula os erros visiveis e o "tem alteracoes nao salvas". */
    private fun EditNoteUiState.recomputed(): EditNoteUiState {
        fun visible(field: NoteField) = submitAttempted || field in touched
        return copy(
            ratingError = NoteValidator.validateRating(fields.rating).takeIf { visible(NoteField.RATING) },
            commentError = NoteValidator.validateComment(fields.comment).takeIf { visible(NoteField.COMMENT) },
            minutesError = NoteValidator.validateMinutes(fields.minutes).takeIf { visible(NoteField.MINUTES) },
            isDirty = fields != initialFields,
        )
    }

    private fun persistToHandle(fields: NoteFields) {
        savedStateHandle[KEY_RATING] = fields.rating
        savedStateHandle[KEY_COMMENT] = fields.comment
        savedStateHandle[KEY_MINUTES] = fields.minutes
        savedStateHandle[KEY_PHOTO] = fields.photoPath
        savedStateHandle[KEY_EDITED] = true
    }

    private fun restoreFromHandle(): NoteFields? {
        if (savedStateHandle.get<Boolean>(KEY_EDITED) != true) return null
        return NoteFields(
            rating = savedStateHandle[KEY_RATING] ?: 0,
            comment = savedStateHandle[KEY_COMMENT] ?: "",
            minutes = savedStateHandle[KEY_MINUTES] ?: "",
            photoPath = savedStateHandle[KEY_PHOTO],
        )
    }

    private companion object {
        const val KEY_RATING = "rating"
        const val KEY_COMMENT = "comment"
        const val KEY_MINUTES = "minutes"
        const val KEY_PHOTO = "photo"
        const val KEY_EDITED = "edited"
    }
}
