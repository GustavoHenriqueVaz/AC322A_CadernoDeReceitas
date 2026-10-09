package com.cadernoreceitas.parcial.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Com `enableEdgeToEdge()` o app desenha por baixo da barra de status e da barra de navegacao.
 * Esta funcao soma ao padding original da View o tamanho das barras do sistema (e do recorte da
 * camera), para o conteudo nao ficar escondido atras delas.
 *
 * Cada lado pode ser desligado: assim o cabecalho colorido cuida so do topo e o restante da tela
 * cuida dos outros lados.
 */
fun View.applySystemBarsPadding(
    left: Boolean = true,
    top: Boolean = true,
    right: Boolean = true,
    bottom: Boolean = true,
) {
    // guarda o padding definido no XML para somar (e nao sobrescrever) a cada novo inset
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val bars = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        view.updatePadding(
            left = initialLeft + if (left) bars.left else 0,
            top = initialTop + if (top) bars.top else 0,
            right = initialRight + if (right) bars.right else 0,
            bottom = initialBottom + if (bottom) bars.bottom else 0,
        )
        // devolve os insets sem consumir: as Views filhas tambem podem usa-los
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}
