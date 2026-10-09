package com.cadernoreceitas.ui.notebook

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FilterListOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadernoreceitas.R
import com.cadernoreceitas.domain.model.SavedRecipe
import com.cadernoreceitas.ui.components.EmptyState
import com.cadernoreceitas.ui.components.ErrorState
import com.cadernoreceitas.ui.components.LoadingState
import com.cadernoreceitas.ui.components.RatingDisplay
import com.cadernoreceitas.ui.components.RecipeImage
import com.cadernoreceitas.ui.components.TestTags
import com.cadernoreceitas.ui.components.message
import com.cadernoreceitas.ui.components.metaLine
import java.io.File

@Composable
fun NotebookRoute(
    viewModel: NotebookViewModel,
    onRecipeClick: (String) -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NotebookScreen(
        state = state,
        onFilterSelected = viewModel::onFilterSelected,
        onRecipeClick = onRecipeClick,
        onRemove = viewModel::remove,
        onUndo = viewModel::undoRemove,
        onRemovalShown = viewModel::onRemovalMessageShown,
        onRetry = viewModel::retry,
        onExplore = onExplore,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(
    state: NotebookUiState,
    onFilterSelected: (NotebookFilter) -> Unit,
    onRecipeClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onUndo: () -> Unit,
    onRemovalShown: () -> Unit,
    onRetry: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val removedText = stringResource(R.string.recipe_removed)
    val undoText = stringResource(R.string.undo)
    LaunchedEffect(state.lastRemoved) {
        if (state.lastRemoved != null) {
            val result = snackbarHostState.showSnackbar(
                message = removedText,
                actionLabel = undoText,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) onUndo() else onRemovalShown()
        }
    }

    // Confirmacao antes de apagar (guarda so o id para sobreviver a rotacao).
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDelete = (state.content as? NotebookContent.Success)?.items?.firstOrNull { it.recipe.id == pendingDeleteId }
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message, pendingDelete.recipe.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(pendingDelete.recipe.id)
                    pendingDeleteId = null
                }) { Text(stringResource(R.string.confirm_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.notebook_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                if (state.totalSaved > 0) {
                    FilterRow(selected = state.filter, onSelected = onFilterSelected)
                }
                AnimatedContent(
                    targetState = state.content,
                    contentKey = { it.kind() },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "notebookContent",
                    modifier = Modifier.weight(1f),
                ) { content ->
                    when (content) {
                        NotebookContent.Loading -> LoadingState()
                        is NotebookContent.Error -> ErrorState(
                            message = content.error.message(),
                            onRetry = onRetry,
                        )
                        is NotebookContent.Empty -> {
                            if (content.filtered) {
                                EmptyState(
                                    icon = Icons.Outlined.FilterListOff,
                                    title = stringResource(R.string.notebook_filtered_empty_title),
                                    message = stringResource(R.string.notebook_filtered_empty_message),
                                    actionLabel = stringResource(R.string.filter_all),
                                    onAction = { onFilterSelected(NotebookFilter.ALL) },
                                )
                            } else {
                                EmptyState(
                                    icon = Icons.Outlined.AutoStories,
                                    title = stringResource(R.string.notebook_empty_title),
                                    message = stringResource(R.string.notebook_empty_message),
                                    actionLabel = stringResource(R.string.notebook_empty_action),
                                    onAction = onExplore,
                                )
                            }
                        }
                        is NotebookContent.Success -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().testTag(TestTags.NOTEBOOK_LIST),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(content.items, key = { it.recipe.id }) { saved ->
                                    SavedRecipeCard(
                                        saved = saved,
                                        onClick = { onRecipeClick(saved.recipe.id) },
                                        onDelete = { pendingDeleteId = saved.recipe.id },
                                        // Itens animam ao entrar/sair/reordenar.
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun NotebookContent.kind(): Int = when (this) {
    NotebookContent.Loading -> 0
    is NotebookContent.Success -> 1
    // Vazio "de verdade" e vazio por filtro tem telas diferentes: chaves distintas.
    is NotebookContent.Empty -> if (filtered) 2 else 3
    is NotebookContent.Error -> 4
}

@Composable
private fun FilterRow(selected: NotebookFilter, onSelected: (NotebookFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(NotebookFilter.entries, key = { it.name }) { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelected(filter) },
                label = {
                    Text(
                        stringResource(
                            when (filter) {
                                NotebookFilter.ALL -> R.string.filter_all
                                NotebookFilter.WITH_NOTE -> R.string.filter_noted
                                NotebookFilter.TOP_RATED -> R.string.filter_top
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun SavedRecipeCard(
    saved: SavedRecipe,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recipe = saved.recipe
    ElevatedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 88.dp).padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RecipeImage(
                // A foto do prato do usuario tem prioridade sobre a miniatura da API.
                model = saved.photoPath?.let { File(it) } ?: recipe.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                metaLine(recipe.category, recipe.area)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.size(4.dp))
                RatingDisplay(rating = saved.rating)
                saved.prepMinutes?.let {
                    Text(
                        text = stringResource(R.string.prep_minutes_format, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val comment = saved.comment?.takeIf { it.isNotBlank() }
                if (comment != null) {
                    Text(
                        text = comment,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.no_note_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete_cd, recipe.name),
                )
            }
        }
    }
}
