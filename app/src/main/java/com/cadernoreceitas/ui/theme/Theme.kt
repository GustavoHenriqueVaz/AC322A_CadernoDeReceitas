package com.cadernoreceitas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Paleta propria (tons de terracota) com contraste verificado:
 * o teste `ColorContrastTest` garante >= 4,5:1 (WCAG AA) em todos os pares texto/fundo.
 */

internal val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF9C3F1B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCD),
    onPrimaryContainer = Color(0xFF380D00),
    secondary = Color(0xFF765849),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE8DF),
    onSecondaryContainer = Color(0xFF2B160C),
    tertiary = Color(0xFF5F6219),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE4E791),
    onTertiaryContainer = Color(0xFF1B1D00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF5DED4),
    onSurfaceVariant = Color(0xFF52443C),
    outline = Color(0xFF7A675D),
    outlineVariant = Color(0xFFD8C2B8),
)

internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB59A),
    onPrimary = Color(0xFF5A1B00),
    primaryContainer = Color(0xFF7D2D07),
    onPrimaryContainer = Color(0xFFFFDBCD),
    secondary = Color(0xFFE6BEAC),
    onSecondary = Color(0xFF442A1E),
    secondaryContainer = Color(0xFF5C4033),
    onSecondaryContainer = Color(0xFFFFDBCD),
    tertiary = Color(0xFFC8CB77),
    onTertiary = Color(0xFF303200),
    tertiaryContainer = Color(0xFF474A00),
    onTertiaryContainer = Color(0xFFE4E791),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A120D),
    onBackground = Color(0xFFF0DFD7),
    surface = Color(0xFF1A120D),
    onSurface = Color(0xFFF0DFD7),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD8C2B8),
    outline = Color(0xFFA08D84),
    outlineVariant = Color(0xFF52443C),
)

@Composable
fun CadernoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
