package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import com.app.community.core.model.GuestActivityPreview
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * Link compartible de una actividad (migración 024). Las RPCs son SECURITY
 * DEFINER y validan el código del link en el servidor.
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

    /** Resuelve el código del link: actividad, comunidad y si ya soy miembro. */
    suspend fun getPreview(code: String): AppResult<GuestActivityPreview> = safeCall {
        val result = postgrest.rpc(
            function = "get_activity_guest_preview",
            parameters = buildJsonObject { put("p_code", code) },
        )
        lenientJson.decodeFromString<GuestActivityPreview>(result.data)
    }
}
