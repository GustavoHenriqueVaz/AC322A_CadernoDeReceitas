package com.cadernoreceitas.ui.editnote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Regras do formulario de anotacao: limites exatos (fronteiras) e sanitizacao. */
class NoteValidatorTest {

    // ------------------------------------------------------------------ nota (1..5)

    @Test
    fun `nota dentro de 1 a 5 e valida`() {
        for (rating in 1..5) {
            assertThat(NoteValidator.validateRating(rating)).isNull()
        }
    }

    @Test
    fun `nota fora de 1 a 5 e obrigatoria (invalida)`() {
        for (rating in listOf(0, -1, 6, 10, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertThat(NoteValidator.validateRating(rating)).isEqualTo(FieldError.Required)
        }
    }

    // ------------------------------------------------------------------ observacoes (10..500)

    @Test
    fun `comentario vazio ou so com espacos e obrigatorio`() {
        assertThat(NoteValidator.validateComment("")).isEqualTo(FieldError.Required)
        assertThat(NoteValidator.validateComment("     ")).isEqualTo(FieldError.Required)
        assertThat(NoteValidator.validateComment(" \t\n ")).isEqualTo(FieldError.Required)
    }

    @Test
    fun `comentario com 9 caracteres e curto e informa quantos faltam`() {
        assertThat(NoteValidator.validateComment("a".repeat(9)))
            .isEqualTo(FieldError.TooShort(min = 10, missing = 1))
        assertThat(NoteValidator.validateComment("a"))
            .isEqualTo(FieldError.TooShort(min = 10, missing = 9))
    }

    @Test
    fun `comentario com 10 caracteres e valido (limite inferior)`() {
        assertThat(NoteValidator.validateComment("a".repeat(10))).isNull()
    }

    @Test
    fun `comentario com 500 caracteres e valido (limite superior)`() {
        assertThat(NoteValidator.validateComment("a".repeat(500))).isNull()
    }

    @Test
    fun `comentario com 501 caracteres e longo demais`() {
        assertThat(NoteValidator.validateComment("a".repeat(501)))
            .isEqualTo(FieldError.TooLong(max = 500))
    }

    @Test
    fun `espacos nas pontas nao contam para os limites`() {
        // 9 letras com espacos em volta continuam sendo 9
        assertThat(NoteValidator.validateComment("   " + "a".repeat(9) + "   "))
            .isEqualTo(FieldError.TooShort(10, 1))
        // 10 letras com espacos em volta passam
        assertThat(NoteValidator.validateComment("   " + "a".repeat(10) + "   ")).isNull()
        // 500 letras + espacos nas pontas ainda cabem
        assertThat(NoteValidator.validateComment("  " + "a".repeat(500) + "  ")).isNull()
        // 501 letras + espacos nas pontas nao
        assertThat(NoteValidator.validateComment("  " + "a".repeat(501) + "  "))
            .isEqualTo(FieldError.TooLong(500))
    }

    @Test
    fun `espacos internos contam como caracteres`() {
        assertThat(NoteValidator.validateComment("a b c d e")).isEqualTo(FieldError.TooShort(10, 1)) // 9 chars
        assertThat(NoteValidator.validateComment("a b c d e f")).isNull() // 11 chars
    }

    // ------------------------------------------------------------------ minutos (opcional, 1..600)

    @Test
    fun `minutos vazio e valido (campo opcional)`() {
        assertThat(NoteValidator.validateMinutes("")).isNull()
        assertThat(NoteValidator.validateMinutes("   ")).isNull()
    }

    @Test
    fun `minutos zero esta fora do intervalo`() {
        assertThat(NoteValidator.validateMinutes("0")).isEqualTo(FieldError.OutOfRange(1, 600))
        assertThat(NoteValidator.validateMinutes("000")).isEqualTo(FieldError.OutOfRange(1, 600))
    }

    @Test
    fun `minutos 1 e 600 sao validos (limites)`() {
        assertThat(NoteValidator.validateMinutes("1")).isNull()
        assertThat(NoteValidator.validateMinutes("600")).isNull()
        assertThat(NoteValidator.validateMinutes("45")).isNull()
        assertThat(NoteValidator.validateMinutes(" 45 ")).isNull() // espacos sao aparados
    }

    @Test
    fun `minutos 601 esta fora do intervalo`() {
        assertThat(NoteValidator.validateMinutes("601")).isEqualTo(FieldError.OutOfRange(1, 600))
        assertThat(NoteValidator.validateMinutes("999")).isEqualTo(FieldError.OutOfRange(1, 600))
    }

    @Test
    fun `minutos negativo esta fora do intervalo`() {
        assertThat(NoteValidator.validateMinutes("-5")).isEqualTo(FieldError.OutOfRange(1, 600))
    }

    @Test
    fun `minutos nao numerico e rejeitado`() {
        assertThat(NoteValidator.validateMinutes("abc")).isEqualTo(FieldError.NotANumber)
        assertThat(NoteValidator.validateMinutes("12a")).isEqualTo(FieldError.NotANumber)
        assertThat(NoteValidator.validateMinutes("1.5")).isEqualTo(FieldError.NotANumber)
        assertThat(NoteValidator.validateMinutes("1,5")).isEqualTo(FieldError.NotANumber)
        assertThat(NoteValidator.validateMinutes("1 2")).isEqualTo(FieldError.NotANumber)
    }

    // ------------------------------------------------------------------ sanitizacao

    @Test
    fun `sanitizeMinutes deixa so digitos e no maximo 3`() {
        assertThat(NoteValidator.sanitizeMinutes("")).isEmpty()
        assertThat(NoteValidator.sanitizeMinutes("abc")).isEmpty()
        assertThat(NoteValidator.sanitizeMinutes("a1b2c3d4")).isEqualTo("123")
        assertThat(NoteValidator.sanitizeMinutes("12345")).isEqualTo("123")
        assertThat(NoteValidator.sanitizeMinutes("-5")).isEqualTo("5")
        assertThat(NoteValidator.sanitizeMinutes("1.5")).isEqualTo("15")
        assertThat(NoteValidator.sanitizeMinutes("60")).isEqualTo("60")
    }

    @Test
    fun `sanitizeMinutes de qualquer entrada produz texto que o validador aceita ou deixa vazio`() {
        for (raw in listOf("", "abc", "9999", "0", "12a3", "-1", "600", "601", "00")) {
            val sanitized = NoteValidator.sanitizeMinutes(raw)
            assertThat(sanitized.length).isAtMost(NoteValidator.MINUTES_MAX_DIGITS)
            // nunca sobra algo que nao seja numero: so pode dar OutOfRange (0, 601..999), nunca NotANumber
            assertThat(NoteValidator.validateMinutes(sanitized)).isNotEqualTo(FieldError.NotANumber)
        }
    }

    @Test
    fun `sanitizeComment remove controles exceto quebra de linha`() {
        assertThat(NoteValidator.sanitizeComment("a\tb\rc\nd\u0000e\u0007f")).isEqualTo("abc\ndef")
        assertThat(NoteValidator.sanitizeComment("linha 1\nlinha 2")).isEqualTo("linha 1\nlinha 2")
    }

    @Test
    fun `sanitizeComment preserva acentos e emojis`() {
        val text = "Ficou ótimo! 😋"

        assertThat(NoteValidator.sanitizeComment(text)).isEqualTo(text)
    }

    @Test
    fun `sanitizeComment corta em 550 caracteres (limite mais 50 de folga)`() {
        val sanitized = NoteValidator.sanitizeComment("x".repeat(1000))

        assertThat(sanitized).hasLength(NoteValidator.COMMENT_MAX + 50)
        assertThat(sanitized).hasLength(550)
        // o que passou da folga e invalido: o validador acusa excesso (nao deixa "passar batido")
        assertThat(NoteValidator.validateComment(sanitized)).isEqualTo(FieldError.TooLong(500))
    }

    @Test
    fun `sanitizeComment nao mexe em texto dentro do limite`() {
        val text = "a".repeat(500)

        assertThat(NoteValidator.sanitizeComment(text)).isEqualTo(text)
    }

    @Test
    fun `constantes batem com as regras documentadas`() {
        assertThat(NoteValidator.RATING_MIN).isEqualTo(1)
        assertThat(NoteValidator.RATING_MAX).isEqualTo(5)
        assertThat(NoteValidator.COMMENT_MIN).isEqualTo(10)
        assertThat(NoteValidator.COMMENT_MAX).isEqualTo(500)
        assertThat(NoteValidator.MINUTES_MIN).isEqualTo(1)
        assertThat(NoteValidator.MINUTES_MAX).isEqualTo(600)
        assertThat(NoteValidator.MINUTES_MAX_DIGITS).isEqualTo(3)
    }
}
