package com.cadernoreceitas.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cadernoreceitas.R
import com.cadernoreceitas.domain.model.RecipeException
import kotlinx.coroutines.CancellationException

/** Tipos de erro que a UI sabe explicar ao usuario (sem expor excecoes/stack traces). */
enum class UiError {
    NETWORK,
    SERVER,
    INVALID_DATA,
    NOT_FOUND,
    UNKNOWN,
}

fun Throwable.toUiError(): UiError = when {
    this is CancellationException -> throw this // nunca transformar cancelamento em erro de tela
    this is RecipeException -> when (kind) {
        RecipeException.Kind.NETWORK -> UiError.NETWORK
        RecipeException.Kind.SERVER -> UiError.SERVER
        RecipeException.Kind.INVALID_DATA -> UiError.INVALID_DATA
        RecipeException.Kind.NOT_FOUND -> UiError.NOT_FOUND
    }
    else -> UiError.UNKNOWN
}

@Composable
fun UiError.message(): String = stringResource(
    when (this) {
        UiError.NETWORK -> R.string.error_network
        UiError.SERVER -> R.string.error_server
        UiError.INVALID_DATA -> R.string.error_invalid
        UiError.NOT_FOUND -> R.string.error_not_found
        UiError.UNKNOWN -> R.string.error_unknown
    },
)
