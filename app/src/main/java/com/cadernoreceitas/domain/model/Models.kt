package com.cadernoreceitas.domain.model

/**
 * Modelos de dominio: imutaveis (`data class` com apenas `val`) e independentes de
 * framework (nada de Room, Retrofit ou Android aqui).
 * Valores opcionais da API sao representados com tipos anulaveis (`String?`, `Int?`).
 */

data class Ingredient(
    val name: String,
    /** Nem todo ingrediente tem medida informada pela API. */
    val measure: String?,
)

/** Versao "resumida" de uma receita, usada nas listas. */
data class RecipeSummary(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val category: String?,
    val area: String?,
)

data class Recipe(
    val id: String,
    val name: String,
    val category: String?,
    val area: String?,
    val instructions: String,
    val thumbnailUrl: String?,
    val tags: List<String>,
    val youtubeUrl: String?,
    val sourceUrl: String?,
    val ingredients: List<Ingredient>,
) {
    fun toSummary() = RecipeSummary(
        id = id,
        name = name,
        thumbnailUrl = thumbnailUrl,
        category = category,
        area = area,
    )
}

data class Category(
    val name: String,
    val thumbnailUrl: String?,
    val description: String?,
)

/**
 * Receita guardada no "Caderno" do usuario, com a anotacao pessoal.
 * Enquanto o usuario nao preenche a anotacao, [rating], [comment], [prepMinutes]
 * e [photoPath] ficam nulos.
 */
data class SavedRecipe(
    val recipe: Recipe,
    val rating: Int?,
    val comment: String?,
    val prepMinutes: Int?,
    val photoPath: String?,
    val savedAt: Long,
    val updatedAt: Long,
) {
    val hasNote: Boolean get() = rating != null || !comment.isNullOrBlank()
}

/** Dados do formulario de anotacao ja validados. */
data class RecipeNote(
    val rating: Int,
    val comment: String,
    val prepMinutes: Int?,
    val photoPath: String?,
)

/** Origem dos dados devolvidos pelo repositorio. */
enum class DataSource {
    /** Veio agora da API. */
    REMOTE,

    /** Veio do cache local ainda valido (dentro do TTL). */
    CACHE_FRESH,

    /** Cache vencido usado porque a API falhou (modo offline). */
    CACHE_STALE,
}

/** Resultado de uma leitura: o valor + de onde ele veio. */
data class Loaded<T>(
    val value: T,
    val source: DataSource,
) {
    val isOffline: Boolean get() = source == DataSource.CACHE_STALE
}

/** Preferencias do usuario guardadas em DataStore. */
data class UserPreferences(
    val lastQuery: String = "",
    val lastCategory: String? = null,
    val reminderEnabled: Boolean = false,
    val dailyRecipeId: String? = null,
    val dailyRecipeEpochDay: Long? = null,
)

/** Falhas esperadas ao acessar dados; a UI traduz cada tipo para uma mensagem. */
class RecipeException(
    val kind: Kind,
    cause: Throwable? = null,
) : Exception(kind.name, cause) {
    enum class Kind { NETWORK, SERVER, INVALID_DATA, NOT_FOUND }
}
