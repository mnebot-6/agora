package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import com.app.community.core.model.SlotTemplate
import com.app.community.core.model.TemplateConfig
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SlotTemplateRepository {

    private val postgrest = SupabaseProvider.client.postgrest

    fun getDefaultTemplates(): List<SlotTemplate> = DefaultTemplates.getAll()

    suspend fun getUserTemplates(userId: String): AppResult<List<SlotTemplate>> =
        safeCall {
            postgrest.from("slot_templates")
                .select {
                    filter { eq("user_id", userId) }
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }
                .decodeList<SlotTemplate>()
        }

    suspend fun saveTemplate(userId: String, name: String, config: TemplateConfig): AppResult<SlotTemplate> =
        safeCall {
            postgrest.from("slot_templates")
                .insert(buildJsonObject {
                    put("user_id", userId)
                    put("name", name)
                    put("config", kotlinx.serialization.json.Json.encodeToJsonElement(TemplateConfig.serializer(), config))
                }) { select() }
                .decodeSingle<SlotTemplate>()
        }

    suspend fun deleteTemplate(templateId: String): AppResult<Unit> =
        safeCall {
            postgrest.from("slot_templates")
                .delete { filter { eq("id", templateId) } }
        }
}
