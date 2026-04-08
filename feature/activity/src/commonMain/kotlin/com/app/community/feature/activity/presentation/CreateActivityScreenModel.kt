package com.app.community.feature.activity.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.data.repository.ActivityRepository
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.SlotRepository
import com.app.community.core.model.SlotMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

// --- Position configurator models ---

data class PositionConfig(
    val id: String = kotlinx.datetime.Clock.System.now().toEpochMilliseconds().toString() + kotlin.random.Random.nextInt(10000),
    val name: String = "",
)

data class GroupConfig(
    val id: String = kotlinx.datetime.Clock.System.now().toEpochMilliseconds().toString() + kotlin.random.Random.nextInt(10000),
    val name: String = "",
    val slots: List<SlotConfig> = emptyList(),
)

data class SlotConfig(
    val id: String = kotlinx.datetime.Clock.System.now().toEpochMilliseconds().toString() + kotlin.random.Random.nextInt(10000),
    val acceptedPositionIds: Set<String> = emptySet(),
)

data class CreateActivityUiState(
    val name: String = "",
    val description: String = "",
    val date: String = "",
    val time: String = "",
    val durationHours: Int = 2,
    val durationMinutes: Int = 0,
    val locationName: String = "",
    val costDescription: String = "",
    val slotMode: SlotMode = SlotMode.UNLIMITED,
    val maxSlots: String = "",
    // Position mode fields
    val positions: List<PositionConfig> = listOf(PositionConfig(name = "")),
    val groups: List<GroupConfig> = listOf(GroupConfig(name = "Equipo 1")),
    val status: CreateActivityStatus = CreateActivityStatus.Idle,
) {
    val totalSlotCount: Int
        get() = groups.sumOf { it.slots.size }
}

sealed class CreateActivityStatus {
    data object Idle : CreateActivityStatus()
    data object Loading : CreateActivityStatus()
    data object Success : CreateActivityStatus()
    data class Error(val message: String) : CreateActivityStatus()
}

