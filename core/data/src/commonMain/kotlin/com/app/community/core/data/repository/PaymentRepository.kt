package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import io.github.jan.supabase.functions.functions
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Estado de cobros de una comunidad. */
@Serializable
data class ConnectStatus(
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("charges_enabled") val chargesEnabled: Boolean = false,
    @SerialName("details_submitted") val detailsSubmitted: Boolean = false,
    @SerialName("payouts_enabled") val payoutsEnabled: Boolean = false,
) {
    /** Hay cuenta pero el alta esta a medias: Stripe todavia pide datos. */
    val isIncomplete: Boolean get() = accountId != null && !chargesEnabled
}

@Serializable
data class ConnectOnboardLink(
    @SerialName("account_id") val accountId: String,
    val url: String,
)

@Serializable
data class CheckoutLink(
    @SerialName("payment_id") val paymentId: String,
    val url: String,
    val resumed: Boolean = false,
)

@Serializable
data class PaymentSyncResult(
    @SerialName("payment_id") val paymentId: String,
    val status: String,
) {
    val isSucceeded: Boolean get() = status == "succeeded"
    /** Sigue abierto: el usuario volvio sin pagar y le queda su retencion. */
    val isPending: Boolean get() = status == "pending"
}

/**
 * Motivos por los que no se puede cobrar. PAYMENTS_NOT_ENABLED es el unico que NO es un
 * error de cara al usuario: significa que esa comunidad todavia no ha completado su alta,
 * y entonces se reserva como siempre y el admin cobra a mano.
 */
enum class PaymentError(val code: String) {
    PAYMENTS_NOT_ENABLED("payments_not_enabled"),
    SLOT_BEING_PAID("slot_being_paid"),
    SLOT_NOT_CLAIMABLE("slot_not_claimable"),
    SLOT_OFFERED_TO_SOMEONE_ELSE("slot_offered_to_someone_else"),
    QUEUE_PRIORITY("queue_priority"),
    ACTIVITY_IS_FREE("activity_is_free"),
    NOT_A_MEMBER("not_a_member"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun from(message: String): PaymentError =
            entries.firstOrNull { it != UNKNOWN && message.contains(it.code) } ?: UNKNOWN
    }
}

@Serializable
private data class FunctionError(
    val error: String? = null,
    val message: String? = null,
)

class PaymentRepository {

    private val functions = SupabaseProvider.client.functions
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Devuelve el enlace de alta de Stripe. Crea la cuenta conectada si la comunidad
     * todavia no tiene. Hay que abrir la URL en el navegador: el formulario lo aloja
     * Stripe y ningun dato de identidad pasa por Agora.
     */
    suspend fun connectOnboard(communityId: String): AppResult<ConnectOnboardLink> =
        invoke("onboard", communityId)

    /**
     * Refresca el estado desde Stripe. Se llama al volver del alta en vez de esperar al
     * webhook: si no, el admin vuelve a la app y sigue viendo "sin configurar".
     */
    suspend fun connectStatus(communityId: String): AppResult<ConnectStatus> =
        invoke("status", communityId)

    /** Abre el cobro de una plaza y devuelve la URL de Checkout. */
    suspend fun createCheckout(slotId: String): AppResult<CheckoutLink> =
        call("stripe-checkout", "create", "slot_id" to slotId)

    /**
     * Sincroniza contra Stripe al volver por deep link. Ejecuta la MISMA transicion que el
     * webhook, asi que el usuario ve el resultado al instante sin esperar a que Stripe avise.
     */
    suspend fun syncPayment(paymentId: String): AppResult<PaymentSyncResult> =
        call("stripe-checkout", "sync", "payment_id" to paymentId)

    private suspend inline fun <reified T> invoke(
        action: String,
        communityId: String,
    ): AppResult<T> = call("stripe-connect", action, "community_id" to communityId)

    private suspend inline fun <reified T> call(
        function: String,
        action: String,
        vararg params: Pair<String, String>,
    ): AppResult<T> = safeCall {
        val body = buildJsonObject {
            put("action", action)
            params.forEach { (k, v) -> put(k, v) }
        }
        val response = functions.invoke(function) {
            contentType(ContentType.Application.Json)
            // Se serializa a mano: el builder que expone functions-kt es el de ktor crudo,
            // sin negociacion de contenido configurada por nosotros.
            setBody(body.toString())
        }
        val text = response.bodyAsText()

        // La funcion responde 200 con cuerpo de error en los casos previstos (no admin,
        // Stripe caido...). Se traduce a AppResult.Error con el mensaje real en vez de
        // reventar al deserializar contra el tipo esperado.
        json.decodeFromString<FunctionError>(text).let { err ->
            if (err.error != null) error(err.message ?: err.error)
        }
        json.decodeFromString<T>(text)
    }
}
