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

// Light mode slot colors — policromia arquitectonica griega
val LightSlotStatusColors = SlotStatusColors(
    available = SlotColorPair(
        container = Color(0xFFD0F0D4),  // Malaquita wash
        content = Color(0xFF2E6B3A),    // Verde malaquita
    ),
    reservedByMe = SlotColorPair(
        container = Color(0xFFCEDEF5),  // Azul Egeo wash
        content = Color(0xFF1A4D8C),    // Azul Egeo
    ),
    reservedByOther = SlotColorPair(
        container = Color(0xFFEDE7DC),  // Caliza erosionada
        content = Color(0xFF78746E),    // Gris piedra
    ),
    paid = SlotColorPair(
        container = Color(0xFFFFEDB8),  // Ocre dorado claro
        content = Color(0xFFC49008),    // Ocre dorado
    ),
)

// Dark mode slot colors — templo a la luz de antorchas
val DarkSlotStatusColors = SlotStatusColors(
    available = SlotColorPair(
        container = Color(0xFF1E4028),  // Malaquita profundo
        content = Color(0xFF8FD49B),    // Malaquita brillante
    ),
    reservedByMe = SlotColorPair(
        container = Color(0xFF103F74),  // Azul Egeo nocturno
        content = Color(0xFFA8CCFE),    // Azul Egeo suave
    ),
    reservedByOther = SlotColorPair(
        container = Color(0xFF48453E),  // Piedra oscura
        content = Color(0xFFC8C2B8),    // Piedra clara
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
