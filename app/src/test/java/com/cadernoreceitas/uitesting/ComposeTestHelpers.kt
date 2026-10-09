package com.cadernoreceitas.uitesting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.cadernoreceitas.ui.theme.CadernoTheme
import com.google.common.truth.Truth.assertWithMessage

/** SDK usado pelos testes Robolectric (o mais novo que o Robolectric 4.17 suporta com folga). */
const val TEST_SDK = 35

/** Aparelho de referencia: ~Pixel 5 (393x851 dp), tela comum de celular. */
const val TEST_DEVICE = "w393dp-h851dp-normal-long-notround-any-440dpi-keyshidden-nonav"

/** Tamanho minimo recomendado para alvos de toque (Material / WCAG 2.5.8 AA = 24, AAA = 44; Android = 48). */
val MinTouchTarget: Dp = 48.dp

/** Escala de fonte usada nos testes de "texto grande" (maximo oferecido pelas configuracoes do Android). */
const val LARGE_FONT_SCALE = 2f

/** Tema do app + (opcionalmente) escala de fonte do sistema. */
@Composable
fun CadernoTestContent(fontScale: Float = 1f, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
        CadernoTheme(darkTheme = false, content = content)
    }
}

fun ComposeContentTestRule.setCadernoContent(fontScale: Float = 1f, content: @Composable () -> Unit) {
    setContent { CadernoTestContent(fontScale, content) }
}

/**
 * Afirma que o alvo de toque do componente tem pelo menos [min] de largura e altura.
 * Usa `touchBoundsInRoot`, que ja inclui a "area minima de toque" que o Material acrescenta a
 * botoes pequenos (e e o que de fato recebe o dedo do usuario).
 */
fun SemanticsNodeInteraction.assertTouchTargetAtLeast(
    rule: ComposeContentTestRule,
    min: Dp = MinTouchTarget,
): SemanticsNodeInteraction {
    val bounds = fetchSemanticsNode().touchBoundsInRoot
    val density = rule.density
    val widthDp = with(density) { bounds.width.toDp() }
    val heightDp = with(density) { bounds.height.toDp() }
    assertWithMessage("largura do alvo de toque (dp)").that(widthDp.value).isAtLeast(min.value - 0.1f)
    assertWithMessage("altura do alvo de toque (dp)").that(heightDp.value).isAtLeast(min.value - 0.1f)
    return this
}

/** Igual a [assertTouchTargetAtLeast], mas pelos limites de layout (`getBoundsInRoot`), para itens de tamanho fixo. */
fun SemanticsNodeInteraction.assertLayoutSizeAtLeast(min: Dp = MinTouchTarget): SemanticsNodeInteraction {
    val bounds = getBoundsInRoot()
    assertWithMessage("largura de layout (dp)").that(bounds.width.value).isAtLeast(min.value - 0.1f)
    assertWithMessage("altura de layout (dp)").that(bounds.height.value).isAtLeast(min.value - 0.1f)
    return this
}
