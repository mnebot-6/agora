package com.app.community.core.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Lightweight event bus for signaling data changes across screens.
 * ScreenModels collect events in their screenModelScope to refresh
 * when relevant mutations happen in other screens.
 */
object RefreshBus {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 20)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun emit(vararg tags: String) {
        tags.forEach { _events.tryEmit(it) }
    }

    const val COMMUNITIES = "communities"
    const val COMMUNITY_DETAIL = "community_detail"
    const val ACTIVITIES = "activities"
    const val ACTIVITY_DETAIL = "activity_detail"
}
