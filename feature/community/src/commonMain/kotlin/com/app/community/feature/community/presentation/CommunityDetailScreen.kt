package com.app.community.feature.community.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.model.Activity
import com.app.community.core.model.Community
import com.app.community.core.model.CommunityMember
import com.app.community.core.model.CommunityVisibility
import com.app.community.core.model.MemberRole
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.koin.core.parameter.parametersOf

@Serializable
data class CommunityDetailScreen(val communityId: String) : Screen {

    override val key: ScreenKey = "CommunityDetailScreen-$communityId"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<CommunityDetailScreenModel> { parametersOf(communityId) }
        val uiState by screenModel.uiState.collectAsState()
        val deleted by screenModel.deleted.collectAsState()
        val actionMessage by screenModel.actionMessage.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        // Navigate back on delete
        LaunchedEffect(deleted) {
            if (deleted) navigator.pop()
        }

        // Show snackbar on action
        LaunchedEffect(actionMessage) {
            actionMessage?.let {
                snackbarHostState.showSnackbar(it)
                screenModel.clearActionMessage()
            }
        }

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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                val isAdmin = (uiState as? CommunityDetailScreenModel.UiState.Content)?.isAdmin == true
                if (isAdmin) {
                    FloatingActionButton(
                        onClick = { navigator.push(CreateActivityScreen(communityId)) },
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.community_detail_create_activity_cd))
                    }
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
                        state = state,
                        screenModel = screenModel,
                        onActivityClick = { activityId -> navigator.push(ActivityDetailScreen(activityId)) },
                        onManageMembers = { navigator.push(MemberManagementScreen(communityId)) },
                        onViewPendingRequests = { navigator.push(JoinRequestsScreen(communityId)) },
                        onChildClick = { childId ->
                            val isMember = state.myCommunityIds.contains(childId)
                            if (isMember) {
                                navigator.push(CommunityDetailScreen(childId))
                            } else {
                                navigator.push(CommunityPreviewScreen(childId))
                            }
                        },
                        onCreateSubcommunity = {
                            navigator.push(
                                CreateCommunityScreen(
                                    parentId = communityId,
                                    parentName = state.community.name,
                                ),
                            )
                        },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityDetailContent(
    state: CommunityDetailScreenModel.UiState.Content,
    screenModel: CommunityDetailScreenModel,
    onActivityClick: (String) -> Unit,
    onManageMembers: () -> Unit,
    onViewPendingRequests: () -> Unit,
    onChildClick: (String) -> Unit,
    onCreateSubcommunity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val community = state.community
    val members = state.members
    val activities = state.activities
    val clipboardManager = LocalClipboardManager.current

    // Edit dialog
    if (state.showEditDialog) {
        EditCommunityDialog(state = state, screenModel = screenModel)
    }

    // Leave confirmation dialog
    if (state.showLeaveDialog) {
        AlertDialog(
            onDismissRequest = screenModel::dismissLeaveDialog,
            title = { Text(stringResource(Res.string.community_detail_leave_title)) },
            text = { Text(stringResource(Res.string.community_detail_leave_message)) },
            confirmButton = {
                TextButton(onClick = screenModel::leaveCommunity) {
                    Text(
                        stringResource(Res.string.community_detail_leave_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = screenModel::dismissLeaveDialog) {
                    Text(stringResource(Res.string.community_detail_leave_cancel))
                }
            },
        )
    }

    // Delete confirmation dialog
    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = screenModel::dismissDeleteDialog,
            title = { Text(stringResource(Res.string.community_detail_delete_title)) },
            text = { Text(stringResource(Res.string.community_detail_delete_message)) },
            confirmButton = {
                TextButton(onClick = screenModel::deleteCommunity) {
                    Text(
                        stringResource(Res.string.community_detail_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = screenModel::dismissDeleteDialog) {
                    Text(stringResource(Res.string.community_detail_delete_cancel))
                }
            },
        )
    }

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

        // Admin controls: edit / delete
        if (state.isAdmin) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
                ) {
                    AgoraButton(
                        text = stringResource(Res.string.community_detail_edit),
                        onClick = screenModel::showEditDialog,
                        variant = AgoraButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.isCreator) {
                        AgoraButton(
                            text = stringResource(Res.string.community_detail_delete),
                            onClick = screenModel::showDeleteDialog,
                            variant = AgoraButtonVariant.Danger,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (state.pendingRequestsCount > 0) {
            item {
                Spacer(Modifier.height(AgoraSpacing.sm))
                AgoraButton(
                    text = stringResource(
                        Res.string.community_pending_requests_count,
                        state.pendingRequestsCount,
                    ),
                    onClick = onViewPendingRequests,
                    variant = AgoraButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Invite code chip — visible para todos si la comunidad no es PRIVATE; si es PRIVATE solo admins
        val showInvite = community.visibility != CommunityVisibility.PRIVATE || state.isAdmin
        if (showInvite && !community.inviteCode.isNullOrBlank()) {
            item {
                MarbleCard(
                    elevation = AgoraElevation.none,
                    borderColor = MaterialTheme.agoraColors.gildedVolute,
                    onClick = {
                        clipboardManager.setText(AnnotatedString(community.inviteCode.orEmpty()))
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
                            text = stringResource(Res.string.community_detail_invite_prefix, community.inviteCode.orEmpty()),
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
        }

        // Members section header
        item {
            FriezeBandHeader(
                title = stringResource(Res.string.community_detail_members_header, members.size),
                trailingContent = if (state.isAdmin) {
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

        // Mostramos las pestañas si ya hay hijas o si eres admin (para poder crear la primera).
        val showTabs = state.children.isNotEmpty() || state.isAdmin

        if (showTabs) {
            item {
                TabRow(
                    selectedTabIndex = state.selectedTab.ordinal,
                    containerColor = MaterialTheme.agoraColors.parchment,
                ) {
                    Tab(
                        selected = state.selectedTab == CommunityDetailScreenModel.DetailTab.ACTIVITIES,
                        onClick = { screenModel.onTabSelected(CommunityDetailScreenModel.DetailTab.ACTIVITIES) },
                        text = { Text(stringResource(Res.string.community_detail_tab_activities)) },
                    )
                    Tab(
                        selected = state.selectedTab == CommunityDetailScreenModel.DetailTab.SUBCOMMUNITIES,
                        onClick = { screenModel.onTabSelected(CommunityDetailScreenModel.DetailTab.SUBCOMMUNITIES) },
                        text = { Text(stringResource(Res.string.community_detail_tab_subcommunities)) },
                    )
                }
            }
        }

        val showActivitiesTab = !showTabs || state.selectedTab == CommunityDetailScreenModel.DetailTab.ACTIVITIES
        val showSubcommunitiesTab = showTabs && state.selectedTab == CommunityDetailScreenModel.DetailTab.SUBCOMMUNITIES

        if (showActivitiesTab) {
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

        if (showSubcommunitiesTab) {
            if (state.isAdmin) {
                item {
                    AgoraButton(
                        text = stringResource(Res.string.community_detail_create_subcommunity),
                        onClick = onCreateSubcommunity,
                        variant = AgoraButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            val (mine, available) = state.children.partition { state.myCommunityIds.contains(it.id) }

            if (mine.isEmpty() && available.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.community_detail_no_subcommunities),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = AgoraSpacing.sm),
                    )
                }
            }

            if (mine.isNotEmpty()) {
                item {
                    FriezeBandHeader(
                        title = stringResource(Res.string.community_detail_my_subcommunities),
                    )
                }
                items(mine, key = { it.id }) { child ->
                    SubcommunityCard(
                        community = child,
                        isMember = true,
                        onClick = { onChildClick(child.id) },
                    )
                }
            }

            if (available.isNotEmpty()) {
                item {
                    FriezeBandHeader(
                        title = stringResource(Res.string.community_detail_available_subcommunities),
                    )
                }
                items(available, key = { it.id }) { child ->
                    SubcommunityCard(
                        community = child,
                        isMember = false,
                        onClick = { onChildClick(child.id) },
                    )
                }
            }
        }

        // Leave community — solo para miembros que NO son el creador
        if (!state.isCreator) {
            item {
                Spacer(Modifier.height(AgoraSpacing.lg))
                AgoraButton(
                    text = stringResource(Res.string.community_detail_leave),
                    onClick = screenModel::showLeaveDialog,
                    variant = AgoraButtonVariant.Danger,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditCommunityDialog(
    state: CommunityDetailScreenModel.UiState.Content,
    screenModel: CommunityDetailScreenModel,
) {
    AlertDialog(
        onDismissRequest = screenModel::dismissEditDialog,
        title = { Text(stringResource(Res.string.community_detail_edit_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
            ) {
                OutlinedTextField(
                    value = state.editName,
                    onValueChange = screenModel::onEditNameChange,
                    label = { Text(stringResource(Res.string.community_detail_edit_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.editDescription,
                    onValueChange = screenModel::onEditDescriptionChange,
                    label = { Text(stringResource(Res.string.community_detail_edit_description_label)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(AgoraSpacing.xs))
                Text(
                    text = stringResource(Res.string.create_community_visibility_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                CommunityVisibility.entries.forEach { visibility ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { screenModel.onEditVisibilityChange(visibility) }
                            .padding(vertical = AgoraSpacing.xs),
                    ) {
                        RadioButton(
                            selected = state.editVisibility == visibility,
                            onClick = { screenModel.onEditVisibilityChange(visibility) },
                        )
                        Spacer(Modifier.width(AgoraSpacing.sm))
                        Column {
                            Text(
                                text = stringResource(visibility.editLabelRes()),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = stringResource(visibility.editDescRes()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(AgoraSpacing.xs))
                Text(
                    text = stringResource(Res.string.create_community_tags_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
                ) {
                    state.availableTags.forEach { tag ->
                        val selected = state.editSelectedTagIds.contains(tag.id)
                        FilterChip(
                            selected = selected,
                            onClick = { screenModel.onEditTagToggle(tag.id) },
                            label = {
                                val icon = tag.icon?.let { "$it " }.orEmpty()
                                Text(icon + tag.nameEs)
                            },
                            enabled = selected || state.editSelectedTagIds.size < 3,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = screenModel::saveCommunity,
                enabled = state.editName.isNotBlank(),
            ) {
                Text(stringResource(Res.string.community_detail_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = screenModel::dismissEditDialog) {
                Text(stringResource(Res.string.community_detail_edit_cancel))
            }
        },
    )
}

private fun CommunityVisibility.editLabelRes(): StringResource = when (this) {
    CommunityVisibility.PUBLIC_OPEN -> Res.string.create_community_visibility_public_open
    CommunityVisibility.PUBLIC_APPROVAL -> Res.string.create_community_visibility_public_approval
    CommunityVisibility.PRIVATE -> Res.string.create_community_visibility_private
}

private fun CommunityVisibility.editDescRes(): StringResource = when (this) {
    CommunityVisibility.PUBLIC_OPEN -> Res.string.create_community_visibility_public_open_desc
    CommunityVisibility.PUBLIC_APPROVAL -> Res.string.create_community_visibility_public_approval_desc
    CommunityVisibility.PRIVATE -> Res.string.create_community_visibility_private_desc
}

@Composable
private fun SubcommunityCard(
    community: Community,
    isMember: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MarbleCard(
        modifier = modifier,
        elevation = AgoraElevation.none,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(AgoraSpacing.md)) {
            Text(
                text = community.name,
                style = MaterialTheme.typography.titleSmall,
            )
            if (!community.description.isNullOrBlank()) {
                Spacer(Modifier.height(AgoraSpacing.xs))
                Text(
                    text = community.description.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(AgoraSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val visibilityLabel = when (community.visibility) {
                    CommunityVisibility.PUBLIC_OPEN -> stringResource(Res.string.create_community_visibility_public_open)
                    CommunityVisibility.PUBLIC_APPROVAL -> stringResource(Res.string.create_community_visibility_public_approval)
                    CommunityVisibility.PRIVATE -> stringResource(Res.string.create_community_visibility_private)
                }
                Text(
                    text = visibilityLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                community.memberCount?.let { count ->
                    Text(
                        text = stringResource(Res.string.community_detail_members_header, count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!isMember) {
                Spacer(Modifier.height(AgoraSpacing.xs))
                val cta = when (community.visibility) {
                    CommunityVisibility.PUBLIC_APPROVAL -> stringResource(Res.string.preview_request_join_button)
                    else -> stringResource(Res.string.preview_join_button)
                }
                Text(
                    text = cta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
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
