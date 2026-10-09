package com.cadernoreceitas.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Acessibilidade: razao de contraste WCAG (luminancia relativa, formula do W3C) em TODOS os pares
 * texto/fundo da paleta clara e da escura. Texto exige >= 4,5:1 (AA); elementos nao textuais
 * (contorno de campos) exigem >= 3:1. Os testes rodam na JVM (sem Android).
 */
class ColorContrastTest {

    private class ContrastCase(val label: String, val foreground: Color, val background: Color, val minimum: Double)

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun hex(color: Color): String = String.format(Locale.ROOT, "#%06X", color.toArgb24())

    private fun Color.toArgb24(): Int {
        val r = (red * 255f + 0.5f).toInt()
        val g = (green * 255f + 0.5f).toInt()
        val b = (blue * 255f + 0.5f).toInt()
        return (r shl 16) or (g shl 8) or b
    }

    /** Pares "texto sobre a cor que o Material 3 usa de fundo dele". */
    private fun textOnContainerPairs(c: ColorScheme) = listOf(
        ContrastCase("onPrimary/primary", c.onPrimary, c.primary, TEXT),
        ContrastCase("onPrimaryContainer/primaryContainer", c.onPrimaryContainer, c.primaryContainer, TEXT),
        ContrastCase("onSecondary/secondary", c.onSecondary, c.secondary, TEXT),
        ContrastCase("onSecondaryContainer/secondaryContainer", c.onSecondaryContainer, c.secondaryContainer, TEXT),
        ContrastCase("onTertiary/tertiary", c.onTertiary, c.tertiary, TEXT),
        ContrastCase("onTertiaryContainer/tertiaryContainer", c.onTertiaryContainer, c.tertiaryContainer, TEXT),
        ContrastCase("onError/error", c.onError, c.error, TEXT),
        ContrastCase("onErrorContainer/errorContainer", c.onErrorContainer, c.errorContainer, TEXT),
        ContrastCase("onBackground/background", c.onBackground, c.background, TEXT),
        ContrastCase("onSurface/surface", c.onSurface, c.surface, TEXT),
        ContrastCase("onSurfaceVariant/surfaceVariant", c.onSurfaceVariant, c.surfaceVariant, TEXT),
        ContrastCase("onSurface/surfaceVariant", c.onSurface, c.surfaceVariant, TEXT),
    )

    /** Texto colorido (links, titulos, erros) sobre o fundo da tela. */
    private fun coloredTextOnBackgroundPairs(c: ColorScheme) = listOf(
        ContrastCase("primary/background", c.primary, c.background, TEXT),
        ContrastCase("secondary/background", c.secondary, c.background, TEXT),
        ContrastCase("tertiary/background", c.tertiary, c.background, TEXT),
        ContrastCase("error/background", c.error, c.background, TEXT),
        ContrastCase("onSurfaceVariant/background", c.onSurfaceVariant, c.background, TEXT),
        ContrastCase("primary/surface", c.primary, c.surface, TEXT),
        ContrastCase("error/surface", c.error, c.surface, TEXT),
    )

    private fun nonTextPairs(c: ColorScheme) = listOf(
        ContrastCase("outline/background", c.outline, c.background, NON_TEXT),
        ContrastCase("outline/surface", c.outline, c.surface, NON_TEXT),
    )

    private fun assertAll(scheme: String, pairs: List<ContrastCase>) {
        val report = pairs.joinToString("\n") {
            val ratio = contrast(it.foreground, it.background)
            String.format(
                Locale.ROOT,
                "  %-42s %s sobre %s = %5.2f:1 (minimo %.1f)%s",
                it.label, hex(it.foreground), hex(it.background), ratio, it.minimum,
                if (ratio >= it.minimum) "" else "   <-- FALHA",
            )
        }
        println("Contraste ($scheme):\n$report")
        val failures = pairs.filter { contrast(it.foreground, it.background) < it.minimum }
        assertThat(failures.map { it.label }).isEmpty()
    }

    // ------------------------------------------------------------------ a propria formula

    @Test
    fun `formula de contraste confere com valores conhecidos do WCAG`() {
        assertThat(contrast(Color.Black, Color.White)).isWithin(0.01).of(21.0)
        assertThat(contrast(Color.White, Color.Black)).isWithin(0.01).of(21.0) // simetrica
        assertThat(contrast(Color.White, Color.White)).isWithin(0.001).of(1.0)
        // #767676 sobre branco e o cinza mais claro que passa AA (4,54:1); #777777 nao (4,48:1)
        assertThat(contrast(Color(0xFF767676), Color.White)).isWithin(0.02).of(4.54)
        assertThat(contrast(Color(0xFF777777), Color.White)).isWithin(0.02).of(4.48)
        assertThat(contrast(Color(0xFF767676), Color.White)).isAtLeast(TEXT)
        assertThat(contrast(Color(0xFF777777), Color.White)).isLessThan(TEXT)
    }

    // ------------------------------------------------------------------ tema claro

    @Test
    fun `tema claro - texto sobre cada cor de fundo tem pelo menos 4,5 para 1`() {
        assertAll("claro/texto", textOnContainerPairs(LightColors))
    }

    @Test
    fun `tema claro - texto colorido sobre o fundo tem pelo menos 4,5 para 1`() {
        assertAll("claro/texto colorido", coloredTextOnBackgroundPairs(LightColors))
    }

    @Test
    fun `tema claro - contorno sobre o fundo tem pelo menos 3 para 1`() {
        assertAll("claro/nao textual", nonTextPairs(LightColors))
    }

    // ------------------------------------------------------------------ tema escuro

    @Test
    fun `tema escuro - texto sobre cada cor de fundo tem pelo menos 4,5 para 1`() {
        assertAll("escuro/texto", textOnContainerPairs(DarkColors))
    }

    @Test
    fun `tema escuro - texto colorido sobre o fundo tem pelo menos 4,5 para 1`() {
        assertAll("escuro/texto colorido", coloredTextOnBackgroundPairs(DarkColors))
    }

    @Test
    fun `tema escuro - contorno sobre o fundo tem pelo menos 3 para 1`() {
        assertAll("escuro/nao textual", nonTextPairs(DarkColors))
    }

    // ------------------------------------------------------------------ coerencia

    @Test
    fun `os dois temas realmente sao diferentes (claro tem fundo claro, escuro tem fundo escuro)`() {
        assertThat(LightColors.background.luminance()).isGreaterThan(0.5f)
        assertThat(DarkColors.background.luminance()).isLessThan(0.1f)
        assertThat(LightColors.onBackground.luminance()).isLessThan(0.1f)
        assertThat(DarkColors.onBackground.luminance()).isGreaterThan(0.5f)
    }

    private companion object {
        const val TEXT = 4.5
        const val NON_TEXT = 3.0
    }
}
