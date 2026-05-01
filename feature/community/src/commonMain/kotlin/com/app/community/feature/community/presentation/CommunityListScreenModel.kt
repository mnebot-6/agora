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

/**
 * Nodo de la lista: una comunidad y sus hijas en las que el usuario también es miembro.
 * Hijas cuyo padre no está en mis comunidades aparecen como nodos raíz (sin children).
 */
data class CommunityNode(
    val community: Community,
    val children: List<Community> = emptyList(),
)

class CommunityListScreenModel(
    private val getMyCommunitiesUseCase: GetMyCommunitiesUseCase,
) : ScreenModel {

    sealed class UiState {
        data object Loading : UiState()
        data class Content(val nodes: List<CommunityNode>) : UiState()
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
            getMyCommunitiesUseCase(rootOnly = false)
                .onSuccess { all ->
                    _uiState.value = UiState.Content(buildTree(all))
                }
                .onError { message, _ ->
                    _uiState.value = UiState.Error(message)
                }
        }
    }

    private fun buildTree(all: List<Community>): List<CommunityNode> {
        val byId = all.associateBy { it.id }
        // Hijas válidas: parentId apunta a una comunidad que también está en mi lista.
        // Las huérfanas (cuyo padre no está en mi lista) se quedan como roots.
        val (children, roots) = all.partition { c ->
            c.parentId != null && byId.containsKey(c.parentId)
        }
        val childrenByParent = children.groupBy { it.parentId!! }
        return roots.map { CommunityNode(it, childrenByParent[it.id].orEmpty()) }
    }
}
