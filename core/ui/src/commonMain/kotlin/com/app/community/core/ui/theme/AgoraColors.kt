package com.app.community.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class SlotColorPair(
    val container: Color,
    val content: Color,
)

@Immutable
data class SlotStatusColors(
    val available: SlotColorPair,
    val reservedByMe: SlotColorPair,
    val reservedByOther: SlotColorPair,
    val paid: SlotColorPair,
)

// Light mode slot colors — orden jonico refinado
val LightSlotStatusColors = SlotStatusColors(
    available = SlotColorPair(
        container = Color(0xFFD0F0D4),  // Malaquita wash
        content = Color(0xFF2E6B3A),    // Verde malaquita
    ),
    reservedByMe = SlotColorPair(
        container = Color(0xFFCCECEB),  // Teal Egeo wash
        content = Color(0xFF1A7D7A),    // Teal Egeo
    ),
    reservedByOther = SlotColorPair(
        container = Color(0xFFEDE9E2),  // Caliza relieve
        content = Color(0xFF7A7770),    // Marmol erosionado
    ),
    paid = SlotColorPair(
        container = Color(0xFFFFF2D6),  // Oro palido
        content = Color(0xFFD4A855),    // Oro refinado
    ),
)

// Dark mode slot colors — Atenas a medianoche, sincronizado con paleta calida
val DarkSlotStatusColors = SlotStatusColors(
    available = SlotColorPair(
        container = Color(0xFF1F4029),  // Malaquita lunar profunda
        content = Color(0xFF8FCEA0),    // Malaquita lunar brillante
    ),
    reservedByMe = SlotColorPair(
        container = Color(0xFF0E5856),  // Templo nocturno teal
        content = Color(0xFF7DC9C5),    // Teal Egeo sereno
    ),
    reservedByOther = SlotColorPair(
        container = Color(0xFF3D352B),  // Caliza nocturna calida
        content = Color(0xFFCBC0B0),    // Caliza clara calida
    ),
    paid = SlotColorPair(
        container = Color(0xFF5A4520),  // Oro brunido bronce
        content = Color(0xFFD9B96B),    // Oro bronce
    ),
)

val LocalSlotStatusColors = staticCompositionLocalOf { LightSlotStatusColors }

val MaterialTheme.slotStatusColors: SlotStatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSlotStatusColors.current
