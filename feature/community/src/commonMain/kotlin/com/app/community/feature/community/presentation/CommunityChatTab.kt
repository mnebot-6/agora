package com.app.community.feature.community.presentation

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.community.core.model.CommunityMessage
import com.app.community.core.ui.theme.AgoraSpacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import agora.feature.community.generated.resources.Res
import agora.feature.community.generated.resources.chat_cancel
import agora.feature.community.generated.resources.chat_delete
import agora.feature.community.generated.resources.chat_delete_confirm
import agora.feature.community.generated.resources.chat_delete_confirm_message
import agora.feature.community.generated.resources.chat_delete_confirm_title
import agora.feature.community.generated.resources.chat_edit
import agora.feature.community.generated.resources.chat_edited_marker
import agora.feature.community.generated.resources.chat_empty_subtitle
import agora.feature.community.generated.resources.chat_empty_title
import agora.feature.community.generated.resources.chat_input_placeholder
import agora.feature.community.generated.resources.chat_load_more
import agora.feature.community.generated.resources.chat_save
import agora.feature.community.generated.resources.chat_send_cd
import agora.feature.community.generated.resources.moderation_block
import agora.feature.community.generated.resources.moderation_report
import agora.feature.community.generated.resources.moderation_report_reason
import agora.feature.community.generated.resources.moderation_report_title
import agora.feature.community.generated.resources.moderation_reason_spam
import agora.feature.community.generated.resources.moderation_reason_harassment
import agora.feature.community.generated.resources.moderation_reason_inappropriate
import agora.feature.community.generated.resources.moderation_reason_other
import agora.feature.community.generated.resources.moderation_send_report
import agora.feature.community.generated.resources.moderation_block_dialog_title
import agora.feature.community.generated.resources.moderation_block_dialog_text
import agora.feature.community.generated.resources.moderation_block_confirm
import androidx.compose.material3.RadioButton
import com.app.community.core.data.repository.ReportReason

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityChatTab(
    screenModel: CommunityChatScreenModel,
    modifier: Modifier = Modifier,
) {
    val state by screenModel.state.collectAsState()

    when (val s = state) {
        is CommunityChatScreenModel.UiState.Loading -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is CommunityChatScreenModel.UiState.Error -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is CommunityChatScreenModel.UiState.Content -> {
            ChatContent(state = s, screenModel = screenModel, modifier = modifier)
        }
    }
}

@Composable
private fun ChatContent(
    state: CommunityChatScreenModel.UiState.Content,
    screenModel: CommunityChatScreenModel,
    modifier: Modifier = Modifier,
) {
    var deleteCandidate by remember { mutableStateOf<String?>(null) }
    var reportCandidate by remember { mutableStateOf<String?>(null) }
    var blockCandidate by remember { mutableStateOf<Pair<String, String>?>(null) }
    val visibleMessages = remember(state.messages, state.blockedUserIds) {
        if (state.blockedUserIds.isEmpty()) state.messages
        else state.messages.filterNot { it.userId in state.blockedUserIds }
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        // Lista de mensajes (mas reciente al fondo, scroll inicia abajo)
        if (visibleMessages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(Res.string.chat_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(AgoraSpacing.xs))
                    Text(
                        stringResource(Res.string.chat_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            val listState = rememberLazyListState()
            // Cuando el usuario llega al tope superior (mas antiguo), cargar mas
            val shouldLoadMore by remember {
                derivedStateOf {
                    val total = listState.layoutInfo.totalItemsCount
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    total > 0 && lastVisible >= total - 3
                }
            }
            LaunchedEffect(shouldLoadMore, state.canLoadMore, state.isLoadingMore) {
                if (shouldLoadMore && state.canLoadMore && !state.isLoadingMore) {
                    screenModel.loadOlder()
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                reverseLayout = true,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(AgoraSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
            ) {
                items(visibleMessages, key = { it.id }) { msg ->
                    MessageBubble(
                        message = msg,
                        isOwn = msg.userId == state.currentUserId,
                        canDelete = msg.userId == state.currentUserId || state.isAdmin,
                        canEdit = msg.userId == state.currentUserId,
                        isEditing = state.editingMessageId == msg.id,
                        editDraft = state.editDraft,
                        onEditStart = { screenModel.startEdit(msg.id) },
                        onEditCancel = screenModel::cancelEdit,
                        onEditSave = screenModel::saveEdit,
                        onEditDraftChange = screenModel::onEditDraftChange,
                        onDeleteRequest = { deleteCandidate = msg.id },
                        onReportRequest = { reportCandidate = msg.id },
                        onBlockRequest = {
                            blockCandidate = msg.userId to (msg.profiles?.displayName ?: "—")
                        },
                    )
                }
                if (state.isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(AgoraSpacing.md), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }

        // Input bar
        MessageInputBar(
            value = state.draft,
            onValueChange = screenModel::onDraftChange,
            onSend = screenModel::send,
            isSending = state.isSending,
        )
    }

    // Confirm delete dialog
    deleteCandidate?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(Res.string.chat_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.chat_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    screenModel.delete(id)
                    deleteCandidate = null
                }) {
                    Text(
                        stringResource(Res.string.chat_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(Res.string.chat_cancel))
                }
            },
        )
    }

    reportCandidate?.let { messageId ->
        ReportReasonDialog(
            title = stringResource(Res.string.moderation_report_title),
            onDismiss = { reportCandidate = null },
            onConfirm = { reason ->
                screenModel.reportMessage(messageId, reason)
                reportCandidate = null
            },
        )
    }

    blockCandidate?.let { (userId, displayName) ->
        AlertDialog(
            onDismissRequest = { blockCandidate = null },
            title = { Text(stringResource(Res.string.moderation_block_dialog_title)) },
            text = { Text(stringResource(Res.string.moderation_block_dialog_text, displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    screenModel.blockUser(userId)
                    blockCandidate = null
                }) {
                    Text(
                        stringResource(Res.string.moderation_block_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { blockCandidate = null }) {
                    Text(stringResource(Res.string.chat_cancel))
                }
            },
        )
    }
}

@Composable
internal fun ReportReasonDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (ReportReason) -> Unit,
) {
    var selected by remember { mutableStateOf(ReportReason.SPAM) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AgoraSpacing.xs)) {
                Text(
                    text = stringResource(Res.string.moderation_report_reason),
                    style = MaterialTheme.typography.labelLarge,
                )
                ReasonRow(label = stringResource(Res.string.moderation_reason_spam),
                    selected = selected == ReportReason.SPAM,
                    onSelect = { selected = ReportReason.SPAM })
                ReasonRow(label = stringResource(Res.string.moderation_reason_harassment),
                    selected = selected == ReportReason.HARASSMENT,
                    onSelect = { selected = ReportReason.HARASSMENT })
                ReasonRow(label = stringResource(Res.string.moderation_reason_inappropriate),
                    selected = selected == ReportReason.INAPPROPRIATE,
                    onSelect = { selected = ReportReason.INAPPROPRIATE })
                ReasonRow(label = stringResource(Res.string.moderation_reason_other),
                    selected = selected == ReportReason.OTHER,
                    onSelect = { selected = ReportReason.OTHER })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(Res.string.moderation_send_report))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.chat_cancel))
            }
        },
    )
}

