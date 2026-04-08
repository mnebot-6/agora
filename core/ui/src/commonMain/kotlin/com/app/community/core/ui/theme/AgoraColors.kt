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
        container = Color(0xFFD6F0D6),  // Malaquita wash
        content = Color(0xFF2E6B3A),    // Verde malaquita
    ),
    reservedByMe = SlotColorPair(
        container = Color(0xFFD4E3F7),  // Azul Egeo wash
        content = Color(0xFF1B4F8A),    // Azul Egeo
    ),
    reservedByOther = SlotColorPair(
        container = Color(0xFFF0EBE2),  // Caliza erosionada
        content = Color(0xFF7A7670),    // Gris piedra
    ),
    paid = SlotColorPair(
        container = Color(0xFFFFF0C8),  // Ocre dorado claro
        content = Color(0xFFB8860B),    // Ocre dorado
    ),
)

// Dark mode slot colors — templo a la luz de antorchas
val DarkSlotStatusColors = SlotStatusColors(
    available = SlotColorPair(
        container = Color(0xFF1E4028),  // Malaquita profundo
        content = Color(0xFF8FD49B),    // Malaquita brillante
    ),
    reservedByMe = SlotColorPair(
        container = Color(0xFF0E3E72),  // Azul Egeo nocturno
        content = Color(0xFFA4C8FA),    // Azul Egeo suave
    ),
    reservedByOther = SlotColorPair(
        container = Color(0xFF49463F),  // Piedra oscura
        content = Color(0xFFCBC5BC),    // Piedra clara
    ),
    paid = SlotColorPair(
        container = Color(0xFF624E00),  // Oro brunido profundo
        content = Color(0xFFE8C86A),    // Oro brillante
    ),
)

val LocalSlotStatusColors = staticCompositionLocalOf { LightSlotStatusColors }

val MaterialTheme.slotStatusColors: SlotStatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSlotStatusColors.current
