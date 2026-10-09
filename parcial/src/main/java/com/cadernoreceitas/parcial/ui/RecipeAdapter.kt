package com.cadernoreceitas.parcial.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cadernoreceitas.parcial.R
import com.cadernoreceitas.parcial.databinding.ItemRecipeBinding
import com.cadernoreceitas.parcial.model.Recipe

/**
 * Adapter da lista de receitas.
 *
 * - `ListAdapter` + `DiffUtil`: ao receber uma nova lista (`submitList`) so os itens que mudaram
 *   sao redesenhados (ex.: trocar a estrela de uma receita).
 * - `ItemRecipeBinding`: cada linha usa ViewBinding (nenhuma busca manual de Views).
 * - O adapter nao decide nada: apenas avisa a Activity (via lambdas) que algo foi tocado.
 */
class RecipeAdapter(
    private val onRecipeClick: (Recipe) -> Unit,
    private val onFavoriteClick: (Recipe) -> Unit,
) : ListAdapter<Recipe, RecipeAdapter.RecipeViewHolder>(RecipeDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ItemRecipeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecipeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecipeViewHolder(
        private val binding: ItemRecipeBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: Recipe) {
            val context = binding.root.context

            binding.imageCategory.setImageResource(recipe.category.icon)
            binding.textName.text = recipe.name
            binding.textMeta.text = context.getString(
                R.string.recipe_meta,
                context.getString(recipe.category.label),
                recipe.prepMinutes,
            )

            // estrela cheia/vazia e descricao para leitores de tela conforme o estado atual
            if (recipe.isFavorite) {
                binding.buttonFavorite.setImageResource(R.drawable.ic_star)
                binding.buttonFavorite.contentDescription =
                    context.getString(R.string.favorite_remove_cd, recipe.name)
            } else {
                binding.buttonFavorite.setImageResource(R.drawable.ic_star_border)
                binding.buttonFavorite.contentDescription =
                    context.getString(R.string.favorite_add_cd, recipe.name)
            }

            binding.root.setOnClickListener { onRecipeClick(recipe) }
            binding.buttonFavorite.setOnClickListener { onFavoriteClick(recipe) }
        }
    }

    private object RecipeDiffCallback : DiffUtil.ItemCallback<Recipe>() {
        // mesmo item = mesmo id (ainda que o favorito tenha mudado)
        override fun areItemsTheSame(oldItem: Recipe, newItem: Recipe): Boolean =
            oldItem.id == newItem.id

        // data class: o equals compara todos os campos
        override fun areContentsTheSame(oldItem: Recipe, newItem: Recipe): Boolean =
            oldItem == newItem
    }
}
