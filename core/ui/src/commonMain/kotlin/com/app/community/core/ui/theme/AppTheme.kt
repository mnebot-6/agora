package com.app.community.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// --- Agora Light Palette — Orden Jonico: elegancia y refinamiento ---
private val AgoraLightScheme = lightColorScheme(
    primary = Color(0xFF1A7D7A),            // Teal Egeo — mar jonico
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCECEB),    // Teal wash suave
    onPrimaryContainer = Color(0xFF002F2E),
    secondary = Color(0xFF5D7D6A),           // Bronce oxidado — patina
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8EDDF),  // Patina suave
    onSecondaryContainer = Color(0xFF1A2E22),
    tertiary = Color(0xFFD4A855),            // Oro refinado — volutas doradas
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFF2D6),   // Oro palido
    onTertiaryContainer = Color(0xFF3A2800),
    background = Color(0xFFFAF8F4),          // Marmol pario luminoso
    onBackground = Color(0xFF1A1A18),        // Negro profundo
    surface = Color(0xFFFFFCF8),             // Marfil pulido
    onSurface = Color(0xFF1A1A18),
    surfaceVariant = Color(0xFFEDE9E2),      // Caliza relieve
    onSurfaceVariant = Color(0xFF49463F),
    surfaceContainerLowest = Color(0xFFFFFFFF),  // Blanco puro
    surfaceContainerLow = Color(0xFFFEFAF4),
    surfaceContainer = Color(0xFFF8F4ED),        // Caliza suave
    surfaceContainerHigh = Color(0xFFF2EEE6),    // Marmol labrado
    surfaceContainerHighest = Color(0xFFECE8E0), // Caliza profunda
    outline = Color(0xFF7A7770),             // Marmol erosionado
    outlineVariant = Color(0xFFCCC8C0),      // Borde de marmol claro
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// --- Agora Dark Palette — "Atenas a medianoche": piedra calida bajo la luna ---
private val AgoraDarkScheme = darkColorScheme(
    primary = Color(0xFF7DC9C5),             // Teal Egeo sereno (menos brillante)
    onPrimary = Color(0xFF002F2D),
    primaryContainer = Color(0xFF0E5856),    // Templo nocturno teal
    onPrimaryContainer = Color(0xFFB8E6E3),
    secondary = Color(0xFFAFC9BB),           // Patina lunar
    onSecondary = Color(0xFF1F3025),
    secondaryContainer = Color(0xFF324638),
    onSecondaryContainer = Color(0xFFCCE2D3),
    tertiary = Color(0xFFD9B96B),            // Oro bronce, no neon
    onTertiary = Color(0xFF3A2800),
    tertiaryContainer = Color(0xFF5A4520),   // Oro brunido profundo
    onTertiaryContainer = Color(0xFFF8E6BD),
    background = Color(0xFF1A1612),          // Tierra calida nocturna (no negro)
    onBackground = Color(0xFFEFEAD9),        // Cream calido (no blanco)
    surface = Color(0xFF221C16),             // Piedra calida un tono arriba
    onSurface = Color(0xFFEFEAD9),
    surfaceVariant = Color(0xFF3D352B),      // Caliza erosionada nocturna
    onSurfaceVariant = Color(0xFFCBC0B0),
    surfaceContainerLowest = Color(0xFF120F0B),  // Casi sombra
    surfaceContainerLow = Color(0xFF1F1A14),
    surfaceContainer = Color(0xFF2A231B),        // Diferencia mas visible entre tonos
    surfaceContainerHigh = Color(0xFF35291F),
    surfaceContainerHighest = Color(0xFF403023),
    outline = Color(0xFF8C8275),             // Marmol en sombra calida
    outlineVariant = Color(0xFF433930),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) AgoraDarkScheme else AgoraLightScheme
    val slotStatusColors = if (darkTheme) DarkSlotStatusColors else LightSlotStatusColors
    val extendedColors = if (darkTheme) DarkAgoraExtendedColors else LightAgoraExtendedColors

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AgoraShapes,
        typography = AgoraTypography,
    ) {
        CompositionLocalProvider(
            LocalSlotStatusColors provides slotStatusColors,
            LocalAgoraExtendedColors provides extendedColors,
            content = content,
        )
    }
}
