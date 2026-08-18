package com.app.community.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.community.core.ui.theme.AgoraSpacing

/**
 * Silueta unica de "comunidad": avatar a la izquierda, nombre, subtitulo.
 * Nunca muestra fecha, que es lo que la separa de una actividad.
 */
@Composable
fun CommunityRow(
    communityId: String,
    name: String,
    iconKey: String?,
    subtitle: String?,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 40.dp,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(AgoraSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CommunityAvatar(
            communityId = communityId,
            name = name,
            iconKey = iconKey,
            size = avatarSize,
        )
        Spacer(Modifier.width(AgoraSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(AgoraSpacing.sm))
            trailing()
        }
    }
}
