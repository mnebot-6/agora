package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import com.app.community.core.model.Activity
import com.app.community.core.model.ActivityStatus
import com.app.community.core.model.SlotMode
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActivityRepository {

    private val postgrest = SupabaseProvider.client.postgrest

    suspend fun getActivities(communityId: String): AppResult<List<Activity>> =
        safeCall {
            postgrest.from("activities")
                .select {
                    filter {
                        eq("community_id", communityId)
                        eq("status", "active")
                    }
                    order("datetime", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }
                .decodeList<Activity>()
        }

    suspend fun getUpcomingActivities(userId: String): AppResult<List<Activity>> =
        safeCall {
            // Get activities from all communities the user belongs to
            postgrest.rpc(
                function = "get_upcoming_activities",
                parameters = kotlinx.serialization.json.buildJsonObject { put("p_user_id", kotlinx.serialization.json.JsonPrimitive(userId)) },
            ).decodeList<Activity>()
        }

    suspend fun getActivity(activityId: String): AppResult<Activity> =
        safeCall {
            postgrest.from("activities")
                .select { filter { eq("id", activityId) } }
                .decodeSingle<Activity>()
        }

    suspend fun createActivity(
        communityId: String,
        name: String,
        description: String?,
        datetime: Instant,
        durationMinutes: Int,
        locationName: String?,
        locationLat: Double?,
        locationLng: Double?,
        costDescription: String?,
        slotMode: SlotMode,
        maxSlots: Int?,
        createdBy: String,
    ): AppResult<Activity> =
        safeCall {
            postgrest.from("activities")
                .insert(buildJsonObject {
                    put("community_id", communityId)
                    put("name", name)
                    description?.let { put("description", it) }
                    put("datetime", datetime.toString())
                    put("duration_minutes", durationMinutes)
                    locationName?.let { put("location_name", it) }
                    locationLat?.let { put("location_lat", it) }
                    locationLng?.let { put("location_lng", it) }
                    costDescription?.let { put("cost_description", it) }
                    put("slot_mode", slotMode.name.lowercase())
                    maxSlots?.let { put("max_slots", it) }
                    put("created_by", createdBy)
                }) { select() }
                .decodeSingle<Activity>()
        }

    suspend fun updateActivityStatus(activityId: String, status: ActivityStatus): AppResult<Unit> =
        safeCall {
            postgrest.from("activities")
                .update({ set("status", status.name.lowercase()) }) {
                    filter { eq("id", activityId) }
                }
        }
}
