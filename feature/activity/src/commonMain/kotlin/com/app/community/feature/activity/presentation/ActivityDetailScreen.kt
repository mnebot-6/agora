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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import com.app.community.core.ui.components.GreekKeyDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.theme.slotStatusColors
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.koin.core.parameter.parametersOf

@Serializable
data class ActivityDetailScreen(val activityId: String) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
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
                TopAppBar(
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
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Activity info header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                Text("Duración: $durationText", style = MaterialTheme.typography.bodyMedium)

                activity.location?.let { loc ->
                    Text("Lugar: ${loc.name}", style = MaterialTheme.typography.bodyMedium)
                }

                activity.costDescription?.let { cost ->
                    Text("Coste: $cost", style = MaterialTheme.typography.bodyMedium)
                }

                activity.description?.let { desc ->
                    if (desc.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        com.mikepenz.markdown.m3.Markdown(
                            content = desc,
                        )
                    }
                }
            }
        }

        // Activity status badge (if not active)
        if (activity.status != ActivityStatus.ACTIVE) {
            item {
                val (label, color) = when (activity.status) {
                    ActivityStatus.CANCELLED -> "Cancelada" to MaterialTheme.colorScheme.error
                    ActivityStatus.COMPLETED -> "Completada" to MaterialTheme.colorScheme.primary
                    else -> "" to MaterialTheme.colorScheme.outline
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Estado: $label",
                        style = MaterialTheme.typography.titleSmall,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        // Admin controls: cancel/complete activity
        if (state.isAdmin && activity.status == ActivityStatus.ACTIVE) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = screenModel::completeActivity,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Completar")
                    }
                    OutlinedButton(
                        onClick = screenModel::cancelActivity,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        }

        item { GreekKeyDivider() }

        // Slots section
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Plazas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                when (activity.slotMode) {
                    SlotMode.UNLIMITED -> Text("${state.participantCount} apuntados")
                    SlotMode.LIMITED, SlotMode.LIMITED_WITH_POSITIONS -> {
                        val total = state.slots.size
                        val occupied = state.slots.count { it.slot.status != SlotStatus.AVAILABLE }
                        Text("$occupied / $total")
                    }
                }
            }
        }

        // Unlimited mode
        if (activity.slotMode == SlotMode.UNLIMITED) {
            item {
                if (state.isUserJoined) {
                    OutlinedButton(
                        onClick = screenModel::leaveUnlimited,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) {
                        Text("Salir", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Button(onClick = screenModel::joinUnlimited, modifier = Modifier.fillMaxWidth()) {
                        Text("Unirse")
                    }
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
                item { HorizontalDivider() }
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
                        modifier = Modifier.padding(top = 4.dp),
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
                item { HorizontalDivider() }
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
    }
}

@Composable
private fun ParticipantRow(slotWithProfile: SlotWithProfile, currentUserId: String) {
    val name = slotWithProfile.profile?.displayName ?: "Usuario"
    val isMe = slotWithProfile.slot.reservedBy == currentUserId
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isMe) "$name (tú)" else name,
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

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = slotColorPair.container),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
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
                            text = if (isMySlot) "$name (tú) - Reservado" else "$name - Reservado",
                            style = MaterialTheme.typography.bodySmall,
                            color = slotColorPair.content,
                        )
                    }

                    SlotStatus.PAID -> {
                        val name = slotWithProfile.profile?.displayName ?: "Usuario"
                        Text(
                            text = if (isMySlot) "$name (tú) - Pagado" else "$name - Pagado",
                            style = MaterialTheme.typography.bodySmall,
                            color = slotColorPair.content,
                        )
                    }
                }
            }

            // Actions
            when {
                slot.status == SlotStatus.AVAILABLE -> {
                    Button(onClick = onReserve, modifier = Modifier.height(36.dp)) {
                        Text("Reservar", style = MaterialTheme.typography.labelMedium)
                    }
                }

                isMySlot -> {
                    TextButton(onClick = onRelease) {
                        Text("Liberar", color = MaterialTheme.colorScheme.error)
                    }
                }

                isAdmin && slot.status == SlotStatus.RESERVED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
    val positionLabel = slotWithProfile.positionNames.joinToString(" / ").ifEmpty { "Sin posición" }
    val slotColors = MaterialTheme.slotStatusColors

    val slotColorPair = when (slot.status) {
        SlotStatus.AVAILABLE -> slotColors.available
        SlotStatus.RESERVED -> if (isMySlot) slotColors.reservedByMe else slotColors.reservedByOther
        SlotStatus.PAID -> slotColors.paid
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = slotColorPair.container),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                                text = if (isMySlot) "$name (tú) - Reservado" else "$name - Reservado",
                                style = MaterialTheme.typography.bodySmall,
                                color = slotColorPair.content,
                            )
                        }
                        SlotStatus.PAID -> {
                            val name = slotWithProfile.profile?.displayName ?: "Usuario"
                            Text(
                                text = if (isMySlot) "$name (tú) - Pagado" else "$name - Pagado",
                                style = MaterialTheme.typography.bodySmall,
                                color = slotColorPair.content,
                            )
                        }
                    }
                }

                when {
                    slot.status == SlotStatus.AVAILABLE -> {
                        Button(onClick = onReserve, modifier = Modifier.height(36.dp)) {
                            Text("Reservar", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    isMySlot -> {
                        TextButton(onClick = onRelease) {
                            Text("Liberar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    isAdmin && slot.status == SlotStatus.RESERVED -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cola de suplentes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (queue.isEmpty()) {
            Text("No hay suplentes en cola", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        } else {
            queue.forEachIndexed { index, entry ->
                val isMe = entry.userId == currentUserId
                val posName = entry.positionId?.let { positionMap[it]?.name }
                val label = buildString {
                    append("${index + 1}. ")
                    append(if (isMe) "Tú" else "Usuario")
                    if (posName != null) append(" ($posName)")
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }

        if (isInQueue) {
            OutlinedButton(onClick = onLeaveQueue, modifier = Modifier.fillMaxWidth()) {
                Text("Salir de la cola")
            }
        } else {
            if (positions.isNotEmpty()) {
                Text(
                    "Apuntarme de suplente para:",
                    style = MaterialTheme.typography.labelLarge,
                )
                Button(
                    onClick = { onJoinQueue(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cualquier posición")
                }
                positions.forEach { position ->
                    OutlinedButton(
                        onClick = { onJoinQueue(position.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(position.name)
                    }
                }
            } else {
                Button(onClick = { onJoinQueue(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Apuntarme de suplente")
                }
            }
        }
    }
}
