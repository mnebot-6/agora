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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.model.Notification
import com.app.community.core.model.NotificationType
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.FlutedColumnDivider
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.DentilDivider
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.theme.AgoraSpacing
import com.app.community.core.ui.theme.agoraColors
import com.app.community.feature.activity.presentation.ActivityDetailScreen
import com.app.community.feature.community.presentation.CommunityDetailScreen
import com.app.community.feature.community.presentation.JoinRequestsScreen
import agora.feature.notification.generated.resources.Res
import agora.feature.notification.generated.resources.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource

class NotificationListScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<NotificationListScreenModel>()
        val state by screenModel.state.collectAsState()
        var showDeleteAllDialog by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { screenModel.load() }

        if (showDeleteAllDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAllDialog = false },
                title = { Text(stringResource(Res.string.delete_confirm_title)) },
                text = { Text(stringResource(Res.string.delete_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteAllDialog = false
                        screenModel.deleteAll()
                    }) {
                        Text(
                            stringResource(Res.string.delete_confirm_yes),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllDialog = false }) {
                        Text(stringResource(Res.string.delete_confirm_no))
                    }
                },
            )
        }

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
                        if (state.notifications.isNotEmpty()) {
                            TextButton(onClick = { showDeleteAllDialog = true }) {
                                Text(
                                    stringResource(Res.string.delete_all),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
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
                            SwipeableNotificationRow(
                                notification = notification,
                                onClick = {
                                    if (!notification.read) {
                                        screenModel.markAsRead(notification.id)
                                    }
                                    val data = notification.data
                                    val communityId = data?.get("community_id")?.jsonPrimitive?.contentOrNull
                                    val activityId = data?.get("activity_id")?.jsonPrimitive?.contentOrNull
                                    when (notification.type) {
                                        NotificationType.JOIN_REQUEST_RECEIVED ->
                                            communityId?.let { navigator.push(JoinRequestsScreen(it)) }
                                        NotificationType.JOIN_REQUEST_APPROVED ->
                                            communityId?.let { navigator.push(CommunityDetailScreen(it)) }
                                        NotificationType.JOIN_REQUEST_REJECTED -> Unit
                                        NotificationType.NEW_ACTIVITY,
                                        NotificationType.SLOT_RELEASED,
                                        NotificationType.SUBSTITUTE_PROMOTED,
                                        NotificationType.ACTIVITY_REMINDER ->
                                            activityId?.let { navigator.push(ActivityDetailScreen(it)) }
                                    }
                                },
                                onDismiss = { screenModel.deleteNotification(notification.id) },
                            )
                            FlutedColumnDivider()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNotificationRow(
    notification: Notification,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDismiss()
                true
            } else false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = AgoraSpacing.lg),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        NotificationRow(notification = notification, onClick = onClick)
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
        NotificationType.ACTIVITY_REMINDER -> stringResource(Res.string.type_activity_reminder)
        NotificationType.JOIN_REQUEST_RECEIVED -> stringResource(Res.string.type_join_request_received)
        NotificationType.JOIN_REQUEST_APPROVED -> stringResource(Res.string.type_join_request_approved)
        NotificationType.JOIN_REQUEST_REJECTED -> stringResource(Res.string.type_join_request_rejected)
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
