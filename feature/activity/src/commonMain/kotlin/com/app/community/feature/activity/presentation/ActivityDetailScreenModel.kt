package com.app.community.feature.activity.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.RefreshBus
import com.app.community.core.data.repository.ActivityRepository
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.CommunityRepository
import com.app.community.core.data.repository.ProfileRepository
import com.app.community.core.data.repository.SlotRepository
import com.app.community.core.model.Activity
import com.app.community.core.model.ActivityStatus
import com.app.community.core.model.MemberRole
import com.app.community.core.model.Position
import com.app.community.core.model.Profile
import com.app.community.core.model.Slot
import com.app.community.core.model.SlotGroup
import com.app.community.core.model.SlotMode
import com.app.community.core.model.SlotPosition
import com.app.community.core.model.SlotStatus
import com.app.community.core.model.SubstituteEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SlotWithProfile(
    val slot: Slot,
    val profile: Profile? = null,
    val positionIds: List<String> = emptyList(),
    val positionNames: List<String> = emptyList(),
)

data class GroupWithSlots(
    val group: SlotGroup,
    val slots: List<SlotWithProfile>,
)

sealed class ActivityDetailUiState {
    data object Loading : ActivityDetailUiState()
    data class Content(
        val activity: Activity,
        val slots: List<SlotWithProfile>,
        val groups: List<GroupWithSlots> = emptyList(),
        val positions: List<Position> = emptyList(),
        val substituteQueue: List<SubstituteEntry>,
        val currentUserId: String,
        val isAdmin: Boolean,
        val participantCount: Int = 0,
        val isUserJoined: Boolean = false,
    ) : ActivityDetailUiState()

    data class Error(val message: String) : ActivityDetailUiState()
}

