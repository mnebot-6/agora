package com.app.community.feature.notification.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.NotificationRepository
import com.app.community.core.model.Notification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
}
