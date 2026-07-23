package com.app.community.core.ui.theme

import agora.core.ui.generated.resources.Res
import agora.core.ui.generated.resources.cinzel
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    get() {
        // OJO: el FontFamily debe recordarse. En web (wasm) las fuentes de
        // compose-resources se cargan de forma ASINCRONA, asi que construir un
        // FontFamily nuevo en cada recomposicion descarta la fuente ya resuelta
        // y reinicia la carga; mientras tanto el texto se pinta vacio. Se nota
        // sobre todo tras cambiar tema o idioma, que recomponen todo el arbol.
        // En Android no daba la cara porque alli la resolucion es sincrona.
        val normal = Font(Res.font.cinzel, FontWeight.Normal)
        val medium = Font(Res.font.cinzel, FontWeight.Medium)
        val semiBold = Font(Res.font.cinzel, FontWeight.SemiBold)
        val bold = Font(Res.font.cinzel, FontWeight.Bold)
        val black = Font(Res.font.cinzel, FontWeight.Black)
        return remember(normal, medium, semiBold, bold, black) {
            FontFamily(normal, medium, semiBold, bold, black)
        }
    }

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
