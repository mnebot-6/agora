package com.app.community.feature.activity.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.model.Activity
import com.app.community.core.model.ActivityStatus
import com.app.community.core.model.Position
import com.app.community.core.model.SlotMode
import com.app.community.core.model.SlotStatus
import com.app.community.core.model.SubstituteEntry
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.FlutedColumnDivider
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.FriezeBandHeader
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.components.SlotStatusBadge
import com.app.community.core.ui.components.MarbleCard
import com.app.community.core.ui.theme.AgoraElevation
import com.app.community.core.ui.theme.AgoraSpacing
import com.app.community.core.ui.theme.MarblePanelShape
import com.app.community.core.ui.theme.slotStatusColors
import agora.feature.activity.generated.resources.Res
import agora.feature.activity.generated.resources.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.platform.LocalUriHandler
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.koin.core.parameter.parametersOf

@Serializable
data class ActivityDetailScreen(val activityId: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<ActivityDetailScreenModel> { parametersOf(activityId) }
        val state by screenModel.state.collectAsState()
        val actionMessage by screenModel.actionMessage.collectAsState()
        val deleted by screenModel.deleted.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) { screenModel.load() }

        LaunchedEffect(deleted) {
            if (deleted) navigator.pop()
        }

        LaunchedEffect(actionMessage) {
            actionMessage?.let {
                snackbarHostState.showSnackbar(it)
                screenModel.clearActionMessage()
            }
        }

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = {
                        val fallback = stringResource(Res.string.detail_title_fallback)
                        val title = when (val s = state) {
                            is ActivityDetailUiState.Content -> s.activity.name
                            else -> fallback
                        }
                        Text(title)
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
            when (val s = state) {
                is ActivityDetailUiState.Loading -> LoadingScreen(Modifier.padding(padding))
                is ActivityDetailUiState.Error -> ErrorScreen(s.message, onRetry = screenModel::load, modifier = Modifier.padding(padding))
                is ActivityDetailUiState.Content -> ActivityDetailContent(
                    state = s,
                    screenModel = screenModel,
                    onEdit = { navigator.push(EditActivityScreen(activityId)) },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun ActivityDetailContent(
    state: ActivityDetailUiState.Content,
    screenModel: ActivityDetailScreenModel,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val activity = state.activity
    val localDateTime = activity.datetime.toLocalDateTime(TimeZone.currentSystemDefault())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AgoraSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(AgoraSpacing.listItemSpacing),
    ) {
        item { Spacer(Modifier.height(AgoraSpacing.sm)) }

        // Activity info header in a MarbleCard with dentil border
        item {
            MarbleCard(
                showDentilBorder = true,
                elevation = AgoraElevation.raised,
            ) {
                Column(
                    modifier = Modifier.padding(AgoraSpacing.cardInternal),
                    verticalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
                ) {
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
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    val hours = activity.durationMinutes / 60
                    val mins = activity.durationMinutes % 60
                    val durationText = if (mins > 0) stringResource(Res.string.detail_duration_hours_minutes, hours, mins) else stringResource(Res.string.detail_duration_hours, hours)
                    Text(durationText, style = MaterialTheme.typography.bodyMedium)

                    activity.location?.let { loc ->
                        LocationLink(activity = activity)
                    }

                    activity.costDescription?.let { cost ->
                        Text(stringResource(Res.string.detail_cost, cost), style = MaterialTheme.typography.bodyMedium)
                    }

                    activity.description?.let { desc ->
                        if (desc.isNotBlank()) {
                            Spacer(Modifier.height(AgoraSpacing.sm))
                            com.mikepenz.markdown.m3.Markdown(
                                content = desc,
                            )
                        }
                    }
                }
            }
        }

        // Activity status badge (if not active)
        if (activity.status != ActivityStatus.ACTIVE) {
            item {
                val archivedLabel = stringResource(Res.string.detail_status_archived)
                val (label, colorPair) = when (activity.status) {
                    ActivityStatus.ARCHIVED -> archivedLabel to MaterialTheme.slotStatusColors.reservedByOther
                    else -> "" to MaterialTheme.slotStatusColors.available
                }
                SlotStatusBadge(
                    text = stringResource(Res.string.detail_status_label, label),
                    colorPair = colorPair,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Admin controls: edit/archive/delete activity
        if (state.isAdmin && activity.status == ActivityStatus.ACTIVE) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
                ) {
                    AgoraButton(
                        text = stringResource(Res.string.detail_edit),
                        onClick = onEdit,
                        variant = AgoraButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    AgoraButton(
                        text = stringResource(Res.string.detail_archive),
                        onClick = screenModel::archiveActivity,
                        variant = AgoraButtonVariant.Tertiary,
                        modifier = Modifier.weight(1f),
                    )
                    AgoraButton(
                        text = stringResource(Res.string.detail_delete),
                        onClick = { showDeleteDialog = true },
                        variant = AgoraButtonVariant.Danger,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item { FlutedColumnDivider() }

        // Slots section header with FriezeBandHeader
        item {
            FriezeBandHeader(
                title = stringResource(Res.string.slots_header),
                trailingContent = {
                    when (activity.slotMode) {
                        SlotMode.UNLIMITED -> Text(
                            stringResource(Res.string.detail_signed_up_count, state.participantCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        SlotMode.LIMITED, SlotMode.LIMITED_WITH_POSITIONS -> {
                            val total = state.slots.size
                            val occupied = state.slots.count { it.slot.status != SlotStatus.AVAILABLE }
                            Text(
                                stringResource(Res.string.detail_slots_count, occupied, total),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                },
            )
        }

        // Unlimited mode
        if (activity.slotMode == SlotMode.UNLIMITED) {
            item {
                if (state.isUserJoined) {
                    AgoraButton(
                        text = stringResource(Res.string.detail_leave),
                        onClick = screenModel::leaveUnlimited,
                        variant = AgoraButtonVariant.Danger,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    AgoraButton(
                        text = stringResource(Res.string.detail_join),
                        onClick = screenModel::joinUnlimited,
                        variant = AgoraButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Participant list
            items(state.slots.filter { it.slot.reservedBy != null }) { slotWithProfile ->
                ParticipantRow(slotWithProfile, state.currentUserId)
            }
        }

        // Limited mode (no positions)
        if (activity.slotMode == SlotMode.LIMITED) {
            itemsIndexed(state.slots) { index, slotWithProfile ->
                SlotCard(
                    index = index + 1,
                    slotWithProfile = slotWithProfile,
                    currentUserId = state.currentUserId,
                    isAdmin = state.isAdmin,
                    hasCost = activity.costDescription != null,
                    hasReservation = state.isUserJoined,
                    onReserve = { screenModel.reserveSlot(slotWithProfile.slot.id) },
                    onRelease = { screenModel.releaseSlot(slotWithProfile.slot.id) },
                    onMarkPaid = { screenModel.markSlotPaid(slotWithProfile.slot.id) },
                )
            }

            // Substitute queue section
            val allOccupied = state.slots.all { it.slot.status != SlotStatus.AVAILABLE }
            if (allOccupied) {
                item { FlutedColumnDivider() }
                item {
                    SubstituteQueueSection(
                        queue = state.substituteQueue,
                        positions = emptyList(),
                        currentUserId = state.currentUserId,
                        isUserJoined = state.isUserJoined,
                        onJoinQueue = { screenModel.joinSubstituteQueue() },
                        onLeaveQueue = { screenModel.leaveSubstituteQueue() },
                    )
                }
            }
        }

        // Limited with positions mode (grouped)
        if (activity.slotMode == SlotMode.LIMITED_WITH_POSITIONS) {
            state.groups.forEach { groupWithSlots ->
                item(key = "group_header_${groupWithSlots.group.id}") {
                    Text(
                        text = groupWithSlots.group.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = AgoraSpacing.xs),
                    )
                }

                itemsIndexed(
                    groupWithSlots.slots,
                    key = { _, s -> s.slot.id },
                ) { index, slotWithProfile ->
                    PositionSlotCard(
                        index = index + 1,
                        slotWithProfile = slotWithProfile,
                        currentUserId = state.currentUserId,
                        isAdmin = state.isAdmin,
                        hasCost = activity.costDescription != null,
                        hasReservation = state.isUserJoined,
                        onReserve = { screenModel.reserveSlot(slotWithProfile.slot.id) },
                        onRelease = { screenModel.releaseSlot(slotWithProfile.slot.id) },
                        onMarkPaid = { screenModel.markSlotPaid(slotWithProfile.slot.id) },
                    )
                }
            }

            // Substitute queue — show when any position has all its slots occupied
            val fullPositions = state.positions.filter { position ->
                val slotsForPosition = state.slots.filter { it.positionIds.contains(position.id) }
                slotsForPosition.isNotEmpty() && slotsForPosition.none { it.slot.status == SlotStatus.AVAILABLE }
            }
            val allOccupied = state.slots.all { it.slot.status != SlotStatus.AVAILABLE }
            if (fullPositions.isNotEmpty() || allOccupied) {
                item { FlutedColumnDivider() }
                item {
                    SubstituteQueueSection(
                        queue = state.substituteQueue,
                        positions = fullPositions,
                        currentUserId = state.currentUserId,
                        isUserJoined = state.isUserJoined,
                        onJoinQueue = { positionId -> screenModel.joinSubstituteQueue(positionId) },
                        onLeaveQueue = { screenModel.leaveSubstituteQueue() },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(AgoraSpacing.sm)) }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(Res.string.detail_delete_dialog_title)) },
            text = { Text(stringResource(Res.string.detail_delete_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    screenModel.deleteActivity()
                }) {
                    Text(stringResource(Res.string.label_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(Res.string.label_cancel))
                }
            },
        )
    }
}

@Composable
private fun ParticipantRow(slotWithProfile: SlotWithProfile, currentUserId: String) {
    val name = slotWithProfile.profile?.displayName ?: stringResource(Res.string.unknown_user)
    val isMe = slotWithProfile.slot.reservedBy == currentUserId
    Row(
        Modifier.fillMaxWidth().padding(vertical = AgoraSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isMe) stringResource(Res.string.name_is_me, name) else name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun LocationLink(activity: Activity) {
    val uriHandler = LocalUriHandler.current
    val loc = activity.location ?: return
    val mapsUrl = if (loc.lat != null && loc.lng != null) {
        "https://maps.google.com/?q=${loc.lat},${loc.lng}"
    } else {
        "https://maps.google.com/maps?q=${loc.name.replace(" ", "+")}"
    }

    Row(
        modifier = Modifier
            .clickable { uriHandler.openUri(mapsUrl) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = stringResource(Res.string.detail_location_cd),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = loc.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
private fun SlotCard(
    index: Int,
    slotWithProfile: SlotWithProfile,
    currentUserId: String,
    isAdmin: Boolean,
    hasCost: Boolean,
    hasReservation: Boolean,
    onReserve: () -> Unit,
    onRelease: () -> Unit,
    onMarkPaid: () -> Unit,
) {
    val slot = slotWithProfile.slot
    val isMySlot = slot.reservedBy == currentUserId
    val slotColors = MaterialTheme.slotStatusColors

    val slotColorPair = when (slot.status) {
        SlotStatus.AVAILABLE -> slotColors.available
        SlotStatus.RESERVED -> if (isMySlot) slotColors.reservedByMe else slotColors.reservedByOther
        SlotStatus.PAID -> slotColors.paid
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MarblePanelShape,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, slotColorPair.content),
    ) {
        Row(
            modifier = Modifier.padding(AgoraSpacing.md).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                when (slot.status) {
                    SlotStatus.AVAILABLE -> {
                        Text(stringResource(Res.string.detail_slot_index, index), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(Res.string.slot_available), style = MaterialTheme.typography.bodySmall, color = slotColorPair.content)
                    }
                    SlotStatus.RESERVED, SlotStatus.PAID -> {
                        val name = slotWithProfile.profile?.displayName ?: stringResource(Res.string.unknown_user)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isMySlot) stringResource(Res.string.name_is_me, name) else name,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (isAdmin && slot.status == SlotStatus.PAID) {
                                Spacer(Modifier.width(AgoraSpacing.xs))
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(Res.string.slot_paid),
                                    modifier = Modifier.size(16.dp),
                                    tint = slotColors.paid.content,
                                )
                            }
                        }
                    }
                }
            }

            when {
                slot.status == SlotStatus.AVAILABLE && !hasReservation -> {
                    AgoraButton(
                        text = stringResource(Res.string.slot_reserve),
                        onClick = onReserve,
                        variant = AgoraButtonVariant.Primary,
                    )
                }
                isMySlot -> {
                    TextButton(onClick = onRelease) {
                        Text(stringResource(Res.string.slot_release), color = MaterialTheme.colorScheme.error)
                    }
                }
                isAdmin && slot.status == SlotStatus.RESERVED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.xs)) {
                        if (hasCost) {
                            TextButton(onClick = onMarkPaid) {
                                Text(stringResource(Res.string.slot_paid), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        TextButton(onClick = onRelease) {
                            Text(stringResource(Res.string.slot_release), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionSlotCard(
    index: Int,
    slotWithProfile: SlotWithProfile,
    currentUserId: String,
    isAdmin: Boolean,
    hasCost: Boolean,
    hasReservation: Boolean,
    onReserve: () -> Unit,
    onRelease: () -> Unit,
    onMarkPaid: () -> Unit,
) {
    val slot = slotWithProfile.slot
    val isMySlot = slot.reservedBy == currentUserId
    val noPositionLabel = stringResource(Res.string.no_position)
    val positionLabel = slotWithProfile.positionNames.joinToString(" / ").ifEmpty { noPositionLabel }
    val slotColors = MaterialTheme.slotStatusColors

    val slotColorPair = when (slot.status) {
        SlotStatus.AVAILABLE -> slotColors.available
        SlotStatus.RESERVED -> if (isMySlot) slotColors.reservedByMe else slotColors.reservedByOther
        SlotStatus.PAID -> slotColors.paid
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MarblePanelShape,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, slotColorPair.content),
    ) {
        Row(
            modifier = Modifier.padding(AgoraSpacing.md).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                when (slot.status) {
                    SlotStatus.AVAILABLE -> {
                        Text(stringResource(Res.string.detail_position_slot_index, index), style = MaterialTheme.typography.titleSmall)
                        Text(positionLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(Res.string.slot_available), style = MaterialTheme.typography.bodySmall, color = slotColorPair.content)
                    }
                    SlotStatus.RESERVED, SlotStatus.PAID -> {
                        val name = slotWithProfile.profile?.displayName ?: stringResource(Res.string.unknown_user)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isMySlot) stringResource(Res.string.name_is_me, name) else name,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (isAdmin && slot.status == SlotStatus.PAID) {
                                Spacer(Modifier.width(AgoraSpacing.xs))
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(Res.string.slot_paid),
                                    modifier = Modifier.size(16.dp),
                                    tint = slotColors.paid.content,
                                )
                            }
                        }
                        Text(positionLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            when {
                slot.status == SlotStatus.AVAILABLE && !hasReservation -> {
                    AgoraButton(
                        text = stringResource(Res.string.slot_reserve),
                        onClick = onReserve,
                        variant = AgoraButtonVariant.Primary,
                    )
                }
                isMySlot -> {
                    TextButton(onClick = onRelease) {
                        Text(stringResource(Res.string.slot_release), color = MaterialTheme.colorScheme.error)
                    }
                }
                isAdmin && slot.status == SlotStatus.RESERVED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.xs)) {
                        if (hasCost) {
                            TextButton(onClick = onMarkPaid) {
                                Text(stringResource(Res.string.slot_paid), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        TextButton(onClick = onRelease) {
                            Text(stringResource(Res.string.slot_release), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubstituteQueueSection(
    queue: List<SubstituteEntry>,
    positions: List<Position>,
    currentUserId: String,
    isUserJoined: Boolean,
    onJoinQueue: (positionId: String?) -> Unit,
    onLeaveQueue: () -> Unit,
) {
    val isInQueue = queue.any { it.userId == currentUserId }
    val positionMap = positions.associateBy { it.id }

    val substituteMeLabel = stringResource(Res.string.detail_substitute_me)
    val unknownUserLabel = stringResource(Res.string.unknown_user)

    Column(verticalArrangement = Arrangement.spacedBy(AgoraSpacing.sm)) {
        FriezeBandHeader(title = stringResource(Res.string.detail_substitute_queue_header))

        if (queue.isEmpty()) {
            Text(
                stringResource(Res.string.detail_no_substitutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = AgoraSpacing.lg),
            )
        } else {
            queue.forEachIndexed { index, entry ->
                val isMe = entry.userId == currentUserId
                val posName = entry.positionId?.let { positionMap[it]?.name }
                val personLabel = if (isMe) substituteMeLabel else unknownUserLabel
                val label = if (posName != null) {
                    stringResource(Res.string.detail_substitute_entry_position, index + 1, personLabel, posName)
                } else {
                    stringResource(Res.string.detail_substitute_entry, index + 1, personLabel)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = AgoraSpacing.lg),
                )
            }
        }

        if (isInQueue) {
            AgoraButton(
                text = stringResource(Res.string.detail_leave_queue),
                onClick = onLeaveQueue,
                variant = AgoraButtonVariant.Danger,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (!isUserJoined) {
            if (positions.isNotEmpty()) {
                Text(
                    stringResource(Res.string.detail_join_substitute_for),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = AgoraSpacing.lg),
                )
                AgoraButton(
                    text = stringResource(Res.string.detail_any_position),
                    onClick = { onJoinQueue(null) },
                    variant = AgoraButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                positions.forEach { position ->
                    AgoraButton(
                        text = position.name,
                        onClick = { onJoinQueue(position.id) },
                        variant = AgoraButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                AgoraButton(
                    text = stringResource(Res.string.detail_join_substitute),
                    onClick = { onJoinQueue(null) },
                    variant = AgoraButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
