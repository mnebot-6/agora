package com.app.community.feature.activity.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.model.Activity
import com.app.community.core.model.ActivityStatus
import com.app.community.core.model.CommunityMember
import com.app.community.core.model.Position
import com.app.community.core.model.Slot
import com.app.community.core.model.SlotMode
import com.app.community.core.model.SlotStatus
import com.app.community.core.model.SubstituteEntry
import com.app.community.core.model.formatEuros
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.FlutedColumnDivider
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.FriezeBandHeader
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.components.SlotStatusBadge
import com.app.community.core.ui.components.MarbleCard
import com.app.community.core.ui.share.rememberInviteSharer
import com.app.community.core.model.PendingGuestRequest
import com.app.community.core.ui.theme.AgoraElevation
import com.app.community.core.ui.theme.AgoraSpacing
import com.app.community.core.ui.theme.MarblePanelShape
import com.app.community.core.ui.theme.slotStatusColors
import agora.feature.activity.generated.resources.Res
import agora.feature.activity.generated.resources.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.LocationOn
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.koin.core.parameter.parametersOf

/**
 * Espeja la logica de release_slot: un admin puede liberar una plaza reservada, o cualquier
 * plaza sin dueno (etiqueta de invitado) sea cual sea su estado. Una plaza PAGADA con dueno
 * sigue siendo cosa suya. Sin la segunda condicion, una plaza de etiqueta marcada como pagada
 * se quedaba sin ningun boton y era imposible de liberar.
 */
