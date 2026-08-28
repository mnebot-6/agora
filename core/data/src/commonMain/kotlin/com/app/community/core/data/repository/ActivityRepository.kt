package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import com.app.community.core.model.Activity
import com.app.community.core.model.ActivityStatus
import com.app.community.core.model.CancellationBreakdown
import com.app.community.core.model.PaymentMode
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

    suspend fun getUpcomingActivities(): AppResult<List<Activity>> =
        safeCall {
            // Server uses auth.uid() from JWT — no userId parameter needed
            postgrest.rpc(
                function = "get_upcoming_activities",
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
        priceCents: Int?,
        paymentMode: PaymentMode,
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
                    priceCents?.let { put("price_cents", it) }
                    put("payment_mode", paymentMode.wire)
                    put("slot_mode", slotMode.name.lowercase())
                    maxSlots?.let { put("max_slots", it) }
                    put("created_by", createdBy)
                }) { select() }
                .decodeSingle<Activity>()
        }

    suspend fun updateActivity(
        activityId: String,
        name: String,
        description: String?,
        datetime: Instant,
        durationMinutes: Int,
        locationName: String?,
        costDescription: String?,
        priceCents: Int?,
    ): AppResult<Activity> =
        safeCall {
            postgrest.from("activities")
                .update(buildJsonObject {
                    put("name", name)
                    put("description", description)
                    put("datetime", datetime.toString())
                    put("duration_minutes", durationMinutes)
                    put("location_name", locationName)
                    put("cost_description", costDescription)
                    // null explicito: asi se pasa una actividad de pago a gratuita.
                    put("price_cents", priceCents)
                }) {
                    filter { eq("id", activityId) }
                    select()
                }
                .decodeSingle<Activity>()
        }

    /** Que dinero se movera al cancelar. Solo lectura, para el dialogo de confirmacion. */
    suspend fun cancellationPreview(activityId: String): AppResult<CancellationBreakdown> =
        safeCall {
            postgrest.rpc("activity_cancellation_preview", buildJsonObject {
                put("p_activity_id", activityId)
            }).decodeAs<CancellationBreakdown>()
        }

    /**
     * Cancela y archiva. Lo cobrado por Stripe se devuelve solo (lo ejecuta el barrido);
     * lo cobrado a mano queda como deuda del admin, porque Agora nunca vio ese dinero.
     */
    suspend fun cancelActivity(activityId: String): AppResult<CancellationBreakdown> =
        safeCall {
            postgrest.rpc("cancel_activity", buildJsonObject {
                put("p_activity_id", activityId)
            }).decodeAs<CancellationBreakdown>()
        }

    suspend fun deleteActivity(activityId: String): AppResult<Unit> =
        safeCall {
            postgrest.from("activities")
                .delete { filter { eq("id", activityId) } }
        }

    suspend fun updateActivityStatus(activityId: String, status: ActivityStatus): AppResult<Unit> =
        safeCall {
            postgrest.from("activities")
                .update({ set("status", status.name.lowercase()) }) {
                    filter { eq("id", activityId) }
                }
        }
}