class CreateActivityScreenModel(
    private val communityId: String,
    private val activityRepository: ActivityRepository,
    private val slotRepository: SlotRepository,
    private val authRepository: AuthRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(CreateActivityUiState())
    val state: StateFlow<CreateActivityUiState> = _state.asStateFlow()

    // --- Basic fields ---
    fun onNameChange(value: String) = _state.update { it.copy(name = value) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }
    fun onDateChange(value: String) = _state.update { it.copy(date = value) }
    fun onTimeChange(value: String) = _state.update { it.copy(time = value) }
    fun onDurationHoursChange(value: Int) = _state.update { it.copy(durationHours = value) }
    fun onDurationMinutesChange(value: Int) = _state.update { it.copy(durationMinutes = value) }
    fun onLocationNameChange(value: String) = _state.update { it.copy(locationName = value) }
    fun onCostDescriptionChange(value: String) = _state.update { it.copy(costDescription = value) }
    fun onSlotModeChange(value: SlotMode) = _state.update { it.copy(slotMode = value) }
    fun onMaxSlotsChange(value: String) = _state.update { it.copy(maxSlots = value) }

    // --- Position management ---
    fun addPosition() = _state.update {
        it.copy(positions = it.positions + PositionConfig())
    }

    fun removePosition(positionId: String) = _state.update { state ->
        val newPositions = state.positions.filter { it.id != positionId }
        // Also remove this position from all slot configs
        val newGroups = state.groups.map { group ->
            group.copy(slots = group.slots.map { slot ->
                slot.copy(acceptedPositionIds = slot.acceptedPositionIds - positionId)
            })
        }
        state.copy(positions = newPositions, groups = newGroups)
    }

    fun updatePositionName(positionId: String, name: String) = _state.update { state ->
        state.copy(positions = state.positions.map {
            if (it.id == positionId) it.copy(name = name) else it
        })
    }

    // --- Group management ---
    fun addGroup() = _state.update { state ->
        val groupNum = state.groups.size + 1
        state.copy(groups = state.groups + GroupConfig(name = "Equipo $groupNum"))
    }

    fun removeGroup(groupId: String) = _state.update { state ->
        state.copy(groups = state.groups.filter { it.id != groupId })
    }

    fun updateGroupName(groupId: String, name: String) = _state.update { state ->
        state.copy(groups = state.groups.map {
            if (it.id == groupId) it.copy(name = name) else it
        })
    }

    // --- Slot management within groups ---
    fun addSlotToGroup(groupId: String) = _state.update { state ->
        state.copy(groups = state.groups.map { group ->
            if (group.id == groupId) {
                group.copy(slots = group.slots + SlotConfig())
            } else group
        })
    }

    fun removeSlotFromGroup(groupId: String, slotId: String) = _state.update { state ->
        state.copy(groups = state.groups.map { group ->
            if (group.id == groupId) {
                group.copy(slots = group.slots.filter { it.id != slotId })
            } else group
        })
    }

    fun toggleSlotPosition(groupId: String, slotId: String, positionId: String) = _state.update { state ->
        state.copy(groups = state.groups.map { group ->
            if (group.id == groupId) {
                group.copy(slots = group.slots.map { slot ->
                    if (slot.id == slotId) {
                        val newPositions = if (positionId in slot.acceptedPositionIds) {
                            slot.acceptedPositionIds - positionId
                        } else {
                            slot.acceptedPositionIds + positionId
                        }
                        slot.copy(acceptedPositionIds = newPositions)
                    } else slot
                })
            } else group
        })
    }

    // --- Create ---
    fun create() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(status = CreateActivityStatus.Error("El nombre es obligatorio")) }
            return
        }
        if (s.date.isBlank() || s.time.isBlank()) {
            _state.update { it.copy(status = CreateActivityStatus.Error("Fecha y hora son obligatorios")) }
            return
        }
        if (s.slotMode == SlotMode.LIMITED && (s.maxSlots.toIntOrNull() ?: 0) <= 0) {
            _state.update { it.copy(status = CreateActivityStatus.Error("Indica el número de plazas")) }
            return
        }
        if (s.slotMode == SlotMode.LIMITED_WITH_POSITIONS) {
            val validPositions = s.positions.filter { it.name.isNotBlank() }
            if (validPositions.isEmpty()) {
                _state.update { it.copy(status = CreateActivityStatus.Error("Define al menos una posición")) }
                return
            }
            if (s.groups.isEmpty()) {
                _state.update { it.copy(status = CreateActivityStatus.Error("Define al menos un grupo")) }
                return
            }
            if (s.totalSlotCount == 0) {
                _state.update { it.copy(status = CreateActivityStatus.Error("Añade al menos un hueco a un grupo")) }
                return
            }
            val hasSlotWithoutPosition = s.groups.any { g ->
                g.slots.any { it.acceptedPositionIds.isEmpty() }
            }
            if (hasSlotWithoutPosition) {
                _state.update { it.copy(status = CreateActivityStatus.Error("Cada hueco debe tener al menos una posición")) }
                return
            }
        }

        val userId = authRepository.currentUserId() ?: return
        val datetime = parseDateTime(s.date, s.time) ?: run {
            _state.update { it.copy(status = CreateActivityStatus.Error("Formato de fecha/hora inválido. Usa DD/MM/YYYY y HH:MM")) }
            return
        }
        val durationMinutes = s.durationHours * 60 + s.durationMinutes

        _state.update { it.copy(status = CreateActivityStatus.Loading) }

        screenModelScope.launch {
            val maxSlots = when (s.slotMode) {
                SlotMode.UNLIMITED -> null
                SlotMode.LIMITED -> s.maxSlots.toIntOrNull()
                SlotMode.LIMITED_WITH_POSITIONS -> s.totalSlotCount
            }

            val result = activityRepository.createActivity(
                communityId = communityId,
                name = s.name,
                description = s.description.ifBlank { null },
                datetime = datetime,
                durationMinutes = durationMinutes,
                locationName = s.locationName.ifBlank { null },
                locationLat = null,
                locationLng = null,
                costDescription = s.costDescription.ifBlank { null },
                slotMode = s.slotMode,
                maxSlots = maxSlots,
                createdBy = userId,
            )

            result
                .onSuccess { activity ->
                    when (s.slotMode) {
                        SlotMode.UNLIMITED -> { /* No slots to create */ }
                        SlotMode.LIMITED -> {
                            val count = s.maxSlots.toIntOrNull() ?: 0
                            if (count > 0) {
                                slotRepository.createSlots(activity.id, count)
                            }
                        }
                        SlotMode.LIMITED_WITH_POSITIONS -> {
                            createPositionedSlots(activity.id, s)
                        }
                    }
                    _state.update { it.copy(status = CreateActivityStatus.Success) }
                }
                .onError { msg, _ ->
                    _state.update { it.copy(status = CreateActivityStatus.Error(msg)) }
                }
        }
    }

    private suspend fun createPositionedSlots(activityId: String, state: CreateActivityUiState) {
        // 1. Create positions in DB and map local IDs to server IDs
        val positionIdMap = mutableMapOf<String, String>() // localId -> serverId
        for (pos in state.positions.filter { it.name.isNotBlank() }) {
            slotRepository.createPosition(activityId, pos.name)
                .onSuccess { serverPosition ->
                    positionIdMap[pos.id] = serverPosition.id
                }
        }

        // 2. Create groups and their slots
        for ((groupIndex, group) in state.groups.withIndex()) {
            val groupName = group.name.ifBlank { "Grupo ${groupIndex + 1}" }
            val groupResult = slotRepository.createSlotGroup(activityId, groupName, groupIndex)

            groupResult.onSuccess { serverGroup ->
                for ((slotIndex, slotConfig) in group.slots.withIndex()) {
                    // Map local position IDs to server position IDs
                    val serverPositionIds = slotConfig.acceptedPositionIds
                        .mapNotNull { positionIdMap[it] }

                    slotRepository.createSlotWithPositions(
                        activityId = activityId,
                        groupId = serverGroup.id,
                        sortOrder = slotIndex,
                        positionIds = serverPositionIds,
                    )
                }
            }
        }
    }

    private fun parseDateTime(date: String, time: String): Instant? {
        return try {
            val dateParts = date.split("/")
            if (dateParts.size != 3) return null
            val day = dateParts[0].toInt()
            val month = dateParts[1].toInt()
            val year = dateParts[2].toInt()

            val timeCleaned = time.replace("h", ":").replace("H", ":")
            val timeParts = timeCleaned.split(":")
            if (timeParts.size != 2) return null
            val hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()

            val localDateTime = kotlinx.datetime.LocalDateTime(year, month, day, hour, minute)
            localDateTime.toInstant(TimeZone.currentSystemDefault())
        } catch (e: Exception) {
            null
        }
    }
}
