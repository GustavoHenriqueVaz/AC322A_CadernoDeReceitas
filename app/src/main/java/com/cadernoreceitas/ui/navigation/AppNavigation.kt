package com.cadernoreceitas.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.cadernoreceitas.R
import com.cadernoreceitas.di.AppContainer
import com.cadernoreceitas.ui.detail.DetailRoute
import com.cadernoreceitas.ui.detail.DetailViewModel
import com.cadernoreceitas.ui.editnote.EditNoteRoute
import com.cadernoreceitas.ui.editnote.EditNoteViewModel
import com.cadernoreceitas.ui.explore.ExploreRoute
import com.cadernoreceitas.ui.explore.ExploreViewModel
import com.cadernoreceitas.ui.notebook.NotebookRoute
import com.cadernoreceitas.ui.notebook.NotebookViewModel
import kotlinx.serialization.Serializable

/*
 * Navigation 3: o back stack e uma lista que NOS controlamos (add / removeLast).
 * Cada rota e um NavKey serializavel; argumentos (ex.: recipeId) fazem parte da propria chave.
 * Por serem @Serializable, o back stack sobrevive a rotacao e a morte do processo.
 */

@Serializable
data object ExploreKey : NavKey

@Serializable
data object NotebookKey : NavKey

@Serializable
data class RecipeDetailKey(val recipeId: String) : NavKey

@Serializable
data class EditNoteKey(val recipeId: String) : NavKey

/**
 * Raiz da navegacao.
 * @param initialRecipeId receita a abrir direto (deep link na abertura a frio), ou `null`.
 * @param pendingDeepLink deep link recebido com o app ja aberto (`onNewIntent`).
 */
@Composable
fun CadernoNavigation(
    container: AppContainer,
    initialRecipeId: String?,
    pendingDeepLink: String?,
    onDeepLinkConsumed: () -> Unit,
) {
    val backStack = rememberNavBackStack(
        *(if (initialRecipeId != null) arrayOf(ExploreKey, RecipeDetailKey(initialRecipeId)) else arrayOf<NavKey>(ExploreKey)),
    )

    // Deep link com o app aberto: empilha a tela de detalhes sobre o Explorar.
    LaunchedEffect(pendingDeepLink) {
        if (pendingDeepLink != null) {
            backStack.clear()
            backStack.add(ExploreKey)
            backStack.add(RecipeDetailKey(pendingDeepLink))
            onDeepLinkConsumed()
        }
    }

    val current = backStack.lastOrNull()
    val isTopLevel = current is ExploreKey || current is NotebookKey

    fun switchTab(key: NavKey) {
        if (backStack.lastOrNull() == key) return
        backStack.clear()
        backStack.add(key)
    }

    // "Voltar" na aba Caderno leva ao Explorar (inicio) em vez de fechar o app.
    BackHandler(enabled = backStack.size == 1 && current is NotebookKey) { switchTab(ExploreKey) }

    // Barra inferior no celular, trilho lateral em telas largas; escondida nas telas internas.
    val suiteType = if (isTopLevel) {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
    } else {
        NavigationSuiteType.None
    }

    NavigationSuiteScaffold(
        layoutType = suiteType,
        navigationSuiteItems = {
            item(
                selected = current is ExploreKey,
                onClick = { switchTab(ExploreKey) },
                icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_explore)) },
            )
            item(
                selected = current is NotebookKey,
                onClick = { switchTab(NotebookKey) },
                icon = { Icon(Icons.Outlined.AutoStories, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_notebook)) },
            )
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            // Estado salvo (scroll etc.) e ViewModels ficam amarrados a CADA entrada do back stack:
            // o ViewModel vive enquanto a tela esta na pilha e morre quando ela e removida.
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<ExploreKey> {
                    val factory = remember {
                        viewModelFactory {
                            initializer {
                                ExploreViewModel(
                                    repository = container.recipeRepository,
                                    preferences = container.userPreferences,
                                    reminderScheduler = container.reminderScheduler,
                                    savedStateHandle = createSavedStateHandle(),
                                )
                            }
                        }
                    }
                    ExploreRoute(
                        viewModel = viewModel(factory = factory),
                        onRecipeClick = { backStack.add(RecipeDetailKey(it)) },
                    )
                }
                entry<NotebookKey> {
                    val factory = remember {
                        viewModelFactory {
                            initializer {
                                NotebookViewModel(container.recipeRepository, createSavedStateHandle())
                            }
                        }
                    }
                    NotebookRoute(
                        viewModel = viewModel(factory = factory),
                        onRecipeClick = { backStack.add(RecipeDetailKey(it)) },
                        onExplore = { switchTab(ExploreKey) },
                    )
                }
                entry<RecipeDetailKey> { key ->
                    val factory = remember(key.recipeId) {
                        viewModelFactory {
                            initializer { DetailViewModel(key.recipeId, container.recipeRepository) }
                        }
                    }
                    DetailRoute(
                        viewModel = viewModel(factory = factory),
                        onBack = { backStack.removeLastOrNull() },
                        onEditNote = { backStack.add(EditNoteKey(it)) },
                    )
                }
                entry<EditNoteKey> { key ->
                    val factory = remember(key.recipeId) {
                        viewModelFactory {
                            initializer {
                                EditNoteViewModel(
                                    recipeId = key.recipeId,
                                    repository = container.recipeRepository,
                                    photoStorage = container.photoStorage,
                                    savedStateHandle = createSavedStateHandle(),
                                )
                            }
                        }
                    }
                    EditNoteRoute(
                        viewModel = viewModel(factory = factory),
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            },
        )
    }
}
