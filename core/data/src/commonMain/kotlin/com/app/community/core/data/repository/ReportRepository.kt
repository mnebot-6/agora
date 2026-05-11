package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class ReportReason(val value: String) {
    SPAM("spam"),
    HARASSMENT("harassment"),
    INAPPROPRIATE("inappropriate"),
    OTHER("other"),
}

class ReportRepository {

    private val postgrest = SupabaseProvider.client.postgrest

    suspend fun reportMessage(
        reporterId: String,
        messageId: String,
        communityId: String,
        reason: ReportReason,
        details: String? = null,
    ): AppResult<Unit> = safeCall {
        postgrest.from("reports").insert(buildJsonObject {
            put("reporter_id", reporterId)
            put("target_type", "message")
            put("target_id", messageId)
            put("community_id", communityId)
            put("reason", reason.value)
            put("details", details?.let { JsonPrimitive(it) } ?: JsonNull)
        })
        Unit
    }

    suspend fun reportProfile(
        reporterId: String,
        targetUserId: String,
        communityId: String? = null,
        reason: ReportReason,
        details: String? = null,
    ): AppResult<Unit> = safeCall {
        postgrest.from("reports").insert(buildJsonObject {
            put("reporter_id", reporterId)
            put("target_type", "profile")
            put("target_id", targetUserId)
            put("community_id", communityId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("reason", reason.value)
            put("details", details?.let { JsonPrimitive(it) } ?: JsonNull)
        })
        Unit
    }
}
