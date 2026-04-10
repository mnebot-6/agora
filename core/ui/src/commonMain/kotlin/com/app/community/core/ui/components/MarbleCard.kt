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
import com.app.community.core.ui.theme.MarblePanelShape

/**
 * Card con estetica de panel de marmol jonico: bordes suavemente
 * redondeados (8dp), borde fino, elevacion sutil.
 * Opcionalmente lleva una moldura denticular en la parte superior.
 */
@Composable
fun MarbleCard(
    modifier: Modifier = Modifier,
    elevation: Dp = AgoraElevation.subtle,
    showDentilBorder: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    dentilBorderColor: Color = MaterialTheme.colorScheme.outlineVariant,
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
            shape = MarblePanelShape,
            colors = cardColors,
            elevation = cardElevation,
            border = border,
        ) {
            CardContent(showDentilBorder, dentilBorderColor, content)
        }
    } else {
        OutlinedCard(
            modifier = cardModifier,
            shape = MarblePanelShape,
            colors = cardColors,
            elevation = cardElevation,
            border = border,
        ) {
            CardContent(showDentilBorder, dentilBorderColor, content)
        }
    }
}

@Composable
private fun ColumnScope.CardContent(
    showDentilBorder: Boolean,
    dentilBorderColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (showDentilBorder) {
        DentilDivider(color = dentilBorderColor, blockWidth = 4.dp, patternHeight = 6.dp)
    }
    Column { content() }
}
