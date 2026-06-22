package com.app.community

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DeepLinkHandler {
    private val _pendingInviteCode = MutableStateFlow<String?>(null)
    val pendingInviteCode: StateFlow<String?> = _pendingInviteCode.asStateFlow()

    private val _pendingActivityCode = MutableStateFlow<String?>(null)
    val pendingActivityCode: StateFlow<String?> = _pendingActivityCode.asStateFlow()

    fun setInviteCode(code: String) {
        _pendingInviteCode.value = code
    }

    fun consumeInviteCode(): String? {
        val code = _pendingInviteCode.value
        _pendingInviteCode.value = null
        return code
    }

    /** Deep link de invitado a actividad (https://share-agora.app/a/{code} o agora://activity/{code}). */
    fun setActivityCode(code: String) {
        _pendingActivityCode.value = code
    }

    fun consumeActivityCode(): String? {
        val code = _pendingActivityCode.value
        _pendingActivityCode.value = null
        return code
    }

    private val _pendingNotificationActivityId = MutableStateFlow<String?>(null)
    val pendingNotificationActivityId: StateFlow<String?> = _pendingNotificationActivityId.asStateFlow()

    fun setNotificationActivityId(activityId: String) {
        _pendingNotificationActivityId.value = activityId
    }

    fun consumeNotificationActivityId(): String? {
        val id = _pendingNotificationActivityId.value
        _pendingNotificationActivityId.value = null
        return id
    }
}
