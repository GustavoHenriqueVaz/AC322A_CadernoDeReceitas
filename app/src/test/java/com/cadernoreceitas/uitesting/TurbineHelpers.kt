package com.cadernoreceitas.uitesting

import app.cash.turbine.ReceiveTurbine

/**
 * Consome itens do Turbine ate achar um que satisfaz [predicate] (descartando os intermediarios)
 * e o devolve. Falha por timeout do proprio Turbine se nunca aparecer.
 */
suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
