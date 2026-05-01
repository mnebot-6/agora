package com.app.community.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("fcm_token") val fcmToken: String? = null,
    @SerialName("dark_mode") val darkMode: Boolean? = null,
    /** "auto" | "es" | "en" — null se trata como "auto". */
    @SerialName("language_preference") val languagePreference: String? = null,
)
