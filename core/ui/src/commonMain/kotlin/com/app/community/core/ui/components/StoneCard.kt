package com.app.community.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.community.core.ui.theme.AgoraElevation
import com.app.community.core.ui.theme.StoneTabletShape

/**
 * Card con estetica de tableta de piedra: esquinas casi rectas,
 * borde fino de piedra, elevacion moderada.
 * Opcionalmente lleva una greca en la parte superior.
 */
@Composable
fun StoneCard(
    modifier: Modifier = Modifier,
    elevation: Dp = AgoraElevation.subtle,
    showGreekBorder: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    greekBorderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardModifier = modifier.fillMaxWidth()

    val cardColors = CardDefaults.outlinedCardColors(containerColor = containerColor)
    val cardElevation = CardDefaults.outlinedCardElevation(defaultElevation = elevation)
    val border = BorderStroke(1.dp, borderColor)

    if (onClick != null) {
        OutlinedCard(
            onClick = onClick,
            modifier = cardModifier,
            shape = StoneTabletShape,
            colors = cardColors,
            elevation = cardElevation,
            border = border,
        ) {
            CardContent(showGreekBorder, greekBorderColor, content)
        }
    } else {
        OutlinedCard(
            modifier = cardModifier,
            shape = StoneTabletShape,
            colors = cardColors,
            elevation = cardElevation,
            border = border,
        ) {
            CardContent(showGreekBorder, greekBorderColor, content)
        }
    }
}

@Composable
private fun ColumnScope.CardContent(
    showGreekBorder: Boolean,
    greekBorderColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (showGreekBorder) {
        GreekKeyDivider(color = greekBorderColor, strokeWidth = 1.dp, patternHeight = 8.dp)
    }
    Column { content() }
}
