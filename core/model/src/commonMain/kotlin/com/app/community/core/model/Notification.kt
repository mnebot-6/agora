package com.app.community.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class NotificationType {
    @SerialName("new_activity") NEW_ACTIVITY,
    @SerialName("slot_released") SLOT_RELEASED,
    @SerialName("substitute_promoted") SUBSTITUTE_PROMOTED,
}

@Serializable
data class Notification(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val data: JsonObject? = null,
    val read: Boolean = false,
    @SerialName("created_at") val createdAt: Instant,
)
