package com.app.community.feature.activity.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.model.Activity
import com.app.community.core.model.SlotMode
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.IonicFrame
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.components.MarbleCard
import com.app.community.core.ui.theme.AgoraElevation
import com.app.community.core.ui.theme.AgoraSpacing
import agora.feature.activity.generated.resources.Res
import agora.feature.activity.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class ActivityFeedScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<ActivityFeedScreenModel>()
        val state by screenModel.state.collectAsState()

        LaunchedEffect(Unit) { screenModel.load() }

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = { Text(stringResource(Res.string.feed_title)) },
                )
            },
            floatingActionButton = {
                if (state is ActivityFeedUiState.Content) {
                    FloatingActionButton(
                        onClick = {
                            val content = state as ActivityFeedUiState.Content
                            if (content.communities.size == 1) {
                                navigator.push(CreateActivityScreen(content.communities.first().id))
                            } else {
                                screenModel.showCommunityPicker()
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.feed_create_activity))
                    }
                }
            },
        ) { padding ->
            when (val s = state) {
                is ActivityFeedUiState.Loading -> LoadingScreen(Modifier.padding(padding))
                is ActivityFeedUiState.Error -> ErrorScreen(s.message, onRetry = screenModel::load, modifier = Modifier.padding(padding))
                is ActivityFeedUiState.Content -> {
                    if (s.showCommunityPicker && s.communities.size > 1) {
                        AlertDialog(
                            onDismissRequest = screenModel::hideCommunityPicker,
                            title = { Text(stringResource(Res.string.feed_select_community)) },
                            text = {
                                Column {
                                    s.communities.forEach { community ->
                                        TextButton(
                                            onClick = {
                                                screenModel.hideCommunityPicker()
                                                navigator.push(CreateActivityScreen(community.id))
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                community.name,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = {
                                TextButton(onClick = screenModel::hideCommunityPicker) {
                                    Text(stringResource(Res.string.label_cancel))
                                }
                            },
                        )
                    }
                    if (s.activities.isEmpty()) {
                        Box(
                            Modifier.fillMaxSize().padding(padding),
                            contentAlignment = Alignment.Center,
                        ) {
                            IonicFrame {
                                Text(
                                    stringResource(Res.string.feed_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(AgoraSpacing.lg),
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(horizontal = AgoraSpacing.screenHorizontal),
                            verticalArrangement = Arrangement.spacedBy(AgoraSpacing.listItemSpacing),
                        ) {
                            item { Spacer(Modifier.height(AgoraSpacing.sm)) }
                            items(s.activities) { activity ->
                                ActivityFeedCard(
                                    activity = activity,
                                    onClick = { navigator.push(ActivityDetailScreen(activity.id)) },
                                )
                            }
                            item { Spacer(Modifier.height(AgoraSpacing.sm)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityFeedCard(activity: Activity, onClick: () -> Unit) {
    val localDateTime = activity.datetime.toLocalDateTime(TimeZone.currentSystemDefault())

    MarbleCard(
        elevation = AgoraElevation.subtle,
        onClick = onClick,
    ) {
        Column(Modifier.padding(AgoraSpacing.cardInternal)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = activity.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                val unlimitedLabel = stringResource(Res.string.slot_mode_unlimited)
                val limitedCountLabel = stringResource(Res.string.slot_mode_limited_count, activity.maxSlots ?: 0)
                val modeLabel = when (activity.slotMode) {
                    SlotMode.UNLIMITED -> unlimitedLabel
                    SlotMode.LIMITED -> limitedCountLabel
                    SlotMode.LIMITED_WITH_POSITIONS -> limitedCountLabel
                }
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(AgoraSpacing.xs))

            Text(
                text = buildString {
                    append(localDateTime.dayOfMonth.toString().padStart(2, '0'))
                    append("/")
                    append(localDateTime.monthNumber.toString().padStart(2, '0'))
                    append("/")
                    append(localDateTime.year)
                    append("  ")
                    append(localDateTime.hour.toString().padStart(2, '0'))
                    append(":")
                    append(localDateTime.minute.toString().padStart(2, '0'))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            activity.locationName?.let { loc ->
                Text(
                    text = loc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
