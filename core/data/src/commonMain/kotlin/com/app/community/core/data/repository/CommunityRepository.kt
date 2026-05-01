package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import com.app.community.core.model.Community
import com.app.community.core.model.CommunityJoinRequest
import com.app.community.core.model.CommunityMember
import com.app.community.core.model.CommunityVisibility
import com.app.community.core.model.JoinRequestStatus
import com.app.community.core.model.MemberRole
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.random.Random

private val lenientJson = Json { ignoreUnknownKeys = true }

private fun CommunityVisibility.serialized(): String = when (this) {
    CommunityVisibility.PUBLIC_OPEN -> "public_open"
    CommunityVisibility.PUBLIC_APPROVAL -> "public_approval"
    CommunityVisibility.PRIVATE -> "private"
}

class CommunityRepository {

    private val postgrest = SupabaseProvider.client.postgrest

    suspend fun getMyCommunities(
        userId: String,
        rootOnly: Boolean = false,
    ): AppResult<List<Community>> =
        safeCall {
            postgrest.from("community_members")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("community_id, communities(*)")) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<CommunityMemberWithCommunity>()
                .mapNotNull { it.communities }
                .let { list -> if (rootOnly) list.filter { it.parentId == null } else list }
        }

    suspend fun getChildren(parentId: String): AppResult<List<Community>> =
        safeCall {
            postgrest.from("communities")
                .select { filter { eq("parent_id", parentId) } }
                .decodeList<Community>()
        }

    suspend fun getMyAdminCommunities(userId: String): AppResult<List<Community>> =
        safeCall {
            postgrest.from("community_members")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("community_id, communities(*)")) {
                    filter {
                        eq("user_id", userId)
                        eq("role", "admin")
                    }
                }
                .decodeList<CommunityMemberWithCommunity>()
                .mapNotNull { it.communities }
        }

    suspend fun getCommunity(communityId: String): AppResult<Community> =
        safeCall {
            val community = postgrest.from("communities")
                .select { filter { eq("id", communityId) } }
                .decodeSingle<Community>()
            // Tags se obtienen aparte porque la relación es many-to-many a través
            // de community_tags. Sin esto el dialog de editar comunidad mostraba
            // el set de tags vacío al abrirse.
            val tags = postgrest.from("community_tags")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("tags(*)")) {
                    filter { eq("community_id", communityId) }
                }
                .decodeList<TagJoinRow>()
                .mapNotNull { it.tags }
            community.copy(tags = tags)
        }

    suspend fun createCommunity(
        name: String,
        description: String?,
        createdBy: String,
        visibility: CommunityVisibility = CommunityVisibility.PRIVATE,
        tagIds: List<String> = emptyList(),
        parentId: String? = null,
    ): AppResult<Community> =
        safeCall {
            val inviteCode = generateInviteCode()
            val created = postgrest.from("communities")
                .insert(buildJsonObject {
                    put("name", name)
                    description?.let { put("description", it) }
                    put("invite_code", inviteCode)
                    put("created_by", createdBy)
                    put("visibility", visibility.serialized())
                    parentId?.let { put("parent_id", it) }
                }) { select() }
                .decodeSingle<Community>()

            if (tagIds.isNotEmpty()) {
                val limited = tagIds.distinct().take(3)
                val rows = limited.map { tagId ->
                    buildJsonObject {
                        put("community_id", created.id)
                        put("tag_id", tagId)
                    }
                }
                postgrest.from("community_tags").insert(rows)
            }

            created
        }

    suspend fun joinByInviteCode(inviteCode: String): AppResult<Community> =
        safeCall {
            val result = postgrest.rpc(
                function = "join_community_by_invite",
                parameters = buildJsonObject { put("p_invite_code", inviteCode) },
            )
            lenientJson.decodeFromString<Community>(result.data)
        }

    /** Result of `join_community_by_invite_v2`. */
    sealed class JoinByInviteResult {
        data class Joined(val community: Community) : JoinByInviteResult()
        data class Pending(val community: Community, val requestId: String?) : JoinByInviteResult()
        data class AlreadyMember(val community: Community) : JoinByInviteResult()
    }

    suspend fun joinByInviteCodeV2(inviteCode: String): AppResult<JoinByInviteResult> =
        safeCall {
            val result = postgrest.rpc(
                function = "join_community_by_invite_v2",
                parameters = buildJsonObject { put("p_invite_code", inviteCode) },
            )
            val root = lenientJson.parseToJsonElement(result.data)
            val obj = root as? kotlinx.serialization.json.JsonObject
                ?: error("join_community_by_invite_v2 devolvió un payload inesperado")
            val status = (obj["status"] as? JsonPrimitive)?.content
                ?: error("join_community_by_invite_v2 sin status")
            val communityJson = obj["community"]?.toString()
                ?: error("join_community_by_invite_v2 sin community")
            val community = lenientJson.decodeFromString<Community>(communityJson)
            val requestId = (obj["request_id"] as? JsonPrimitive)?.contentOrNull
            when (status) {
                "joined" -> JoinByInviteResult.Joined(community)
                "pending" -> JoinByInviteResult.Pending(community, requestId)
                "already_member" -> JoinByInviteResult.AlreadyMember(community)
                else -> error("Estado desconocido: $status")
            }
        }

    suspend fun getMembers(communityId: String): AppResult<List<CommunityMember>> =
        safeCall {
            postgrest.from("community_members")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("*, profiles(*)")) {
                    filter { eq("community_id", communityId) }
                }
                .decodeList<CommunityMember>()
        }

    suspend fun updateMemberRole(communityId: String, userId: String, role: MemberRole): AppResult<Unit> =
        safeCall {
            postgrest.from("community_members")
                .update({ set("role", role.name.lowercase()) }) {
                    filter {
                        eq("community_id", communityId)
                        eq("user_id", userId)
                    }
                }
        }

    suspend fun removeMember(communityId: String, userId: String): AppResult<Unit> =
        safeCall {
            postgrest.from("community_members")
                .delete {
                    filter {
                        eq("community_id", communityId)
                        eq("user_id", userId)
                    }
                }
        }

    suspend fun updateCommunity(id: String, name: String, description: String?): AppResult<Unit> =
        safeCall {
            postgrest.from("communities")
                .update({
                    set("name", name)
                    set("description", description)
                }) { filter { eq("id", id) } }
        }

    suspend fun deleteCommunity(id: String): AppResult<Unit> =
        safeCall {
            postgrest.from("communities")
                .delete { filter { eq("id", id) } }
        }

    // ------------------------------------------------------------------
    // Public communities + tags + join requests (migration 014)
    // ------------------------------------------------------------------

    suspend fun searchPublicCommunities(
        query: String? = null,
        tagIds: List<String> = emptyList(),
        limit: Int = 20,
        offset: Int = 0,
    ): AppResult<List<Community>> = safeCall {
        val result = postgrest.rpc(
            function = "search_public_communities",
            parameters = buildJsonObject {
                query?.let { put("p_query", it) }
                if (tagIds.isNotEmpty()) {
                    put(
                        "p_tag_ids",
                        JsonArray(tagIds.map { JsonPrimitive(it) }),
                    )
                }
                put("p_limit", limit)
                put("p_offset", offset)
            },
        )
        lenientJson.decodeFromString<List<Community>>(result.data)
    }

    suspend fun getPublicCommunityPreview(communityId: String): AppResult<Community> = safeCall {
        val result = postgrest.rpc(
            function = "get_public_community_preview",
            parameters = buildJsonObject { put("p_community_id", communityId) },
        )
        lenientJson.decodeFromString<Community>(result.data)
    }

    /**
     * Returns one of:
     *  - {"status":"joined"}          — public_open, joined immediately
     *  - {"status":"already_member"}  — already a member
     *  - {"status":"pending","request_id":"..."} — public_approval, request created
     */
    suspend fun requestToJoinCommunity(
        communityId: String,
        message: String? = null,
    ): AppResult<JoinRequestResult> = safeCall {
        val result = postgrest.rpc(
            function = "request_to_join_community",
            parameters = buildJsonObject {
                put("p_community_id", communityId)
                message?.let { put("p_message", it) }
            },
        )
        lenientJson.decodeFromString<JoinRequestResult>(result.data)
    }

    suspend fun cancelJoinRequest(requestId: String): AppResult<Unit> = safeCall {
        postgrest.rpc(
            function = "cancel_join_request",
            parameters = buildJsonObject { put("p_request_id", requestId) },
        )
        Unit
    }

    suspend fun approveJoinRequest(requestId: String): AppResult<Unit> = safeCall {
        postgrest.rpc(
            function = "approve_join_request",
            parameters = buildJsonObject { put("p_request_id", requestId) },
        )
        Unit
    }

    suspend fun rejectJoinRequest(requestId: String): AppResult<Unit> = safeCall {
        postgrest.rpc(
            function = "reject_join_request",
            parameters = buildJsonObject { put("p_request_id", requestId) },
        )
        Unit
    }

    suspend fun getPendingJoinRequests(communityId: String): AppResult<List<PendingJoinRequest>> = safeCall {
        // NOTA: community_join_requests tiene dos FKs a profiles (user_id y resolved_by).
        // Hay que desambiguar el embed con !user_id; sin ello PostgREST devuelve 300
        // (Multiple Choices) que supabase-kt reporta como "Unknown error".
        postgrest.from("community_join_requests")
            .select(columns = io.github.jan.supabase.postgrest.query.Columns.raw(
                "id, community_id, user_id, status, message, requested_at, profiles!user_id(id, display_name, avatar_url)"
            )) {
                filter {
                    eq("community_id", communityId)
                    eq("status", "pending")
                }
                order(column = "requested_at", order = Order.ASCENDING)
            }
            .decodeList<PendingJoinRequest>()
    }

    suspend fun getPendingJoinRequestForUser(
        communityId: String,
        userId: String,
    ): AppResult<CommunityJoinRequest?> = safeCall {
        postgrest.from("community_join_requests")
            .select {
                filter {
                    eq("community_id", communityId)
                    eq("user_id", userId)
                    eq("status", "pending")
                }
                limit(1)
            }
            .decodeList<CommunityJoinRequest>()
            .firstOrNull()
    }

    suspend fun updateCommunityVisibility(
        communityId: String,
        visibility: CommunityVisibility,
    ): AppResult<Unit> = safeCall {
        postgrest.from("communities")
            .update({
                set("visibility", visibility.serialized())
            }) { filter { eq("id", communityId) } }
    }

    suspend fun updateCommunityTags(
        communityId: String,
        tagIds: List<String>,
    ): AppResult<Unit> = safeCall {
        postgrest.from("community_tags")
            .delete { filter { eq("community_id", communityId) } }

        if (tagIds.isNotEmpty()) {
            val limited = tagIds.distinct().take(3)
            val rows = limited.map { tagId ->
                buildJsonObject {
                    put("community_id", communityId)
                    put("tag_id", tagId)
                }
            }
            postgrest.from("community_tags").insert(rows)
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}

@Serializable
private data class CommunityMemberWithCommunity(
    val communities: Community? = null,
)

@Serializable
private data class TagJoinRow(
    val tags: com.app.community.core.model.Tag? = null,
)

@Serializable
data class JoinRequestResult(
    val status: String,
    @SerialName("request_id") val requestId: String? = null,
)

@Serializable
data class PendingJoinRequest(
    val id: String,
    @SerialName("community_id") val communityId: String,
    @SerialName("user_id") val userId: String,
    val status: JoinRequestStatus,
    val message: String? = null,
    @SerialName("requested_at") val requestedAt: kotlinx.datetime.Instant,
    val profiles: RequesterProfile? = null,
)

@Serializable
data class RequesterProfile(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)
