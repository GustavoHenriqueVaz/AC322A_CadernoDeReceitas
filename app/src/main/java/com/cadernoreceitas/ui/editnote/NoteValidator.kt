package com.cadernoreceitas.ui.editnote

/** Erros de validacao por campo. A UI converte cada um em mensagem (com os numeros certos). */
sealed interface FieldError {
    data object Required : FieldError
    data class TooShort(val min: Int, val missing: Int) : FieldError
    data class TooLong(val max: Int) : FieldError
    data class OutOfRange(val min: Int, val max: Int) : FieldError
    data object NotANumber : FieldError
}

/**
 * Regras do formulario, como funcoes puras (faceis de testar sem Android).
 *  - nota: obrigatoria, 1..5
 *  - observacoes: obrigatorias, 10..500 caracteres (ignorando espacos nas pontas)
 *  - tempo de preparo: opcional, inteiro 1..600
 */
object NoteValidator {
    const val RATING_MIN = 1
    const val RATING_MAX = 5
    const val COMMENT_MIN = 10
    const val COMMENT_MAX = 500
    const val MINUTES_MIN = 1
    const val MINUTES_MAX = 600

    /** Limita o campo de minutos a 3 digitos numericos (ja filtra na digitacao). */
    const val MINUTES_MAX_DIGITS = 3

    fun validateRating(rating: Int): FieldError? =
        if (rating !in RATING_MIN..RATING_MAX) FieldError.Required else null

    fun validateComment(comment: String): FieldError? {
        val length = comment.trim().length
        return when {
            length == 0 -> FieldError.Required
            length < COMMENT_MIN -> FieldError.TooShort(COMMENT_MIN, COMMENT_MIN - length)
            length > COMMENT_MAX -> FieldError.TooLong(COMMENT_MAX)
            else -> null
        }
    }

    /** Campo vazio e valido (e opcional). */
    fun validateMinutes(text: String): FieldError? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val value = trimmed.toIntOrNull() ?: return FieldError.NotANumber
        return if (value in MINUTES_MIN..MINUTES_MAX) null else FieldError.OutOfRange(MINUTES_MIN, MINUTES_MAX)
    }

    /** Aceita so digitos, no maximo [MINUTES_MAX_DIGITS]. */
    fun sanitizeMinutes(raw: String): String = raw.filter { it.isDigit() }.take(MINUTES_MAX_DIGITS)

    /** Remove caracteres de controle (exceto quebra de linha) e corta no limite maximo + folga. */
    fun sanitizeComment(raw: String): String =
        raw.filter { !it.isISOControl() || it == '\n' }.take(COMMENT_MAX + 50)
}
