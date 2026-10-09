package com.cadernoreceitas.parcial.ui

import android.app.Activity
import android.content.Intent
import android.os.Looper
import android.view.View
import androidx.core.content.IntentCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadernoreceitas.parcial.R
import com.cadernoreceitas.parcial.data.MockRecipes
import com.cadernoreceitas.parcial.databinding.ItemRecipeBinding
import com.cadernoreceitas.parcial.model.Recipe
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RecipeListActivityTest {

    private fun adapterOf(activity: RecipeListActivity): RecipeAdapter =
        activity.binding.recyclerView.adapter as RecipeAdapter

    /**
     * O ListAdapter calcula as diferencas em outra thread e devolve o resultado pelo Looper
     * principal: espera (com limite) ate a condicao ficar verdadeira.
     */
    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
    }

    private fun awaitItemCount(activity: RecipeListActivity, expected: Int) {
        awaitUntil { adapterOf(activity).itemCount == expected }
    }

    /**
     * Em teste o RecyclerView nao e medido/desenhado sozinho. Em vez de forcar o layout, cria e
     * vincula (bind) um item do adapter, exatamente como o RecyclerView faria, e devolve o binding.
     */
    private fun boundItem(activity: RecipeListActivity, position: Int): ItemRecipeBinding {
        val list = activity.binding.recyclerView
        val adapter = adapterOf(activity)
        val holder = adapter.createViewHolder(list, adapter.getItemViewType(position))
        adapter.bindViewHolder(holder, position)
        return ItemRecipeBinding.bind(holder.itemView)
    }

    private fun resultFor(id: Int, favorite: Boolean) = Intent()
        .putExtra(RecipeDetailActivity.EXTRA_RESULT_ID, id)
        .putExtra(RecipeDetailActivity.EXTRA_RESULT_FAVORITE, favorite)

    @Test
    fun `lista mostra as 8 receitas e o contador`() {
        ActivityScenario.launch(RecipeListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertThat(adapterOf(activity).itemCount).isEqualTo(8)
                assertThat(activity.binding.textCount.text.toString()).isEqualTo("8 receitas")
                assertThat(activity.binding.recyclerView.visibility).isEqualTo(View.VISIBLE)
                assertThat(activity.binding.emptyState.visibility).isEqualTo(View.GONE)
            }
        }
    }

    @Test
    fun `item mostra nome categoria e estrela vazia por padrao`() {
        ActivityScenario.launch(RecipeListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val first = MockRecipes.all.first()
                val item = boundItem(activity, 0)

                assertThat(item.textName.text.toString()).isEqualTo(first.name)
                assertThat(item.textMeta.text.toString()).isEqualTo(
                    activity.getString(
                        R.string.recipe_meta,
                        activity.getString(first.category.label),
                        first.prepMinutes,
                    ),
                )
                assertThat(item.buttonFavorite.contentDescription.toString())
                    .isEqualTo(activity.getString(R.string.favorite_add_cd, first.name))
            }
        }
    }

    @Test
    fun `filtro Rapidas reduz a lista e Todas volta ao total`() {
        ActivityScenario.launch(RecipeListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.binding.buttonQuick.performClick()
                awaitItemCount(activity, 5)

                assertThat(adapterOf(activity).itemCount).isEqualTo(5)
                assertThat(adapterOf(activity).currentList.all { it.isQuick }).isTrue()
                assertThat(activity.binding.textCount.text.toString()).isEqualTo("5 receitas")

                activity.binding.buttonAll.performClick()
                awaitItemCount(activity, 8)

                assertThat(adapterOf(activity).itemCount).isEqualTo(8)
                assertThat(activity.binding.textCount.text.toString()).isEqualTo("8 receitas")
            }
        }
    }

    @Test
    fun `filtro Favoritas sem favoritas mostra estado vazio com a mensagem certa`() {
        ActivityScenario.launch(RecipeListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.binding.buttonFavorites.performClick()
                awaitItemCount(activity, 0)

                assertThat(activity.binding.textCount.text.toString()).isEqualTo("0 receitas")
                assertThat(activity.binding.emptyState.visibility).isEqualTo(View.VISIBLE)
                assertThat(activity.binding.recyclerView.visibility).isEqualTo(View.GONE)
                assertThat(activity.binding.emptyMessage.text.toString())
                    .isEqualTo(activity.getString(R.string.empty_favorites))

                // voltando para Todas o estado vazio some
                activity.binding.buttonAll.performClick()
                awaitItemCount(activity, 8)
                assertThat(activity.binding.emptyState.visibility).isEqualTo(View.GONE)
                assertThat(activity.binding.recyclerView.visibility).isEqualTo(View.VISIBLE)
            }
        }
    }

    @Test
    fun `tocar na estrela de um item favorita a receita e atualiza a interface`() {
        ActivityScenario.launch(RecipeListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val first = MockRecipes.all.first()
                boundItem(activity, 0).buttonFavorite.performClick()
                awaitUntil { adapterOf(activity).currentList.first().isFavorite }

                // a lista agora tem a receita como favorita e o item mostra a estrela cheia
                assertThat(adapterOf(activity).currentList.first().isFavorite).isTrue()
                assertThat(boundItem(activity, 0).buttonFavorite.contentDescription.toString())
                    .isEqualTo(activity.getString(R.string.favorite_remove_cd, first.name))

                // e a receita aparece no filtro Favoritas
                activity.binding.buttonFavorites.performClick()
                awaitItemCount(activity, 1)
                assertThat(activity.binding.textCount.text.toString()).isEqualTo("1 receita")
                assertThat(activity.binding.emptyState.visibility).isEqualTo(View.GONE)

                // desfavoritar dentro do filtro Favoritas esvazia a lista e mostra o estado vazio
                boundItem(activity, 0).buttonFavorite.performClick()
                awaitItemCount(activity, 0)
                assertThat(activity.binding.emptyState.visibility).isEqualTo(View.VISIBLE)
            }
        }
    }

    @Test
    fun `clicar em um item abre os detalhes por Intent explicito com a receita`() {
        ActivityScenario.launch(RecipeListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                boundItem(activity, 0).root.performClick()

                val started = shadowOf(activity).nextStartedActivity
                checkNotNull(started) { "nenhuma Activity foi iniciada" }

                // explicito: tem componente (classe) de destino
                assertThat(started.component?.className)
                    .isEqualTo(RecipeDetailActivity::class.java.name)
                val extra = IntentCompat.getParcelableExtra(
                    started,
                    RecipeDetailActivity.EXTRA_RECIPE,
                    Recipe::class.java,
                )
                assertThat(extra).isEqualTo(MockRecipes.all.first())
            }
        }
    }

    @Test
    fun `resultado da tela de detalhes atualiza o favorito na lista`() {
        ActivityScenario.launch(RecipeListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                boundItem(activity, 2).root.performClick()
                val started = shadowOf(activity).nextStartedActivity
                val clicked = IntentCompat.getParcelableExtra(
                    started,
                    RecipeDetailActivity.EXTRA_RECIPE,
                    Recipe::class.java,
                )!!

                // simula a tela 2 devolvendo "esta receita agora e favorita"
                shadowOf(activity).receiveResult(
                    started,
                    Activity.RESULT_OK,
                    resultFor(clicked.id, favorite = true),
                )

                activity.binding.buttonFavorites.performClick()
                awaitItemCount(activity, 1)
                assertThat(adapterOf(activity).currentList.single().id).isEqualTo(clicked.id)
                assertThat(activity.binding.textCount.text.toString()).isEqualTo("1 receita")
            }
        }
    }

    @Test
    fun `resultado cancelado ou sem dados nao altera a lista`() {
        ActivityScenario.launch(RecipeListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                boundItem(activity, 0).root.performClick()
                val started = shadowOf(activity).nextStartedActivity

                shadowOf(activity).receiveResult(started, Activity.RESULT_CANCELED, null)

                assertThat(adapterOf(activity).currentList.none { it.isFavorite }).isTrue()
            }
        }
    }

    @Test
    fun `filtro e favoritos sobrevivem a recriacao da Activity`() {
        ActivityScenario.launch(RecipeListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                boundItem(activity, 0).root.performClick()
                val started = shadowOf(activity).nextStartedActivity
                shadowOf(activity).receiveResult(
                    started,
                    Activity.RESULT_OK,
                    resultFor(MockRecipes.all.first().id, favorite = true),
                )
                activity.binding.buttonFavorites.performClick()
                awaitItemCount(activity, 1)
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                assertThat(activity.binding.filterGroup.checkedButtonId).isEqualTo(R.id.buttonFavorites)
                assertThat(adapterOf(activity).itemCount).isEqualTo(1)
                assertThat(adapterOf(activity).currentList.single().id)
                    .isEqualTo(MockRecipes.all.first().id)
                assertThat(activity.binding.textCount.text.toString()).isEqualTo("1 receita")
            }
        }
    }
}
