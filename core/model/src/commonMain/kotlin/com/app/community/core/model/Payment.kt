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
 * Por que via se pago una plaza. De esto depende si un reembolso se puede automatizar:
 * lo que no paso por Stripe lo devuelve el admin a mano.
 */
@Serializable(with = PaymentMethodSerializer::class)
enum class PaymentMethod(val wire: String) {
    STRIPE("stripe"),
    /** Bizum, efectivo... El admin lo marco a mano. Agora nunca vio ese dinero. */
    MANUAL("manual"),
    UNKNOWN("unknown"),
}

object PaymentMethodSerializer : KSerializer<PaymentMethod> {
    private val byWire = PaymentMethod.entries.associateBy { it.wire }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PaymentMethod", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PaymentMethod) = encoder.encodeString(value.wire)

    override fun deserialize(decoder: Decoder): PaymentMethod =
        byWire[decoder.decodeString()] ?: PaymentMethod.UNKNOWN
}

/**
 * Estados de un cobro. Igual que SlotStatus, el serializer tolerante es load-bearing:
 * el servidor puede emitir estados nuevos antes de que la app se actualice.
 */
@Serializable(with = PaymentStatusSerializer::class)
enum class PaymentStatus(val wire: String) {
    /** Checkout abierto. La plaza esta retenida. */
    PENDING("pending"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    /** Caduco sin llegar a pagarse. */
    EXPIRED("expired"),
    /** La plaza se libero: este pago espera a que otro la ocupe para devolverse. */
    AWAITING_SUBSTITUTE("awaiting_substitute"),
    /** Hay que devolverlo. Lo ejecuta el worker, con reintentos. */
    REFUND_PENDING("refund_pending"),
    REFUNDED("refunded"),
    /** Hay que devolverlo A MANO: no paso por Stripe y Agora no puede hacerlo. */
    REFUND_OWED("refund_owed"),
    UNKNOWN("unknown"),
}

object PaymentStatusSerializer : KSerializer<PaymentStatus> {
    private val byWire = PaymentStatus.entries.associateBy { it.wire }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PaymentStatus", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PaymentStatus) = encoder.encodeString(value.wire)

    override fun deserialize(decoder: Decoder): PaymentStatus =
        byWire[decoder.decodeString()] ?: PaymentStatus.UNKNOWN
}

/**
 * Un cobro. `amountCents` es una foto del importe en el momento de pagar: si el admin cambia
 * el precio despues, esto NO se mueve. Un registro de dinero no se recalcula nunca.
 */
@Serializable
data class Payment(
    val id: String,
    @SerialName("activity_id") val activityId: String,
    @SerialName("slot_id") val slotId: String? = null,
    /** null si esa persona borro su cuenta. El importe y los ids de Stripe sobreviven. */
    @SerialName("user_id") val userId: String? = null,
    @SerialName("community_id") val communityId: String,
    @SerialName("amount_cents") val amountCents: Int,
    @SerialName("application_fee_cents") val applicationFeeCents: Int = 0,
    val method: PaymentMethod,
    val status: PaymentStatus,
    @SerialName("created_at") val createdAt: Instant,
) {
    val amountLabel: String get() = formatEuros(amountCents)

    /** Cobrado y en pie: ocupa plaza. */
    val isSettled: Boolean
        get() = status == PaymentStatus.SUCCEEDED || status == PaymentStatus.AWAITING_SUBSTITUTE

    /** El admin tiene que devolver esto por su cuenta: nunca paso por Stripe. */
    val needsManualRefund: Boolean get() = status == PaymentStatus.REFUND_OWED
}
