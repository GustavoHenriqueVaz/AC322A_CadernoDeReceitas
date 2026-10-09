package com.cadernoreceitas.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Validacao de ids vindos de fora do app (deep link, notificacao). Testes JVM puros. */
class RecipeIdsTest {

    // ------------------------------------------------------------------ isValid

    @Test
    fun `ids numericos de 1 a 12 digitos sao validos`() {
        for (id in listOf("1", "0", "52772", "007", "123456789012")) {
            assertThat(RecipeIds.isValid(id)).isTrue()
        }
    }

    @Test
    fun `limite de tamanho - 12 digitos passam e 13 nao`() {
        assertThat(RecipeIds.isValid("1".repeat(12))).isTrue()
        assertThat(RecipeIds.isValid("1".repeat(13))).isFalse()
        assertThat(RecipeIds.isValid("1".repeat(1000))).isFalse()
    }

    @Test
    fun `null e vazio sao invalidos`() {
        assertThat(RecipeIds.isValid(null)).isFalse()
        assertThat(RecipeIds.isValid("")).isFalse()
    }

    @Test
    fun `letras, espacos e simbolos sao invalidos`() {
        val invalid = listOf(
            "abc",
            "12a",
            "a12",
            "12 3",
            " 123",
            "123 ",
            "12-3",
            "-123",
            "+123",
            "1.5",
            "1,5",
            "0x1F",
            "1e5",
        )
        for (id in invalid) {
            assertThat(RecipeIds.isValid(id)).isFalse()
        }
    }

    @Test
    fun `tentativas de injecao e path traversal sao invalidas`() {
        val attacks = listOf(
            "../x",
            "..",
            "../../etc/passwd",
            "1/../2",
            "1/2",
            "1\\2",
            "52772?x=1",
            "52772#frag",
            "52772%20",
            "1;DROP TABLE recipes",
            "1' OR '1'='1",
            "<script>1</script>",
        )
        for (id in attacks) {
            assertThat(RecipeIds.isValid(id)).isFalse()
        }
    }

    @Test
    fun `quebra de linha no fim nao passa (a regex usa matches, nao find)`() {
        assertThat(RecipeIds.isValid("123\n")).isFalse()
        assertThat(RecipeIds.isValid("\n123")).isFalse()
        assertThat(RecipeIds.isValid("1\n2")).isFalse()
        assertThat(RecipeIds.isValid("123\r\n")).isFalse()
    }

    @Test
    fun `digitos nao ASCII sao invalidos`() {
        assertThat(RecipeIds.isValid("١٢٣")).isFalse() // algarismos arabe-indicos
        assertThat(RecipeIds.isValid("１２")).isFalse() // algarismos de largura total
    }

    // ------------------------------------------------------------------ deep link

    @Test
    fun `deepLink monta cadernoreceitas barra barra receita barra id`() {
        assertThat(RecipeIds.deepLink("52772")).isEqualTo("cadernoreceitas://receita/52772")
        assertThat(RecipeIds.SCHEME).isEqualTo("cadernoreceitas")
        assertThat(RecipeIds.HOST).isEqualTo("receita")
    }

    @Test
    fun `fromDeepLink extrai o id de um link valido`() {
        assertThat(RecipeIds.fromDeepLink("cadernoreceitas", "receita", listOf("52772"))).isEqualTo("52772")
    }

    @Test
    fun `fromDeepLink com esquema errado ou ausente devolve null`() {
        assertThat(RecipeIds.fromDeepLink("https", "receita", listOf("1"))).isNull()
        assertThat(RecipeIds.fromDeepLink("http", "receita", listOf("1"))).isNull()
        assertThat(RecipeIds.fromDeepLink("CadernoReceitas", "receita", listOf("1"))).isNull() // exato, sem normalizar
        assertThat(RecipeIds.fromDeepLink("", "receita", listOf("1"))).isNull()
        assertThat(RecipeIds.fromDeepLink(null, "receita", listOf("1"))).isNull()
    }

    @Test
    fun `fromDeepLink com host errado ou ausente devolve null`() {
        assertThat(RecipeIds.fromDeepLink("cadernoreceitas", "recipe", listOf("1"))).isNull()
        assertThat(RecipeIds.fromDeepLink("cadernoreceitas", "evil.com", listOf("1"))).isNull()
        assertThat(RecipeIds.fromDeepLink("cadernoreceitas", "receita.evil.com", listOf("1"))).isNull()
        assertThat(RecipeIds.fromDeepLink("cadernoreceitas", "", listOf("1"))).isNull()
        assertThat(RecipeIds.fromDeepLink("cadernoreceitas", null, listOf("1"))).isNull()
    }

    @Test
    fun `fromDeepLink exige exatamente um segmento de caminho`() {
        assertThat(RecipeIds.fromDeepLink("cadernoreceitas", "receita", emptyList())).isNull()
        assertThat(RecipeIds.fromDeepLink("cadernoreceitas", "receita", listOf("1", "2"))).isNull()
        assertThat(RecipeIds.fromDeepLink("cadernoreceitas", "receita", listOf("52772", "extra"))).isNull()
        assertThat(RecipeIds.fromDeepLink("cadernoreceitas", "receita", listOf("1", ""))).isNull()
    }

    @Test
    fun `fromDeepLink rejeita segmento que nao e um id valido`() {
        assertThat(RecipeIds.fromDeepLink("cadernoreceitas", "receita", listOf("abc"))).isNull()
        assertThat(RecipeIds.fromDeepLink("cadernoreceitas", "receita", listOf(""))).isNull()
        assertThat(RecipeIds.fromDeepLink("cadernoreceitas", "receita", listOf(".."))).isNull()
        assertThat(RecipeIds.fromDeepLink("cadernoreceitas", "receita", listOf("1".repeat(13)))).isNull()
        assertThat(RecipeIds.fromDeepLink("cadernoreceitas", "receita", listOf("12 3"))).isNull()
    }

    @Test
    fun `deepLink e fromDeepLink sao inversos para ids validos (feito a mao, sem Uri)`() {
        val id = "53318"
        val link = RecipeIds.deepLink(id)
        // "cadernoreceitas://receita/53318" -> scheme, host e path
        val scheme = link.substringBefore("://")
        val rest = link.substringAfter("://")
        val host = rest.substringBefore('/')
        val segments = rest.substringAfter('/').split('/')

        assertThat(RecipeIds.fromDeepLink(scheme, host, segments)).isEqualTo(id)
    }
}
