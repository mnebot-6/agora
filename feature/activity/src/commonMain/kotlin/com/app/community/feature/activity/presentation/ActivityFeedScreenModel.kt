package com.app.community.feature.activity.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.data.repository.ActivityRepository
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.model.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ActivityFeedUiState {
    data object Loading : ActivityFeedUiState()
    data class Content(val activities: List<Activity>) : ActivityFeedUiState()
    data class Error(val message: String) : ActivityFeedUiState()
}

class ActivityFeedScreenModel(
    private val activityRepository: ActivityRepository,
    private val authRepository: AuthRepository,
) : ScreenModel {

    private val _state = MutableStateFlow<ActivityFeedUiState>(ActivityFeedUiState.Loading)
    val state: StateFlow<ActivityFeedUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ActivityFeedUiState.Loading
        screenModelScope.launch {
            val userId = authRepository.currentUserId() ?: run {
                _state.value = ActivityFeedUiState.Error("No autenticado")
                return@launch
            }

            activityRepository.getUpcomingActivities(userId)
                .onSuccess { activities ->
                    _state.value = ActivityFeedUiState.Content(activities)
                }
                .onError { msg, _ ->
                    _state.value = ActivityFeedUiState.Error(msg)
                }
        }
    }
}
