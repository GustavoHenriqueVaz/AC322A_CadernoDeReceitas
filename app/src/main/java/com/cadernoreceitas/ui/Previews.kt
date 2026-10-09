package com.cadernoreceitas.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cadernoreceitas.domain.model.RecipeSummary
import com.cadernoreceitas.ui.components.EmptyState
import com.cadernoreceitas.ui.components.ErrorState
import com.cadernoreceitas.ui.components.LoadingState
import com.cadernoreceitas.ui.components.RatingDisplay
import com.cadernoreceitas.ui.components.RecipeCard
import com.cadernoreceitas.ui.explore.DailyState
import com.cadernoreceitas.ui.explore.ExploreContent
import com.cadernoreceitas.ui.explore.ExploreScreen
import com.cadernoreceitas.ui.explore.ExploreUiState
import com.cadernoreceitas.ui.theme.CadernoTheme

/*
 * Previews do Android Studio (painel "Design"): permitem ver os componentes e estados
 * sem rodar o app. Usam apenas dados de exemplo.
 */

private val sampleRecipes = listOf(
    RecipeSummary("52772", "Teriyaki Chicken Casserole", null, "Chicken", "Japanese"),
    RecipeSummary("52795", "Chicken Handi", null, "Chicken", "India"),
    RecipeSummary("53147", "Arroz con gambas y calamar", null, null, "Spanish"),
)

@Preview(name = "Card de receita", showBackground = true)
@Composable
private fun RecipeCardPreview() {
    CadernoTheme { RecipeCard(recipe = sampleRecipes[0], isSaved = true, onClick = {}) }
}

@Preview(name = "Estrelas", showBackground = true)
@Composable
private fun RatingPreview() {
    CadernoTheme { RatingDisplay(rating = 4) }
}

@Preview(name = "Explorar - conteudo", showBackground = true, heightDp = 700)
@Composable
private fun ExploreSuccessPreview() {
    CadernoTheme {
        ExploreScreen(
            state = ExploreUiState(
                content = ExploreContent.Success(sampleRecipes, isOffline = true),
                daily = DailyState.Ready(sampleRecipes[1]),
                savedIds = setOf("52772"),
            ),
            onQueryChange = {},
            onClearSearch = {},
            onCategorySelected = {},
            onRetry = {},
            onRecipeClick = {},
            onMessageShown = {},
            onReminderToggle = {},
        )
    }
}

@Preview(name = "Estado: carregando", showBackground = true, heightDp = 300)
@Composable
private fun LoadingPreview() {
    CadernoTheme { LoadingState() }
}

@Preview(name = "Estado: erro", showBackground = true, heightDp = 400)
@Composable
private fun ErrorPreview() {
    CadernoTheme { ErrorState(message = "Não foi possível conectar à internet.", onRetry = {}) }
}

@Preview(name = "Estado: vazio", showBackground = true, heightDp = 400)
@Composable
private fun EmptyPreview() {
    CadernoTheme {
        EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = "Nenhuma receita encontrada",
            message = "Tente outro nome.",
        )
    }
}
