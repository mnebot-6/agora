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

// Dark mode slot colors — templo jonico a la luz de la luna
val DarkSlotStatusColors = SlotStatusColors(
    available = SlotColorPair(
        container = Color(0xFF1E4028),  // Malaquita profundo
        content = Color(0xFF8FD49B),    // Malaquita brillante
    ),
    reservedByMe = SlotColorPair(
        container = Color(0xFF0A5C5A),  // Teal Egeo nocturno
        content = Color(0xFF8ED4D2),    // Teal Egeo suave
    ),
    reservedByOther = SlotColorPair(
        container = Color(0xFF48453E),  // Piedra oscura
        content = Color(0xFFC8C4BC),    // Piedra clara
    ),
    paid = SlotColorPair(
        container = Color(0xFF654F00),  // Oro brunido profundo
        content = Color(0xFFECCC6E),    // Oro brillante
    ),
)

val LocalSlotStatusColors = staticCompositionLocalOf { LightSlotStatusColors }

val MaterialTheme.slotStatusColors: SlotStatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSlotStatusColors.current
