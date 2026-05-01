package com.app.community.core.ui.theme

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/**
 * Formas personalizadas inspiradas en elementos del orden jonico griego.
 */

/** Panel de marmol: bordes suavemente redondeados, elegante y pulido */
val MarblePanelShape = RoundedCornerShape(8.dp)

/** Capitel jonico: esquinas superiores amplias (voluta), base mas recta */
val IonicCapitalShape = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 16.dp,
    bottomStart = 4.dp,
    bottomEnd = 4.dp,
)

/** Barra de navegacion: borde recto, sin redondeo. La sombra elevada provee la separacion visual. */
val NavBarShape = RectangleShape

/**
 * Capitel jonico miniatura: trapecio invertido (base ancha abajo, tapa estrecha arriba).
 * Usado como indicador de tab seleccionada en la barra de navegacion inferior.
 */
val IonicCapitalIndicatorShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val inset = w * 0.15f
    moveTo(inset, 0f)              // top-left (estrecho)
    lineTo(w - inset, 0f)          // top-right (estrecho)
    lineTo(w, h)                   // bottom-right (ancho)
    lineTo(0f, h)                  // bottom-left (ancho)
    close()
}

/**
 * Par de volutas jonicas estilizadas.
 * Dibuja dos curvas en espiral simetricas que se abren desde el centro,
 * el motivo definitorio del capitel jonico.
 */
val VoluteShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val midX = w / 2f
    val midY = h / 2f

    // Voluta izquierda: curva S que se enrolla hacia la izquierda
    moveTo(midX, midY)
    cubicTo(
        midX - w * 0.15f, midY - h * 0.4f,
        w * 0.05f, midY - h * 0.2f,
        w * 0.1f, midY + h * 0.1f,
    )
    cubicTo(
        w * 0.15f, midY + h * 0.35f,
        midX - w * 0.1f, midY + h * 0.2f,
        midX - w * 0.05f, midY,
    )

    // Voluta derecha: curva S que se enrolla hacia la derecha
    moveTo(midX, midY)
    cubicTo(
        midX + w * 0.15f, midY - h * 0.4f,
        w * 0.95f, midY - h * 0.2f,
        w * 0.9f, midY + h * 0.1f,
    )
    cubicTo(
        w * 0.85f, midY + h * 0.35f,
        midX + w * 0.1f, midY + h * 0.2f,
        midX + w * 0.05f, midY,
    )
}
