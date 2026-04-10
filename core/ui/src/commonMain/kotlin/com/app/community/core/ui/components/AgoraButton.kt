package com.app.community.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

enum class AgoraButtonVariant { Primary, Secondary, Tertiary, Danger }

/**
 * Boton tematizado con variantes inspiradas en la paleta jonica.
 * Incluye estado de carga con spinner de curvas fluidas.
 */
@Composable
fun AgoraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AgoraButtonVariant = AgoraButtonVariant.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val isEnabled = enabled && !isLoading

    when (variant) {
        AgoraButtonVariant.Primary -> {
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = isEnabled,
                shape = MaterialTheme.shapes.small,
            ) {
                ButtonContent(text, isLoading, MaterialTheme.colorScheme.onPrimary)
            }
        }

        AgoraButtonVariant.Secondary -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = isEnabled,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                ButtonContent(text, isLoading, MaterialTheme.colorScheme.primary)
            }
        }

        AgoraButtonVariant.Tertiary -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = isEnabled,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.tertiary,
                ),
            ) {
                ButtonContent(text, isLoading, MaterialTheme.colorScheme.tertiary)
            }
        }

        AgoraButtonVariant.Danger -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = isEnabled,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                ButtonContent(text, isLoading, MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    isLoading: Boolean,
    indicatorColor: androidx.compose.ui.graphics.Color,
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.height(20.dp).padding(end = 4.dp),
            strokeWidth = 2.dp,
            color = indicatorColor,
            strokeCap = StrokeCap.Round,
        )
    }
    Text(text)
}
