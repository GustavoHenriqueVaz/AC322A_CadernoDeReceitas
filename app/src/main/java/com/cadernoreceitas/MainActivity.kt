package com.cadernoreceitas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cadernoreceitas.ui.navigation.CadernoNavigation
import com.cadernoreceitas.ui.theme.CadernoTheme
import com.cadernoreceitas.util.RecipeIds

/**
 * Unica Activity do app (single-activity): todas as telas sao composables
 * trocados pela Navigation 3.
 *
 * Aceita o deep link `cadernoreceitas://receita/{id}` (usado pela notificacao da
 * "Receita do dia"). Teste manual:
 *   adb shell am start -a android.intent.action.VIEW -d "cadernoreceitas://receita/52772"
 */
class MainActivity : ComponentActivity() {

    /** Deep link recebido com o app ja aberto (onNewIntent). */
    private var pendingDeepLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // So interpreta o intent na abertura "de verdade": ao recriar (rotacao) o back stack ja foi restaurado.
        val initialRecipeId = if (savedInstanceState == null) deepLinkRecipeId(intent) else null

        setContent {
            CadernoTheme {
                CadernoNavigation(
                    container = appContainer,
                    initialRecipeId = initialRecipeId,
                    pendingDeepLink = pendingDeepLink,
                    onDeepLinkConsumed = { pendingDeepLink = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkRecipeId(intent)?.let { pendingDeepLink = it }
    }

    /** Valida o deep link: so aceita o formato esperado e um id numerico. */
    private fun deepLinkRecipeId(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (intent.action != Intent.ACTION_VIEW) return null
        return RecipeIds.fromDeepLink(data.scheme, data.host, data.pathSegments)
    }
}
