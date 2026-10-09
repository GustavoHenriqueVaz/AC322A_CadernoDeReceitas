package com.cadernoreceitas.util

/**
 * Validacao de identificadores de receita vindos de fora do app (deep link, notificacao).
 * Os ids da TheMealDB sao numericos; qualquer outra coisa e rejeitada, o que evita
 * injetar texto arbitrario em URLs/consultas.
 */
object RecipeIds {
    private val pattern = Regex("^[0-9]{1,12}$")

    fun isValid(id: String?): Boolean = id != null && pattern.matches(id)

    const val SCHEME = "cadernoreceitas"
    const val HOST = "receita"

    fun deepLink(id: String): String = "$SCHEME://$HOST/$id"

    /** Extrai o id de `cadernoreceitas://receita/{id}`; `null` se a URI for invalida. */
    fun fromDeepLink(scheme: String?, host: String?, pathSegments: List<String>): String? {
        if (scheme != SCHEME || host != HOST) return null
        val id = pathSegments.singleOrNull()
        return id?.takeIf { isValid(it) }
    }
}
