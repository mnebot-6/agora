package com.app.community.feature.activity.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.data.repository.ActivityRepository
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.CommunityRepository
import com.app.community.core.model.Activity
import com.app.community.core.model.Community
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ActivityFeedUiState {
    data object Loading : ActivityFeedUiState()
    data class Content(
        val activities: List<Activity>,
        val communities: List<Community> = emptyList(),
        val showCommunityPicker: Boolean = false,
    ) : ActivityFeedUiState()
    data class Error(val message: String) : ActivityFeedUiState()
}

class ActivityFeedScreenModel(
    private val activityRepository: ActivityRepository,
    private val authRepository: AuthRepository,
    private val communityRepository: CommunityRepository,
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
                    var communities = emptyList<Community>()
                    communityRepository.getMyCommunities(userId)
                        .onSuccess { communities = it }
                    _state.value = ActivityFeedUiState.Content(
                        activities = activities,
                        communities = communities,
                    )
                }
                .onError { msg, _ ->
                    _state.value = ActivityFeedUiState.Error(msg)
                }
        }
    }

    fun showCommunityPicker() {
        val current = _state.value
        if (current is ActivityFeedUiState.Content) {
            _state.value = current.copy(showCommunityPicker = true)
        }
    }

    fun hideCommunityPicker() {
        val current = _state.value
        if (current is ActivityFeedUiState.Content) {
            _state.value = current.copy(showCommunityPicker = false)
        }
    }
}
