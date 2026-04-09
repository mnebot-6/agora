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
 * Divisor de tres lineas paralelas que evoca
 * las estriaduras de una columna dorica.
 */
@Composable
fun ColumnDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.agoraColors.columnStone,
    lineThickness: Dp = 1.dp,
    lineSpacing: Dp = 2.dp,
) {
    val totalHeight = lineThickness * 3 + lineSpacing * 2
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight),
    ) {
        val lt = lineThickness.toPx()
        val ls = lineSpacing.toPx()
        val w = size.width

        val y1 = lt / 2f
        val y2 = lt + ls + lt / 2f
        val y3 = (lt + ls) * 2 + lt / 2f

        drawLine(color, Offset(0f, y1), Offset(w, y1), lt)
        drawLine(color, Offset(0f, y2), Offset(w, y2), lt)
        drawLine(color, Offset(0f, y3), Offset(w, y3), lt)
    }
}
