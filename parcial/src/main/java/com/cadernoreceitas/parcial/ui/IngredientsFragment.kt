package com.cadernoreceitas.parcial.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import com.cadernoreceitas.parcial.R
import com.cadernoreceitas.parcial.databinding.FragmentIngredientsBinding
import com.cadernoreceitas.parcial.databinding.ItemIngredientBinding
import com.cadernoreceitas.parcial.model.Ingredient

/**
 * Fragment com a lista de ingredientes e a lista de "separados" (checkboxes).
 *
 * Ciclo de vida + ViewBinding: o binding so existe entre onCreateView e onDestroyView.
 * O Fragment pode continuar vivo depois de sua View ser destruida, por isso `_binding` e
 * zerado em onDestroyView (evita vazamento de memoria).
 */
class IngredientsFragment : Fragment() {

    private var _binding: FragmentIngredientsBinding? = null

    // so use entre onCreateView e onDestroyView
    private val binding get() = _binding!!

    private var ingredients: List<Ingredient> = emptyList()

    /** Posicoes dos ingredientes marcados (sobrevive a rotacao via onSaveInstanceState). */
    private val checkedPositions = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // a lista chega por arguments (Bundle), nunca por construtor com parametros
        ingredients = arguments
            ?.let { BundleCompat.getParcelableArrayList(it, ARG_INGREDIENTS, Ingredient::class.java) }
            .orEmpty()

        savedInstanceState
            ?.getIntegerArrayList(STATE_CHECKED)
            ?.let { checkedPositions.addAll(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentIngredientsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // infla o layout item_ingredient uma vez para cada ingrediente
        ingredients.forEachIndexed { position, ingredient ->
            val item = ItemIngredientBinding.inflate(layoutInflater, binding.ingredientsContainer, false)
            item.checkIngredient.text = ingredientText(ingredient)

            // todos os CheckBox tem o mesmo id no XML: sem isto o Android restauraria o estado de
            // um sobre os outros. O estado e restaurado por checkedPositions.
            item.checkIngredient.isSaveEnabled = false
            item.checkIngredient.isChecked = position in checkedPositions

            item.checkIngredient.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) checkedPositions.add(position) else checkedPositions.remove(position)
                updateProgress()
            }
            binding.ingredientsContainer.addView(item.root)
        }
        updateProgress()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntegerArrayList(STATE_CHECKED, ArrayList(checkedPositions))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** "nome — quantidade"; quando a quantidade e nula, mostra so o nome. */
    private fun ingredientText(ingredient: Ingredient): String {
        val quantity = ingredient.quantity
        return if (quantity == null) {
            ingredient.name
        } else {
            getString(R.string.ingredient_with_quantity, ingredient.name, quantity)
        }
    }

    private fun updateProgress() {
        binding.ingredientsProgress.text = getString(
            R.string.ingredients_progress,
            checkedPositions.size,
            ingredients.size,
        )
    }

    companion object {
        private const val ARG_INGREDIENTS = "arg_ingredients"
        private const val STATE_CHECKED = "state_checked"

        fun newInstance(ingredients: List<Ingredient>): IngredientsFragment =
            IngredientsFragment().apply {
                arguments = Bundle().apply {
                    // ArrayList: o Bundle precisa de uma lista Parcelable concreta
                    putParcelableArrayList(ARG_INGREDIENTS, ArrayList(ingredients))
                }
            }
    }
}
