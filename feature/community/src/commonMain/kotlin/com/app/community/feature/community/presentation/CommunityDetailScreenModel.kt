package com.app.community.feature.community.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.data.repository.ActivityRepository
import com.app.community.core.common.RefreshBus
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.CommunityRepository
import com.app.community.core.data.repository.TagRepository
import com.app.community.core.model.Activity
import com.app.community.core.model.Community
import com.app.community.core.model.CommunityMember
import com.app.community.core.model.CommunityVisibility
import com.app.community.core.model.MemberRole
import com.app.community.core.model.Tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommunityDetailScreenModel(
    private val communityId: String,
    private val communityRepository: CommunityRepository,
    private val activityRepository: ActivityRepository,
    private val authRepository: AuthRepository,
    private val tagRepository: TagRepository,
) : ScreenModel {

    sealed class UiState {
        data object Loading : UiState()
        data class Content(
            val community: Community,
            val members: List<CommunityMember>,
            val activities: List<Activity>,
            val isAdmin: Boolean = false,
            val isCreator: Boolean = false,
            val showEditDialog: Boolean = false,
            val editName: String = "",
            val editDescription: String = "",
            val editVisibility: CommunityVisibility = CommunityVisibility.PRIVATE,
            val editSelectedTagIds: Set<String> = emptySet(),
            val availableTags: List<Tag> = emptyList(),
            val showDeleteDialog: Boolean = false,
            val pendingRequestsCount: Int = 0,
        ) : UiState()

        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    init {
        loadDetails()
        screenModelScope.launch {
            RefreshBus.events.collect { tag ->
                if (tag == RefreshBus.COMMUNITY_DETAIL || tag == RefreshBus.ACTIVITIES) {
                    loadDetails()
                }
            }
        }
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
            val isCreator = community.createdBy == userId

            val pendingRequestsCount =
                if (isAdmin && community.visibility == CommunityVisibility.PUBLIC_APPROVAL) {
                    communityRepository.getPendingJoinRequests(communityId).getOrNull()?.size ?: 0
                } else {
                    0
                }

            _uiState.value = UiState.Content(
                community = community,
                members = members,
                activities = activities,
                isAdmin = isAdmin,
                isCreator = isCreator,
                pendingRequestsCount = pendingRequestsCount,
            )
        }
    }

    // --- Edit community ---

    fun showEditDialog() {
        val current = _uiState.value as? UiState.Content ?: return
        _uiState.value = current.copy(
            showEditDialog = true,
            editName = current.community.name,
            editDescription = current.community.description.orEmpty(),
            editVisibility = current.community.visibility,
            editSelectedTagIds = current.community.tags.map { it.id }.toSet(),
        )
        if (current.availableTags.isEmpty()) {
            screenModelScope.launch {
                tagRepository.getAllTags().onSuccess { tags ->
                    val now = _uiState.value as? UiState.Content ?: return@onSuccess
                    _uiState.value = now.copy(availableTags = tags)
                }
            }
        }
    }

    fun dismissEditDialog() {
        val current = _uiState.value as? UiState.Content ?: return
        _uiState.value = current.copy(showEditDialog = false)
    }

    fun onEditNameChange(name: String) {
        val current = _uiState.value as? UiState.Content ?: return
        _uiState.value = current.copy(editName = name)
    }

    fun onEditDescriptionChange(description: String) {
        val current = _uiState.value as? UiState.Content ?: return
        _uiState.value = current.copy(editDescription = description)
    }

    fun onEditVisibilityChange(visibility: CommunityVisibility) {
        val current = _uiState.value as? UiState.Content ?: return
        _uiState.value = current.copy(editVisibility = visibility)
    }

    fun onEditTagToggle(tagId: String) {
        val current = _uiState.value as? UiState.Content ?: return
        val newSet = when {
            current.editSelectedTagIds.contains(tagId) -> current.editSelectedTagIds - tagId
            current.editSelectedTagIds.size >= 3 -> current.editSelectedTagIds
            else -> current.editSelectedTagIds + tagId
        }
        _uiState.value = current.copy(editSelectedTagIds = newSet)
    }

    fun saveCommunity() {
        val current = _uiState.value as? UiState.Content ?: return
        val name = current.editName.trim()
        if (name.isBlank()) return

        screenModelScope.launch {
            val nameOrDescChanged = name != current.community.name ||
                current.editDescription.trim().ifBlank { null } != current.community.description
            val visibilityChanged = current.editVisibility != current.community.visibility
            val originalTagIds = current.community.tags.map { it.id }.toSet()
            val tagsChanged = current.editSelectedTagIds != originalTagIds

            var firstError: String? = null

            if (nameOrDescChanged) {
                communityRepository.updateCommunity(
                    id = communityId,
                    name = name,
                    description = current.editDescription.trim().ifBlank { null },
                ).onError { msg, _ -> firstError = firstError ?: msg }
            }
            if (firstError == null && visibilityChanged) {
                communityRepository.updateCommunityVisibility(communityId, current.editVisibility)
                    .onError { msg, _ -> firstError = firstError ?: msg }
            }
            if (firstError == null && tagsChanged) {
                communityRepository.updateCommunityTags(
                    communityId,
                    current.editSelectedTagIds.toList(),
                ).onError { msg, _ -> firstError = firstError ?: msg }
            }

            if (firstError == null) {
                RefreshBus.emit(RefreshBus.COMMUNITIES)
                _actionMessage.value = "Comunidad actualizada"
                _uiState.value = current.copy(showEditDialog = false)
                loadDetails()
            } else {
                _actionMessage.value = "Error: $firstError"
            }
        }
    }

    // --- Delete community ---

    fun showDeleteDialog() {
        val current = _uiState.value as? UiState.Content ?: return
        _uiState.value = current.copy(showDeleteDialog = true)
    }

    fun dismissDeleteDialog() {
        val current = _uiState.value as? UiState.Content ?: return
        _uiState.value = current.copy(showDeleteDialog = false)
    }

    fun deleteCommunity() {
        screenModelScope.launch {
            communityRepository.deleteCommunity(communityId)
                .onSuccess {
                    RefreshBus.emit(RefreshBus.COMMUNITIES)
                    _actionMessage.value = "Comunidad eliminada"
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
