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

// --- Agora Dark Palette — Templo jonico a la luz de la luna ---
private val AgoraDarkScheme = darkColorScheme(
    primary = Color(0xFF8ED4D2),             // Teal Egeo suave
    onPrimary = Color(0xFF00403E),
    primaryContainer = Color(0xFF0A5C5A),    // Teal templo nocturno
    onPrimaryContainer = Color(0xFFCCECEB),
    secondary = Color(0xFFBCD4C6),           // Patina suave
    onSecondary = Color(0xFF263D30),
    secondaryContainer = Color(0xFF3C5546),  // Bronce en sombra
    onSecondaryContainer = Color(0xFFD8EDDF),
    tertiary = Color(0xFFECCC6E),            // Oro a la luz de la luna
    onTertiary = Color(0xFF453000),
    tertiaryContainer = Color(0xFF654F00),   // Oro brunido profundo
    onTertiaryContainer = Color(0xFFFFF2D6),
    background = Color(0xFF131312),          // Obsidiana
    onBackground = Color(0xFFE6E3DC),
    surface = Color(0xFF1B1A18),             // Piedra oscura
    onSurface = Color(0xFFE6E3DC),
    surfaceVariant = Color(0xFF48463F),      // Piedra oscura erosionada
    onSurfaceVariant = Color(0xFFC8C4BC),
    surfaceContainerLowest = Color(0xFF0E0D0B),
    surfaceContainerLow = Color(0xFF1B1A18),
    surfaceContainer = Color(0xFF201F1C),
    surfaceContainerHigh = Color(0xFF2B2A26),
    surfaceContainerHighest = Color(0xFF363530),
    outline = Color(0xFF949088),             // Marmol en penumbra
    outlineVariant = Color(0xFF48463F),
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
