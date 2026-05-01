package com.app.community.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommunityMessage(
    val id: String,
    @SerialName("community_id") val communityId: String,
    @SerialName("user_id") val userId: String,
    val body: String,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("edited_at") val editedAt: Instant? = null,
    val profiles: Profile? = null,
)
