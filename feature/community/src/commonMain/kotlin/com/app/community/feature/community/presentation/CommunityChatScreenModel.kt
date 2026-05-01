package com.app.community.feature.community.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.CommunityMessageRepository
import com.app.community.core.data.repository.CommunityRepository
import com.app.community.core.data.repository.MessageEvent
import com.app.community.core.model.CommunityMember
import com.app.community.core.model.CommunityMessage
import com.app.community.core.model.MemberRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class CommunityChatScreenModel(
    private val communityId: String,
    private val messageRepo: CommunityMessageRepository,
    private val communityRepo: CommunityRepository,
    private val authRepo: AuthRepository,
) : ScreenModel {

    sealed class UiState {
        data object Loading : UiState()
        data class Error(val message: String) : UiState()
        data class Content(
            val messages: List<CommunityMessage> = emptyList(),
            val draft: String = "",
            val isSending: Boolean = false,
            val currentUserId: String,
            val isAdmin: Boolean,
            val canLoadMore: Boolean = true,
            val isLoadingMore: Boolean = false,
            val editingMessageId: String? = null,
            val editDraft: String = "",
            val actionMessage: String? = null,
        ) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        loadInitial()
    }

    private fun loadInitial() {
        screenModelScope.launch {
            val userId = authRepo.currentUserId()
            if (userId == null) {
                _state.value = UiState.Error("No autenticado")
                return@launch
            }
            // Determinar si es admin (afecta permisos de borrado)
            val isAdmin = communityRepo.getMembers(communityId).getOrNull()
                ?.any { m: CommunityMember -> m.userId == userId && m.role == MemberRole.ADMIN }
                ?: false

            messageRepo.getMessages(communityId, before = null, limit = 50)
                .onSuccess { messages ->
                    _state.value = UiState.Content(
                        messages = messages,
                        currentUserId = userId,
                        isAdmin = isAdmin,
                        canLoadMore = messages.size >= 50,
                    )
                    subscribeToRealtime()
                }
                .onError { msg, _ ->
                    _state.value = UiState.Error(msg)
                }
        }
    }

    private fun subscribeToRealtime() {
        screenModelScope.launch {
            messageRepo.observeMessages(communityId).collect { event ->
                _state.update { current ->
                    if (current !is UiState.Content) return@update current
                    when (event) {
                        is MessageEvent.Inserted -> {
                            // Evita duplicar si el INSERT es del propio usuario y ya esta en la lista
                            if (current.messages.any { it.id == event.message.id }) current
                            else current.copy(messages = listOf(event.message) + current.messages)
                        }
                        is MessageEvent.Updated -> current.copy(
                            messages = current.messages.map {
                                if (it.id == event.message.id) {
                                    // Realtime no incluye el embed profiles(*), así que
                                    // conservamos el profile del mensaje original.
                                    event.message.copy(profiles = it.profiles)
                                } else it
                            },
                        )
                        is MessageEvent.Deleted -> current.copy(
                            messages = current.messages.filterNot { it.id == event.messageId },
                        )
                    }
                }
            }
        }
    }

    fun onDraftChange(value: String) {
        _state.update {
            (it as? UiState.Content)?.copy(draft = value) ?: it
        }
    }

    fun send() {
        val current = _state.value as? UiState.Content ?: return
        val body = current.draft.trim()
        if (body.isBlank() || current.isSending) return
        _state.update { (it as? UiState.Content)?.copy(isSending = true) ?: it }
        screenModelScope.launch {
            messageRepo.sendMessage(communityId, body, current.currentUserId)
                .onSuccess { sent ->
                    _state.update { s ->
                        val c = s as? UiState.Content ?: return@update s
                        // Optimistic add (Realtime tambien lo entregara — el filtro en
                        // subscribeToRealtime evita duplicados).
                        c.copy(
                            messages = listOf(sent) + c.messages,
                            draft = "",
                            isSending = false,
                        )
                    }
                }
                .onError { msg, _ ->
                    _state.update { s ->
                        (s as? UiState.Content)?.copy(
                            isSending = false,
                            actionMessage = "Error: $msg",
                        ) ?: s
                    }
                }
        }
    }

    fun loadOlder() {
        val current = _state.value as? UiState.Content ?: return
        if (!current.canLoadMore || current.isLoadingMore) return
        val oldest = current.messages.lastOrNull() ?: return
        _state.update { (it as? UiState.Content)?.copy(isLoadingMore = true) ?: it }
        screenModelScope.launch {
            messageRepo.getMessages(communityId, before = oldest.createdAt, limit = 50)
                .onSuccess { older ->
                    _state.update { s ->
                        val c = s as? UiState.Content ?: return@update s
                        c.copy(
                            messages = c.messages + older,
                            canLoadMore = older.size >= 50,
                            isLoadingMore = false,
                        )
                    }
                }
                .onError { _, _ ->
                    _state.update { (it as? UiState.Content)?.copy(isLoadingMore = false) ?: it }
                }
        }
    }

    fun startEdit(messageId: String) {
        _state.update { s ->
            val c = s as? UiState.Content ?: return@update s
            val msg = c.messages.firstOrNull { it.id == messageId } ?: return@update s
            c.copy(editingMessageId = messageId, editDraft = msg.body)
        }
    }

    fun cancelEdit() {
        _state.update {
            (it as? UiState.Content)?.copy(editingMessageId = null, editDraft = "") ?: it
        }
    }

    fun onEditDraftChange(value: String) {
        _state.update { (it as? UiState.Content)?.copy(editDraft = value) ?: it }
    }

    fun saveEdit() {
        val current = _state.value as? UiState.Content ?: return
        val id = current.editingMessageId ?: return
        val newBody = current.editDraft.trim()
        if (newBody.isBlank()) return
        screenModelScope.launch {
            messageRepo.editMessage(id, newBody)
                .onSuccess {
                    // Actualización optimista: el Realtime también entregará
                    // el Updated, pero sin profiles. Lo actualizamos ya.
                    val now = Clock.System.now()
                    _state.update { s ->
                        val c = s as? UiState.Content ?: return@update s
                        c.copy(
                            messages = c.messages.map { m ->
                                if (m.id == id) m.copy(body = newBody, editedAt = now) else m
                            },
                            editingMessageId = null,
                            editDraft = "",
                        )
                    }
                }
                .onError { msg, _ ->
                    _state.update {
                        (it as? UiState.Content)?.copy(actionMessage = "Error: $msg") ?: it
                    }
                }
        }
    }

    fun delete(messageId: String) {
        // Borrado optimista: elimina de la UI inmediatamente
        val removed = (_state.value as? UiState.Content)?.messages
            ?.firstOrNull { it.id == messageId }
        _state.update { s ->
            (s as? UiState.Content)?.copy(
                messages = s.messages.filterNot { it.id == messageId },
            ) ?: s
        }
        screenModelScope.launch {
            messageRepo.deleteMessage(messageId).onError { msg, _ ->
                // Si falla, restauramos el mensaje
                _state.update { s ->
                    val c = s as? UiState.Content ?: return@update s
                    if (removed != null) {
                        val restored = (listOf(removed) + c.messages)
                            .sortedByDescending { it.createdAt }
                        c.copy(messages = restored, actionMessage = "Error: $msg")
                    } else {
                        c.copy(actionMessage = "Error: $msg")
                    }
                }
            }
        }
    }

    fun clearActionMessage() {
        _state.update { (it as? UiState.Content)?.copy(actionMessage = null) ?: it }
    }
}
