package com.app.community.feature.notification.presentation

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import com.app.community.core.model.Notification
import com.app.community.core.model.NotificationType
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.LoadingScreen
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class NotificationListScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val screenModel = koinScreenModel<NotificationListScreenModel>()
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Notificaciones") },
                    actions = {
                        if (state.unreadCount > 0) {
                            TextButton(onClick = screenModel::markAllAsRead) {
                                Text("Leer todo")
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
                        Text(
                            "Sin novedades por ahora",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline,
                        )
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
                            HorizontalDivider()
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
    val containerColor = if (notification.read) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
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
        NotificationType.NEW_ACTIVITY -> "Nueva actividad"
        NotificationType.SLOT_RELEASED -> "Plaza liberada"
        NotificationType.SUBSTITUTE_PROMOTED -> "Promocionado"
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
