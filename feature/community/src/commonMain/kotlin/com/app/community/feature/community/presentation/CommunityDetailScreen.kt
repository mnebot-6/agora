package com.app.community.feature.community.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import com.app.community.core.ui.components.GreekKeyDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.model.Activity
import com.app.community.core.model.Community
import com.app.community.core.model.CommunityMember
import com.app.community.feature.activity.presentation.ActivityDetailScreen
import com.app.community.feature.activity.presentation.CreateActivityScreen
import com.app.community.core.model.MemberRole
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.LoadingScreen
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.koin.core.parameter.parametersOf

@Serializable
data class CommunityDetailScreen(val communityId: String) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<CommunityDetailScreenModel> { parametersOf(communityId) }
        val uiState by screenModel.uiState.collectAsState()

        val title = when (val state = uiState) {
            is CommunityDetailScreenModel.UiState.Content -> state.community.name
            else -> "Comunidad"
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navigator.push(CreateActivityScreen(communityId)) },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Crear actividad")
                }
            },
        ) { padding ->
            when (val state = uiState) {
                is CommunityDetailScreenModel.UiState.Loading -> {
                    LoadingScreen(modifier = Modifier.padding(padding))
                }

                is CommunityDetailScreenModel.UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { screenModel.refresh() },
                        modifier = Modifier.padding(padding),
                    )
                }

                is CommunityDetailScreenModel.UiState.Content -> {
                    CommunityDetailContent(
                        community = state.community,
                        members = state.members,
                        activities = state.activities,
                        isAdmin = state.isAdmin,
                        onActivityClick = { activityId -> navigator.push(ActivityDetailScreen(activityId)) },
                        onManageMembers = { navigator.push(MemberManagementScreen(communityId)) },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityDetailContent(
    community: Community,
    members: List<CommunityMember>,
    activities: List<Activity>,
    isAdmin: Boolean,
    onActivityClick: (String) -> Unit,
    onManageMembers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Description
        if (!community.description.isNullOrBlank()) {
            item {
                Text(
                    text = community.description.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Invite code chip
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(community.inviteCode))
                    },
                    label = { Text("Invite: ${community.inviteCode}") },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Copiar código",
                            modifier = Modifier.height(16.dp),
                        )
                    },
                )
            }
        }

        // Members section
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${members.size} miembro${if (members.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isAdmin) {
                    TextButton(onClick = onManageMembers) {
                        Text("Gestionar")
                    }
                }
            }
        }

        // Activities section header
        item {
            Spacer(Modifier.height(4.dp))
            GreekKeyDivider()
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Actividades",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (activities.isEmpty()) {
            item {
                Text(
                    text = "Aún no hay actividades. Crea la primera.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(activities, key = { it.id }) { activity ->
                ActivityCard(
                    activity = activity,
                    onClick = { onActivityClick(activity.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityCard(
    activity: Activity,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val localDateTime = activity.datetime.toLocalDateTime(TimeZone.currentSystemDefault())
    val dateText = "${localDateTime.dayOfMonth}/${localDateTime.monthNumber}/${localDateTime.year}"
    val timeText = "${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = activity.name,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$dateText  $timeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val slotText = when {
                    activity.maxSlots != null -> "${activity.maxSlots} plazas"
                    else -> "Sin límite"
                }
                Text(
                    text = slotText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
