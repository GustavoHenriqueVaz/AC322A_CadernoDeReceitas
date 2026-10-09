package com.cadernoreceitas.parcial.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.CheckBox
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadernoreceitas.parcial.R
import com.cadernoreceitas.parcial.data.MockRecipes
import com.cadernoreceitas.parcial.databinding.FragmentIngredientsBinding
import com.cadernoreceitas.parcial.model.Recipe
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RecipeDetailActivityTest {

    private fun detailIntent(recipe: Recipe?): Intent {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Intent(context, RecipeDetailActivity::class.java).apply {
            if (recipe != null) putExtra(RecipeDetailActivity.EXTRA_RECIPE, recipe)
        }
    }

    private fun launch(recipe: Recipe) = ActivityScenario.launch<RecipeDetailActivity>(detailIntent(recipe))

    /** Binding do Fragment de ingredientes (usa a View raiz do proprio Fragment, sem busca manual por id). */
    private fun ingredientsBinding(activity: RecipeDetailActivity): FragmentIngredientsBinding {
        // a transacao do Fragment e assincrona: garante que ela ja foi executada
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.ingredientsContainer)
        checkNotNull(fragment) { "IngredientsFragment nao foi adicionado" }
        return FragmentIngredientsBinding.bind(fragment.requireView())
    }

    private fun checkbox(binding: FragmentIngredientsBinding, index: Int): CheckBox =
        binding.ingredientsContainer.getChildAt(index) as CheckBox

    @Test
    fun `receita completa preenche titulo chips dica e botao de video`() {
        val recipe = MockRecipes.byId(1)!! // tem dica e video
        launch(recipe).use { scenario ->
            scenario.onActivity { activity ->
                val binding = activity.binding

                assertThat(binding.textTitle.text.toString()).isEqualTo(recipe.name)
                assertThat(binding.chipTime.chipLabel.text.toString())
                    .isEqualTo(activity.getString(R.string.chip_time, recipe.prepMinutes))
                assertThat(binding.chipServings.chipLabel.text.toString())
                    .isEqualTo(activity.resources.getQuantityString(R.plurals.chip_servings_count, recipe.servings, recipe.servings))
                assertThat(binding.chipDifficulty.chipLabel.text.toString())
                    .isEqualTo(activity.getString(recipe.difficulty.label))

                assertThat(binding.tipCard.visibility).isEqualTo(View.VISIBLE)
                assertThat(binding.textTip.text.toString()).isEqualTo(recipe.tip)
                assertThat(binding.buttonVideo.visibility).isEqualTo(View.VISIBLE)

                // um item inflado para cada passo
                assertThat(binding.stepsContainer.childCount).isEqualTo(recipe.steps.size)
            }
        }
    }

    @Test
    fun `chip de porcoes usa o singular quando serve uma pessoa`() {
        val recipe = MockRecipes.all.first { it.servings == 1 }
        launch(recipe).use { scenario ->
            scenario.onActivity { activity ->
                // "1 porcao" (com cedilha e til, escrito em unicode para nao depender da codificacao)
                assertThat(activity.binding.chipServings.chipLabel.text.toString())
                    .isEqualTo("1 porção")
            }
        }
    }

    @Test
    fun `receita sem dica e sem video esconde o card e o botao`() {
        val recipe = MockRecipes.byId(3)!! // salada caprese: tip e videoUrl nulos
        assertThat(recipe.tip).isNull()
        assertThat(recipe.videoUrl).isNull()

        launch(recipe).use { scenario ->
            scenario.onActivity { activity ->
                assertThat(activity.binding.tipCard.visibility).isEqualTo(View.GONE)
                assertThat(activity.binding.buttonVideo.visibility).isEqualTo(View.GONE)
            }
        }
    }

    @Test
    fun `receita com dica mas sem video mostra so o card`() {
        val recipe = MockRecipes.byId(2)!! // macarrao: tem dica, nao tem video
        launch(recipe).use { scenario ->
            scenario.onActivity { activity ->
                assertThat(activity.binding.tipCard.visibility).isEqualTo(View.VISIBLE)
                assertThat(activity.binding.buttonVideo.visibility).isEqualTo(View.GONE)
            }
        }
    }

    @Test
    fun `favoritar muda o texto do botao e produz resultado`() {
        val recipe = MockRecipes.byId(4)!!
        launch(recipe).use { scenario ->
            scenario.onActivity { activity ->
                val button = activity.binding.buttonFavorite
                assertThat(button.text.toString()).isEqualTo(activity.getString(R.string.favorite_button_add))
                assertThat(shadowOf(activity).resultCode).isEqualTo(Activity.RESULT_CANCELED)

                button.performClick()

                assertThat(button.text.toString()).isEqualTo(activity.getString(R.string.favorite_button_remove))
                assertThat(shadowOf(activity).resultCode).isEqualTo(Activity.RESULT_OK)
                val result = shadowOf(activity).resultIntent
                assertThat(result.getIntExtra(RecipeDetailActivity.EXTRA_RESULT_ID, -1)).isEqualTo(recipe.id)
                assertThat(result.getBooleanExtra(RecipeDetailActivity.EXTRA_RESULT_FAVORITE, false)).isTrue()

                // tocar de novo desfavorita
                button.performClick()

                assertThat(button.text.toString()).isEqualTo(activity.getString(R.string.favorite_button_add))
                assertThat(
                    shadowOf(activity).resultIntent
                        .getBooleanExtra(RecipeDetailActivity.EXTRA_RESULT_FAVORITE, true),
                ).isFalse()
            }
        }
    }

    @Test
    fun `receita que ja chega favorita mostra o botao como favorita`() {
        val recipe = MockRecipes.byId(4)!!.copy(isFavorite = true)
        launch(recipe).use { scenario ->
            scenario.onActivity { activity ->
                assertThat(activity.binding.buttonFavorite.text.toString())
                    .isEqualTo(activity.getString(R.string.favorite_button_remove))
            }
        }
    }

    @Test
    fun `fragment lista os ingredientes e conta os separados`() {
        val recipe = MockRecipes.byId(1)!!
        launch(recipe).use { scenario ->
            scenario.onActivity { activity ->
                val fragmentBinding = ingredientsBinding(activity)
                val total = recipe.ingredients.size

                assertThat(fragmentBinding.ingredientsContainer.childCount).isEqualTo(total)

                // com quantidade: "nome — quantidade"; sem quantidade: so o nome
                val withQuantity = recipe.ingredients.first { it.quantity != null }
                val withoutQuantity = recipe.ingredients.first { it.quantity == null }
                assertThat(checkbox(fragmentBinding, recipe.ingredients.indexOf(withQuantity)).text.toString())
                    .isEqualTo(
                        activity.getString(
                            R.string.ingredient_with_quantity,
                            withQuantity.name,
                            withQuantity.quantity,
                        ),
                    )
                assertThat(checkbox(fragmentBinding, recipe.ingredients.indexOf(withoutQuantity)).text.toString())
                    .isEqualTo(withoutQuantity.name)

                // marcar/desmarcar atualiza o progresso
                assertThat(fragmentBinding.ingredientsProgress.text.toString())
                    .isEqualTo(activity.getString(R.string.ingredients_progress, 0, total))
                checkbox(fragmentBinding, 0).performClick()
                checkbox(fragmentBinding, 1).performClick()
                assertThat(fragmentBinding.ingredientsProgress.text.toString())
                    .isEqualTo(activity.getString(R.string.ingredients_progress, 2, total))
                checkbox(fragmentBinding, 0).performClick()
                assertThat(fragmentBinding.ingredientsProgress.text.toString())
                    .isEqualTo(activity.getString(R.string.ingredients_progress, 1, total))
            }
        }
    }

    @Test
    fun `favorito e ingredientes marcados sobrevivem a recriacao da Activity`() {
        val recipe = MockRecipes.byId(1)!!
        launch(recipe).use { scenario ->
            scenario.onActivity { activity ->
                activity.binding.buttonFavorite.performClick()
                val fragmentBinding = ingredientsBinding(activity)
                checkbox(fragmentBinding, 0).performClick()
                checkbox(fragmentBinding, 2).performClick()
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                // favorito mantido e resultado reenviado para a tela 1
                assertThat(activity.binding.buttonFavorite.text.toString())
                    .isEqualTo(activity.getString(R.string.favorite_button_remove))
                assertThat(shadowOf(activity).resultCode).isEqualTo(Activity.RESULT_OK)

                // so os dois marcados continuam marcados (cada CheckBox guarda seu proprio estado)
                val fragmentBinding = ingredientsBinding(activity)
                val checked = recipe.ingredients.indices.map { checkbox(fragmentBinding, it).isChecked }
                assertThat(checked.withIndex().filter { it.value }.map { it.index }).containsExactly(0, 2)
                assertThat(fragmentBinding.ingredientsProgress.text.toString())
                    .isEqualTo(activity.getString(R.string.ingredients_progress, 2, recipe.ingredients.size))
                // os passos nao duplicam apos recriar
                assertThat(activity.binding.stepsContainer.childCount).isEqualTo(recipe.steps.size)
            }
        }
    }

    @Test
    fun `sem receita no Intent mostra aviso e finaliza a tela`() {
        val controller = Robolectric.buildActivity(RecipeDetailActivity::class.java, detailIntent(null))
        val activity = controller.setup().get()

        assertThat(activity.isFinishing).isTrue()
        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo(activity.getString(R.string.recipe_not_found))
    }

    @Test
    fun `botao voltar finaliza a tela`() {
        launch(MockRecipes.all.first()).use { scenario ->
            scenario.onActivity { activity ->
                activity.binding.buttonBack.performClick()

                assertThat(activity.isFinishing).isTrue()
            }
        }
    }
}
