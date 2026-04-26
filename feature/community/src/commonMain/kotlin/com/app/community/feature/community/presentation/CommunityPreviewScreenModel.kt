package com.app.community.feature.community.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.RefreshBus
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.CommunityRepository
import com.app.community.core.model.Community
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CommunityPreviewScreenModel(
    private val communityId: String,
    private val communityRepository: CommunityRepository,
    private val authRepository: AuthRepository,
) : ScreenModel {

    sealed class UiState {
        data object Loading : UiState()
        data class Content(
            val community: Community,
            val isAlreadyMember: Boolean = false,
            val hasPendingRequest: Boolean = false,
            val isJoining: Boolean = false,
            val joinedNow: Boolean = false,
            val pendingNow: Boolean = false,
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    init {
        load()
    }

    fun load() {
        screenModelScope.launch {
            _state.value = UiState.Loading
            val previewResult = communityRepository.getPublicCommunityPreview(communityId)
            val community = previewResult.getOrNull()
            if (community == null) {
                _state.value = UiState.Error(
                    (previewResult as? com.app.community.core.common.AppResult.Error)?.message
                        ?: "No se pudo cargar la comunidad",
                )
                return@launch
            }

            val userId = authRepository.currentUserId()
            val isMember = userId?.let { uid ->
                communityRepository.getMembers(communityId).getOrNull()
                    ?.any { it.userId == uid } == true
            } ?: false

            val hasPending = if (!isMember && userId != null) {
                communityRepository.getPendingJoinRequestForUser(communityId, userId)
                    .getOrNull() != null
            } else false

            _state.value = UiState.Content(
                community = community,
                isAlreadyMember = isMember,
                hasPendingRequest = hasPending,
            )
        }
    }

    fun join() {
        val current = _state.value as? UiState.Content ?: return
        if (current.isJoining || current.isAlreadyMember || current.hasPendingRequest) return
        screenModelScope.launch {
            _state.update {
                (it as? UiState.Content)?.copy(isJoining = true) ?: it
            }
            communityRepository.requestToJoinCommunity(communityId)
                .onSuccess { result ->
                    when (result.status) {
                        "joined", "already_member" -> {
                            RefreshBus.emit(RefreshBus.COMMUNITIES)
                            _state.update {
                                (it as? UiState.Content)?.copy(
                                    isJoining = false,
                                    isAlreadyMember = true,
                                    joinedNow = true,
                                ) ?: it
                            }
                        }
                        "pending" -> {
                            _state.update {
                                (it as? UiState.Content)?.copy(
                                    isJoining = false,
                                    hasPendingRequest = true,
                                    pendingNow = true,
                                ) ?: it
                            }
                            _actionMessage.value = "Solicitud enviada"
                        }
                        else -> {
                            _state.update {
                                (it as? UiState.Content)?.copy(isJoining = false) ?: it
                            }
                        }
                    }
                }
                .onError { msg, _ ->
                    _state.update {
                        (it as? UiState.Content)?.copy(isJoining = false) ?: it
                    }
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
