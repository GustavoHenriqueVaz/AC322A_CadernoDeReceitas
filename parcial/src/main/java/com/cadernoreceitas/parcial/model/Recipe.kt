package com.cadernoreceitas.parcial.model

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.cadernoreceitas.parcial.R
import kotlinx.parcelize.Parcelize

/*
 * Modelos IMUTAVEIS (data class com apenas `val`).
 * Para "alterar" algo (ex.: favoritar) criamos uma copia com `copy(...)`, nunca mutamos o objeto.
 * `@Parcelize` permite enviar o objeto dentro de um Intent (putExtra) sem escrever o codigo
 * de serializacao na mao.
 */

enum class RecipeCategory(@StringRes val label: Int, @DrawableRes val icon: Int) {
    MAIN(R.string.category_main, R.drawable.ic_category_main),
    BREAKFAST(R.string.category_breakfast, R.drawable.ic_category_breakfast),
    SALAD(R.string.category_salad, R.drawable.ic_category_salad),
    DESSERT(R.string.category_dessert, R.drawable.ic_category_dessert),
    SOUP(R.string.category_soup, R.drawable.ic_category_soup),
}

enum class Difficulty(@StringRes val label: Int) {
    EASY(R.string.difficulty_easy),
    MEDIUM(R.string.difficulty_medium),
    HARD(R.string.difficulty_hard),
}

@Parcelize
data class Ingredient(
    val name: String,
    /** Opcional: "sal a gosto" nao tem quantidade. */
    val quantity: String? = null,
) : Parcelable

@Parcelize
data class Recipe(
    val id: Int,
    val name: String,
    val category: RecipeCategory,
    val prepMinutes: Int,
    val servings: Int,
    val difficulty: Difficulty,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    /** Dica do cozinheiro: nem toda receita tem (valor opcional). */
    val tip: String? = null,
    /** Link de video: opcional. */
    val videoUrl: String? = null,
    val isFavorite: Boolean = false,
) : Parcelable {

    /** "Rapida" = ate 30 minutos. */
    val isQuick: Boolean get() = prepMinutes <= QUICK_LIMIT_MINUTES

    companion object {
        const val QUICK_LIMIT_MINUTES = 30
    }
}

/** Filtros da lista (botoes no topo da primeira tela). */
enum class RecipeFilter {
    ALL,
    QUICK,
    FAVORITES,
    ;

    fun matches(recipe: Recipe): Boolean = when (this) {
        ALL -> true
        QUICK -> recipe.isQuick
        FAVORITES -> recipe.isFavorite
    }
}
