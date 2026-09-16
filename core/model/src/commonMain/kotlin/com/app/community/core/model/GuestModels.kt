package com.app.community.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GuestActivityInfo(val id: String)

@Serializable
data class GuestCommunityInfo(val id: String)

/**
 * Respuesta de `get_activity_guest_preview`, lo justo para resolver el link de una
 * actividad. `status` = "ok" | "not_found".
 */
@Serializable
data class GuestActivityPreview(
    val status: String,
    val activity: GuestActivityInfo? = null,
    val community: GuestCommunityInfo? = null,
    @SerialName("is_member") val isMember: Boolean = false,
)
