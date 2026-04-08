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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

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

    suspend fun joinByInviteCode(inviteCode: String, userId: String): AppResult<Community> =
        safeCall {
            val community = postgrest.from("communities")
                .select { filter { eq("invite_code", inviteCode) } }
                .decodeSingle<Community>()

            postgrest.from("community_members")
                .insert(buildJsonObject {
                    put("community_id", community.id)
                    put("user_id", userId)
                    put("role", "user")
                })

            community
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

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}

@kotlinx.serialization.Serializable
private data class CommunityMemberWithCommunity(
    val communities: Community? = null,
)
