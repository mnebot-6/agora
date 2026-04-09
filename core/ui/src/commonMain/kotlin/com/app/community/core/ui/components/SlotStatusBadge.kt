package com.app.community.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.community.core.ui.theme.SlotColorPair

/**
 * Badge de estado de plaza con colores de la paleta de slots.
 * Forma de tableta de piedra con el par de colores container/content.
 */
@Composable
fun SlotStatusBadge(
    text: String,
    colorPair: SlotColorPair,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
) {
    Surface(
        modifier = modifier,
        color = colorPair.container,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            color = colorPair.content,
            style = if (isCompact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelLarge
            },
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                horizontal = if (isCompact) 8.dp else 12.dp,
                vertical = if (isCompact) 4.dp else 6.dp,
            ),
        )
    }
}
