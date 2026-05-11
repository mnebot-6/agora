package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class BlockRepository {

    private val postgrest = SupabaseProvider.client.postgrest

    @Serializable
    private data class BlockedRow(val blocked_id: String)

    suspend fun blockUser(blockerId: String, blockedId: String): AppResult<Unit> = safeCall {
        postgrest.from("user_blocks").insert(buildJsonObject {
            put("blocker_id", blockerId)
            put("blocked_id", blockedId)
        })
        Unit
    }

    suspend fun unblockUser(blockerId: String, blockedId: String): AppResult<Unit> = safeCall {
        postgrest.from("user_blocks").delete {
            filter {
                eq("blocker_id", blockerId)
                eq("blocked_id", blockedId)
            }
        }
        Unit
    }

    suspend fun getBlockedUserIds(blockerId: String): AppResult<Set<String>> = safeCall {
        postgrest.from("user_blocks")
            .select(columns = Columns.list("blocked_id")) {
                filter { eq("blocker_id", blockerId) }
            }
            .decodeList<BlockedRow>()
            .map { it.blocked_id }
            .toSet()
    }
}
