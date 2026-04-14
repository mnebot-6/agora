package com.app.community.feature.community.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.RefreshBus
import com.app.community.core.domain.community.GetMyCommunitiesUseCase
import com.app.community.core.model.Community
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommunityListScreenModel(
    private val getMyCommunitiesUseCase: GetMyCommunitiesUseCase,
) : ScreenModel {

    sealed class UiState {
        data object Loading : UiState()
        data class Content(val communities: List<Community>) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadCommunities()
        screenModelScope.launch {
            RefreshBus.events.collect { tag ->
                if (tag == RefreshBus.COMMUNITIES) loadCommunities()
            }
        }
    }

    fun refresh() {
        loadCommunities()
    }

    private fun loadCommunities() {
        screenModelScope.launch {
            _uiState.value = UiState.Loading
            getMyCommunitiesUseCase()
                .onSuccess { communities ->
                    _uiState.value = UiState.Content(communities)
                }
                .onError { message, _ ->
                    _uiState.value = UiState.Error(message)
                }
        }
    }
}
