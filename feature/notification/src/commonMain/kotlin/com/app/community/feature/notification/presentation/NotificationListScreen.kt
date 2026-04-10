package com.app.community.feature.notification.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import com.app.community.core.model.Notification
import com.app.community.core.model.NotificationType
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.FlutedColumnDivider
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.DentilDivider
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.theme.AgoraSpacing
import com.app.community.core.ui.theme.agoraColors
import agora.feature.notification.generated.resources.Res
import agora.feature.notification.generated.resources.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

class NotificationListScreen : Screen {

    @Composable
    override fun Content() {
        val screenModel = koinScreenModel<NotificationListScreenModel>()
        val state by screenModel.state.collectAsState()

        LaunchedEffect(Unit) { screenModel.load() }

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = { Text(stringResource(Res.string.notifications_title)) },
                    actions = {
                        if (state.unreadCount > 0) {
                            TextButton(onClick = screenModel::markAllAsRead) {
                                Text(stringResource(Res.string.mark_all_read), color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    },
                )
            },
        ) { padding ->
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(padding))
                state.error != null -> ErrorScreen(
                    message = state.error!!,
                    onRetry = screenModel::load,
                    modifier = Modifier.padding(padding),
                )
                state.notifications.isEmpty() -> {
                    Box(
                        Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AgoraSpacing.lg),
                        ) {
                            DentilDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AgoraSpacing.xxl),
                            )
                            Text(
                                stringResource(Res.string.no_notifications),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            DentilDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AgoraSpacing.xxl),
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                    ) {
                        items(state.notifications, key = { it.id }) { notification ->
                            NotificationRow(
                                notification = notification,
                                onClick = {
                                    if (!notification.read) {
                                        screenModel.markAsRead(notification.id)
                                    }
                                },
                            )
                            FlutedColumnDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: Notification,
    onClick: () -> Unit,
) {
    val parchmentColor = MaterialTheme.agoraColors.parchment
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val isUnread = !notification.read

    val containerColor = if (isUnread) {
        parchmentColor
    } else {
        MaterialTheme.colorScheme.surface
    }

    val localDateTime = notification.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
    val timeText = buildString {
        append(localDateTime.dayOfMonth.toString().padStart(2, '0'))
        append("/")
        append(localDateTime.monthNumber.toString().padStart(2, '0'))
        append(" ")
        append(localDateTime.hour.toString().padStart(2, '0'))
        append(":")
        append(localDateTime.minute.toString().padStart(2, '0'))
    }

    val typeIcon = when (notification.type) {
        NotificationType.NEW_ACTIVITY -> stringResource(Res.string.type_new_activity)
        NotificationType.SLOT_RELEASED -> stringResource(Res.string.type_slot_released)
        NotificationType.SUBSTITUTE_PROMOTED -> stringResource(Res.string.type_substitute_promoted)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor)
            .then(
                if (isUnread) {
                    Modifier.drawBehind {
                        drawRect(
                            color = secondaryColor,
                            topLeft = Offset.Zero,
                            size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height),
                        )
                    }
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(
                start = if (isUnread) AgoraSpacing.lg + AgoraSpacing.xs else AgoraSpacing.lg,
                end = AgoraSpacing.lg,
                top = AgoraSpacing.md,
                bottom = AgoraSpacing.md,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = typeIcon,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (notification.read) FontWeight.Normal else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = notification.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
