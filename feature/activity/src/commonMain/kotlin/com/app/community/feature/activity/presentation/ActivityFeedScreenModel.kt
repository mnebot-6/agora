package com.app.community.feature.activity.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.RefreshBus
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
        val adminCommunities: List<Community> = emptyList(),
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
        screenModelScope.launch {
            RefreshBus.events.collect { tag ->
                if (tag == RefreshBus.ACTIVITIES) load()
            }
        }
    }

    fun load() {
        _state.value = ActivityFeedUiState.Loading
        screenModelScope.launch {
            val userId = authRepository.currentUserId() ?: run {
                _state.value = ActivityFeedUiState.Error("No autenticado")
                return@launch
            }

            activityRepository.getUpcomingActivities()
                .onSuccess { activities ->
                    var communities = emptyList<Community>()
                    var adminCommunities = emptyList<Community>()
                    communityRepository.getMyCommunities(userId)
                        .onSuccess { communities = it }
                    communityRepository.getMyAdminCommunities(userId)
                        .onSuccess { adminCommunities = it }
                    _state.value = ActivityFeedUiState.Content(
                        activities = activities,
                        communities = communities,
                        adminCommunities = adminCommunities,
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
