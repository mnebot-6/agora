package com.app.community.core.ui.theme

import agora.core.ui.generated.resources.Res
import agora.core.ui.generated.resources.cinzel
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * Carga Cinzel ANTES de montar la UI y la deja en [AgoraFonts].
 *
 * Motivo: en web `Font(Res.font.cinzel, ...)` es asincrono. Hasta que termina,
 * compose-resources devuelve una fuente vacia de relleno (metricas pero sin
 * glifos) y todo el texto en Cinzel se dibuja como cuadraditos. Peor: esa carga
 * vive en una corrutina atada a la composicion, asi que si el subarbol se
 * recrea a media carga la corrutina muere y la fuente se queda vacia para
 * siempre.
 *
 * Llamando a esto antes de `ComposeViewport` la fuente esta lista antes del
 * primer frame: se acaba la carrera y el placeholder no llega a usarse nunca.
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun preloadAgoraFonts() {
    val bytes = getFontResourceBytes(getSystemResourceEnvironment(), Res.font.cinzel)
    // El mismo archivo para todos los pesos, igual que la definicion original.
    AgoraFonts.preloadedCinzel = FontFamily(
        Font("cinzel", bytes, FontWeight.Normal, FontStyle.Normal),
        Font("cinzel", bytes, FontWeight.Medium, FontStyle.Normal),
        Font("cinzel", bytes, FontWeight.SemiBold, FontStyle.Normal),
        Font("cinzel", bytes, FontWeight.Bold, FontStyle.Normal),
        Font("cinzel", bytes, FontWeight.Black, FontStyle.Normal),
    )
}
