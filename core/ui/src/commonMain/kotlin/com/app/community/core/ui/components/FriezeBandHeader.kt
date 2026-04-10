package com.app.community.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.community.core.ui.theme.agoraColors

/**
 * Banda horizontal de friso continuo — evoca las bandas
 * narrativas del friso jonico, una superficie continua
 * sin las interrupciones de triglifos del orden dorico.
 *
 * Fondo tintado con el color secundario (patina bronce),
 * bordes finos arriba y abajo, titulo en secondary.
 */
@Composable
fun FriezeBandHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    backgroundColor: Color = MaterialTheme.agoraColors.friezeBandTint,
    contentColor: Color = MaterialTheme.colorScheme.secondary,
    dividerColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = dividerColor)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f),
                    )
                }
            }
            trailingContent?.invoke()
        }
        HorizontalDivider(thickness = 1.dp, color = dividerColor)
    }
}
