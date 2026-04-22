package com.app.community.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class JoinRequestStatus {
    @SerialName("pending") PENDING,
    @SerialName("approved") APPROVED,
    @SerialName("rejected") REJECTED,
    @SerialName("cancelled") CANCELLED,
}

@Serializable
data class CommunityJoinRequest(
    val id: String,
    @SerialName("community_id") val communityId: String,
    @SerialName("user_id") val userId: String,
    val status: JoinRequestStatus,
    val message: String? = null,
    @SerialName("requested_at") val requestedAt: Instant,
    @SerialName("resolved_at") val resolvedAt: Instant? = null,
    @SerialName("resolved_by") val resolvedBy: String? = null,
)
