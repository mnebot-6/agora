package com.app.community.dashboard

import com.app.community.core.model.Activity

data class ActivityWithSlotInfo(
    val activity: Activity,
    val availableSlots: Int = 0,
    val isUserReserved: Boolean = false,
    val userQueuePosition: Int? = null,
)
