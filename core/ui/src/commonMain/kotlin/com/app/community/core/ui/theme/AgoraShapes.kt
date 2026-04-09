package com.app.community.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Formas inspiradas en la arquitectura griega antigua.
 * Radios afilados para evocar piedra tallada y tabletas de marmol.
 * Mas angular que el Material3 por defecto — la piedra no tiene
 * esquinas blandas.
 */
val AgoraShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)
