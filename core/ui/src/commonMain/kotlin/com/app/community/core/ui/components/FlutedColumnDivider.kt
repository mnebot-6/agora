package com.app.community.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.community.core.ui.theme.agoraColors

/**
 * Divisor de cinco lineas finas paralelas que evoca
 * las 24 estriaduras esbeltas de una columna jonica.
 * Mas delicado y refinado que el estriado dorico de 3 lineas.
 */
@Composable
fun FlutedColumnDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.agoraColors.marbleRelief,
    lineThickness: Dp = 0.5.dp,
    lineSpacing: Dp = 1.5.dp,
) {
    val lineCount = 5
    val totalHeight = lineThickness * lineCount + lineSpacing * (lineCount - 1)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight),
    ) {
        val lt = lineThickness.toPx()
        val ls = lineSpacing.toPx()
        val w = size.width

        for (i in 0 until lineCount) {
            val y = i * (lt + ls) + lt / 2f
            drawLine(color, Offset(0f, y), Offset(w, y), lt)
        }
    }
}
