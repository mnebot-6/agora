package com.app.community.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SlotTemplate(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val name: String,
    val config: TemplateConfig,
)

@Serializable
data class TemplateConfig(
    val positions: List<String> = emptyList(),
    val groups: List<GroupTemplate> = emptyList(),
)

@Serializable
data class GroupTemplate(
    val name: String,
    val slots: List<SlotTemplateEntry> = emptyList(),
)

@Serializable
data class SlotTemplateEntry(
    @SerialName("position_indices") val positionIndices: Set<Int> = emptySet(),
)
