package com.app.community.feature.community.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.RefreshBus
import com.app.community.core.domain.community.JoinCommunityUseCase
import com.app.community.core.model.Community
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class JoinCommunityScreenModel(
    private val joinCommunityUseCase: JoinCommunityUseCase,
) : ScreenModel {

    sealed class UiState {
        data object Idle : UiState()
        data object Loading : UiState()
        data class Success(val community: Community) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    var inviteCode: String = ""
        private set

    fun onInviteCodeChange(value: String) {
        inviteCode = value.uppercase().take(8)
    }

    fun join() {
        if (inviteCode.length != 8) {
            _uiState.value = UiState.Error("Invite code must be 8 characters")
            return
        }
        screenModelScope.launch {
            _uiState.value = UiState.Loading
            joinCommunityUseCase(inviteCode)
                .onSuccess { community ->
                    RefreshBus.emit(RefreshBus.COMMUNITIES)
                    _uiState.value = UiState.Success(community)
                }
                .onError { message, _ ->
                    _uiState.value = UiState.Error(message)
                }
        }
    }
}
