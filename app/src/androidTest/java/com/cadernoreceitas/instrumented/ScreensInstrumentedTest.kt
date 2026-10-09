package com.cadernoreceitas.instrumented

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadernoreceitas.domain.model.RecipeSummary
import com.cadernoreceitas.ui.components.RatingInput
import com.cadernoreceitas.ui.components.TestTags
import com.cadernoreceitas.ui.components.UiError
import com.cadernoreceitas.ui.editnote.EditNoteScreen
import com.cadernoreceitas.ui.editnote.EditNoteUiState
import com.cadernoreceitas.ui.editnote.FieldError
import com.cadernoreceitas.ui.editnote.NoteFields
import com.cadernoreceitas.ui.explore.ExploreContent
import com.cadernoreceitas.ui.explore.ExploreScreen
import com.cadernoreceitas.ui.explore.ExploreUiState
import com.cadernoreceitas.ui.explore.SearchInput
import com.cadernoreceitas.ui.theme.CadernoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Telas isoladas (sem ViewModel) em um Android de verdade: os 4 estados visuais, validacao do
 * formulario, semantica das estrelas e "texto grande" (fonte em 200%).
 * Equivalente instrumentado de `ExploreScreenTest`/`EditNoteScreenTest` (src/test, Robolectric).
 */
@RunWith(AndroidJUnit4::class)
class ScreensInstrumentedTest {

    @get:Rule
    val rule = createComposeRule()

    private val summaries = listOf(Sample.teriyaki.toSummary(), Sample.crumble.toSummary())

    private fun show(fontScale: Float = 1f, content: @Composable () -> Unit) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                CadernoTheme(darkTheme = false, content = content)
            }
        }
    }

    private fun explore(state: ExploreUiState, onRetry: () -> Unit = {}, onClear: () -> Unit = {}, onClick: (String) -> Unit = {}) {
        show {
            ExploreScreen(
                state = state,
                onQueryChange = {},
                onClearSearch = onClear,
                onCategorySelected = {},
                onRetry = onRetry,
                onRecipeClick = onClick,
                onMessageShown = {},
                onReminderToggle = {},
            )
        }
    }

    private fun success(list: List<RecipeSummary> = summaries) =
        ExploreUiState(content = ExploreContent.Success(list, isOffline = false))

    @Test
    fun exploreLoadingState() {
        explore(ExploreUiState(content = ExploreContent.Loading))

        rule.onNodeWithTag(TestTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun exploreSuccessStateAndClick() {
        var clicked: String? = null
        explore(success(), onClick = { clicked = it })

        rule.onNodeWithTag(TestTags.RECIPE_LIST).assertIsDisplayed()
        rule.onNodeWithText(Sample.crumble.name).performClick()

        assertEquals(Sample.CRUMBLE_ID, clicked)
    }

    @Test
    fun exploreEmptyStateOffersFeatured() {
        var cleared = 0
        explore(ExploreUiState(content = ExploreContent.Empty(SearchInput("zzz"))), onClear = { cleared++ })

        rule.onNodeWithTag(TestTags.EMPTY).assertIsDisplayed()
        rule.onNodeWithText("Ver receitas em destaque").performClick()

        assertEquals(1, cleared)
    }

    @Test
    fun exploreErrorStateOffersRetry() {
        var retries = 0
        explore(ExploreUiState(content = ExploreContent.Error(UiError.NETWORK)), onRetry = { retries++ })

        rule.onNodeWithTag(TestTags.ERROR).assertIsDisplayed()
        rule.onNodeWithText("Tentar novamente").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun exploreWithLargeTextKeepsMainControlsVisible() {
        show(fontScale = 2f) {
            ExploreScreen(
                state = success().copy(input = SearchInput(query = "pasta")),
                onQueryChange = {},
                onClearSearch = {},
                onCategorySelected = {},
                onRetry = {},
                onRecipeClick = {},
                onMessageShown = {},
                onReminderToggle = {},
            )
        }

        rule.onNodeWithTag(TestTags.SEARCH_FIELD).assertIsDisplayed()
        rule.onNodeWithTag(TestTags.RECIPE_LIST).assertIsDisplayed()
        val clear = rule.onNodeWithContentDescription("Limpar busca").assertIsDisplayed().getBoundsInRoot()
        assertTrue("alvo de toque < 48dp: ${clear.width} x ${clear.height}", clear.width.value >= 48f && clear.height.value >= 48f)
    }

    @Test
    fun ratingInputExposesSelectionAndHasBigTargets() {
        show { RatingInput(rating = 3, onRatingChange = {}) }

        rule.onNodeWithContentDescription("3 estrelas").assertIsSelected()
        listOf("1 estrela", "2 estrelas", "3 estrelas", "4 estrelas", "5 estrelas").forEach {
            val bounds = rule.onNodeWithContentDescription(it).getBoundsInRoot()
            assertTrue("$it menor que 48dp", bounds.width.value >= 48f && bounds.height.value >= 48f)
        }
    }

    @Test
    fun noteFormShowsValidationMessages() {
        show {
            EditNoteScreen(
                state = EditNoteUiState(
                    isLoading = false,
                    recipeName = Sample.teriyaki.name,
                    fields = NoteFields(comment = "abc"),
                    ratingError = FieldError.Required,
                    commentError = FieldError.TooShort(10, 7),
                    minutesError = FieldError.OutOfRange(1, 600),
                ),
                onBack = {},
                onRatingChange = {},
                onCommentChange = {},
                onMinutesChange = {},
                onFieldBlurred = {},
                onPhotoPicked = {},
                onPhotoRemoved = {},
                onPhotoErrorShown = {},
                onSaveFailedShown = {},
                onSave = {},
                onRetry = {},
            )
        }

        rule.onNodeWithText("Escolha de 1 a 5 estrelas.").assertIsDisplayed()
        rule.onNodeWithText("Escreva pelo menos 10 caracteres (faltam 7).").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Informe um valor entre 1 e 600 minutos.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Corrija os campos destacados.").performScrollTo().assertIsDisplayed()
    }
}
