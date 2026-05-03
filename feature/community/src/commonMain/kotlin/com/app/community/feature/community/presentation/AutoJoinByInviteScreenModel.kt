package com.app.community.feature.community.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.RefreshBus
import com.app.community.core.data.repository.CommunityRepository
import com.app.community.core.model.Community
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AutoJoinByInviteScreenModel(
    private val inviteCode: String,
    private val communityRepository: CommunityRepository,
) : ScreenModel {

    sealed class UiState {
        data object Loading : UiState()
        /** Pedir confirmación al usuario antes de enviar la solicitud. */
        data class ConfirmApproval(val community: Community) : UiState()
        /** Mostrar pantalla "solicitud enviada". */
        data class RequestSent(val community: Community) : UiState()
        /** Triggers navigation to CommunityDetailScreen (consumido por la UI). */
        data class NavigateToCommunity(val communityId: String) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        resolve()
    }

    private fun resolve() {
        screenModelScope.launch {
            communityRepository.lookupCommunityByInvite(inviteCode)
                .onSuccess { result ->
                    when (result) {
                        is CommunityRepository.InviteLookupResult.AlreadyMember -> {
                            _state.value = UiState.NavigateToCommunity(result.community.id)
                        }
                        is CommunityRepository.InviteLookupResult.CanJoinDirectly -> {
                            // Auto-join sin confirmación
                            joinNow()
                        }
                        is CommunityRepository.InviteLookupResult.RequiresApproval -> {
                            _state.value = UiState.ConfirmApproval(result.community)
                        }
                        CommunityRepository.InviteLookupResult.NotFound -> {
                            _state.value = UiState.Error("Invitación inválida o caducada")
                        }
                    }
                }
                .onError { msg, _ ->
                    _state.value = UiState.Error(msg)
                }
        }
    }

    /** Llamado desde la UI al confirmar el dialog de aprobación. */
    fun confirmRequest() {
        _state.value = UiState.Loading
        joinNow()
    }

    private fun joinNow() {
        screenModelScope.launch {
            communityRepository.joinByInviteCodeV2(inviteCode)
                .onSuccess { result ->
                    when (result) {
                        is CommunityRepository.JoinByInviteResult.Joined -> {
                            RefreshBus.emit(RefreshBus.COMMUNITIES)
                            _state.value = UiState.NavigateToCommunity(result.community.id)
                        }
                        is CommunityRepository.JoinByInviteResult.AlreadyMember -> {
                            RefreshBus.emit(RefreshBus.COMMUNITIES)
                            _state.value = UiState.NavigateToCommunity(result.community.id)
                        }
                        is CommunityRepository.JoinByInviteResult.Pending -> {
                            _state.value = UiState.RequestSent(result.community)
                        }
                    }
                }
                .onError { msg, _ ->
                    _state.value = UiState.Error(msg)
                }
        }
    }
}
