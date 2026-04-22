package com.app.community.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CommunityVisibility {
    @SerialName("public_open") PUBLIC_OPEN,
    @SerialName("public_approval") PUBLIC_APPROVAL,
    @SerialName("private") PRIVATE,
}
