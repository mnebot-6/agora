package com.app.community.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class DentilVariant { Single, Double }

/**
 * Divisor decorativo con moldura denticular jonica.
 * Dibuja una fila de pequenos bloques rectangulares equidistantes,
 * el ornamento caracteristico de la cornisa del orden jonico.
 */
@Composable
fun DentilDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    blockWidth: Dp = 6.dp,
    patternHeight: Dp = 8.dp,
    gapRatio: Float = 0.6f,
    variant: DentilVariant = DentilVariant.Single,
) {
    val rowCount = if (variant == DentilVariant.Double) 2 else 1
    val rowGap = if (rowCount > 1) 3.dp else 0.dp
    val totalHeight = patternHeight * rowCount + rowGap * (rowCount - 1)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .padding(vertical = 2.dp),
    ) {
        val bw = blockWidth.toPx()
        val ph = patternHeight.toPx()
        val gap = bw * gapRatio
        val unit = bw + gap
        val width = size.width
        val rowGapPx = rowGap.toPx()

        for (row in 0 until rowCount) {
            val rowOffset = row * (ph + rowGapPx)

            val blockCount = (width / unit).toInt()
            val totalPatternWidth = blockCount * unit - gap
            val startX = (width - totalPatternWidth) / 2f

            for (i in 0 until blockCount) {
                val x = startX + i * unit
                drawRect(
                    color = color,
                    topLeft = Offset(x, rowOffset),
                    size = Size(bw, ph),
                )
            }
        }
    }
}
