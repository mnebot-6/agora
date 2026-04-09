package com.app.community.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Marco decorativo con patron de greca/meandro griego
 * en los 4 bordes. Envuelve su contenido con un margen
 * para el patron.
 */
@Composable
fun GreekFrame(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    strokeWidth: Dp = 1.dp,
    patternSize: Dp = 8.dp,
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
            val ps = patternSize.toPx()
            val fp = framePadding.toPx()
            val w = size.width
            val h = size.height

            val margin = fp / 2f

            // Outer rectangle
            drawLine(borderColor, Offset(margin, margin), Offset(w - margin, margin), sw)
            drawLine(borderColor, Offset(w - margin, margin), Offset(w - margin, h - margin), sw)
            drawLine(borderColor, Offset(w - margin, h - margin), Offset(margin, h - margin), sw)
            drawLine(borderColor, Offset(margin, h - margin), Offset(margin, margin), sw)

            // Inner rectangle
            val inner = margin + ps
            drawLine(borderColor, Offset(inner, inner), Offset(w - inner, inner), sw)
            drawLine(borderColor, Offset(w - inner, inner), Offset(w - inner, h - inner), sw)
            drawLine(borderColor, Offset(w - inner, h - inner), Offset(inner, h - inner), sw)
            drawLine(borderColor, Offset(inner, h - inner), Offset(inner, inner), sw)

            // Corner meander connections (small L-shapes at each corner)
            val mid = (margin + inner) / 2f
            // Top-left corner
            drawLine(borderColor, Offset(margin, mid), Offset(inner, mid), sw)
            drawLine(borderColor, Offset(mid, margin), Offset(mid, inner), sw)
            // Top-right corner
            drawLine(borderColor, Offset(w - inner, mid), Offset(w - margin, mid), sw)
            drawLine(borderColor, Offset(w - mid, margin), Offset(w - mid, inner), sw)
            // Bottom-left corner
            drawLine(borderColor, Offset(margin, h - mid), Offset(inner, h - mid), sw)
            drawLine(borderColor, Offset(mid, h - inner), Offset(mid, h - margin), sw)
            // Bottom-right corner
            drawLine(borderColor, Offset(w - inner, h - mid), Offset(w - margin, h - mid), sw)
            drawLine(borderColor, Offset(w - mid, h - inner), Offset(w - mid, h - margin), sw)
        }
    }
}
