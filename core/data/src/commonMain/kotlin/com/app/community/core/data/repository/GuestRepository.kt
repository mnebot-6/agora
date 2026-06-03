package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import com.app.community.core.model.GuestActivityPreview
import com.app.community.core.model.GuestRequestResult
import com.app.community.core.model.PendingGuestRequest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * Acceso a las RPCs de invitados a actividad (migración 024). Todas son
 * SECURITY DEFINER y validan el código del link en el servidor; el cliente
 * nunca pasa IDs de comunidad/actividad para autorizar.
 */
class GuestRepository {

    private val postgrest = SupabaseProvider.client.postgrest

    /** Admin de comunidad pública: genera (o recupera) el link de la actividad. */
    suspend fun generateLink(activityId: String): AppResult<String> = safeCall {
        val result = postgrest.rpc(
            function = "generate_activity_guest_link",
            parameters = buildJsonObject { put("p_activity_id", activityId) },
        )
        val obj = lenientJson.parseToJsonElement(result.data)
        val url = (obj as? kotlinx.serialization.json.JsonObject)
            ?.get("url")
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            ?: error("generate_activity_guest_link sin url")
        url
    }

    /** Preview de la actividad para un invitado (sirve a anónimos). */
    suspend fun getPreview(code: String): AppResult<GuestActivityPreview> = safeCall {
        val result = postgrest.rpc(
            function = "get_activity_guest_preview",
            parameters = buildJsonObject { put("p_code", code) },
        )
        lenientJson.decodeFromString<GuestActivityPreview>(result.data)
    }

    /** Solicita asistencia como invitado (retiene un slot pending). */
    suspend fun requestSlot(
        code: String,
        name: String,
        phone: String,
    ): AppResult<GuestRequestResult> = safeCall {
        val result = postgrest.rpc(
            function = "request_guest_slot",
            parameters = buildJsonObject {
                put("p_code", code)
                put("p_name", name)
                put("p_phone", phone)
            },
        )
        lenientJson.decodeFromString<GuestRequestResult>(result.data)
    }

    /** Admin: cola FIFO de solicitudes pendientes de una actividad. */
    suspend fun listPendingRequests(activityId: String): AppResult<List<PendingGuestRequest>> = safeCall {
        val result = postgrest.rpc(
            function = "list_pending_guest_requests",
            parameters = buildJsonObject { put("p_activity_id", activityId) },
        )
        lenientJson.decodeFromString<List<PendingGuestRequest>>(result.data)
    }

    /** Admin: aprueba una solicitud (confirma el slot). */
    suspend fun approveRequest(requestId: String): AppResult<Unit> = safeCall {
        postgrest.rpc(
            function = "approve_guest_request",
            parameters = buildJsonObject { put("p_request_id", requestId) },
        )
        Unit
    }

    /** Admin: rechaza una solicitud (libera el slot). */
    suspend fun rejectRequest(requestId: String): AppResult<Unit> = safeCall {
        postgrest.rpc(
            function = "reject_guest_request",
            parameters = buildJsonObject { put("p_request_id", requestId) },
        )
        Unit
    }
}
