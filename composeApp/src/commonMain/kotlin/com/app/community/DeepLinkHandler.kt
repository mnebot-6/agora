package com.app.community

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DeepLinkHandler {
    private val _pendingInviteCode = MutableStateFlow<String?>(null)
    val pendingInviteCode: StateFlow<String?> = _pendingInviteCode.asStateFlow()

    fun setInviteCode(code: String) {
        _pendingInviteCode.value = code
    }

    fun consumeInviteCode(): String? {
        val code = _pendingInviteCode.value
        _pendingInviteCode.value = null
        return code
    }
}
