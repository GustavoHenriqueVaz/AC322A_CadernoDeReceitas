package com.cadernoreceitas.ui.editnote

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadernoreceitas.R
import com.cadernoreceitas.ui.components.ErrorState
import com.cadernoreceitas.ui.components.LoadingState
import com.cadernoreceitas.ui.components.RatingInput
import com.cadernoreceitas.ui.components.RecipeImage
import com.cadernoreceitas.ui.components.TestTags
import com.cadernoreceitas.ui.components.message
import java.io.File

@Composable
fun EditNoteRoute(
    viewModel: EditNoteViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EditNoteScreen(
        state = state,
        onBack = onBack,
        onRatingChange = viewModel::onRatingChange,
        onCommentChange = viewModel::onCommentChange,
        onMinutesChange = viewModel::onMinutesChange,
        onFieldBlurred = viewModel::onFieldBlurred,
        onPhotoPicked = viewModel::onPhotoPicked,
        onPhotoRemoved = viewModel::onPhotoRemoved,
        onPhotoErrorShown = viewModel::onPhotoErrorShown,
        onSaveFailedShown = viewModel::onSaveFailedShown,
        onSave = viewModel::onSave,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteScreen(
    state: EditNoteUiState,
    onBack: () -> Unit,
    onRatingChange: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    onFieldBlurred: (NoteField) -> Unit,
    onPhotoPicked: (Uri?) -> Unit,
    onPhotoRemoved: () -> Unit,
    onPhotoErrorShown: () -> Unit,
    onSaveFailedShown: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Ao gravar com sucesso volta para a tela anterior (que ja mostra a anotacao atualizada).
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val requestBack = { if (state.isDirty && !state.saved) showDiscardDialog = true else onBack() }
    BackHandler(enabled = state.isDirty && !state.saved) { showDiscardDialog = true }

    val snackbarHostState = remember { SnackbarHostState() }
    val photoErrorText = stringResource(R.string.photo_error)
    val saveFailedText = stringResource(R.string.save_failed)
    LaunchedEffect(state.photoFailed) {
        if (state.photoFailed) {
            snackbarHostState.showSnackbar(photoErrorText)
            onPhotoErrorShown()
        }
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbarHostState.showSnackbar(saveFailedText)
            onSaveFailedShown()
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.keep_editing)) }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_note_title)) },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                state.isLoading -> LoadingState()
                state.loadError != null -> ErrorState(message = state.loadError.message(), onRetry = onRetry)
                else -> NoteForm(
                    state = state,
                    onRatingChange = onRatingChange,
                    onCommentChange = onCommentChange,
                    onMinutesChange = onMinutesChange,
                    onFieldBlurred = onFieldBlurred,
                    onPhotoPicked = onPhotoPicked,
                    onPhotoRemoved = onPhotoRemoved,
                    onSave = onSave,
                )
            }
        }
    }
}

@Composable
private fun NoteForm(
    state: EditNoteUiState,
    onRatingChange: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    onFieldBlurred: (NoteField) -> Unit,
    onPhotoPicked: (Uri?) -> Unit,
    onPhotoRemoved: () -> Unit,
    onSave: () -> Unit,
) {
    // Photo Picker do Android: nao precisa de permissao de armazenamento.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        onPhotoPicked(uri)
    }

    Column(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = state.recipeName,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )

        // ---- Nota
        Column {
            Text(stringResource(R.string.rating_label), style = MaterialTheme.typography.titleMedium)
            RatingInput(
                rating = state.fields.rating,
                onRatingChange = onRatingChange,
                isError = state.ratingError != null,
            )
            state.ratingError?.let {
                Text(
                    text = it.text(R.string.rating_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        // ---- Observacoes (obrigatorio, 10-500 caracteres)
        var commentHadFocus by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = state.fields.comment,
            onValueChange = onCommentChange,
            label = { Text(stringResource(R.string.comment_label)) },
            isError = state.commentError != null,
            supportingText = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = state.commentError?.text(R.string.comment_error_required)
                            ?: stringResource(R.string.comment_supporting, NoteValidator.COMMENT_MIN, NoteValidator.COMMENT_MAX),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Text(
                        text = stringResource(
                            R.string.counter,
                            state.fields.comment.trim().length,
                            NoteValidator.COMMENT_MAX,
                        ),
                    )
                }
            },
            minLines = 4,
            maxLines = 8,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.FIELD_COMMENT)
                .onFocusChanged {
                    if (it.isFocused) commentHadFocus = true
                    else if (commentHadFocus) onFieldBlurred(NoteField.COMMENT)
                },
        )

        // ---- Tempo de preparo (opcional, numerico)
        var minutesHadFocus by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = state.fields.minutes,
            onValueChange = onMinutesChange,
            label = { Text(stringResource(R.string.minutes_label)) },
            isError = state.minutesError != null,
            supportingText = {
                Text(
                    text = state.minutesError?.text(R.string.minutes_error)
                        ?: stringResource(R.string.minutes_supporting, NoteValidator.MINUTES_MIN, NoteValidator.MINUTES_MAX),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.FIELD_MINUTES)
                .onFocusChanged {
                    if (it.isFocused) minutesHadFocus = true
                    else if (minutesHadFocus) onFieldBlurred(NoteField.MINUTES)
                },
        )

        // ---- Foto do prato (Photo Picker)
        PhotoSection(
            photoPath = state.fields.photoPath,
            isImporting = state.isImportingPhoto,
            onPick = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemove = onPhotoRemoved,
        )

        if (state.hasVisibleErrors) {
            Text(
                text = stringResource(R.string.fix_errors),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Button(
            onClick = onSave,
            enabled = !state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(TestTags.BUTTON_SAVE_NOTE),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.saving))
            } else {
                Text(stringResource(R.string.save_note))
            }
        }
    }
}

@Composable
private fun PhotoSection(
    photoPath: String?,
    isImporting: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.photo_title), style = MaterialTheme.typography.titleMedium)
        if (photoPath != null) {
            RecipeImage(
                model = File(photoPath),
                contentDescription = stringResource(R.string.photo_cd),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onPick, enabled = !isImporting, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(if (photoPath == null) R.string.photo_add else R.string.photo_change))
            }
            if (photoPath != null) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.photo_remove))
                }
            }
            if (isImporting) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

/** Converte um [FieldError] em texto. [requiredRes] varia por campo ("Escolha de 1 a 5 estrelas" etc.). */
@Composable
private fun FieldError.text(requiredRes: Int): String = when (this) {
    FieldError.Required -> stringResource(requiredRes)
    is FieldError.TooShort -> stringResource(R.string.comment_error_short, min, missing)
    is FieldError.TooLong -> stringResource(R.string.comment_error_long, max)
    is FieldError.OutOfRange -> stringResource(R.string.minutes_error, min, max)
    FieldError.NotANumber -> stringResource(R.string.minutes_error_number)
}
