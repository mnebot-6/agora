package com.app.community.feature.community.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.data.repository.CommunityRepository
import com.app.community.core.data.repository.TagRepository
import com.app.community.core.model.Community
import com.app.community.core.model.Tag
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreUiState(
    val query: String = "",
    val selectedTagIds: Set<String> = emptySet(),
    val availableTags: List<Tag> = emptyList(),
    val results: List<Community> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class ExploreCommunitiesScreenModel(
    private val communityRepository: CommunityRepository,
    private val tagRepository: TagRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    private val _queryFlow = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    fun init() {
        // no-op: init block runs anyway
    }

    init {
        screenModelScope.launch {
            tagRepository.getAllTags().onSuccess { tags ->
                _state.update { it.copy(availableTags = tags) }
            }
        }
        search("", emptySet())
        observeQuery()
    }

    @OptIn(FlowPreview::class)
    private fun observeQuery() {
        screenModelScope.launch {
            _queryFlow
                .drop(1) // ignore initial empty
                .debounce(300)
                .distinctUntilChanged()
                .collect { q ->
                    search(q, _state.value.selectedTagIds)
                }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        _queryFlow.value = value
    }

    fun onTagToggle(tagId: String) {
        val current = _state.value.selectedTagIds
        val newSet = if (current.contains(tagId)) current - tagId else current + tagId
        _state.update { it.copy(selectedTagIds = newSet) }
        search(_state.value.query, newSet)
    }

    fun clearTags() {
        _state.update { it.copy(selectedTagIds = emptySet()) }
        search(_state.value.query, emptySet())
    }

    private fun search(query: String, tagIds: Set<String>) {
        screenModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            communityRepository.searchPublicCommunities(
                query = query.trim().ifBlank { null },
                tagIds = tagIds.toList(),
                limit = 30,
                offset = 0,
            )
                .onSuccess { list ->
                    // Solo mostrar comunidades raíz (sin comunidad padre) para
                    // evitar mostrar subcomunidades anidadas en el explorador.
                    val rootOnly = list.filter { it.parentId == null }
                    _state.update { it.copy(results = rootOnly, isLoading = false) }
                }
                .onError { msg, _ ->
                    _state.update { it.copy(isLoading = false, error = msg) }
                }
        }
    }
}
