package com.app.community.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Community(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("icon_key") val iconKey: String? = null,
    @SerialName("invite_code") val inviteCode: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    val visibility: CommunityVisibility = CommunityVisibility.PRIVATE,
    val tags: List<Tag> = emptyList(),
    @SerialName("member_count") val memberCount: Int? = null,
    @SerialName("activity_count_upcoming") val activityCountUpcoming: Int? = null,
    @SerialName("parent_id") val parentId: String? = null,
    /**
     * Esta comunidad puede cobrar por Stripe. Lo mantiene al dia el webhook account.updated.
     * Decide si una actividad 'agora' se ejecuta como tal o degrada a externa.
     */
    @SerialName("stripe_charges_enabled") val stripeChargesEnabled: Boolean = false,
    val breadcrumb: String? = null,
)
