package com.app.community.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colores extendidos inspirados en el orden jonico griego:
 * patina de bronce, marmol pario, oro de volutas doradas.
 */
@Immutable
data class AgoraExtendedColors(
    val malachiteGreen: Color,
    val malachiteGreenContainer: Color,
    val onMalachiteGreen: Color,
    val marbleRelief: Color,
    val gildedVolute: Color,
    val onGildedVolute: Color,
    val parchment: Color,
    val onParchment: Color,
    val friezeBandTint: Color,
)

val LightAgoraExtendedColors = AgoraExtendedColors(
    malachiteGreen = Color(0xFF2E6B3A),
    malachiteGreenContainer = Color(0xFFD0F0D4),
    onMalachiteGreen = Color(0xFFFFFFFF),
    marbleRelief = Color(0xFFD6D2CA),
    gildedVolute = Color(0xFFD4A855),
    onGildedVolute = Color(0xFFFFFFFF),
    parchment = Color(0xFFF6F2EA),
    onParchment = Color(0xFF1A1A18),
    friezeBandTint = Color(0x145D7D6A), // secondary jonico 8% alpha
)

val DarkAgoraExtendedColors = AgoraExtendedColors(
    malachiteGreen = Color(0xFF8FD49B),
    malachiteGreenContainer = Color(0xFF1E4028),
    onMalachiteGreen = Color(0xFF0E2614),
    marbleRelief = Color(0xFF48453E),
    gildedVolute = Color(0xFFECCC6E),
    onGildedVolute = Color(0xFF3A2800),
    parchment = Color(0xFF252420),
    onParchment = Color(0xFFE6E3DC),
    friezeBandTint = Color(0x14BCD4C6), // secondary dark jonico 8% alpha
)

val LocalAgoraExtendedColors = staticCompositionLocalOf { LightAgoraExtendedColors }

val MaterialTheme.agoraColors: AgoraExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAgoraExtendedColors.current
