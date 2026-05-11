package com.app.community.feature.community.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.RefreshBus
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.BlockRepository
import com.app.community.core.data.repository.CommunityRepository
import com.app.community.core.data.repository.ReportReason
import com.app.community.core.data.repository.ReportRepository
import com.app.community.core.model.CommunityMember
import com.app.community.core.model.MemberRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MemberManagementUiState(
    val members: List<CommunityMember> = emptyList(),
    val currentUserId: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val actionMessage: String? = null,
)

class MemberManagementScreenModel(
    private val communityId: String,
    private val communityRepository: CommunityRepository,
    private val authRepository: AuthRepository,
    private val blockRepository: BlockRepository,
    private val reportRepository: ReportRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(MemberManagementUiState())
    val state: StateFlow<MemberManagementUiState> = _state.asStateFlow()

    init {
        load()
        screenModelScope.launch {
            RefreshBus.events.collect { tag ->
                if (tag == RefreshBus.COMMUNITY_DETAIL) load()
            }
        }
    }

    fun load() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val userId = authRepository.currentUserId() ?: run {
                _state.value = _state.value.copy(isLoading = false, error = "No autenticado")
                return@launch
            }
            val membersResult = communityRepository.getMembers(communityId)
            membersResult
                .onSuccess { members ->
                    _state.value = _state.value.copy(
                        members = members,
                        currentUserId = userId,
                        isLoading = false,
                    )
                }
                .onError { msg, _ ->
                    _state.value = _state.value.copy(isLoading = false, error = msg)
                }
        }
    }

    fun toggleRole(member: CommunityMember) {
        val newRole = if (member.role == MemberRole.ADMIN) MemberRole.USER else MemberRole.ADMIN
        screenModelScope.launch {
            communityRepository.updateMemberRole(communityId, member.userId, newRole)
                .onSuccess {
                    RefreshBus.emit(RefreshBus.COMMUNITY_DETAIL)
                    _state.value = _state.value.copy(
                        actionMessage = if (newRole == MemberRole.ADMIN) {
                            "${member.profiles?.displayName ?: "Usuario"} ahora es admin"
                        } else {
                            "${member.profiles?.displayName ?: "Usuario"} ya no es admin"
                        },
                    )
                    load()
                }
                .onError { msg, _ ->
                    _state.value = _state.value.copy(actionMessage = "Error: $msg")
                }
        }
    }

    fun removeMember(member: CommunityMember) {
        screenModelScope.launch {
            communityRepository.removeMember(communityId, member.userId)
                .onSuccess {
                    RefreshBus.emit(RefreshBus.COMMUNITY_DETAIL)
                    _state.value = _state.value.copy(
                        actionMessage = "${member.profiles?.displayName ?: "Usuario"} eliminado",
                    )
                    load()
                }
                .onError { msg, _ ->
                    _state.value = _state.value.copy(actionMessage = "Error: $msg")
                }
        }
    }

    fun blockMember(member: CommunityMember) {
        val me = _state.value.currentUserId
        if (me.isBlank() || member.userId == me) return
        screenModelScope.launch {
            blockRepository.blockUser(me, member.userId)
                .onSuccess {
                    _state.value = _state.value.copy(
                        actionMessage = "${member.profiles?.displayName ?: "Usuario"} bloqueado",
                    )
                }
                .onError { msg, _ ->
                    _state.value = _state.value.copy(actionMessage = "Error: $msg")
                }
        }
    }

    fun reportMember(member: CommunityMember, reason: ReportReason) {
        val me = _state.value.currentUserId
        if (me.isBlank() || member.userId == me) return
        screenModelScope.launch {
            reportRepository.reportProfile(
                reporterId = me,
                targetUserId = member.userId,
                communityId = communityId,
                reason = reason,
            )
                .onSuccess {
                    _state.value = _state.value.copy(actionMessage = "Reporte enviado")
                }
                .onError { msg, _ ->
                    _state.value = _state.value.copy(actionMessage = "Error: $msg")
                }
        }
    }

    fun clearActionMessage() {
        _state.value = _state.value.copy(actionMessage = null)
    }
}
