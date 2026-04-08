package com.app.community.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tipografia epigrafica inspirada en inscripciones griegas.
 * Letter-spacing amplio en labels evoca el espaciado deliberado
 * de las mayusculas inscritas en piedra.
 */
val AgoraTypography: Typography
    get() {
        val base = Typography()
        return Typography(
            displayLarge = base.displayLarge.copy(
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.5).sp,
            ),
            displayMedium = base.displayMedium.copy(
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.25).sp,
            ),
            displaySmall = base.displaySmall.copy(
                fontWeight = FontWeight.Light,
            ),
            headlineLarge = base.headlineLarge.copy(
                fontWeight = FontWeight.Normal,
                letterSpacing = (-0.25).sp,
            ),
            headlineMedium = base.headlineMedium.copy(
                fontWeight = FontWeight.Normal,
            ),
            headlineSmall = base.headlineSmall.copy(
                fontWeight = FontWeight.Normal,
            ),
            titleLarge = base.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.15.sp,
            ),
            titleMedium = base.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.1.sp,
            ),
            titleSmall = base.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            bodyLarge = base.bodyLarge,
            bodyMedium = base.bodyMedium,
            bodySmall = base.bodySmall,
            labelLarge = base.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            ),
            labelMedium = base.labelMedium.copy(
                letterSpacing = 0.4.sp,
            ),
            labelSmall = base.labelSmall.copy(
                letterSpacing = 0.5.sp,
            ),
        )
    }
