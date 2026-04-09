package com.app.community.core.ui.theme

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Formas personalizadas inspiradas en elementos arquitectonicos griegos.
 */

/** Tableta de piedra: rectangulo casi perfecto con minima suavidad */
val StoneTabletShape = RoundedCornerShape(2.dp)

/** Base de columna: parte superior estrecha, base mas ancha */
val ColumnBaseShape = RoundedCornerShape(
    topStart = 2.dp,
    topEnd = 2.dp,
    bottomStart = 6.dp,
    bottomEnd = 6.dp,
)

/** Borde superior de la barra de navegacion: evoca la base del estilobato */
val NavBarShape = RoundedCornerShape(
    topStart = 8.dp,
    topEnd = 8.dp,
    bottomStart = 0.dp,
    bottomEnd = 0.dp,
)

/**
 * Forma triangular de fronton griego.
 * Dibuja un triangulo isoceles: base abajo, vertice arriba.
 */
val PedimentShape = GenericShape { size, _ ->
    moveTo(0f, size.height)
    lineTo(size.width / 2f, 0f)
    lineTo(size.width, size.height)
    close()
}
