package com.app.community.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MemberRole {
    @SerialName("admin") ADMIN,
    @SerialName("user") USER,
}

@Serializable
data class CommunityMember(
    @SerialName("community_id") val communityId: String,
    @SerialName("user_id") val userId: String,
    val role: MemberRole,
    val profiles: Profile? = null,
)
