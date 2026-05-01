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
    malachiteGreen = Color(0xFF8FCEA0),         // Malaquita lunar
    malachiteGreenContainer = Color(0xFF1F4029),
    onMalachiteGreen = Color(0xFF0E2614),
    marbleRelief = Color(0xFF433930),           // Caliza nocturna calida
    gildedVolute = Color(0xFFD9B96B),           // Oro bronce sincronizado con tertiary
    onGildedVolute = Color(0xFF3A2800),
    parchment = Color(0xFF221C16),              // Pergamino quemado, sincronizado con surface
    onParchment = Color(0xFFEFEAD9),
    friezeBandTint = Color(0x18C9B583),         // Frieze tint calido, mas visible que el 0x14 anterior
)

val LocalAgoraExtendedColors = staticCompositionLocalOf { LightAgoraExtendedColors }

val MaterialTheme.agoraColors: AgoraExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAgoraExtendedColors.current
