package com.app.community.feature.community.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.AppResult
import com.app.community.core.common.RefreshBus
import com.app.community.core.data.repository.TagRepository
import com.app.community.core.domain.community.CreateCommunityUseCase
import com.app.community.core.model.Community
import com.app.community.core.model.CommunityVisibility
import com.app.community.core.model.Tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateCommunityScreenModel(
    private val createCommunityUseCase: CreateCommunityUseCase,
    private val tagRepository: TagRepository,
) : ScreenModel {

    data class FormState(
        val name: String = "",
        val description: String = "",
        val visibility: CommunityVisibility = CommunityVisibility.PRIVATE,
        val selectedTagIds: Set<String> = emptySet(),
        val availableTags: List<Tag> = emptyList(),
    )

    sealed class UiState {
        data object Idle : UiState()
        data object Loading : UiState()
        data class Success(val community: Community) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        screenModelScope.launch {
            when (val result = tagRepository.getAllTags()) {
                is AppResult.Success -> _form.update { it.copy(availableTags = result.data) }
                is AppResult.Error -> Unit
            }
        }
    }

    fun onNameChange(value: String) = _form.update { it.copy(name = value) }
    fun onDescriptionChange(value: String) = _form.update { it.copy(description = value) }
    fun onVisibilityChange(value: CommunityVisibility) = _form.update { it.copy(visibility = value) }

    fun onTagToggle(tagId: String) = _form.update { state ->
        val newSet = when {
            state.selectedTagIds.contains(tagId) -> state.selectedTagIds - tagId
            state.selectedTagIds.size >= 3 -> state.selectedTagIds
            else -> state.selectedTagIds + tagId
        }
        state.copy(selectedTagIds = newSet)
    }

    fun create() {
        val state = _form.value
        if (state.name.isBlank()) {
            _uiState.value = UiState.Error("Name is required")
            return
        }
        if (state.selectedTagIds.isEmpty()) {
            _uiState.value = UiState.Error("Select at least 1 category")
            return
        }
        screenModelScope.launch {
            _uiState.value = UiState.Loading
            createCommunityUseCase(
                name = state.name.trim(),
                description = state.description.trim().ifBlank { null },
                visibility = state.visibility,
                tagIds = state.selectedTagIds.toList(),
            )
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
