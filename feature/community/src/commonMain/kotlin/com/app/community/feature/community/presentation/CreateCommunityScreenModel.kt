package com.app.community.feature.community.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.domain.community.CreateCommunityUseCase
import com.app.community.core.model.Community
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CreateCommunityScreenModel(
    private val createCommunityUseCase: CreateCommunityUseCase,
) : ScreenModel {

    sealed class UiState {
        data object Idle : UiState()
        data object Loading : UiState()
        data class Success(val community: Community) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    var name: String = ""
        private set

    var description: String = ""
        private set

    fun onNameChange(value: String) {
        name = value
    }

    fun onDescriptionChange(value: String) {
        description = value
    }

    fun create() {
        if (name.isBlank()) {
            _uiState.value = UiState.Error("Name is required")
            return
        }
        screenModelScope.launch {
            _uiState.value = UiState.Loading
            createCommunityUseCase(
                name = name.trim(),
                description = description.trim().ifBlank { null },
            )
                .onSuccess { community ->
                    _uiState.value = UiState.Success(community)
                }
                .onError { message, _ ->
                    _uiState.value = UiState.Error(message)
                }
        }
    }
}
