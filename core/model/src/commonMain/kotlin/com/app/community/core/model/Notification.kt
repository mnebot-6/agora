package com.app.community.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject

/**
 * El servidor puede empezar a emitir tipos nuevos antes de que la app se actualice. Con un enum
 * cerrado, decodeList tumba la lista ENTERA ante un valor desconocido, no solo la fila mala.
 * UNKNOWN y su serializer son load-bearing: no los quites.
 */
@Serializable(with = NotificationTypeSerializer::class)
enum class NotificationType(val wire: String) {
    NEW_ACTIVITY("new_activity"),
    SLOT_RELEASED("slot_released"),
    SUBSTITUTE_PROMOTED("substitute_promoted"),
    ACTIVITY_REMINDER("activity_reminder"),
    JOIN_REQUEST_RECEIVED("join_request_received"),
    JOIN_REQUEST_APPROVED("join_request_approved"),
    JOIN_REQUEST_REJECTED("join_request_rejected"),
    GUEST_REQUEST_RECEIVED("guest_request_received"),
    GUEST_REQUEST_APPROVED("guest_request_approved"),
    GUEST_REQUEST_REJECTED("guest_request_rejected"),
    PAYMENT_CONFIRMED("payment_confirmed"),
    SLOT_REMOVED("slot_removed"),
    ACTIVITY_FULL("activity_full"),
    ACTIVITY_CANCELLED("activity_cancelled"),
    ACTIVITY_UPDATED("activity_updated"),
    SLOT_ASSIGNED("slot_assigned"),

    /** Se te ofrece una plaza liberada como suplente. Caduca a las 6 h. */
    SUBSTITUTE_OFFER("substitute_offer"),

    /** Otra persona ha ocupado tu plaza liberada y te hemos devuelto el dinero. */
    PAYMENT_REFUNDED("payment_refunded"),

    /** Al admin: hay dinero que devolver a mano porque no paso por Stripe. */
    REFUND_OWED("refund_owed"),
    UNKNOWN("unknown"),
}

object NotificationTypeSerializer : KSerializer<NotificationType> {
    private val byWire = NotificationType.entries.associateBy { it.wire }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NotificationType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: NotificationType) =
        encoder.encodeString(value.wire)

    override fun deserialize(decoder: Decoder): NotificationType =
        byWire[decoder.decodeString()] ?: NotificationType.UNKNOWN
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
