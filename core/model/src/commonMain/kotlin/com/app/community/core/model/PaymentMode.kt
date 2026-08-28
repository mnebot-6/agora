package com.app.community.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Como se cobra una actividad. Lo elige quien la crea y no cambia despues.
 *
 * El serializer tolerante es load-bearing, por la misma razon que el de SlotStatus: con un
 * enum cerrado, un modo que el servidor empiece a emitir antes de que la app se actualice
 * tumbaria la lista ENTERA de actividades, no solo la fila mala.
 *
 * Un modo desconocido se lee como EXTERNAL a proposito: es el unico repliegue seguro.
 * Con FREE se regalarian plazas de pago; con AGORA se abriria un Checkout que el servidor
 * va a rechazar. EXTERNAL reserva al instante y deja el cobro en manos del admin, que es
 * como se comportaba la app entera antes de que existiera Stripe.
 */
@Serializable(with = PaymentModeSerializer::class)
enum class PaymentMode(val wire: String) {
    /** Sin coste. Reservar es instantaneo. */
    FREE("free"),

    /** Hay importe, pero Agora no mueve el dinero: lo cobra el admin por su cuenta. */
    EXTERNAL("external"),

    /** Reservar sale a Stripe Checkout. Requiere cobrador dado de alta en la comunidad. */
    AGORA("agora"),
}

object PaymentModeSerializer : KSerializer<PaymentMode> {
    private val byWire = PaymentMode.entries.associateBy { it.wire }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PaymentMode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PaymentMode) = encoder.encodeString(value.wire)

    override fun deserialize(decoder: Decoder): PaymentMode =
        byWire[decoder.decodeString()] ?: PaymentMode.EXTERNAL
}

/**
 * El modo que se ejecuta de verdad. `paymentMode` es la intencion del admin; si la
 * comunidad pierde la capacidad de cobrar, una actividad 'agora' se comporta como externa
 * hasta que la recupere. Espejo exacto de la funcion SQL activity_payment_mode().
 */
fun Activity.effectiveMode(communityChargesEnabled: Boolean): PaymentMode =
    when (paymentMode) {
        PaymentMode.FREE -> PaymentMode.FREE
        PaymentMode.EXTERNAL -> PaymentMode.EXTERNAL
        PaymentMode.AGORA ->
            if (communityChargesEnabled) PaymentMode.AGORA else PaymentMode.EXTERNAL
    }
