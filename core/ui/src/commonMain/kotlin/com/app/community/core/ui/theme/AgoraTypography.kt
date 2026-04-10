package com.app.community.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tipografia del orden jonico — elegante y refinada.
 *
 * Headlines con peso ligero y spacing sutil evocan inscripciones
 * en marmol pario pulido, no piedra tallada a cincel.
 * Titles con peso medio para equilibrio entre elegancia y legibilidad.
 * Body con line-height generoso como rollos de papiro.
 * Labels refinados, no pesados.
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
            // Headlines — inscripcion elegante en friso continuo jonico
            headlineLarge = base.headlineLarge.copy(
                fontWeight = FontWeight.W300,
                letterSpacing = 0.4.sp,
            ),
            headlineMedium = base.headlineMedium.copy(
                fontWeight = FontWeight.W300,
                letterSpacing = 0.25.sp,
            ),
            headlineSmall = base.headlineSmall.copy(
                fontWeight = FontWeight.W300,
                letterSpacing = 0.15.sp,
            ),
            // Titles — seccion de marmol pulido
            titleLarge = base.titleLarge.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.25.sp,
            ),
            titleMedium = base.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.15.sp,
            ),
            titleSmall = base.titleSmall.copy(
                fontWeight = FontWeight.Medium,
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
            // Labels — marcas refinadas, no pesadas
            labelLarge = base.labelLarge.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            ),
            labelMedium = base.labelMedium.copy(
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.4.sp,
            ),
            labelSmall = base.labelSmall.copy(
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp,
            ),
        )
    }
