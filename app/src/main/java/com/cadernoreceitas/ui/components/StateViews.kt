package com.cadernoreceitas.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cadernoreceitas.R

/*
 * Os quatro estados visuais obrigatorios das telas de dados:
 * carregando (LoadingState), conteudo (a propria tela), lista vazia (EmptyState) e erro (ErrorState).
 * Todos rolam na vertical para continuar utilizaveis com fonte grande / tela pequena.
 */

object TestTags {
    const val LOADING = "state_loading"
    const val EMPTY = "state_empty"
    const val ERROR = "state_error"
    const val OFFLINE_BANNER = "offline_banner"
    const val RECIPE_LIST = "recipe_list"
    const val NOTEBOOK_LIST = "notebook_list"
    const val SEARCH_FIELD = "search_field"
    const val FIELD_COMMENT = "field_comment"
    const val FIELD_MINUTES = "field_minutes"
    const val BUTTON_SAVE_NOTE = "button_save_note"
    const val BUTTON_TOGGLE_SAVED = "button_toggle_saved"
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String = stringResource(R.string.loading)) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTags.LOADING)
            // O leitor de tela anuncia a mensagem quando ela aparece.
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    CenteredMessage(
        icon = icon,
        title = title,
        message = message,
        modifier = modifier.testTag(TestTags.EMPTY),
    ) {
        if (actionLabel != null && onAction != null) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredMessage(
        icon = Icons.Outlined.ErrorOutline,
        title = stringResource(R.string.error_title),
        message = message,
        modifier = modifier
            .testTag(TestTags.ERROR)
            .semantics { liveRegion = LiveRegionMode.Polite },
        iconTint = MaterialTheme.colorScheme.error,
    ) {
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun CenteredMessage(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    action: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(16.dp))
        action()
    }
}

/** Faixa informando que os dados exibidos vieram do cache por falta de conexao. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.OFFLINE_BANNER),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.offline_banner),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
