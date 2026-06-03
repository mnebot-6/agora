package com.app.community.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SlotStatus {
    @SerialName("available") AVAILABLE,
    @SerialName("reserved") RESERVED,
    @SerialName("paid") PAID,

    /** Slot retenido por un invitado a la espera de aprobación de un admin. */
    @SerialName("pending") PENDING,
}

@Serializable
data class SlotGroup(
    val id: String,
    @SerialName("activity_id") val activityId: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int,
)

@Serializable
data class Position(
    val id: String,
    @SerialName("activity_id") val activityId: String,
    val name: String,
)

@Serializable
data class Slot(
    val id: String,
    @SerialName("activity_id") val activityId: String,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("sort_order") val sortOrder: Int,
    val status: SlotStatus = SlotStatus.AVAILABLE,
    @SerialName("reserved_by") val reservedBy: String? = null,
    @SerialName("reserved_at") val reservedAt: Instant? = null,
    @SerialName("is_guest") val isGuest: Boolean = false,
) {
    val isAvailable: Boolean get() = status == SlotStatus.AVAILABLE
    val isReserved: Boolean get() = status == SlotStatus.RESERVED
    val isPaid: Boolean get() = status == SlotStatus.PAID
    val isPendingGuest: Boolean get() = status == SlotStatus.PENDING
}

@Serializable
data class SlotPosition(
    @SerialName("slot_id") val slotId: String,
    @SerialName("position_id") val positionId: String,
)

@Serializable
data class SubstituteEntry(
    val id: String,
    @SerialName("activity_id") val activityId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("position_id") val positionId: String? = null,
    @SerialName("queued_at") val queuedAt: Instant,
)
