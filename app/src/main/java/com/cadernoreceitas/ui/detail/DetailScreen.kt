package com.cadernoreceitas.ui.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadernoreceitas.R
import com.cadernoreceitas.domain.model.Recipe
import com.cadernoreceitas.domain.model.SavedRecipe
import com.cadernoreceitas.ui.components.ErrorState
import com.cadernoreceitas.ui.components.LoadingState
import com.cadernoreceitas.ui.components.OfflineBanner
import com.cadernoreceitas.ui.components.RatingDisplay
import com.cadernoreceitas.ui.components.RecipeImage
import com.cadernoreceitas.ui.components.SectionTitle
import com.cadernoreceitas.ui.components.TestTags
import com.cadernoreceitas.ui.components.message
import com.cadernoreceitas.ui.components.metaLine
import com.cadernoreceitas.util.RecipeIds
import java.io.File

@Composable
fun DetailRoute(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onEditNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // LocalResources acompanha mudancas de configuracao (idioma/tema), ao contrario de context.getString.
    val resources = LocalResources.current
    val chooserTitle = stringResource(R.string.share_chooser)

    DetailScreen(
        state = state,
        onBack = onBack,
        onToggleSaved = viewModel::toggleSaved,
        onEditNote = onEditNote,
        onRetry = viewModel::retry,
        onUndo = viewModel::undoRemoval,
        onMessageShown = viewModel::onMessageShown,
        onShare = { recipe ->
            // Compartilhamento: Intent ACTION_SEND abre o seletor de apps do sistema.
            val link = shareUrl(recipe.id)
            val text = if (link != null) resources.getString(R.string.share_text, recipe.name, link) else recipe.name
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, recipe.name)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, chooserTitle))
        },
        onOpenVideo = { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            } catch (_: ActivityNotFoundException) {
                // Sem app/navegador para abrir o link: ignora em silencio.
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    state: DetailUiState,
    onBack: () -> Unit,
    onToggleSaved: () -> Unit,
    onEditNote: (String) -> Unit,
    onRetry: () -> Unit,
    onUndo: (SavedRecipe) -> Unit,
    onMessageShown: () -> Unit,
    onShare: (Recipe) -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.recipe_saved)
    val removedText = stringResource(R.string.recipe_removed)
    val failedText = stringResource(R.string.save_failed)
    val undoText = stringResource(R.string.undo)
    LaunchedEffect(state.message) {
        when (val message = state.message) {
            null -> Unit
            DetailMessage.Saved -> {
                snackbarHostState.showSnackbar(savedText)
                onMessageShown()
            }
            DetailMessage.Failed -> {
                snackbarHostState.showSnackbar(failedText)
                onMessageShown()
            }
            is DetailMessage.Removed -> {
                val result = snackbarHostState.showSnackbar(
                    message = removedText,
                    actionLabel = undoText,
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed) onUndo(message.item)
                onMessageShown()
            }
        }
    }

    val recipe = (state.content as? DetailContent.Success)?.recipe

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = recipe?.name ?: stringResource(R.string.detail_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (recipe != null) {
                        IconButton(onClick = { onShare(recipe) }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share))
                        }
                        SaveToggle(isSaved = state.isSaved, onClick = onToggleSaved)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (val content = state.content) {
                DetailContent.Loading -> LoadingState()
                is DetailContent.Error -> ErrorState(message = content.error.message(), onRetry = onRetry)
                is DetailContent.Success -> {
                    if (content.isOffline) OfflineBanner()
                    RecipeContent(
                        recipe = content.recipe,
                        saved = state.saved,
                        onEditNote = { onEditNote(content.recipe.id) },
                        onOpenVideo = onOpenVideo,
                    )
                }
            }
        }
    }
}

/** Botao de favoritar: o icone muda e a cor anima; o leitor de tela fala o estado atual. */
@Composable
private fun SaveToggle(isSaved: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(
        targetValue = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "saveTint",
    )
    val description = stringResource(if (isSaved) R.string.remove_from_notebook else R.string.save_to_notebook)
    val state = stringResource(if (isSaved) R.string.saved_state else R.string.not_saved_state)
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .testTag(TestTags.BUTTON_TOGGLE_SAVED)
            .semantics { stateDescription = state },
    ) {
        Icon(
            imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = description,
            tint = tint,
        )
    }
}

