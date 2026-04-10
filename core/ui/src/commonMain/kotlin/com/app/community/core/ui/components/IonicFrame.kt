package com.app.community.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Marco decorativo del orden jonico: borde redondeado (8dp)
 * con pequenos ornamentos de voluta en las esquinas.
 * Mas elegante que el marco de meandro dorico.
 */
@Composable
fun IonicFrame(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    strokeWidth: Dp = 1.dp,
    cornerSize: Dp = 8.dp,
    framePadding: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        // Content with padding for frame
        Box(modifier = Modifier.padding(framePadding)) {
            content()
        }
        // Draw frame overlay
        Canvas(modifier = Modifier.matchParentSize()) {
            val sw = strokeWidth.toPx()
            val cs = cornerSize.toPx()
            val fp = framePadding.toPx()
            val w = size.width
            val h = size.height

            val margin = fp / 2f
            val radius = CornerRadius(cs, cs)

            // Borde redondeado principal
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(margin, margin),
                size = Size(w - margin * 2, h - margin * 2),
                cornerRadius = radius,
                style = Stroke(width = sw),
            )

            // Ornamentos de voluta en las esquinas — pequeñas curvas en espiral
            val voluteSize = cs * 1.2f
            val voluteStroke = Stroke(
                width = sw * 0.8f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )

            // Esquina superior izquierda
            val tlPath = Path().apply {
                moveTo(margin + cs, margin + voluteSize * 0.3f)
                cubicTo(
                    margin + cs * 0.4f, margin + voluteSize * 0.1f,
                    margin + voluteSize * 0.1f, margin + cs * 0.4f,
                    margin + voluteSize * 0.3f, margin + cs,
                )
            }
            drawPath(tlPath, borderColor, style = voluteStroke)

            // Esquina superior derecha
            val trPath = Path().apply {
                moveTo(w - margin - cs, margin + voluteSize * 0.3f)
                cubicTo(
                    w - margin - cs * 0.4f, margin + voluteSize * 0.1f,
                    w - margin - voluteSize * 0.1f, margin + cs * 0.4f,
                    w - margin - voluteSize * 0.3f, margin + cs,
                )
            }
            drawPath(trPath, borderColor, style = voluteStroke)

            // Esquina inferior izquierda
            val blPath = Path().apply {
                moveTo(margin + cs, h - margin - voluteSize * 0.3f)
                cubicTo(
                    margin + cs * 0.4f, h - margin - voluteSize * 0.1f,
                    margin + voluteSize * 0.1f, h - margin - cs * 0.4f,
                    margin + voluteSize * 0.3f, h - margin - cs,
                )
            }
            drawPath(blPath, borderColor, style = voluteStroke)

            // Esquina inferior derecha
            val brPath = Path().apply {
                moveTo(w - margin - cs, h - margin - voluteSize * 0.3f)
                cubicTo(
                    w - margin - cs * 0.4f, h - margin - voluteSize * 0.1f,
                    w - margin - voluteSize * 0.1f, h - margin - cs * 0.4f,
                    w - margin - voluteSize * 0.3f, h - margin - cs,
                )
            }
            drawPath(brPath, borderColor, style = voluteStroke)
        }
    }
}
