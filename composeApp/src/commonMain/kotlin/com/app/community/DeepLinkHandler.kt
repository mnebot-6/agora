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

    /** Comunidad cuya alta de Stripe acaba de terminar. Al volver hay que refrescar su estado. */
    private val _pendingConnectCommunityId = MutableStateFlow<String?>(null)
    val pendingConnectCommunityId: StateFlow<String?> = _pendingConnectCommunityId.asStateFlow()

    fun setConnectCommunityId(communityId: String) {
        _pendingConnectCommunityId.value = communityId
    }

    fun consumeConnectCommunityId(): String? {
        val id = _pendingConnectCommunityId.value
        _pendingConnectCommunityId.value = null
        return id
    }

    /** Pago del que acabamos de volver. Al consumirlo se sincroniza contra Stripe. */
    private val _pendingPaymentId = MutableStateFlow<String?>(null)
    val pendingPaymentId: StateFlow<String?> = _pendingPaymentId.asStateFlow()

    fun setPaymentId(paymentId: String) {
        _pendingPaymentId.value = paymentId
    }

    fun consumePaymentId(): String? {
        val id = _pendingPaymentId.value
        _pendingPaymentId.value = null
        return id
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
