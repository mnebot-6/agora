package com.app.community.feature.community.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.data.repository.ReportReason
import com.app.community.core.model.CommunityMember
import com.app.community.core.model.MemberRole
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.FlutedColumnDivider
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.FriezeBandHeader
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.theme.AgoraSpacing
import agora.feature.community.generated.resources.Res
import agora.feature.community.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.serialization.Serializable
import org.koin.core.parameter.parametersOf

@Serializable
data class MemberManagementScreen(val communityId: String) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<MemberManagementScreenModel> { parametersOf(communityId) }
        val state by screenModel.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(state.actionMessage) {
            state.actionMessage?.let {
                snackbarHostState.showSnackbar(it)
                screenModel.clearActionMessage()
            }
        }

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = {
                        Text(
                            stringResource(Res.string.member_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back_cd))
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(padding))
                state.error != null -> ErrorScreen(
                    message = state.error!!,
                    onRetry = screenModel::load,
                    modifier = Modifier.padding(padding),
                )
                else -> MemberList(
                    members = state.members,
                    currentUserId = state.currentUserId,
                    isCurrentUserAdmin = state.members.any {
                        it.userId == state.currentUserId && it.role == MemberRole.ADMIN
                    },
                    onToggleRole = screenModel::toggleRole,
                    onRemove = screenModel::removeMember,
                    onBlock = screenModel::blockMember,
                    onReport = screenModel::reportMember,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun MemberList(
    members: List<CommunityMember>,
    currentUserId: String,
    isCurrentUserAdmin: Boolean,
    onToggleRole: (CommunityMember) -> Unit,
    onRemove: (CommunityMember) -> Unit,
    onBlock: (CommunityMember) -> Unit,
    onReport: (CommunityMember, ReportReason) -> Unit,
    modifier: Modifier = Modifier,
) {
    val admins = members.filter { it.role == MemberRole.ADMIN }
    val users = members.filter { it.role == MemberRole.USER }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = AgoraSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
    ) {
        item {
            FriezeBandHeader(title = stringResource(Res.string.member_admins_header, admins.size))
        }
        items(admins, key = { it.userId }) { member ->
            MemberRow(
                member = member,
                currentUserId = currentUserId,
                isCurrentUserAdmin = isCurrentUserAdmin,
                onToggleRole = onToggleRole,
                onRemove = onRemove,
                onBlock = onBlock,
                onReport = onReport,
            )
            FlutedColumnDivider(modifier = Modifier.padding(vertical = AgoraSpacing.xs))
        }

        item {
            FriezeBandHeader(title = stringResource(Res.string.member_users_header, users.size))
        }
        items(users, key = { it.userId }) { member ->
            MemberRow(
                member = member,
                currentUserId = currentUserId,
                isCurrentUserAdmin = isCurrentUserAdmin,
                onToggleRole = onToggleRole,
                onRemove = onRemove,
                onBlock = onBlock,
                onReport = onReport,
            )
            FlutedColumnDivider(modifier = Modifier.padding(vertical = AgoraSpacing.xs))
        }
    }
}

@Composable
private fun MemberRow(
    member: CommunityMember,
    currentUserId: String,
    isCurrentUserAdmin: Boolean,
    onToggleRole: (CommunityMember) -> Unit,
    onRemove: (CommunityMember) -> Unit,
    onBlock: (CommunityMember) -> Unit,
    onReport: (CommunityMember, ReportReason) -> Unit,
) {
    val isMe = member.userId == currentUserId
    val defaultName = stringResource(Res.string.member_default_name)
    val displayName = member.profiles?.displayName ?: defaultName
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AgoraSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isMe) stringResource(Res.string.member_you_suffix, displayName) else displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = if (member.role == MemberRole.ADMIN) stringResource(Res.string.member_role_admin) else stringResource(Res.string.member_role_user),
                style = MaterialTheme.typography.bodySmall,
                color = if (member.role == MemberRole.ADMIN) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }

        if (!isMe) {
            Row(horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.xs)) {
                if (isCurrentUserAdmin) {
                    TextButton(onClick = { onToggleRole(member) }) {
                        Text(
                            if (member.role == MemberRole.ADMIN) stringResource(Res.string.member_demote_admin) else stringResource(Res.string.member_promote_admin),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    TextButton(onClick = { showRemoveDialog = true }) {
                        Text(
                            stringResource(Res.string.member_remove_button),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                TextButton(onClick = { showReportDialog = true }) {
                    Text(
                        stringResource(Res.string.moderation_report),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                TextButton(onClick = { showBlockDialog = true }) {
                    Text(
                        stringResource(Res.string.moderation_block),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringResource(Res.string.member_remove_dialog_title)) },
            text = { Text(stringResource(Res.string.member_remove_dialog_text, displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDialog = false
                    onRemove(member)
                }) {
                    Text(stringResource(Res.string.member_remove_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text(stringResource(Res.string.member_remove_cancel))
                }
            },
        )
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text(stringResource(Res.string.moderation_block_dialog_title)) },
            text = { Text(stringResource(Res.string.moderation_block_dialog_text, displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    showBlockDialog = false
                    onBlock(member)
                }) {
                    Text(stringResource(Res.string.moderation_block_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text(stringResource(Res.string.member_remove_cancel))
                }
            },
        )
    }

    if (showReportDialog) {
        ReportReasonDialog(
            title = stringResource(Res.string.moderation_report_user_title),
            onDismiss = { showReportDialog = false },
            onConfirm = { reason ->
                showReportDialog = false
                onReport(member, reason)
            },
        )
    }
}
