package com.app.community.core.ui.theme

import agora.core.ui.generated.resources.Res
import agora.core.ui.generated.resources.cinzel
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font

/**
 * Tipografia del orden jonico — elegante y refinada.
 *
 * Cinzel para display, headlines y titles: capitulares clasicas
 * inspiradas en inscripciones romanas, con serifas finas y proporciones
 * que evocan la epigrafia en marmol de los templos jonicos.
 *
 * Body y labels con la fuente por defecto del sistema para maxima
 * legibilidad en texto corrido.
 */
val CinzelFamily: FontFamily
    @Composable
    get() = FontFamily(
        Font(Res.font.cinzel, FontWeight.Normal),
        Font(Res.font.cinzel, FontWeight.Medium),
        Font(Res.font.cinzel, FontWeight.SemiBold),
        Font(Res.font.cinzel, FontWeight.Bold),
        Font(Res.font.cinzel, FontWeight.Black),
    )

val AgoraTypography: Typography
    @Composable
    get() {
        val cinzel = CinzelFamily
        val base = Typography()
        return Typography(
            // Display — titulos monumentales, como el nombre del templo
            displayLarge = base.displayLarge.copy(
                fontFamily = cinzel,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp,
            ),
            displayMedium = base.displayMedium.copy(
                fontFamily = cinzel,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.25.sp,
            ),
            displaySmall = base.displaySmall.copy(
                fontFamily = cinzel,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
            ),
            // Headlines — inscripcion elegante en friso continuo jonico
            headlineLarge = base.headlineLarge.copy(
                fontFamily = cinzel,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.4.sp,
            ),
            headlineMedium = base.headlineMedium.copy(
                fontFamily = cinzel,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.25.sp,
            ),
            headlineSmall = base.headlineSmall.copy(
                fontFamily = cinzel,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.15.sp,
            ),
            // Titles — seccion de marmol pulido
            titleLarge = base.titleLarge.copy(
                fontFamily = cinzel,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.25.sp,
            ),
            titleMedium = base.titleMedium.copy(
                fontFamily = cinzel,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.15.sp,
            ),
            titleSmall = base.titleSmall.copy(
                fontFamily = cinzel,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.1.sp,
            ),
            // Body — papiro legible (fuente del sistema)
            bodyLarge = base.bodyLarge.copy(
                lineHeight = 24.sp,
            ),
            bodyMedium = base.bodyMedium.copy(
                lineHeight = 22.sp,
            ),
            bodySmall = base.bodySmall.copy(
                lineHeight = 18.sp,
            ),
            // Labels — marcas refinadas, no pesadas (fuente del sistema)
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
