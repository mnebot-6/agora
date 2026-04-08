package com.app.community.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// --- Agora Light Palette — Policromía arquitectónica griega ---
private val AgoraLightScheme = lightColorScheme(
    primary = Color(0xFF1B4F8A),            // Azul Egeo (azul egipcio de triglifos)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4E3F7),    // Azul fresco deslavado
    onPrimaryContainer = Color(0xFF002B5C),
    secondary = Color(0xFF9B2E2E),           // Rojo Cinabrio (frisos pintados)
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFADCDC),  // Terracota pálido
    onSecondaryContainer = Color(0xFF3E0707),
    tertiary = Color(0xFFB8860B),            // Ocre Dorado (capiteles/detalles)
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFF0C8),   // Caliza dorada
    onTertiaryContainer = Color(0xFF3A2800),
    background = Color(0xFFFBF7F0),          // Mármol pentélico
    onBackground = Color(0xFF1C1B17),        // Negro hueso
    surface = Color(0xFFFFFCF7),             // Mármol pulido
    onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFF0EBE2),      // Caliza erosionada
    onSurfaceVariant = Color(0xFF49463F),
    outline = Color(0xFF7A7670),             // Gris piedra erosionada
    outlineVariant = Color(0xFFCBC5BC),      // Borde de piedra claro
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// --- Agora Dark Palette — Templo a la luz de antorchas ---
private val AgoraDarkScheme = darkColorScheme(
    primary = Color(0xFFA4C8FA),             // Azul Egeo suave
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF0E3E72),    // Azul templo nocturno
    onPrimaryContainer = Color(0xFFD4E3F7),
    secondary = Color(0xFFFFB4A9),           // Cinabrio suave
    onSecondary = Color(0xFF5F1414),
    secondaryContainer = Color(0xFF7D2121),  // Friso en sombra
    onSecondaryContainer = Color(0xFFFADCDC),
    tertiary = Color(0xFFE8C86A),            // Oro a la luz de antorcha
    onTertiary = Color(0xFF453000),
    tertiaryContainer = Color(0xFF624E00),   // Oro bruñido profundo
    onTertiaryContainer = Color(0xFFFFF0C8),
    background = Color(0xFF141311),          // Obsidiana/basalto
    onBackground = Color(0xFFE7E2D9),
    surface = Color(0xFF1C1B17),             // Piedra oscura
    onSurface = Color(0xFFE7E2D9),
    surfaceVariant = Color(0xFF49463F),      // Piedra oscura erosionada
    onSurfaceVariant = Color(0xFFCBC5BC),
    outline = Color(0xFF948F87),             // Piedra en penumbra
    outlineVariant = Color(0xFF49463F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) AgoraDarkScheme else AgoraLightScheme
    val slotStatusColors = if (darkTheme) DarkSlotStatusColors else LightSlotStatusColors

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AgoraShapes,
        typography = AgoraTypography,
    ) {
        CompositionLocalProvider(
            LocalSlotStatusColors provides slotStatusColors,
            content = content,
        )
    }
}
