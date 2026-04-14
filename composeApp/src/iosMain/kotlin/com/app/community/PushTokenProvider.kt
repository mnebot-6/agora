package com.app.community

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val pushTokenFlow = MutableStateFlow<String?>(null)

// Called from Swift: PushTokenProviderKt.setPushToken(token: "...")
fun setPushToken(token: String) {
    pushTokenFlow.value = token
}

actual suspend fun fetchPushToken(): String? =
    withTimeoutOrNull(10_000) { pushTokenFlow.filterNotNull().first() }