@Composable
private fun ReasonRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(AgoraSpacing.xs))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: CommunityMessage,
    isOwn: Boolean,
    canDelete: Boolean,
    canEdit: Boolean,
    isEditing: Boolean,
    editDraft: String,
    onEditStart: () -> Unit,
    onEditCancel: () -> Unit,
    onEditSave: () -> Unit,
    onEditDraftChange: (String) -> Unit,
    onDeleteRequest: () -> Unit,
    onReportRequest: () -> Unit,
    onBlockRequest: () -> Unit,
) {
    val alignment = if (isOwn) Alignment.End else Alignment.Start
    val containerColor = if (isOwn) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isOwn) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val senderName = message.profiles?.displayName ?: "—"
    val time = remember(message.createdAt) {
        val local = message.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
        "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        if (!isOwn) {
            Text(
                text = senderName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AgoraSpacing.sm, bottom = 2.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isOwn) 16.dp else 4.dp,
                bottomEnd = if (isOwn) 4.dp else 16.dp,
            ),
            color = containerColor,
            modifier = Modifier
                .combinedClickable(
                    onClick = {},
                    onLongClick = { menuOpen = true },
                ),
        ) {
            Box {
                if (isEditing) {
                    Column(Modifier.padding(AgoraSpacing.sm)) {
                        OutlinedTextField(
                            value = editDraft,
                            onValueChange = onEditDraftChange,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4,
                        )
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onEditCancel) {
                                Text(stringResource(Res.string.chat_cancel))
                            }
                            TextButton(onClick = onEditSave) {
                                Text(stringResource(Res.string.chat_save))
                            }
                        }
                    }
                } else {
                    Column(Modifier.padding(horizontal = AgoraSpacing.md, vertical = AgoraSpacing.sm)) {
                        Text(
                            text = message.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor,
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = time,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.7f),
                            )
                            if (message.editedAt != null) {
                                Spacer(Modifier.width(AgoraSpacing.xs))
                                Text(
                                    text = stringResource(Res.string.chat_edited_marker),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Light,
                                )
                            }
                        }
                    }
                }

                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (canEdit) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.chat_edit)) },
                            onClick = {
                                menuOpen = false
                                onEditStart()
                            },
                        )
                    }
                    if (canDelete) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(Res.string.chat_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onDeleteRequest()
                            },
                        )
                    }
                    if (!isOwn) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.moderation_report)) },
                            onClick = {
                                menuOpen = false
                                onReportRequest()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.moderation_block)) },
                            onClick = {
                                menuOpen = false
                                onBlockRequest()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AgoraSpacing.sm, vertical = AgoraSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(Res.string.chat_input_placeholder)) },
                maxLines = 4,
                enabled = !isSending,
            )
            Spacer(Modifier.width(AgoraSpacing.sm))
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && !isSending,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(Res.string.chat_send_cd),
                    tint = if (value.isNotBlank() && !isSending) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