class ActivityDetailScreenModel(
    private val activityId: String,
    private val activityRepository: ActivityRepository,
    private val slotRepository: SlotRepository,
    private val authRepository: AuthRepository,
    private val communityRepository: CommunityRepository,
    private val profileRepository: ProfileRepository,
) : ScreenModel {

    private val _state = MutableStateFlow<ActivityDetailUiState>(ActivityDetailUiState.Loading)
    val state: StateFlow<ActivityDetailUiState> = _state.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    init {
        load()
        screenModelScope.launch {
            RefreshBus.events.collect { tag ->
                if (tag == RefreshBus.ACTIVITY_DETAIL) load()
            }
        }
    }

    fun load() {
        _state.value = ActivityDetailUiState.Loading
        screenModelScope.launch {
            val userId = authRepository.currentUserId() ?: run {
                _state.value = ActivityDetailUiState.Error("No autenticado")
                return@launch
            }

            val activityResult = activityRepository.getActivity(activityId)
            val activity = activityResult.getOrNull() ?: run {
                _state.value = ActivityDetailUiState.Error("No se pudo cargar la actividad")
                return@launch
            }

            // Check if user is admin
            val membersResult = communityRepository.getMembers(activity.communityId)
            val members = membersResult.getOrNull() ?: emptyList()
            val isAdmin = members.any { it.userId == userId && it.role == MemberRole.ADMIN }

            loadSlots(activity, userId, isAdmin)
        }
    }

    private suspend fun loadSlots(activity: Activity, userId: String, isAdmin: Boolean) {
        val slotsResult = slotRepository.getSlots(activityId)
        val slots = slotsResult.getOrNull() ?: emptyList()
        val substituteQueue = slotRepository.getSubstituteQueue(activityId).getOrNull() ?: emptyList()

        when (activity.slotMode) {
            SlotMode.UNLIMITED -> {
                val slotsWithProfiles = loadProfiles(slots)
                _state.value = ActivityDetailUiState.Content(
                    activity = activity,
                    slots = slotsWithProfiles,
                    substituteQueue = substituteQueue,
                    currentUserId = userId,
                    isAdmin = isAdmin,
                    participantCount = slots.count { it.status != SlotStatus.AVAILABLE },
                    isUserJoined = slots.any { it.reservedBy == userId },
                )
            }

            SlotMode.LIMITED -> {
                val slotsWithProfiles = loadProfiles(slots)
                _state.value = ActivityDetailUiState.Content(
                    activity = activity,
                    slots = slotsWithProfiles,
                    substituteQueue = substituteQueue,
                    currentUserId = userId,
                    isAdmin = isAdmin,
                    participantCount = slots.count { it.status != SlotStatus.AVAILABLE },
                    isUserJoined = slots.any { it.reservedBy == userId },
                )
            }

            SlotMode.LIMITED_WITH_POSITIONS -> {
                val positions = slotRepository.getPositions(activityId).getOrNull() ?: emptyList()
                val groups = slotRepository.getSlotGroups(activityId).getOrNull() ?: emptyList()
                val slotIds = slots.map { it.id }
                val slotPositions = if (slotIds.isNotEmpty()) {
                    slotRepository.getSlotPositions(slotIds).getOrNull() ?: emptyList()
                } else emptyList()

                val positionMap = positions.associateBy { it.id }
                val slotPositionMap = slotPositions.groupBy { it.slotId }

                val slotsWithProfiles = loadProfilesWithPositions(slots, slotPositionMap, positionMap)

                val groupsWithSlots = groups.map { group ->
                    GroupWithSlots(
                        group = group,
                        slots = slotsWithProfiles.filter { it.slot.groupId == group.id },
                    )
                }

                _state.value = ActivityDetailUiState.Content(
                    activity = activity,
                    slots = slotsWithProfiles,
                    groups = groupsWithSlots,
                    positions = positions,
                    substituteQueue = substituteQueue,
                    currentUserId = userId,
                    isAdmin = isAdmin,
                    participantCount = slots.count { it.status != SlotStatus.AVAILABLE },
                    isUserJoined = slots.any { it.reservedBy == userId },
                )
            }
        }
    }

    private suspend fun loadProfiles(slots: List<Slot>): List<SlotWithProfile> {
        val userIds = slots.mapNotNull { it.reservedBy }.distinct()
        val profiles = userIds.mapNotNull { uid ->
            profileRepository.getProfile(uid).getOrNull()
        }
        val profileMap = profiles.associateBy { it.id }
        return slots.map { slot ->
            SlotWithProfile(slot, slot.reservedBy?.let { profileMap[it] })
        }
    }

    private suspend fun loadProfilesWithPositions(
        slots: List<Slot>,
        slotPositionMap: Map<String, List<SlotPosition>>,
        positionMap: Map<String, Position>,
    ): List<SlotWithProfile> {
        val userIds = slots.mapNotNull { it.reservedBy }.distinct()
        val profiles = userIds.mapNotNull { uid ->
            profileRepository.getProfile(uid).getOrNull()
        }
        val profileMap = profiles.associateBy { it.id }
        return slots.map { slot ->
            val slotPositionEntries = slotPositionMap[slot.id] ?: emptyList()
            val posIds = slotPositionEntries.map { it.positionId }
            val posNames = slotPositionEntries.mapNotNull { sp -> positionMap[sp.positionId]?.name }
            SlotWithProfile(
                slot = slot,
                profile = slot.reservedBy?.let { profileMap[it] },
                positionIds = posIds,
                positionNames = posNames,
            )
        }
    }

    fun reserveSlot(slotId: String) {
        screenModelScope.launch {
            slotRepository.reserveSlot(slotId)
                .onSuccess { success ->
                    if (success) {
                        _actionMessage.value = "Plaza reservada"
                        load()
                    } else {
                        _actionMessage.value = "La plaza ya no está disponible"
                    }
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    fun releaseSlot(slotId: String) {
        screenModelScope.launch {
            slotRepository.releaseSlot(slotId)
                .onSuccess { success ->
                    if (success) {
                        _actionMessage.value = "Plaza liberada"
                        load()
                    } else {
                        _actionMessage.value = "No se pudo liberar la plaza"
                    }
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    fun markSlotPaid(slotId: String) {
        screenModelScope.launch {
            slotRepository.markSlotPaid(slotId)
                .onSuccess { success ->
                    if (success) {
                        _actionMessage.value = "Marcado como pagado"
                        load()
                    } else {
                        _actionMessage.value = "No se pudo marcar como pagado"
                    }
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    fun joinUnlimited() {
        screenModelScope.launch {
            // For unlimited mode, create a new slot and reserve it
            slotRepository.createSlots(activityId, 1)
                .onSuccess {
                    // Reload to get the new slot, then reserve it
                    val slots = slotRepository.getSlots(activityId).getOrNull() ?: return@onSuccess
                    val availableSlot = slots.lastOrNull { it.isAvailable }
                    if (availableSlot != null) {
                        slotRepository.reserveSlot(availableSlot.id)
                    }
                    load()
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    fun leaveUnlimited() {
        val currentState = _state.value as? ActivityDetailUiState.Content ?: return
        val userId = authRepository.currentUserId() ?: return
        val mySlot = currentState.slots.firstOrNull { it.slot.reservedBy == userId }
        if (mySlot != null) {
            releaseSlot(mySlot.slot.id)
        }
    }

    fun joinSubstituteQueue(positionId: String? = null) {
        screenModelScope.launch {
            slotRepository.joinSubstituteQueue(activityId, positionId)
                .onSuccess {
                    _actionMessage.value = "Te has apuntado como suplente"
                    load()
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    fun leaveSubstituteQueue(positionId: String? = null) {
        screenModelScope.launch {
            slotRepository.leaveSubstituteQueue(activityId, positionId)
                .onSuccess {
                    _actionMessage.value = "Has salido de la cola de suplentes"
                    load()
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    fun archiveActivity() {
        screenModelScope.launch {
            activityRepository.updateActivityStatus(activityId, ActivityStatus.ARCHIVED)
                .onSuccess {
                    RefreshBus.emit(RefreshBus.ACTIVITIES, RefreshBus.COMMUNITY_DETAIL)
                    _actionMessage.value = "Actividad archivada"
                    load()
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    fun deleteActivity() {
        screenModelScope.launch {
            activityRepository.deleteActivity(activityId)
                .onSuccess {
                    RefreshBus.emit(RefreshBus.ACTIVITIES, RefreshBus.COMMUNITY_DETAIL)
                    _actionMessage.value = "Actividad eliminada"
                    _deleted.value = true
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
