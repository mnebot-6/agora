package com.app.community.feature.notification.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.NotificationRepository
import com.app.community.core.model.Notification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationListUiState(
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val unreadCount: Int = 0,
)

class NotificationListScreenModel(
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(NotificationListUiState())
    val state: StateFlow<NotificationListUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val userId = authRepository.currentUserId() ?: run {
                _state.value = _state.value.copy(isLoading = false, error = "No autenticado")
                return@launch
            }
            notificationRepository.getNotifications(userId)
                .onSuccess { notifications ->
                    _state.value = _state.value.copy(
                        notifications = notifications,
                        isLoading = false,
                        unreadCount = notifications.count { !it.read },
                    )
                }
                .onError { msg, _ ->
                    _state.value = _state.value.copy(isLoading = false, error = msg)
                }
        }
    }

    fun markAsRead(notificationId: String) {
        screenModelScope.launch {
            notificationRepository.markAsRead(notificationId)
                .onSuccess { load() }
        }
    }

    fun markAllAsRead() {
        screenModelScope.launch {
            val userId = authRepository.currentUserId() ?: return@launch
            notificationRepository.markAllAsRead(userId)
                .onSuccess { load() }
        }
    }

    fun deleteNotification(notificationId: String) {
        // Optimistic update
        _state.update { current ->
            val remaining = current.notifications.filterNot { it.id == notificationId }
            current.copy(
                notifications = remaining,
                unreadCount = remaining.count { !it.read },
            )
        }
        screenModelScope.launch {
            notificationRepository.deleteNotification(notificationId)
                .onError { _, _ -> load() } // revert on failure
        }
    }

    fun deleteAll() {
        val previous = _state.value.notifications
        _state.update { it.copy(notifications = emptyList(), unreadCount = 0) }
        screenModelScope.launch {
            val userId = authRepository.currentUserId() ?: return@launch
            notificationRepository.deleteAll(userId)
                .onError { _, _ ->
                    _state.update {
                        it.copy(
                            notifications = previous,
                            unreadCount = previous.count { n -> !n.read },
                        )
                    }
                }
        }
    }
}
