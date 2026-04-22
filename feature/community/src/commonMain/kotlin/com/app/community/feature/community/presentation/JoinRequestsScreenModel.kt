package com.app.community.feature.community.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.AppResult
import com.app.community.core.data.repository.CommunityRepository
import com.app.community.core.data.repository.PendingJoinRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class JoinRequestsScreenModel(
    private val communityId: String,
    private val communityRepository: CommunityRepository,
) : ScreenModel {

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val requests: List<PendingJoinRequest>) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _actionInProgress = MutableStateFlow<String?>(null)
    val actionInProgress: StateFlow<String?> = _actionInProgress.asStateFlow()

    init { load() }

    fun load() {
        screenModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = when (val r = communityRepository.getPendingJoinRequests(communityId)) {
                is AppResult.Success -> UiState.Loaded(r.data)
                is AppResult.Error -> UiState.Error(r.message)
            }
        }
    }

    fun approve(requestId: String) = handleAction(requestId) {
        communityRepository.approveJoinRequest(requestId)
    }

    fun reject(requestId: String) = handleAction(requestId) {
        communityRepository.rejectJoinRequest(requestId)
    }

    private fun handleAction(requestId: String, action: suspend () -> AppResult<Unit>) {
        screenModelScope.launch {
            _actionInProgress.value = requestId
            val result = action()
            _actionInProgress.value = null
            when (result) {
                is AppResult.Success -> load()
                is AppResult.Error -> _uiState.value = UiState.Error(result.message)
            }
        }
    }
}
