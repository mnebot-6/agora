package com.app.community.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colores extendidos inspirados en pigmentos arquitectonicos griegos
 * que no encajan en los slots fijos de Material3.
 */
@Immutable
data class AgoraExtendedColors(
    val malachiteGreen: Color,
    val malachiteGreenContainer: Color,
    val onMalachiteGreen: Color,
    val columnStone: Color,
    val goldLeaf: Color,
    val onGoldLeaf: Color,
    val parchment: Color,
    val onParchment: Color,
    val friezeBandTint: Color,
)

val LightAgoraExtendedColors = AgoraExtendedColors(
    malachiteGreen = Color(0xFF2E6B3A),
    malachiteGreenContainer = Color(0xFFD0F0D4),
    onMalachiteGreen = Color(0xFFFFFFFF),
    columnStone = Color(0xFFC8C2B8),
    goldLeaf = Color(0xFFC49008),
    onGoldLeaf = Color(0xFFFFFFFF),
    parchment = Color(0xFFF5EFDF),
    onParchment = Color(0xFF1A1814),
    friezeBandTint = Color(0x14A62D2D), // secondary 8% alpha
)

val DarkAgoraExtendedColors = AgoraExtendedColors(
    malachiteGreen = Color(0xFF8FD49B),
    malachiteGreenContainer = Color(0xFF1E4028),
    onMalachiteGreen = Color(0xFF0E2614),
    columnStone = Color(0xFF48453E),
    goldLeaf = Color(0xFFECCC6E),
    onGoldLeaf = Color(0xFF3A2800),
    parchment = Color(0xFF252420),
    onParchment = Color(0xFFE8E3DA),
    friezeBandTint = Color(0x14FFB8AE), // secondary 8% alpha
)

val LocalAgoraExtendedColors = staticCompositionLocalOf { LightAgoraExtendedColors }

val MaterialTheme.agoraColors: AgoraExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAgoraExtendedColors.current
