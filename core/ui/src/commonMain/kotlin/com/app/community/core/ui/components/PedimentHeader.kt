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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Encabezado decorativo con lineas de fronton triangular
 * dibujadas sobre el titulo — evoca la fachada de un templo griego.
 */
@Composable
fun PedimentHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showPediment: Boolean = true,
    pedimentColor: Color = MaterialTheme.colorScheme.tertiary,
    titleColor: Color = MaterialTheme.colorScheme.primary,
    pedimentHeight: Dp = 10.dp,
    pedimentStrokeWidth: Dp = 1.5.dp,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showPediment) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(pedimentHeight),
            ) {
                val sw = pedimentStrokeWidth.toPx()
                val midX = size.width / 2f
                // Triangulo de fronton
                drawLine(
                    pedimentColor, Offset(0f, size.height), Offset(midX, 0f),
                    sw, StrokeCap.Round,
                )
                drawLine(
                    pedimentColor, Offset(midX, 0f), Offset(size.width, size.height),
                    sw, StrokeCap.Round,
                )
                // Base del fronton
                drawLine(
                    pedimentColor, Offset(0f, size.height), Offset(size.width, size.height),
                    sw, StrokeCap.Round,
                )
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
