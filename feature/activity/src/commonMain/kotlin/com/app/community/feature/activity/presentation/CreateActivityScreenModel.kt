package com.app.community.feature.activity.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.RefreshBus
import com.app.community.core.data.repository.ActivityRepository
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.SlotRepository
import com.app.community.core.data.repository.SlotTemplateRepository
import com.app.community.core.model.SlotMode
import com.app.community.core.model.SlotTemplate
import com.app.community.core.model.TemplateConfig
import com.app.community.core.model.GroupTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

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
    val dateMillis: Long? = null,
    val timeHour: Int = 20,
    val timeMinute: Int = 0,
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
    // Templates
    val templates: List<SlotTemplate> = emptyList(),
    val showSaveTemplateDialog: Boolean = false,
    val saveTemplateName: String = "",
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
    private val slotTemplateRepository: SlotTemplateRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(CreateActivityUiState())
    val state: StateFlow<CreateActivityUiState> = _state.asStateFlow()

    init {
        loadTemplates()
    }

    private fun loadTemplates() {
        screenModelScope.launch {
            val defaults = slotTemplateRepository.getDefaultTemplates()
            val userId = authRepository.currentUserId()
            val userTemplates = if (userId != null) {
                slotTemplateRepository.getUserTemplates(userId).let { result ->
                    var list = emptyList<SlotTemplate>()
                    result.onSuccess { list = it }
                    list
                }
            } else emptyList()
            _state.update { it.copy(templates = defaults + userTemplates) }
        }
    }

    // --- Basic fields ---
    fun onNameChange(value: String) = _state.update { it.copy(name = value) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }
    fun onDateSelected(millis: Long?) = _state.update { it.copy(dateMillis = millis) }
    fun onTimeSelected(hour: Int, minute: Int) = _state.update { it.copy(timeHour = hour, timeMinute = minute) }
    fun onDurationSelected(hours: Int, minutes: Int) = _state.update { it.copy(durationHours = hours, durationMinutes = minutes) }
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

    // --- Templates ---
    fun applyTemplate(template: SlotTemplate) {
        val config = template.config
        val newPositions = config.positions.map { PositionConfig(name = it) }
        val newGroups = config.groups.map { groupTemplate ->
            GroupConfig(
                name = groupTemplate.name,
                slots = groupTemplate.slots.map { entry ->
                    val positionIds = entry.positionIndices.mapNotNull { idx ->
                        newPositions.getOrNull(idx)?.id
                    }.toSet()
                    SlotConfig(acceptedPositionIds = positionIds)
                },
            )
        }
        _state.update {
            it.copy(
                slotMode = if (config.positions.isNotEmpty()) SlotMode.LIMITED_WITH_POSITIONS else SlotMode.LIMITED,
                positions = newPositions.ifEmpty { listOf(PositionConfig(name = "")) },
                groups = newGroups.ifEmpty { listOf(GroupConfig(name = "Equipo 1")) },
            )
        }
    }

    fun showSaveTemplateDialog() = _state.update { it.copy(showSaveTemplateDialog = true) }
    fun hideSaveTemplateDialog() = _state.update { it.copy(showSaveTemplateDialog = false, saveTemplateName = "") }
    fun onSaveTemplateNameChange(value: String) = _state.update { it.copy(saveTemplateName = value) }

    fun saveAsTemplate() {
        val s = _state.value
        val name = s.saveTemplateName.trim()
        if (name.isBlank()) return
        val userId = authRepository.currentUserId() ?: return

        val validPositions = s.positions.filter { it.name.isNotBlank() }
        val config = TemplateConfig(
            positions = validPositions.map { it.name },
            groups = s.groups.map { group ->
                GroupTemplate(
                    name = group.name,
                    slots = group.slots.map { slot ->
                        com.app.community.core.model.SlotTemplateEntry(
                            positionIndices = slot.acceptedPositionIds.mapNotNull { pid ->
                                validPositions.indexOfFirst { it.id == pid }.takeIf { it >= 0 }
                            }.toSet(),
                        )
                    },
                )
            },
        )

        screenModelScope.launch {
            slotTemplateRepository.saveTemplate(userId, name, config)
                .onSuccess { saved ->
                    _state.update {
                        it.copy(
                            templates = it.templates + saved,
                            showSaveTemplateDialog = false,
                            saveTemplateName = "",
                        )
                    }
                }
        }
    }

    fun deleteTemplate(templateId: String) {
        screenModelScope.launch {
            slotTemplateRepository.deleteTemplate(templateId)
                .onSuccess {
                    _state.update { state ->
                        state.copy(templates = state.templates.filter { it.id != templateId })
                    }
                }
        }
    }

    // --- Create ---
    fun create() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(status = CreateActivityStatus.Error("El nombre es obligatorio")) }
            return
        }
        if (s.dateMillis == null) {
            _state.update { it.copy(status = CreateActivityStatus.Error("Selecciona una fecha")) }
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
        val datetime = buildDatetime(s.dateMillis, s.timeHour, s.timeMinute)
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
                    RefreshBus.emit(RefreshBus.ACTIVITIES, RefreshBus.COMMUNITY_DETAIL)
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

    private fun buildDatetime(dateMillis: Long, timeHour: Int, timeMinute: Int): Instant {
        val utcDate = Instant.fromEpochMilliseconds(dateMillis)
            .toLocalDateTime(TimeZone.UTC).date
        val localDateTime = LocalDateTime(
            utcDate.year, utcDate.monthNumber, utcDate.dayOfMonth,
            timeHour, timeMinute,
        )
        return localDateTime.toInstant(TimeZone.currentSystemDefault())
    }
}
