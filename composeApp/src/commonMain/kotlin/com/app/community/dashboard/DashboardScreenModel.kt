package com.app.community.dashboard

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.AppResult
import com.app.community.core.data.repository.ActivityRepository
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.SlotRepository
import com.app.community.core.model.Activity
import com.app.community.core.model.SlotMode
import com.app.community.core.model.SlotStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardScreenModel(
    private val activityRepository: ActivityRepository,
    private val slotRepository: SlotRepository,
    private val authRepository: AuthRepository,
) : ScreenModel {

    sealed class UiState {
        data object Loading : UiState()
        data class Content(
            val nextActivity: ActivityWithSlotInfo? = null,
            val upcomingActivities: List<ActivityWithSlotInfo> = emptyList(),
        ) : UiState()

        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    fun refresh() = load()

    private fun load() {
        screenModelScope.launch {
            _uiState.value = UiState.Loading
            val userId = authRepository.currentUserId()
            if (userId == null) {
                _uiState.value = UiState.Error("No autenticado")
                return@launch
            }

            when (val result = activityRepository.getUpcomingActivities(userId)) {
                is AppResult.Success -> {
                    val activities = result.data
                    if (activities.isEmpty()) {
                        _uiState.value = UiState.Content()
                        return@launch
                    }

                    // Enrich each activity with slot info
                    val enriched = activities.map { activity ->
                        enrichActivity(activity, userId)
                    }

                    _uiState.value = UiState.Content(
                        nextActivity = enriched.firstOrNull(),
                        upcomingActivities = enriched,
                    )
                }

                is AppResult.Error -> {
                    _uiState.value = UiState.Error(result.message)
                }
            }
        }
    }

    private suspend fun enrichActivity(activity: Activity, userId: String): ActivityWithSlotInfo {
        if (activity.slotMode == SlotMode.UNLIMITED) {
            return ActivityWithSlotInfo(activity = activity)
        }

        return when (val slotsResult = slotRepository.getSlots(activity.id)) {
            is AppResult.Success -> {
                val slots = slotsResult.data
                val available = slots.count { it.status == SlotStatus.AVAILABLE }
                val isReserved = slots.any {
                    it.reservedBy == userId && (it.status == SlotStatus.RESERVED || it.status == SlotStatus.PAID)
                }

                var queuePosition: Int? = null
                if (!isReserved) {
                    val queueResult = slotRepository.getSubstituteQueue(activity.id)
                    if (queueResult is AppResult.Success) {
                        val idx = queueResult.data.indexOfFirst { it.userId == userId }
                        if (idx >= 0) queuePosition = idx + 1
                    }
                }

                ActivityWithSlotInfo(
                    activity = activity,
                    availableSlots = available,
                    isUserReserved = isReserved,
                    userQueuePosition = queuePosition,
                )
            }

            is AppResult.Error -> ActivityWithSlotInfo(activity = activity)
        }
    }
}
