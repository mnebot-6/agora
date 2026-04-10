package com.app.community.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Encabezado decorativo con volutas jonicas estilizadas
 * flanqueando el titulo — evoca el capitel del orden jonico.
 *
 * Dos espirales simetricas se abren desde el centro,
 * conectadas por una linea horizontal sutil.
 */
@Composable
fun IonicVoluteHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showVolutes: Boolean = true,
    voluteColor: Color = MaterialTheme.colorScheme.tertiary,
    titleColor: Color = MaterialTheme.colorScheme.primary,
    voluteHeight: Dp = 20.dp,
    voluteStrokeWidth: Dp = 1.5.dp,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showVolutes) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(voluteHeight),
            ) {
                val sw = voluteStrokeWidth.toPx()
                val w = size.width
                val h = size.height
                val midX = w / 2f
                val midY = h * 0.5f

                val stroke = Stroke(
                    width = sw,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )

                // Linea horizontal central
                drawLine(
                    voluteColor,
                    Offset(w * 0.15f, midY),
                    Offset(w * 0.85f, midY),
                    sw,
                    StrokeCap.Round,
                )

                // Voluta izquierda — espiral que se enrolla hacia afuera
                val leftPath = Path().apply {
                    moveTo(midX - w * 0.05f, midY)
                    cubicTo(
                        midX - w * 0.12f, midY - h * 0.5f,
                        w * 0.08f, midY - h * 0.45f,
                        w * 0.1f, midY,
                    )
                    cubicTo(
                        w * 0.12f, midY + h * 0.35f,
                        w * 0.18f, midY + h * 0.25f,
                        w * 0.2f, midY + h * 0.05f,
                    )
                }
                drawPath(leftPath, voluteColor, style = stroke)

                // Voluta derecha — espiral simetrica
                val rightPath = Path().apply {
                    moveTo(midX + w * 0.05f, midY)
                    cubicTo(
                        midX + w * 0.12f, midY - h * 0.5f,
                        w * 0.92f, midY - h * 0.45f,
                        w * 0.9f, midY,
                    )
                    cubicTo(
                        w * 0.88f, midY + h * 0.35f,
                        w * 0.82f, midY + h * 0.25f,
                        w * 0.8f, midY + h * 0.05f,
                    )
                }
                drawPath(rightPath, voluteColor, style = stroke)
            }
            Spacer(Modifier.height(8.dp))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = titleColor,
            textAlign = TextAlign.Center,
        )

        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}
