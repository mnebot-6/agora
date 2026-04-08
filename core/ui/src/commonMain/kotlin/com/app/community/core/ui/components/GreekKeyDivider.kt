package com.app.community.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Divisor decorativo con patron de greca/meandro griego.
 * Dibuja el clasico patron escalonado que se encuentra en
 * frisos y bordes de la arquitectura griega antigua.
 */
@Composable
fun GreekKeyDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    strokeWidth: Dp = 1.dp,
    patternHeight: Dp = 10.dp,
) {
    val totalHeight = patternHeight + strokeWidth * 2
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .padding(vertical = 2.dp),
    ) {
        val sw = strokeWidth.toPx()
        val ph = patternHeight.toPx()
        val unitWidth = ph * 2f
        val width = size.width

        val midY = size.height / 2f
        val top = midY - ph / 2f
        val bottom = midY + ph / 2f

        // How many full pattern units fit
        val unitCount = (width / unitWidth).toInt()
        val totalPatternWidth = unitCount * unitWidth
        val startX = (width - totalPatternWidth) / 2f

        // Draw the meander pattern: each unit is a squared spiral step
        for (i in 0 until unitCount) {
            val x = startX + i * unitWidth
            val halfUnit = unitWidth / 2f
            val quarter = unitWidth / 4f

            // Top horizontal line across full unit
            drawLine(color, Offset(x, top), Offset(x + unitWidth, top), sw, StrokeCap.Butt)

            // Right side: vertical down from top
            drawLine(color, Offset(x + unitWidth, top), Offset(x + unitWidth, bottom), sw, StrokeCap.Butt)

            // Bottom horizontal back to midpoint
            drawLine(color, Offset(x + unitWidth, bottom), Offset(x + halfUnit, bottom), sw, StrokeCap.Butt)

            // Vertical up to center
            drawLine(color, Offset(x + halfUnit, bottom), Offset(x + halfUnit, midY), sw, StrokeCap.Butt)

            // Horizontal step inward
            drawLine(color, Offset(x + halfUnit, midY), Offset(x + quarter, midY), sw, StrokeCap.Butt)

            // Vertical down from center to near bottom
            drawLine(color, Offset(x + quarter, midY), Offset(x + quarter, bottom - quarter), sw, StrokeCap.Butt)

            // Connect bottom left
            drawLine(color, Offset(x + quarter, bottom - quarter), Offset(x, bottom - quarter), sw, StrokeCap.Butt)

            // Left side vertical up
            drawLine(color, Offset(x, bottom - quarter), Offset(x, top), sw, StrokeCap.Butt)
        }
    }
}
