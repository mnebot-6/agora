package com.app.community.feature.community.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.data.repository.ActivityRepository
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.CommunityRepository
import com.app.community.core.model.Activity
import com.app.community.core.model.Community
import com.app.community.core.model.CommunityMember
import com.app.community.core.model.MemberRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommunityDetailScreenModel(
    private val communityId: String,
    private val communityRepository: CommunityRepository,
    private val activityRepository: ActivityRepository,
    private val authRepository: AuthRepository,
) : ScreenModel {

    sealed class UiState {
        data object Loading : UiState()
        data class Content(
            val community: Community,
            val members: List<CommunityMember>,
            val activities: List<Activity>,
            val isAdmin: Boolean = false,
        ) : UiState()

        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    fun refresh() {
        loadDetails()
    }

    private fun loadDetails() {
        screenModelScope.launch {
            _uiState.value = UiState.Loading

            val userId = authRepository.currentUserId()

            val communityResult = communityRepository.getCommunity(communityId)
            val community = communityResult.getOrNull()

            if (community == null) {
                _uiState.value = UiState.Error(
                    (communityResult as? com.app.community.core.common.AppResult.Error)?.message
                        ?: "Failed to load community",
                )
                return@launch
            }

            val members = communityRepository.getMembers(communityId).getOrNull().orEmpty()
            val activities = activityRepository.getActivities(communityId).getOrNull().orEmpty()
            val isAdmin = members.any { it.userId == userId && it.role == MemberRole.ADMIN }

            _uiState.value = UiState.Content(
                community = community,
                members = members,
                activities = activities,
                isAdmin = isAdmin,
            )
        }
    }
}
