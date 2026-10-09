package com.cadernoreceitas.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** `normalizeQuery` e `escapeLike` (companion interno do repositorio). Testes JVM puros. */
class RecipeQueryUtilsTest {

    private fun normalize(raw: String) = DefaultRecipeRepository.normalizeQuery(raw)

    // ------------------------------------------------------------------ normalizeQuery

    @Test
    fun `apara as pontas e colapsa espacos repetidos`() {
        assertThat(normalize("  Chicken   Soup ")).isEqualTo("Chicken Soup")
        assertThat(normalize("a    b     c")).isEqualTo("a b c")
        assertThat(normalize("   ")).isEmpty()
        assertThat(normalize("")).isEmpty()
    }

    @Test
    fun `preserva maiusculas, acentos e emojis`() {
        assertThat(normalize("Pão de Queijo")).isEqualTo("Pão de Queijo")
        assertThat(normalize("cake 🍰")).isEqualTo("cake 🍰")
    }

    @Test
    fun `tab e quebras de linha colados no campo separam palavras (nao as juntam)`() {
        assertThat(normalize("chicken\tsoup")).isEqualTo("chicken soup")
        assertThat(normalize("chicken\nsoup")).isEqualTo("chicken soup")
        assertThat(normalize("chicken\r\nsoup")).isEqualTo("chicken soup")
        assertThat(normalize("\n  chicken \t\n soup \r\n")).isEqualTo("chicken soup")
    }

    @Test
    fun `caracteres de controle nao chegam a consulta`() {
        val normalized = normalize("ab\u0000cd\u0007ef\u001Fgh\u007Fij")

        assertThat(normalized.none { it.isISOControl() }).isTrue()
        assertThat(normalized).isEqualTo("ab cd ef gh ij")
    }

    @Test
    fun `limita a 60 caracteres`() {
        assertThat(normalize("a".repeat(60))).hasLength(60)
        assertThat(normalize("a".repeat(61))).hasLength(60)
        assertThat(normalize("a".repeat(5000))).hasLength(DefaultRecipeRepository.MAX_QUERY_LENGTH)
        assertThat(DefaultRecipeRepository.MAX_QUERY_LENGTH).isEqualTo(60)
    }

    @Test
    fun `o corte nao deixa espaco sobrando no fim`() {
        // 59 letras + espaco + mais texto: o corte em 60 cairia logo apos o espaco
        val normalized = normalize("a".repeat(59) + " bbbb")

        assertThat(normalized).isEqualTo("a".repeat(59))
        assertThat(normalized).isEqualTo(normalized.trim())
    }

    @Test
    fun `normalizar duas vezes da o mesmo resultado (idempotente)`() {
        val samples = listOf(
            "  Chicken   Soup ",
            "a".repeat(59) + " bbbb",
            "x\ty\nz",
            "ab\u0000cd",
            "",
            "   ",
            "Água   com  gás",
        )
        for (sample in samples) {
            val once = normalize(sample)
            assertThat(normalize(once)).isEqualTo(once)
        }
    }

    // ------------------------------------------------------------------ escapeLike

    @Test
    fun `escapeLike escapa porcentagem, sublinhado e barra invertida`() {
        assertThat(DefaultRecipeRepository.escapeLike("50%")).isEqualTo("50\\%")
        assertThat(DefaultRecipeRepository.escapeLike("a_b")).isEqualTo("a\\_b")
        assertThat(DefaultRecipeRepository.escapeLike("c\\d")).isEqualTo("c\\\\d")
    }

    @Test
    fun `escapeLike escapa a barra primeiro, sem escapar duas vezes`() {
        // "%" -> "\%" ; a barra que veio do escape nao pode ser escapada de novo
        assertThat(DefaultRecipeRepository.escapeLike("100%_\\")).isEqualTo("100\\%\\_\\\\")
        assertThat(DefaultRecipeRepository.escapeLike("%%")).isEqualTo("\\%\\%")
    }

    @Test
    fun `escapeLike nao altera texto comum`() {
        assertThat(DefaultRecipeRepository.escapeLike("chicken soup")).isEqualTo("chicken soup")
        assertThat(DefaultRecipeRepository.escapeLike("")).isEmpty()
        assertThat(DefaultRecipeRepository.escapeLike("Pão")).isEqualTo("Pão")
    }
}
