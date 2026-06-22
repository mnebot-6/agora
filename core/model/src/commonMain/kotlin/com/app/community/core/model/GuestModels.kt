package com.app.community.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Estado de una solicitud de invitado a una actividad. */
@Serializable
enum class GuestRequestStatus {
    @SerialName("pending") PENDING,
    @SerialName("approved") APPROVED,
    @SerialName("rejected") REJECTED,
    @SerialName("cancelled") CANCELLED,
}

/** Datos seguros de la actividad expuestos a un invitado (sin listas de miembros). */
@Serializable
data class GuestActivityInfo(
    val id: String,
    val name: String,
    val description: String? = null,
    val datetime: Instant,
    @SerialName("duration_minutes") val durationMinutes: Int,
    @SerialName("location_name") val locationName: String? = null,
    @SerialName("cost_description") val costDescription: String? = null,
    @SerialName("slot_mode") val slotMode: SlotMode,
    /** null = sin tope (modo ilimitado). */
    val capacity: Int? = null,
    val taken: Int = 0,
)

@Serializable
data class GuestCommunityInfo(
    val id: String,
    val name: String,
)

/** Solicitud propia del caller (para mostrar estado persistente al reabrir el link). */
@Serializable
data class GuestMyRequest(
    val id: String,
    val status: GuestRequestStatus,
    @SerialName("guest_name") val guestName: String,
    @SerialName("requested_at") val requestedAt: Instant,
)

/** Posición disponible para un invitado (limited_with_positions). */
@Serializable
data class GuestPositionInfo(
    val id: String,
    val name: String,
    val available: Int = 0,
)

/** Respuesta de `get_activity_guest_preview`. `status` = "ok" | "not_found". */
@Serializable
data class GuestActivityPreview(
    val status: String,
    val activity: GuestActivityInfo? = null,
    val community: GuestCommunityInfo? = null,
    val positions: List<GuestPositionInfo>? = null,
    @SerialName("is_member") val isMember: Boolean = false,
    @SerialName("my_request") val myRequest: GuestMyRequest? = null,
)

/** Respuesta de `request_guest_slot`. */
@Serializable
data class GuestRequestResult(
    /** "pending" | "approved" | "full" | "already_member". */
    val status: String,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("slot_id") val slotId: String? = null,
)

/** Fila de la cola FIFO de solicitudes pendientes (vista admin). */
@Serializable
data class PendingGuestRequest(
    val id: String,
    @SerialName("activity_id") val activityId: String,
    @SerialName("slot_id") val slotId: String? = null,
    @SerialName("guest_name") val guestName: String,
    @SerialName("guest_phone") val guestPhone: String,
    @SerialName("requested_at") val requestedAt: Instant,
    @SerialName("requested_positions") val requestedPositions: List<String> = emptyList(),
)
