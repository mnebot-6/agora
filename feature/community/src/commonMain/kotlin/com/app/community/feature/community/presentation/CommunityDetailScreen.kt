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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.app.community.core.model.MemberRole
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.FriezeBandHeader
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.components.MarbleCard
import com.app.community.core.ui.theme.AgoraElevation
import com.app.community.core.ui.theme.AgoraSpacing
import com.app.community.core.ui.theme.MarblePanelShape
import com.app.community.core.ui.theme.agoraColors
import com.app.community.feature.activity.presentation.ActivityDetailScreen
import com.app.community.feature.activity.presentation.CreateActivityScreen
import agora.feature.community.generated.resources.Res
import agora.feature.community.generated.resources.*
import org.jetbrains.compose.resources.stringResource
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

        LaunchedEffect(Unit) { screenModel.refresh() }

        val title = when (val state = uiState) {
            is CommunityDetailScreenModel.UiState.Content -> state.community.name
            else -> stringResource(Res.string.community_detail_default_title)
        }

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = {
                        Text(
                            title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back_cd))
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navigator.push(CreateActivityScreen(communityId)) },
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.community_detail_create_activity_cd))
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
        contentPadding = PaddingValues(AgoraSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(AgoraSpacing.listItemSpacing),
    ) {
        // Description
        if (!community.description.isNullOrBlank()) {
            item {
                Surface(
                    color = MaterialTheme.agoraColors.parchment,
                    shape = MarblePanelShape,
                ) {
                    Text(
                        text = community.description.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.agoraColors.onParchment,
                        modifier = Modifier.padding(AgoraSpacing.cardInternal),
                    )
                }
            }
        }

        // Invite code chip
        item {
            MarbleCard(
                elevation = AgoraElevation.none,
                borderColor = MaterialTheme.agoraColors.gildedVolute,
                onClick = {
                    clipboardManager.setText(AnnotatedString(community.inviteCode))
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AgoraSpacing.cardInternal, vertical = AgoraSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(Res.string.community_detail_invite_prefix, community.inviteCode),
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing * 1.5,
                        ),
                        color = MaterialTheme.agoraColors.gildedVolute,
                    )
                    Icon(
                        Icons.Default.Share,
                        contentDescription = stringResource(Res.string.community_detail_copy_code_cd),
                        modifier = Modifier.height(16.dp),
                        tint = MaterialTheme.agoraColors.gildedVolute,
                    )
                }
            }
        }

        // Members section header
        item {
            FriezeBandHeader(
                title = stringResource(Res.string.community_detail_members_header, members.size),
                trailingContent = if (isAdmin) {
                    {
                        TextButton(onClick = onManageMembers) {
                            Text(stringResource(Res.string.community_detail_manage))
                        }
                    }
                } else {
                    null
                },
            )
        }

        // Activities section header
        item {
            FriezeBandHeader(
                title = stringResource(Res.string.community_detail_activities_header, activities.size),
            )
        }

        if (activities.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.community_detail_no_activities),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = AgoraSpacing.sm),
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

@Composable
private fun ActivityCard(
    activity: Activity,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val localDateTime = activity.datetime.toLocalDateTime(TimeZone.currentSystemDefault())
    val dateText = "${localDateTime.dayOfMonth}/${localDateTime.monthNumber}/${localDateTime.year}"
    val timeText = "${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"

    MarbleCard(
        modifier = modifier,
        elevation = AgoraElevation.none,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(AgoraSpacing.md)) {
            Text(
                text = activity.name,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(AgoraSpacing.xs))
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
                    activity.maxSlots != null -> stringResource(Res.string.community_detail_slots, activity.maxSlots!!)
                    else -> stringResource(Res.string.community_detail_no_limit)
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
