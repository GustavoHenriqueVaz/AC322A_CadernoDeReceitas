package com.cadernoreceitas.parcial.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import com.cadernoreceitas.parcial.R
import com.cadernoreceitas.parcial.databinding.ActivityRecipeDetailBinding
import com.cadernoreceitas.parcial.databinding.ItemStepBinding
import com.cadernoreceitas.parcial.databinding.ViewInfoChipBinding
import com.cadernoreceitas.parcial.model.Recipe

/**
 * Tela 2: detalhes de uma receita.
 *
 * Recebe a [Recipe] por Intent explicito (extra [EXTRA_RECIPE]) e devolve para a tela 1 o id e o
 * novo estado de favorito (setResult), para a lista se atualizar quando o usuario voltar.
 */
class RecipeDetailActivity : AppCompatActivity() {

    // internal (e nao private) so para os testes lerem as Views sem busca manual por id
    internal lateinit var binding: ActivityRecipeDetailBinding
        private set

    /** Receita exibida; e imutavel, entao favoritar troca este valor por uma copia. */
    private lateinit var recipe: Recipe

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // le o dado enviado pelo Intent; se nao veio (nulo), avisa e encerra a tela
        val received = IntentCompat.getParcelableExtra(intent, EXTRA_RECIPE, Recipe::class.java)
        if (received == null) {
            Toast.makeText(this, R.string.recipe_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // apos girar a tela o Intent continua com o valor original: o favorito vem do estado salvo
        val favorite = savedInstanceState
            ?.takeIf { it.containsKey(STATE_FAVORITE) }
            ?.getBoolean(STATE_FAVORITE)
            ?: received.isFavorite
        recipe = received.copy(isFavorite = favorite)

        binding = ActivityRecipeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        bindRecipe()
        setupIngredients(savedInstanceState)
        setupActions()

        // o resultado nao sobrevive a recriacao da Activity: reenvia se o favorito ja tinha mudado
        if (favorite != received.isFavorite) publishResult()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // so existe se a tela chegou a ser montada (receita valida)
        if (::recipe.isInitialized) outState.putBoolean(STATE_FAVORITE, recipe.isFavorite)
    }

    /** Preenche as Views que dependem apenas da receita (nao mudam com os toques). */
    private fun bindRecipe() {
        binding.imageHero.setImageResource(recipe.category.icon)
        binding.imageHero.contentDescription =
            getString(R.string.hero_cd, getString(recipe.category.label))
        binding.textTitle.text = recipe.name
        binding.textCategory.text = getString(R.string.category_label, getString(recipe.category.label))

        // o mesmo layout (view_info_chip) reutilizado 3x via <include>; cada include tem seu binding
        bindChip(binding.chipTime, R.drawable.ic_clock, getString(R.string.chip_time, recipe.prepMinutes))
        bindChip(
            binding.chipServings,
            R.drawable.ic_people,
            resources.getQuantityString(R.plurals.chip_servings_count, recipe.servings, recipe.servings),
        )
        bindChip(binding.chipDifficulty, R.drawable.ic_level, getString(recipe.difficulty.label))

        // dica: valor opcional, o card inteiro some quando for nula ou vazia
        val tip = recipe.tip?.takeIf { it.isNotBlank() }
        binding.tipCard.isVisible = tip != null
        binding.textTip.text = tip

        // video: valor opcional, o botao so aparece se houver link
        val videoUrl = recipe.videoUrl?.takeIf { it.isNotBlank() }
        binding.buttonVideo.isVisible = videoUrl != null
        if (videoUrl != null) {
            binding.buttonVideo.setOnClickListener { openVideo(videoUrl) }
        }

        // modo de preparo: infla o layout item_step uma vez para cada passo
        recipe.steps.forEachIndexed { index, step ->
            val stepBinding = ItemStepBinding.inflate(layoutInflater, binding.stepsContainer, false)
            stepBinding.stepNumber.text = getString(R.string.step_number, index + 1)
            stepBinding.stepText.text = step
            binding.stepsContainer.addView(stepBinding.root)
        }
    }

    private fun bindChip(chip: ViewInfoChipBinding, @DrawableRes iconRes: Int, label: String) {
        chip.chipIcon.setImageResource(iconRes)
        chip.chipLabel.text = label
    }

    /** A lista de ingredientes e um Fragment; ao recriar a Activity o FragmentManager o restaura. */
    private fun setupIngredients(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(
                    R.id.ingredientsContainer,
                    IngredientsFragment.newInstance(recipe.ingredients),
                )
            }
        }
    }

    private fun setupActions() {
        // botao da tela e botao "voltar" do sistema seguem o mesmo caminho e finalizam a Activity
        binding.buttonBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        updateFavoriteButton()
        binding.buttonFavorite.setOnClickListener { toggleFavorite() }
    }

    private fun toggleFavorite() {
        // modelo imutavel: cria uma copia com o favorito invertido
        recipe = recipe.copy(isFavorite = !recipe.isFavorite)
        updateFavoriteButton()
        publishResult()
    }

    /** Atualiza texto, icone e descricao do botao conforme o estado atual. */
    private fun updateFavoriteButton() {
        val button = binding.buttonFavorite
        if (recipe.isFavorite) {
            button.setText(R.string.favorite_button_remove)
            button.setIconResource(R.drawable.ic_star)
            button.contentDescription = getString(R.string.favorite_remove_cd, recipe.name)
        } else {
            button.setText(R.string.favorite_button_add)
            button.setIconResource(R.drawable.ic_star_border)
            button.contentDescription = getString(R.string.favorite_add_cd, recipe.name)
        }
    }

    /** Devolve o resultado para a tela 1 (quem abriu esta tela por registerForActivityResult). */
    private fun publishResult() {
        val data = Intent()
            .putExtra(EXTRA_RESULT_ID, recipe.id)
            .putExtra(EXTRA_RESULT_FAVORITE, recipe.isFavorite)
        setResult(RESULT_OK, data)
    }

    private fun openVideo(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            // aparelho sem navegador/app capaz de abrir o link
            Toast.makeText(this, R.string.video_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        /** Extra de entrada: a receita (Parcelable) enviada pela tela 1. */
        const val EXTRA_RECIPE = "com.cadernoreceitas.parcial.extra.RECIPE"

        /** Extras de saida (setResult): id da receita e o novo estado de favorito. */
        const val EXTRA_RESULT_ID = "com.cadernoreceitas.parcial.extra.RESULT_ID"
        const val EXTRA_RESULT_FAVORITE = "com.cadernoreceitas.parcial.extra.RESULT_FAVORITE"

        private const val STATE_FAVORITE = "state_favorite"
    }
}
