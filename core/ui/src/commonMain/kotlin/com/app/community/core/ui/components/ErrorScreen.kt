package com.app.community.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ErrorScreen(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GreekKeyDivider(
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(0.5f),
            patternHeight = 8.dp,
        )

        Spacer(Modifier.height(16.dp))

        StoneCard(
            borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (onRetry != null) {
                    Spacer(Modifier.height(16.dp))
                    AgoraButton(
                        text = "Reintentar",
                        onClick = onRetry,
                        variant = AgoraButtonVariant.Secondary,
                    )
                }
            }
        }
    }
}
