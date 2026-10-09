package com.cadernoreceitas.parcial.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.cadernoreceitas.parcial.R
import com.cadernoreceitas.parcial.data.MockRecipes
import com.cadernoreceitas.parcial.databinding.ActivityRecipeListBinding
import com.cadernoreceitas.parcial.model.Recipe
import com.cadernoreceitas.parcial.model.RecipeFilter

/**
 * Tela 1: lista de receitas (launcher).
 *
 * - Views acessadas por ViewBinding ([ActivityRecipeListBinding]).
 * - Os filtros, a estrela de cada item e o resultado da tela 2 atualizam a interface.
 * - Abre a tela 2 com Intent explicito e recebe o resultado por registerForActivityResult.
 */
class RecipeListActivity : AppCompatActivity() {

    // internal (e nao private) so para os testes lerem as Views sem busca manual por id
    internal lateinit var binding: ActivityRecipeListBinding
        private set

    private val adapter = RecipeAdapter(
        onRecipeClick = ::openDetail,
        onFavoriteClick = ::toggleFavorite,
    )

    /** Fonte da verdade da tela: lista imutavel, trocada por uma nova a cada alteracao. */
    private var recipes: List<Recipe> = MockRecipes.all
    private var filter: RecipeFilter = RecipeFilter.ALL

    // precisa ser registrado antes da Activity ficar STARTED (por isso e uma propriedade)
    private val detailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null &&
            data.hasExtra(RecipeDetailActivity.EXTRA_RESULT_FAVORITE)
        ) {
            val id = data.getIntExtra(RecipeDetailActivity.EXTRA_RESULT_ID, NO_ID)
            val favorite = data.getBooleanExtra(RecipeDetailActivity.EXTRA_RESULT_FAVORITE, false)
            setFavorite(id, favorite)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityRecipeListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // cabecalho cuida da barra de status; o restante da tela, dos outros lados
        binding.header.applySystemBarsPadding(left = false, right = false, bottom = false)
        binding.root.applySystemBarsPadding(top = false)

        restoreState(savedInstanceState)
        setupRecyclerView()
        setupFilters()
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_FILTER, filter.name)
        outState.putIntArray(
            STATE_FAVORITES,
            recipes.filter { it.isFavorite }.map { it.id }.toIntArray(),
        )
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        savedInstanceState ?: return

        savedInstanceState.getString(STATE_FILTER)?.let { name ->
            filter = RecipeFilter.entries.firstOrNull { it.name == name } ?: RecipeFilter.ALL
        }
        savedInstanceState.getIntArray(STATE_FAVORITES)?.let { ids ->
            val favoriteIds = ids.toSet()
            recipes = MockRecipes.all.map { it.copy(isFavorite = it.id in favoriteIds) }
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupFilters() {
        // marca o botao do filtro atual antes de registrar o listener
        binding.filterGroup.check(buttonIdFor(filter))
        binding.filterGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            // o grupo avisa duas vezes (um botao desmarca, outro marca): so o marcado importa
            if (isChecked) {
                filterFor(checkedId)?.let {
                    filter = it
                    render()
                }
            }
        }
    }

    /** Redesenha a lista, o contador e o estado vazio a partir de `recipes` e `filter`. */
    private fun render() {
        val visible = recipes.filter { filter.matches(it) }

        adapter.submitList(visible)
        binding.textCount.text =
            resources.getQuantityString(R.plurals.recipe_count, visible.size, visible.size)

        val isEmpty = visible.isEmpty()
        binding.recyclerView.isVisible = !isEmpty
        binding.emptyState.isVisible = isEmpty
        if (isEmpty) binding.emptyMessage.setText(emptyMessageFor(filter))
    }

    private fun toggleFavorite(recipe: Recipe) {
        setFavorite(recipe.id, !recipe.isFavorite)
    }

    /** Modelo imutavel: troca a receita por uma copia com o novo valor e redesenha. */
    private fun setFavorite(id: Int, favorite: Boolean) {
        recipes = recipes.map { if (it.id == id) it.copy(isFavorite = favorite) else it }
        render()
    }

    private fun openDetail(recipe: Recipe) {
        // Intent EXPLICITO: informa a classe de destino e leva a receita como dado
        val intent = Intent(this, RecipeDetailActivity::class.java)
            .putExtra(RecipeDetailActivity.EXTRA_RECIPE, recipe)
        detailLauncher.launch(intent)
    }

    @IdRes
    private fun buttonIdFor(filter: RecipeFilter): Int = when (filter) {
        RecipeFilter.ALL -> R.id.buttonAll
        RecipeFilter.QUICK -> R.id.buttonQuick
        RecipeFilter.FAVORITES -> R.id.buttonFavorites
    }

    private fun filterFor(@IdRes buttonId: Int): RecipeFilter? = when (buttonId) {
        R.id.buttonAll -> RecipeFilter.ALL
        R.id.buttonQuick -> RecipeFilter.QUICK
        R.id.buttonFavorites -> RecipeFilter.FAVORITES
        else -> null
    }

    @StringRes
    private fun emptyMessageFor(filter: RecipeFilter): Int = when (filter) {
        RecipeFilter.ALL -> R.string.empty_all
        RecipeFilter.QUICK -> R.string.empty_quick
        RecipeFilter.FAVORITES -> R.string.empty_favorites
    }

    private companion object {
        const val NO_ID = -1
        const val STATE_FILTER = "state_filter"
        const val STATE_FAVORITES = "state_favorites"
    }
}
