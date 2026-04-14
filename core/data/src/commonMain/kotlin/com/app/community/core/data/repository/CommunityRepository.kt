package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import com.app.community.core.model.Community
import com.app.community.core.model.CommunityMember
import com.app.community.core.model.MemberRole
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

private val lenientJson = Json { ignoreUnknownKeys = true }

class CommunityRepository {

    private val postgrest = SupabaseProvider.client.postgrest

    suspend fun getMyCommunities(userId: String): AppResult<List<Community>> =
        safeCall {
            postgrest.from("community_members")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("community_id, communities(*)")) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<CommunityMemberWithCommunity>()
                .mapNotNull { it.communities }
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
            postgrest.from("communities")
                .select { filter { eq("id", communityId) } }
                .decodeSingle<Community>()
        }

    suspend fun createCommunity(name: String, description: String?, createdBy: String): AppResult<Community> =
        safeCall {
            val inviteCode = generateInviteCode()
            postgrest.from("communities")
                .insert(buildJsonObject {
                    put("name", name)
                    description?.let { put("description", it) }
                    put("invite_code", inviteCode)
                    put("created_by", createdBy)
                }) { select() }
                .decodeSingle<Community>()
        }

    suspend fun joinByInviteCode(inviteCode: String): AppResult<Community> =
        safeCall {
            // Server-side RPC handles lookup + membership insert atomically
            // using auth.uid() — no userId parameter needed
            val result = postgrest.rpc(
                function = "join_community_by_invite",
                parameters = buildJsonObject { put("p_invite_code", inviteCode) },
            )
            // RPC returns a single JSON object, not an array
            lenientJson.decodeFromString<Community>(result.data)
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

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}

@kotlinx.serialization.Serializable
private data class CommunityMemberWithCommunity(
    val communities: Community? = null,
)