/** "14:30" en hora local. Una oferta sin hora no deberia existir, pero no se revienta por ello. */
private fun formatTime(instant: Instant?): String {
    val local = (instant ?: return "?").toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

private val Slot.isAdminReleasable: Boolean
    get() = reservedBy == null || status == SlotStatus.RESERVED

@Serializable
data class ActivityDetailScreen(val activityId: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<ActivityDetailScreenModel> { parametersOf(activityId) }
        val state by screenModel.state.collectAsState()
        val actionMessage by screenModel.actionMessage.collectAsState()
        val deleted by screenModel.deleted.collectAsState()
        val guestShareUrl by screenModel.guestShareUrl.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        val inviteSharer = rememberInviteSharer()

        LaunchedEffect(Unit) { screenModel.load() }

        LaunchedEffect(guestShareUrl) {
            guestShareUrl?.let {
                inviteSharer.share(it)
                screenModel.consumeGuestShareUrl()
            }
        }

        LaunchedEffect(deleted) {
            if (deleted) navigator.pop()
        }

        LaunchedEffect(actionMessage) {
            actionMessage?.let {
                snackbarHostState.showSnackbar(it)
                screenModel.clearActionMessage()
            }
        }

        // Salida a Stripe Checkout. El formulario de tarjeta lo aloja Stripe: ningun dato
        // de pago toca la app, que es lo que la mantiene fuera del alcance de PCI.
        val checkoutUrl by screenModel.checkoutUrl.collectAsState()
        val uriHandler = LocalUriHandler.current
        LaunchedEffect(checkoutUrl) {
            checkoutUrl?.let {
                uriHandler.openUri(it)
                screenModel.consumeCheckoutUrl()
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
    var assignTarget by remember { mutableStateOf<AssignTarget?>(null) }
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

                    // Importe estructurado si lo hay; si no, el texto libre antiguo, que
                    // sigue siendo lo unico que tienen las actividades ya creadas.
                    (activity.priceCents?.let { formatEuros(it) } ?: activity.costDescription)?.let { cost ->
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
                        onClick = screenModel::askToArchive,
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

            // Compartir con invitados: solo comunidades públicas
            if (state.isPublicCommunity) {
                item {
                    AgoraButton(
                        text = stringResource(Res.string.detail_share_guest),
                        onClick = screenModel::generateGuestLink,
                        variant = AgoraButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Cola FIFO de solicitudes de invitados pendientes
            if (state.pendingGuestRequests.isNotEmpty()) {
                item { FriezeBandHeader(title = stringResource(Res.string.detail_guest_requests_header)) }
                items(state.pendingGuestRequests, key = { it.id }) { request ->
                    GuestRequestRow(
                        request = request,
                        onApprove = { screenModel.approveGuestRequest(request.id) },
                        onReject = { screenModel.rejectGuestRequest(request.id) },
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
                Column(verticalArrangement = Arrangement.spacedBy(AgoraSpacing.xs)) {
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
                    if (state.isAdmin) {
                        AgoraButton(
                            text = stringResource(Res.string.assign_button),
                            onClick = { assignTarget = AssignTarget.NewSlot },
                            variant = AgoraButtonVariant.Secondary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Participant list
            items(state.slots.filter { it.slot.reservedBy != null || it.slot.guestLabel != null }) { slotWithProfile ->
                ParticipantRow(
                    slotWithProfile = slotWithProfile,
                    currentUserId = state.currentUserId,
                    isAdmin = state.isAdmin,
                    onRelease = { screenModel.releaseSlot(slotWithProfile.slot.id) },
                )
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
                    hasCost = activity.priceCents != null || activity.costDescription != null,
                    hasReservation = state.isUserJoined,
                    withPositions = false,
                    onReserve = { screenModel.reserveSlot(slotWithProfile.slot.id) },
                    onRelease = { screenModel.releaseSlot(slotWithProfile.slot.id) },
                    onMarkPaid = { screenModel.markSlotPaid(slotWithProfile.slot.id) },
                    onAssign = { assignTarget = AssignTarget.ExistingSlot(slotWithProfile.slot.id) },
                    onDeclineOffer = { screenModel.declineOffer(slotWithProfile.slot.id) },
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
                    SlotCard(
                        index = index + 1,
                        slotWithProfile = slotWithProfile,
                        currentUserId = state.currentUserId,
                        isAdmin = state.isAdmin,
                        hasCost = activity.priceCents != null || activity.costDescription != null,
                        hasReservation = state.isUserJoined,
                        withPositions = true,
                        onReserve = { screenModel.reserveSlot(slotWithProfile.slot.id) },
                        onRelease = { screenModel.releaseSlot(slotWithProfile.slot.id) },
                        onMarkPaid = { screenModel.markSlotPaid(slotWithProfile.slot.id) },
                        onAssign = { assignTarget = AssignTarget.ExistingSlot(slotWithProfile.slot.id) },
                        onDeclineOffer = { screenModel.declineOffer(slotWithProfile.slot.id) },
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

    // Cancelacion: el dinero PRIMERO. Cancelar sin ver cuanto se mueve, y sobre todo sin
    // ver a quien hay que pagar a mano, es como se pierde el rastro de una devolucion.
    val cancellationPreview by screenModel.cancellationPreview.collectAsState()
    cancellationPreview?.let { breakdown ->
        AlertDialog(
            onDismissRequest = screenModel::dismissCancellation,
            title = { Text(stringResource(Res.string.cancel_activity_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AgoraSpacing.sm)) {
                    Text(stringResource(Res.string.cancel_activity_message))
                    if (breakdown.autoCount > 0) {
                        Text(
                            text = stringResource(
                                Res.string.cancel_activity_auto,
                                breakdown.autoCount,
                                breakdown.autoLabel,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (breakdown.manual.isNotEmpty()) {
                        Text(
                            text = stringResource(Res.string.cancel_activity_manual_header),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        breakdown.manual.forEach { debt ->
                            Text(
                                text = "· ${debt.name} — ${debt.amountLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = screenModel::archiveActivity) {
                    Text(
                        stringResource(Res.string.cancel_activity_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = screenModel::dismissCancellation) {
                    Text(stringResource(Res.string.cancel_activity_dismiss))
                }
            },
        )
    }

    // Deudas que quedan tras cancelar, en su propio dialogo con cierre explicito: si solo
    // se ensenaran en el snackbar, el admin perderia la lista de a quien debe dinero.
    val manualDebts by screenModel.manualDebts.collectAsState()
    if (manualDebts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = screenModel::clearManualDebts,
            title = { Text(stringResource(Res.string.cancel_activity_debts_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AgoraSpacing.xs)) {
                    Text(stringResource(Res.string.cancel_activity_debts_body))
                    manualDebts.forEach { debt ->
                        Text(
                            text = "· ${debt.name} — ${debt.amountLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = screenModel::clearManualDebts) {
                    Text(stringResource(Res.string.cancel_activity_debts_ok))
                }
            },
        )
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

    assignTarget?.let { target ->
        AssignSlotDialog(
            members = state.members,
            queueSize = state.substituteQueue.size,
            onDismiss = { assignTarget = null },
            onConfirm = { userId, label ->
                when (target) {
                    is AssignTarget.ExistingSlot -> screenModel.assignSlot(target.slotId, userId, label)
                    AssignTarget.NewSlot -> screenModel.assignNewSlot(userId, label)
                }
            },
        )
    }
}

@Composable
private fun ParticipantRow(
    slotWithProfile: SlotWithProfile,
    currentUserId: String,
    isAdmin: Boolean = false,
    onRelease: (() -> Unit)? = null,
) {
    val name = slotWithProfile.profile?.displayName
        ?: slotWithProfile.slot.guestLabel
        ?: stringResource(Res.string.unknown_user)
    val isMe = slotWithProfile.slot.reservedBy == currentUserId
    Row(
        Modifier.fillMaxWidth().padding(vertical = AgoraSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // El nombre lleva weight para que el boton de liberar se mida primero y conserve su
        // ancho: sin el, un nombre largo se quedaba toda la fila y el boton desaparecia.
        Text(
            text = if (isMe) stringResource(Res.string.name_is_me, name) else name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (isAdmin && onRelease != null && slotWithProfile.slot.isAdminReleasable) {
            TextButton(onClick = onRelease) {
                Text(
                    stringResource(Res.string.slot_release),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun GuestRequestRow(
    request: PendingGuestRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MarblePanelShape,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(AgoraSpacing.md).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(request.guestName, style = MaterialTheme.typography.titleSmall)
                Text(
                    request.guestEmail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (request.requestedPositions.isNotEmpty()) {
                    Text(
                        request.requestedPositions.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.xs)) {
                TextButton(onClick = onApprove) {
                    Text(stringResource(Res.string.detail_guest_request_approve), style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onReject) {
                    Text(
                        stringResource(Res.string.detail_guest_request_reject),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
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

/**
 * Tarjeta de una plaza. Con [withPositions] pinta ademas la linea de posiciones y numera
 * "Puesto n" en vez de "Plaza n": era la unica diferencia real entre las dos tarjetas que
 * habia antes, 120 lineas identicas cada una.
 */
@Composable
private fun SlotCard(
    index: Int,
    slotWithProfile: SlotWithProfile,
    currentUserId: String,
    isAdmin: Boolean,
    hasCost: Boolean,
    hasReservation: Boolean,
    withPositions: Boolean,
    onReserve: () -> Unit,
    onRelease: () -> Unit,
    onMarkPaid: () -> Unit,
    onAssign: () -> Unit,
    onDeclineOffer: () -> Unit,
) {
    val slot = slotWithProfile.slot
    val isMySlot = slot.reservedBy == currentUserId
    val slotColors = MaterialTheme.slotStatusColors
    val noPositionLabel = stringResource(Res.string.no_position)
    val slotLabel = if (withPositions) {
        stringResource(Res.string.detail_position_slot_index, index)
    } else {
        stringResource(Res.string.detail_slot_index, index)
    }
    val positionLabel = if (withPositions) {
        slotWithProfile.positionNames.joinToString(" / ").ifEmpty { noPositionLabel }
    } else {
        null
    }

    val slotColorPair = when (slot.status) {
        SlotStatus.AVAILABLE -> slotColors.available
        SlotStatus.RESERVED -> if (isMySlot) slotColors.reservedByMe else slotColors.reservedByOther
        SlotStatus.PAID -> slotColors.paid
        SlotStatus.PENDING -> slotColors.reservedByOther
        SlotStatus.PENDING_PAYMENT ->
            if (isMySlot) slotColors.reservedByMe else slotColors.reservedByOther
        // Estado que esta version no conoce: se pinta como ocupada, que es lo unico seguro.
        SlotStatus.UNKNOWN -> slotColors.reservedByOther
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
                        Text(slotLabel, style = MaterialTheme.typography.titleSmall)
                        positionLabel?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(stringResource(Res.string.slot_available), style = MaterialTheme.typography.bodySmall, color = slotColorPair.content)
                    }
                    SlotStatus.RESERVED,
                    SlotStatus.PAID,
                    SlotStatus.PENDING,
                    SlotStatus.PENDING_PAYMENT,
                    SlotStatus.UNKNOWN,
                    -> {
                        val guestChip = stringResource(Res.string.detail_guest_chip)
                        val name = when {
                            slot.isGuest && slot.status == SlotStatus.PENDING -> guestChip
                            else -> slotWithProfile.profile?.displayName ?: slot.guestLabel ?: guestChip
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isMySlot) stringResource(Res.string.name_is_me, name) else name,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (slot.guestLabel != null) {
                                Spacer(Modifier.width(AgoraSpacing.xs))
                                Text(
                                    text = guestChip,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (slot.status == SlotStatus.PENDING) {
                                Spacer(Modifier.width(AgoraSpacing.xs))
                                Text(
                                    text = stringResource(Res.string.detail_guest_pending_chip),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // Pagada por su dueno pero ofrecida: SIGUE SIENDO SUYA. Sin este
                            // aviso, ver un nombre en una plaza que otro puede ocupar no se
                            // entiende, que es justo lo que el diseno marcaba como confuso.
                            if (slot.releasedAt != null) {
                                Spacer(Modifier.width(AgoraSpacing.xs))
                                Text(
                                    text = stringResource(Res.string.detail_released_chip),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (slot.status == SlotStatus.PENDING_PAYMENT) {
                                Spacer(Modifier.width(AgoraSpacing.xs))
                                Text(
                                    text = stringResource(Res.string.detail_pending_payment_chip),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
                        positionLabel?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            SlotActions(
                slot = slot,
                slotLabel = slotLabel,
                isMySlot = isMySlot,
                currentUserId = currentUserId,
                isAdmin = isAdmin,
                hasCost = hasCost,
                hasReservation = hasReservation,
                onReserve = onReserve,
                onRelease = onRelease,
                onMarkPaid = onMarkPaid,
                onDeclineOffer = onDeclineOffer,
                onAssign = onAssign,
            )
        }
    }
}

/**
 * Acciones de una plaza: una sola visible y las demas en el menu de overflow.
 *
 * Antes eran una fila de botones sin restriccion de ancho. En un Row los hijos sin weight
 * se miden primero con todo el ancho disponible, asi que los botones se quedaban el sitio
 * y a la columna de la etiqueta, que si lleva weight, no le sobraba nada: las posiciones
 * acababan partidas en tres lineas.
 *
 * Queda visible la accion de uso corriente en cada estado -reservar, liberar la plaza
 * propia, marcar pagado- y el resto va al menu. Las destructivas viven en el menu, como en
 * MemberManagementScreen: liberar la plaza de otro no debe estar a un toque de distancia.
 * Cuando en un estado solo queda una accion se pinta suelta, sin menu de un unico elemento.
 */
@Composable
private fun SlotActions(
    slot: Slot,
    slotLabel: String,
    isMySlot: Boolean,
    currentUserId: String?,
    isAdmin: Boolean,
    hasCost: Boolean,
    hasReservation: Boolean,
    onReserve: () -> Unit,
    onRelease: () -> Unit,
    onMarkPaid: () -> Unit,
    onAssign: () -> Unit,
    onDeclineOffer: () -> Unit,
) {
    val now = Clock.System.now()
    when {
        // Apalabrada para MI. Va lo primero: manda sobre cualquier otro estado de la plaza,
        // porque para el ofertado esta es la unica accion que importa.
        slot.isOfferedTo(currentUserId, now) -> {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(Res.string.slot_offer_deadline, formatTime(slot.offerExpiresAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDeclineOffer) {
                        Text(
                            stringResource(Res.string.slot_offer_decline),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    AgoraButton(
                        text = stringResource(Res.string.slot_offer_accept),
                        onClick = onReserve,
                        variant = AgoraButtonVariant.Primary,
                    )
                }
            }
        }

        // Apalabrada para OTRO: no es reclamable hasta que caduque.
        slot.hasLiveOffer(now) -> {
            Text(
                text = stringResource(Res.string.slot_offer_held, formatTime(slot.offerExpiresAt)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Pagada por otro y liberada, sin oferta viva: cualquiera puede pagarla.
        slot.isAwaitingSubstitute && slot.reservedBy != currentUserId -> {
            AgoraButton(
                text = stringResource(Res.string.slot_take_over),
                onClick = onReserve,
                variant = AgoraButtonVariant.Primary,
            )
        }

        slot.status == SlotStatus.AVAILABLE -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!hasReservation) {
                    AgoraButton(
                        text = stringResource(Res.string.slot_reserve),
                        onClick = onReserve,
                        variant = AgoraButtonVariant.Primary,
                    )
                }
                if (isAdmin) {
                    if (hasReservation) {
                        // Sin boton de reservar no hay accion principal a la que ceder el
                        // sitio: apuntar se queda a la vista en vez de esconderse.
                        TextButton(onClick = onAssign) {
                            Text(
                                stringResource(Res.string.assign_button),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    } else {
                        SlotOverflowMenu(slotLabel) { dismiss ->
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.assign_button)) },
                                onClick = { dismiss(); onAssign() },
                            )
                        }
                    }
                }
            }
        }
        // Plaza retenida mientras su dueno esta en Checkout: no hay nada que liberar todavia,
        // y el servidor rechaza release_slot en este estado. Se ensena solo el aviso.
        slot.status == SlotStatus.PENDING_PAYMENT -> {
            Text(
                text = stringResource(Res.string.slot_pending_payment_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        isMySlot -> {
            TextButton(onClick = onRelease) {
                Text(stringResource(Res.string.slot_release), color = MaterialTheme.colorScheme.error)
            }
        }
        isAdmin && slot.isAdminReleasable -> {
            if (hasCost && slot.status == SlotStatus.RESERVED) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onMarkPaid) {
                        Text(stringResource(Res.string.slot_paid), style = MaterialTheme.typography.labelMedium)
                    }
                    SlotOverflowMenu(slotLabel) { dismiss ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(Res.string.slot_release),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = { dismiss(); onRelease() },
                        )
                    }
                }
            } else {
                // Unica accion del estado: se pinta suelta, sin menu de un solo elemento.
                TextButton(onClick = onRelease) {
                    Text(stringResource(Res.string.slot_release), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Boton de tres puntos con las acciones secundarias de una plaza. El area de toque son los
 * 48dp que trae IconButton por defecto, y cada DropdownMenuItem los suyos.
 */
@Composable
private fun SlotOverflowMenu(
    slotLabel: String,
    items: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(Res.string.slot_actions_cd, slotLabel),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items { expanded = false }
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

/** Qué plaza va a recibir la asignación: una existente, o una nueva en modo ilimitado. */
private sealed interface AssignTarget {
    data class ExistingSlot(val slotId: String) : AssignTarget
    data object NewSlot : AssignTarget
}

@Composable
private fun AssignSlotDialog(
    members: List<CommunityMember>,
    queueSize: Int,
    onDismiss: () -> Unit,
    onConfirm: (userId: String?, guestLabel: String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedUserId by remember { mutableStateOf<String?>(null) }
    var guestName by remember { mutableStateOf("") }

    val filtered = remember(members, query) {
        if (query.isBlank()) members
        else members.filter { it.profiles?.displayName?.contains(query, ignoreCase = true) == true }
    }
    val canConfirm = selectedUserId != null || guestName.isNotBlank()
    val unknownUserLabel = stringResource(Res.string.unknown_user)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.assign_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AgoraSpacing.sm)) {
                if (queueSize > 0) {
                    Text(
                        text = stringResource(Res.string.assign_queue_warning, queueSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(Res.string.assign_search_member)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(filtered, key = { it.userId }) { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedUserId = member.userId
                                    guestName = ""
                                }
                                .padding(vertical = AgoraSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedUserId == member.userId,
                                onClick = {
                                    selectedUserId = member.userId
                                    guestName = ""
                                },
                            )
                            Text(
                                text = member.profiles?.displayName ?: unknownUserLabel,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = guestName,
                    onValueChange = {
                        guestName = it
                        if (it.isNotBlank()) selectedUserId = null
                    },
                    label = { Text(stringResource(Res.string.assign_guest_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    onConfirm(selectedUserId, guestName.takeIf { it.isNotBlank() })
                    onDismiss()
                },
            ) {
                Text(
                    stringResource(
                        if (queueSize > 0) Res.string.assign_confirm_anyway
                        else Res.string.assign_confirm
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.label_cancel)) }
        },
    )
}
