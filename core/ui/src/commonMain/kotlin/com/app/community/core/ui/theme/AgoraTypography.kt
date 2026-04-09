package com.app.community.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tipografia epigrafica inspirada en inscripciones griegas.
 *
 * Headlines con letter-spacing positivo evocan las mayusculas
 * deliberadamente espaciadas inscritas en piedra pentelica.
 * Labels anchos como las marcas en ceramica atica.
 * Body con line-height generoso como rollos de papiro.
 */
val AgoraTypography: Typography
    get() {
        val base = Typography()
        return Typography(
            // Display — titulos monumentales, como el nombre del templo
            displayLarge = base.displayLarge.copy(
                fontWeight = FontWeight.W300,
                letterSpacing = 0.sp,
            ),
            displayMedium = base.displayMedium.copy(
                fontWeight = FontWeight.W300,
                letterSpacing = 0.sp,
            ),
            displaySmall = base.displaySmall.copy(
                fontWeight = FontWeight.W300,
                letterSpacing = 0.sp,
            ),
            // Headlines — inscripcion en el friso, spacing positivo
            headlineLarge = base.headlineLarge.copy(
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp,
            ),
            headlineMedium = base.headlineMedium.copy(
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.3.sp,
            ),
            headlineSmall = base.headlineSmall.copy(
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.2.sp,
            ),
            // Titles — nombre de seccion grabado en piedra
            titleLarge = base.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.25.sp,
            ),
            titleMedium = base.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.15.sp,
            ),
            titleSmall = base.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.1.sp,
            ),
            // Body — papiro legible
            bodyLarge = base.bodyLarge.copy(
                lineHeight = 24.sp,
            ),
            bodyMedium = base.bodyMedium.copy(
                lineHeight = 22.sp,
            ),
            bodySmall = base.bodySmall.copy(
                lineHeight = 18.sp,
            ),
            // Labels — marcas de ceramica, spacing amplio
            labelLarge = base.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            ),
            labelMedium = base.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            ),
            labelSmall = base.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
            ),
        )
    }
