package com.app.community.feature.activity.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.model.ActivityStatus
import com.app.community.core.model.Position
import com.app.community.core.model.SlotMode
import com.app.community.core.model.SlotStatus
import com.app.community.core.model.SubstituteEntry
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.ColumnDivider
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.FriezeBandHeader
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.components.SlotStatusBadge
import com.app.community.core.ui.components.StoneCard
import com.app.community.core.ui.theme.AgoraElevation
import com.app.community.core.ui.theme.AgoraSpacing
import com.app.community.core.ui.theme.StoneTabletShape
import com.app.community.core.ui.theme.slotStatusColors
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
        val snackbarHostState = remember { SnackbarHostState() }

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
                        val title = when (val s = state) {
                            is ActivityDetailUiState.Content -> s.activity.name
                            else -> "Actividad"
                        }
                        Text(title)
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
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
    modifier: Modifier = Modifier,
) {
    val activity = state.activity
    val localDateTime = activity.datetime.toLocalDateTime(TimeZone.currentSystemDefault())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AgoraSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(AgoraSpacing.listItemSpacing),
    ) {
        item { Spacer(Modifier.height(AgoraSpacing.sm)) }

        // Activity info header in a StoneCard with Greek border
        item {
            StoneCard(
                showGreekBorder = true,
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
                    val durationText = if (mins > 0) "${hours}h ${mins}min" else "${hours}h"
                    Text("Duracion: $durationText", style = MaterialTheme.typography.bodyMedium)

                    activity.location?.let { loc ->
                        Text("Lugar: ${loc.name}", style = MaterialTheme.typography.bodyMedium)
                    }

                    activity.costDescription?.let { cost ->
                        Text("Coste: $cost", style = MaterialTheme.typography.bodyMedium)
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
                val (label, colorPair) = when (activity.status) {
                    ActivityStatus.CANCELLED -> "Cancelada" to MaterialTheme.slotStatusColors.reservedByOther
                    ActivityStatus.COMPLETED -> "Completada" to MaterialTheme.slotStatusColors.paid
                    else -> "" to MaterialTheme.slotStatusColors.available
                }
                SlotStatusBadge(
                    text = "Estado: $label",
                    colorPair = colorPair,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Admin controls: cancel/complete activity
        if (state.isAdmin && activity.status == ActivityStatus.ACTIVE) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
                ) {
                    AgoraButton(
                        text = "Completar",
                        onClick = screenModel::completeActivity,
                        variant = AgoraButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    AgoraButton(
                        text = "Cancelar",
                        onClick = screenModel::cancelActivity,
                        variant = AgoraButtonVariant.Danger,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item { ColumnDivider() }

        // Slots section header with FriezeBandHeader
        item {
            FriezeBandHeader(
                title = "Plazas",
                trailingContent = {
                    when (activity.slotMode) {
                        SlotMode.UNLIMITED -> Text(
                            "${state.participantCount} apuntados",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        SlotMode.LIMITED, SlotMode.LIMITED_WITH_POSITIONS -> {
                            val total = state.slots.size
                            val occupied = state.slots.count { it.slot.status != SlotStatus.AVAILABLE }
                            Text(
                                "$occupied / $total",
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
                        text = "Salir",
                        onClick = screenModel::leaveUnlimited,
                        variant = AgoraButtonVariant.Danger,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    AgoraButton(
                        text = "Unirse",
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
                    onReserve = { screenModel.reserveSlot(slotWithProfile.slot.id) },
                    onRelease = { screenModel.releaseSlot(slotWithProfile.slot.id) },
                    onMarkPaid = { screenModel.markSlotPaid(slotWithProfile.slot.id) },
                )
            }

            // Substitute queue section
            val allOccupied = state.slots.all { it.slot.status != SlotStatus.AVAILABLE }
            if (allOccupied) {
                item { ColumnDivider() }
                item {
                    SubstituteQueueSection(
                        queue = state.substituteQueue,
                        positions = emptyList(),
                        currentUserId = state.currentUserId,
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
                        onReserve = { screenModel.reserveSlot(slotWithProfile.slot.id) },
                        onRelease = { screenModel.releaseSlot(slotWithProfile.slot.id) },
                        onMarkPaid = { screenModel.markSlotPaid(slotWithProfile.slot.id) },
                    )
                }
            }

            // Substitute queue with position filter
            val allOccupied = state.slots.all { it.slot.status != SlotStatus.AVAILABLE }
            if (allOccupied) {
                item { ColumnDivider() }
                item {
                    SubstituteQueueSection(
                        queue = state.substituteQueue,
                        positions = state.positions,
                        currentUserId = state.currentUserId,
                        onJoinQueue = { positionId -> screenModel.joinSubstituteQueue(positionId) },
                        onLeaveQueue = { screenModel.leaveSubstituteQueue() },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(AgoraSpacing.sm)) }
    }
}

@Composable
private fun ParticipantRow(slotWithProfile: SlotWithProfile, currentUserId: String) {
    val name = slotWithProfile.profile?.displayName ?: "Usuario"
    val isMe = slotWithProfile.slot.reservedBy == currentUserId
    Row(
        Modifier.fillMaxWidth().padding(vertical = AgoraSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isMe) "$name (tu)" else name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SlotCard(
    index: Int,
    slotWithProfile: SlotWithProfile,
    currentUserId: String,
    isAdmin: Boolean,
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

    val barColor = slotColorPair.content

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = barColor,
                    topLeft = Offset.Zero,
                    size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height),
                )
            },
        shape = StoneTabletShape,
        colors = CardDefaults.outlinedCardColors(containerColor = slotColorPair.container),
    ) {
        Row(
            modifier = Modifier.padding(AgoraSpacing.md).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Plaza $index", style = MaterialTheme.typography.titleSmall, color = slotColorPair.content)
                when (slot.status) {
                    SlotStatus.AVAILABLE -> {
                        Text("Libre", style = MaterialTheme.typography.bodySmall, color = slotColorPair.content)
                    }

                    SlotStatus.RESERVED -> {
                        val name = slotWithProfile.profile?.displayName ?: "Usuario"
                        Text(
                            text = if (isMySlot) "$name (tu) - Reservado" else "$name - Reservado",
                            style = MaterialTheme.typography.bodySmall,
                            color = slotColorPair.content,
                        )
                    }

                    SlotStatus.PAID -> {
                        val name = slotWithProfile.profile?.displayName ?: "Usuario"
                        Text(
                            text = if (isMySlot) "$name (tu) - Pagado" else "$name - Pagado",
                            style = MaterialTheme.typography.bodySmall,
                            color = slotColorPair.content,
                        )
                    }
                }
            }

            // Actions
            when {
                slot.status == SlotStatus.AVAILABLE -> {
                    AgoraButton(
                        text = "Reservar",
                        onClick = onReserve,
                        variant = AgoraButtonVariant.Primary,
                    )
                }

                isMySlot -> {
                    TextButton(onClick = onRelease) {
                        Text("Liberar", color = MaterialTheme.colorScheme.error)
                    }
                }

                isAdmin && slot.status == SlotStatus.RESERVED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.xs)) {
                        TextButton(onClick = onMarkPaid) {
                            Text("Pagado", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = onRelease) {
                            Text("Liberar", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
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
    onReserve: () -> Unit,
    onRelease: () -> Unit,
    onMarkPaid: () -> Unit,
) {
    val slot = slotWithProfile.slot
    val isMySlot = slot.reservedBy == currentUserId
    val positionLabel = slotWithProfile.positionNames.joinToString(" / ").ifEmpty { "Sin posicion" }
    val slotColors = MaterialTheme.slotStatusColors

    val slotColorPair = when (slot.status) {
        SlotStatus.AVAILABLE -> slotColors.available
        SlotStatus.RESERVED -> if (isMySlot) slotColors.reservedByMe else slotColors.reservedByOther
        SlotStatus.PAID -> slotColors.paid
    }

    val barColor = slotColorPair.content

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = barColor,
                    topLeft = Offset.Zero,
                    size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height),
                )
            },
        shape = StoneTabletShape,
        colors = CardDefaults.outlinedCardColors(containerColor = slotColorPair.container),
    ) {
        Column(modifier = Modifier.padding(AgoraSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hueco $index", style = MaterialTheme.typography.titleSmall, color = slotColorPair.content)
                    Text(
                        text = positionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = slotColorPair.content,
                    )
                    when (slot.status) {
                        SlotStatus.AVAILABLE -> {
                            Text("Libre", style = MaterialTheme.typography.bodySmall, color = slotColorPair.content)
                        }
                        SlotStatus.RESERVED -> {
                            val name = slotWithProfile.profile?.displayName ?: "Usuario"
                            Text(
                                text = if (isMySlot) "$name (tu) - Reservado" else "$name - Reservado",
                                style = MaterialTheme.typography.bodySmall,
                                color = slotColorPair.content,
                            )
                        }
                        SlotStatus.PAID -> {
                            val name = slotWithProfile.profile?.displayName ?: "Usuario"
                            Text(
                                text = if (isMySlot) "$name (tu) - Pagado" else "$name - Pagado",
                                style = MaterialTheme.typography.bodySmall,
                                color = slotColorPair.content,
                            )
                        }
                    }
                }

                when {
                    slot.status == SlotStatus.AVAILABLE -> {
                        AgoraButton(
                            text = "Reservar",
                            onClick = onReserve,
                            variant = AgoraButtonVariant.Primary,
                        )
                    }
                    isMySlot -> {
                        TextButton(onClick = onRelease) {
                            Text("Liberar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    isAdmin && slot.status == SlotStatus.RESERVED -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.xs)) {
                            TextButton(onClick = onMarkPaid) {
                                Text("Pagado", style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(onClick = onRelease) {
                                Text("Liberar", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                            }
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
    onJoinQueue: (positionId: String?) -> Unit,
    onLeaveQueue: () -> Unit,
) {
    val isInQueue = queue.any { it.userId == currentUserId }
    val positionMap = positions.associateBy { it.id }

    Column(verticalArrangement = Arrangement.spacedBy(AgoraSpacing.sm)) {
        FriezeBandHeader(title = "Cola de suplentes")

        if (queue.isEmpty()) {
            Text(
                "No hay suplentes en cola",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = AgoraSpacing.lg),
            )
        } else {
            queue.forEachIndexed { index, entry ->
                val isMe = entry.userId == currentUserId
                val posName = entry.positionId?.let { positionMap[it]?.name }
                val label = buildString {
                    append("${index + 1}. ")
                    append(if (isMe) "Tu" else "Usuario")
                    if (posName != null) append(" ($posName)")
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
                text = "Salir de la cola",
                onClick = onLeaveQueue,
                variant = AgoraButtonVariant.Danger,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            if (positions.isNotEmpty()) {
                Text(
                    "Apuntarme de suplente para:",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = AgoraSpacing.lg),
                )
                AgoraButton(
                    text = "Cualquier posicion",
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
                    text = "Apuntarme de suplente",
                    onClick = { onJoinQueue(null) },
                    variant = AgoraButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
