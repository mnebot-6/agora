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

    private suspend inline fun <reified T> invoke(
        action: String,
        communityId: String,
    ): AppResult<T> = safeCall {
        val body = buildJsonObject {
            put("action", action)
            put("community_id", communityId)
        }
        val response = functions.invoke("stripe-connect") {
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
