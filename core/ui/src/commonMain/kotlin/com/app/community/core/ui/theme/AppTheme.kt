package com.app.community.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// --- Agora Light Palette — Policromia arquitectonica griega ---
private val AgoraLightScheme = lightColorScheme(
    primary = Color(0xFF1A4D8C),            // Azul Egipcio — triglifos
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCEDEF5),    // Azul fresco deslavado
    onPrimaryContainer = Color(0xFF002B5C),
    secondary = Color(0xFFA62D2D),           // Cinabrio vivo — frisos, bandas
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF8D3CF),  // Terracota calido
    onSecondaryContainer = Color(0xFF3E0707),
    tertiary = Color(0xFFC49008),            // Ocre dorado enriquecido — capiteles
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFEDB8),   // Caliza dorada rica
    onTertiaryContainer = Color(0xFF3A2800),
    background = Color(0xFFFAF6EE),          // Marmol pentelico calido
    onBackground = Color(0xFF1A1814),        // Negro vid
    surface = Color(0xFFFFFBF5),             // Marmol pulido
    onSurface = Color(0xFF1A1814),
    surfaceVariant = Color(0xFFEDE7DC),      // Caliza erosionada
    onSurfaceVariant = Color(0xFF49463F),
    surfaceContainerLowest = Color(0xFFFFFFFF),  // Yeso blanco
    surfaceContainerLow = Color(0xFFFDF8F1),
    surfaceContainer = Color(0xFFF7F2E9),        // Caliza
    surfaceContainerHigh = Color(0xFFF1ECE3),    // Piedra labrada
    surfaceContainerHighest = Color(0xFFEBE6DD), // Caliza profunda
    outline = Color(0xFF78746E),             // Gris piedra erosionada
    outlineVariant = Color(0xFFC8C2B8),      // Borde de piedra claro
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// --- Agora Dark Palette — Templo a la luz de antorchas ---
private val AgoraDarkScheme = darkColorScheme(
    primary = Color(0xFFA8CCFE),             // Azul Egeo suave
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF103F74),    // Azul templo nocturno
    onPrimaryContainer = Color(0xFFCEDEF5),
    secondary = Color(0xFFFFB8AE),           // Cinabrio suave
    onSecondary = Color(0xFF5F1414),
    secondaryContainer = Color(0xFF802222),  // Friso en sombra
    onSecondaryContainer = Color(0xFFF8D3CF),
    tertiary = Color(0xFFECCC6E),            // Oro a la luz de antorcha
    onTertiary = Color(0xFF453000),
    tertiaryContainer = Color(0xFF654F00),   // Oro brunido profundo
    onTertiaryContainer = Color(0xFFFFEDB8),
    background = Color(0xFF131210),          // Obsidiana/basalto
    onBackground = Color(0xFFE8E3DA),
    surface = Color(0xFF1B1A16),             // Piedra oscura
    onSurface = Color(0xFFE8E3DA),
    surfaceVariant = Color(0xFF49463F),      // Piedra oscura erosionada
    onSurfaceVariant = Color(0xFFC8C2B8),
    surfaceContainerLowest = Color(0xFF0E0D0B),
    surfaceContainerLow = Color(0xFF1B1A16),
    surfaceContainer = Color(0xFF201F1B),
    surfaceContainerHigh = Color(0xFF2B2A25),
    surfaceContainerHighest = Color(0xFF363530),
    outline = Color(0xFF948F87),             // Piedra en penumbra
    outlineVariant = Color(0xFF49463F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = false, // TODO: restaurar isSystemInDarkTheme() cuando el modo oscuro este listo
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
