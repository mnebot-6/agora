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

/**
 * El servidor puede empezar a emitir estados nuevos antes de que la app se actualice. Con un enum
 * cerrado, decodeList tumba la lista ENTERA de plazas ante un valor desconocido, no solo la fila
 * mala. UNKNOWN y su serializer son load-bearing: no los quites.
 */
@Serializable(with = SlotStatusSerializer::class)
enum class SlotStatus(val wire: String) {
    AVAILABLE("available"),
    RESERVED("reserved"),
    PAID("paid"),

    /** Slot retenido por un invitado a la espera de aprobación de un admin. */
    PENDING("pending"),

    /** Alguien tiene el Checkout abierto sobre esta plaza. La retiene hasta `holdExpiresAt`. */
    PENDING_PAYMENT("pending_payment"),

    /** Estado que esta version de la app no conoce. Nunca reclamable, nunca liberable. */
    UNKNOWN("unknown"),
}

object SlotStatusSerializer : KSerializer<SlotStatus> {
    private val byWire = SlotStatus.entries.associateBy { it.wire }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SlotStatus", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SlotStatus) = encoder.encodeString(value.wire)

    override fun deserialize(decoder: Decoder): SlotStatus =
        byWire[decoder.decodeString()] ?: SlotStatus.UNKNOWN
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
    @SerialName("guest_label") val guestLabel: String? = null,
) {
    val isAvailable: Boolean get() = status == SlotStatus.AVAILABLE
    val isReserved: Boolean get() = status == SlotStatus.RESERVED
    val isPaid: Boolean get() = status == SlotStatus.PAID
    val isPendingGuest: Boolean get() = status == SlotStatus.PENDING
    val isPendingPayment: Boolean get() = status == SlotStatus.PENDING_PAYMENT
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