@Composable
private fun RecipeContent(
    recipe: Recipe,
    saved: SavedRecipe?,
    onEditNote: () -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    // Marcacao dos ingredientes ja separados (estado de tela, sobrevive a rotacao).
    val checked = rememberSaveable(
        recipe.id,
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf<Int>() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        if (wide) {
            // Tablet / paisagem: duas colunas independentes, cada uma com sua rolagem.
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Hero(recipe)
                    Ingredients(recipe, checked)
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Header(recipe)
                    MyNoteCard(saved, onEditNote)
                    Instructions(recipe)
                    VideoButton(recipe, onOpenVideo)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Hero(recipe)
                Header(recipe)
                MyNoteCard(saved, onEditNote)
                Ingredients(recipe, checked)
                Instructions(recipe)
                VideoButton(recipe, onOpenVideo)
            }
        }
    }
}

@Composable
private fun Hero(recipe: Recipe) {
    RecipeImage(
        model = recipe.thumbnailUrl,
        contentDescription = stringResource(R.string.cd_recipe_photo, recipe.name),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(16.dp)),
    )
}

@Composable
private fun Header(recipe: Recipe) {
    Text(
        text = recipe.name,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
    metaLine(recipe.category, recipe.area)?.let {
        Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (recipe.tags.isNotEmpty()) {
        Text(
            text = recipe.tags.joinToString("  ") { "#$it" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun MyNoteCard(saved: SavedRecipe?, onEditNote: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.my_note),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.semantics { heading() },
            )
            if (saved != null && saved.hasNote) {
                RatingDisplay(rating = saved.rating)
                saved.prepMinutes?.let {
                    Text(
                        text = stringResource(R.string.prep_minutes_format, it),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                saved.comment?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                saved.photoPath?.let { path ->
                    RecipeImage(
                        model = File(path),
                        contentDescription = stringResource(R.string.photo_cd),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .aspectRatio(16f / 10f)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.my_note_empty),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            val hasNote = saved?.hasNote == true
            OutlinedButton(onClick = onEditNote, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(if (hasNote) R.string.edit_note else R.string.add_note))
            }
        }
    }
}

@Composable
private fun Ingredients(recipe: Recipe, checked: androidx.compose.runtime.snapshots.SnapshotStateList<Int>) {
    SectionTitle(stringResource(R.string.ingredients_title))
    if (recipe.ingredients.isEmpty()) {
        Text(stringResource(R.string.no_ingredients), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    recipe.ingredients.forEachIndexed { index, ingredient ->
        val isChecked = index in checked
        val label = listOfNotNull(ingredient.measure, ingredient.name).joinToString(" ")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                // A linha toda e clicavel (area de toque grande) e age como um unico checkbox.
                .toggleable(
                    value = isChecked,
                    role = Role.Checkbox,
                    onValueChange = { if (it) checked.add(index) else checked.remove(index) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = isChecked, onCheckedChange = null)
            Spacer(Modifier.size(12.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Instructions(recipe: Recipe) {
    SectionTitle(stringResource(R.string.instructions_title))
    if (recipe.instructions.isBlank()) {
        Text(stringResource(R.string.no_instructions), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val isLong = recipe.instructions.length > COLLAPSED_CHARS
    var expanded by rememberSaveable(recipe.id) { mutableStateOf(false) }
    Text(
        text = recipe.instructions,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = if (isLong && !expanded) COLLAPSED_LINES else Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.animateContentSize(),
    )
    if (isLong) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(if (expanded) R.string.show_less else R.string.show_more))
        }
    }
}

@Composable
private fun VideoButton(recipe: Recipe, onOpenVideo: (String) -> Unit) {
    val url = recipe.youtubeUrl ?: return
    Button(onClick = { onOpenVideo(url) }, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.watch_video))
    }
}

private const val COLLAPSED_CHARS = 700
private const val COLLAPSED_LINES = 8

/** Garante que apenas ids validos montem a URL de compartilhamento. */
internal fun shareUrl(recipeId: String): String? =
    if (RecipeIds.isValid(recipeId)) "https://www.themealdb.com/meal/$recipeId" else null
