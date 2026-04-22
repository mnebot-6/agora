package com.app.community.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tag(
    val id: String,
    val slug: String,
    @SerialName("name_es") val nameEs: String,
    @SerialName("name_en") val nameEn: String,
    val icon: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
)
