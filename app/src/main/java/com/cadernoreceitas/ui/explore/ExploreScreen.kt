package com.cadernoreceitas.ui.explore

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadernoreceitas.R
import com.cadernoreceitas.domain.model.Category
import com.cadernoreceitas.domain.model.RecipeSummary
import com.cadernoreceitas.ui.components.EmptyState
import com.cadernoreceitas.ui.components.ErrorState
import com.cadernoreceitas.ui.components.LoadingState
import com.cadernoreceitas.ui.components.OfflineBanner
import com.cadernoreceitas.ui.components.RecipeCard
import com.cadernoreceitas.ui.components.TestTags
import com.cadernoreceitas.ui.components.message

/** Ponto de ligacao com o ViewModel (a tela em si, [ExploreScreen], nao conhece ViewModel). */
@Composable
fun ExploreRoute(
    viewModel: ExploreViewModel,
    onRecipeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onReminderToggled(enabled = true, permissionGranted = granted) }

    ExploreScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onClearSearch = viewModel::onClearSearch,
        onCategorySelected = viewModel::onCategorySelected,
        onRetry = viewModel::retry,
        onRecipeClick = onRecipeClick,
        onMessageShown = viewModel::onMessageShown,
        onReminderToggle = { enabled ->
            val needsPermission = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onReminderToggled(enabled)
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    state: ExploreUiState,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onCategorySelected: (String?) -> Unit,
    onRetry: () -> Unit,
    onRecipeClick: (String) -> Unit,
    onMessageShown: () -> Unit,
    onReminderToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { stringResource(it.stringRes()) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.explore_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            // Em telas largas (tablet/paisagem) o conteudo nao se estica ate as bordas.
            Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                val focusManager = LocalFocusManager.current
                SearchField(
                    query = state.input.query,
                    onQueryChange = onQueryChange,
                    onClear = onClearSearch,
                    focusManager = focusManager,
                )
                CategoryChips(
                    categories = state.categories,
                    selected = state.input.category,
                    onSelected = onCategorySelected,
                )
                val offline = (state.content as? ExploreContent.Success)?.isOffline == true
                AnimatedVisibility(
                    visible = offline,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) { OfflineBanner() }

                // Transicao suave entre carregando / conteudo / vazio / erro.
                // contentKey: so anima quando muda o TIPO de estado; trocar a lista dentro de
                // Success apenas recompoe (sem piscar).
                AnimatedContent(
                    targetState = state.content,
                    contentKey = { it.kind() },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "exploreContent",
                    modifier = Modifier.weight(1f),
                ) { content ->
                    when (content) {
                        ExploreContent.Loading -> LoadingState()
                        is ExploreContent.Error -> ErrorState(
                            message = content.error.message(),
                            onRetry = onRetry,
                        )
                        is ExploreContent.Empty -> EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = stringResource(R.string.explore_empty_title),
                            message = stringResource(R.string.explore_empty_message),
                            actionLabel = stringResource(R.string.explore_empty_action),
                            onAction = onClearSearch,
                        )
                        is ExploreContent.Success -> RecipeList(
                            state = state,
                            recipes = content.recipes,
                            onRecipeClick = onRecipeClick,
                            onReminderToggle = onReminderToggle,
                        )
                    }
                }
            }
        }
    }
}

private fun ExploreContent.kind(): Int = when (this) {
    ExploreContent.Loading -> 0
    is ExploreContent.Success -> 1
    is ExploreContent.Empty -> 2
    is ExploreContent.Error -> 3
}

private fun ExploreMessage.stringRes() = when (this) {
    ExploreMessage.REMINDER_ON -> R.string.daily_reminder_on
    ExploreMessage.REMINDER_OFF -> R.string.daily_reminder_off
    ExploreMessage.PERMISSION_DENIED -> R.string.daily_permission_denied
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    focusManager: FocusManager,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(stringResource(R.string.search_label)) },
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                // IconButton ja garante a area de toque minima de 48 dp.
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .testTag(TestTags.SEARCH_FIELD),
    )
}

@Composable
private fun CategoryChips(
    categories: List<Category>,
    selected: String?,
    onSelected: (String?) -> Unit,
) {
    if (categories.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it.name }) { category ->
            FilterChip(
                selected = category.name == selected,
                onClick = { onSelected(category.name) },
                label = { Text(category.name) },
            )
        }
    }
}

@Composable
private fun RecipeList(
    state: ExploreUiState,
    recipes: List<RecipeSummary>,
    onRecipeClick: (String) -> Unit,
    onReminderToggle: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val showDaily = state.input.query.isEmpty() && state.input.category == null
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(TestTags.RECIPE_LIST),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showDaily) {
            item(key = "daily") {
                DailyCard(
                    daily = state.daily,
                    reminderEnabled = state.reminderEnabled,
                    onRecipeClick = onRecipeClick,
                    onReminderToggle = onReminderToggle,
                )
            }
        }
        item(key = "header") {
            Text(
                text = listTitle(state.input),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = pluralStringResource(R.plurals.recipe_count, recipes.size, recipes.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(recipes, key = { it.id }) { recipe ->
            RecipeCard(
                recipe = recipe,
                isSaved = recipe.id in state.savedIds,
                onClick = { onRecipeClick(recipe.id) },
            )
        }
    }
}

@Composable
private fun listTitle(input: SearchInput): String = when {
    input.query.isNotEmpty() -> stringResource(R.string.results_for, input.query)
    input.category != null -> stringResource(R.string.category_results_title, input.category)
    else -> stringResource(R.string.featured_title)
}

/** Cartao "Receita do dia" + chave do lembrete diario (WorkManager + notificacao). */
@Composable
private fun DailyCard(
    daily: DailyState,
    reminderEnabled: Boolean,
    onRecipeClick: (String) -> Unit,
    onReminderToggle: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.daily_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.size(8.dp))
            when (daily) {
                DailyState.Loading -> Text(
                    text = stringResource(R.string.loading),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                DailyState.Unavailable -> Text(
                    text = stringResource(R.string.daily_unavailable),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                is DailyState.Ready -> RecipeCard(
                    recipe = daily.recipe,
                    isSaved = false,
                    onClick = { onRecipeClick(daily.recipe.id) },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    // A linha inteira e o alvo de toque (nao so o Switch) e fala "ligado/desligado".
                    .toggleable(
                        value = reminderEnabled,
                        role = Role.Switch,
                        onValueChange = onReminderToggle,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.daily_reminder_label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.daily_reminder_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Switch(checked = reminderEnabled, onCheckedChange = null)
            }
        }
    }
}
