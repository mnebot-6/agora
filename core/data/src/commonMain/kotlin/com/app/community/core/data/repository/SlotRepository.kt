package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import com.app.community.core.model.Position
import com.app.community.core.model.Slot
import com.app.community.core.model.SlotGroup
import com.app.community.core.model.SlotPosition
import com.app.community.core.model.SubstituteEntry
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SlotRepository {

    private val postgrest = SupabaseProvider.client.postgrest
    private val realtime = SupabaseProvider.client.realtime

    // --- Queries ---

    suspend fun getSlots(activityId: String): AppResult<List<Slot>> =
        safeCall {
            postgrest.from("slots")
                .select {
                    filter { eq("activity_id", activityId) }
                    order("sort_order", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }
                .decodeList<Slot>()
        }

    suspend fun getSlotGroups(activityId: String): AppResult<List<SlotGroup>> =
        safeCall {
            postgrest.from("slot_groups")
                .select {
                    filter { eq("activity_id", activityId) }
                    order("sort_order", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }
                .decodeList<SlotGroup>()
        }

    suspend fun getPositions(activityId: String): AppResult<List<Position>> =
        safeCall {
            postgrest.from("positions")
                .select { filter { eq("activity_id", activityId) } }
                .decodeList<Position>()
        }

    suspend fun getSlotPositions(slotIds: List<String>): AppResult<List<SlotPosition>> =
        safeCall {
            postgrest.from("slot_positions")
                .select { filter { isIn("slot_id", slotIds) } }
                .decodeList<SlotPosition>()
        }

    suspend fun getSubstituteQueue(activityId: String): AppResult<List<SubstituteEntry>> =
        safeCall {
            postgrest.from("substitute_queue")
                .select {
                    filter { eq("activity_id", activityId) }
                    order("queued_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }
                .decodeList<SubstituteEntry>()
        }

    // --- Atomic RPC Operations ---

    suspend fun reserveSlot(slotId: String): AppResult<Boolean> =
        safeCall {
            val result = postgrest.rpc(
                function = "reserve_slot",
                parameters = buildJsonObject {
                    put("p_slot_id", slotId)
                },
            ).data
            result.trim().toBoolean()
        }

    suspend fun releaseSlot(slotId: String): AppResult<Boolean> =
        safeCall {
            val result = postgrest.rpc(
                function = "release_slot",
                parameters = buildJsonObject {
                    put("p_slot_id", slotId)
                },
            ).data
            result.trim().toBoolean()
        }

    suspend fun markSlotPaid(slotId: String): AppResult<Boolean> =
        safeCall {
            val result = postgrest.rpc(
                function = "mark_slot_paid",
                parameters = buildJsonObject {
                    put("p_slot_id", slotId)
                },
            ).data
            result.trim().toBoolean()
        }

    suspend fun joinSubstituteQueue(
        activityId: String,
        positionId: String?,
    ): AppResult<Unit> =
        safeCall {
            postgrest.rpc(
                function = "join_substitute_queue",
                parameters = buildJsonObject {
                    put("p_activity_id", activityId)
                    positionId?.let { put("p_position_id", it) }
                },
            )
            Unit
        }

    suspend fun leaveSubstituteQueue(
        activityId: String,
        positionId: String?,
    ): AppResult<Unit> =
        safeCall {
            postgrest.rpc(
                function = "leave_substitute_queue",
                parameters = buildJsonObject {
                    put("p_activity_id", activityId)
                    positionId?.let { put("p_position_id", it) }
                },
            )
            Unit
        }

    /** Devuelve false si la plaza dejó de estar libre mientras el diálogo estaba abierto. */
    suspend fun adminAssignSlot(
        slotId: String,
        userId: String?,
        guestLabel: String?,
    ): AppResult<Boolean> =
        safeCall {
            val result = postgrest.rpc(
                function = "admin_assign_slot",
                parameters = buildJsonObject {
                    put("p_slot_id", slotId)
                    userId?.let { put("p_user_id", it) }
                    guestLabel?.let { put("p_guest_label", it) }
                },
            ).data
            result.trim().toBoolean()
        }

    /** Modo ilimitado: crea la plaza y la asigna en la misma transacción. Devuelve su id. */
    suspend fun adminAssignNewSlot(
        activityId: String,
        userId: String?,
        guestLabel: String?,
    ): AppResult<String> =
        safeCall {
            postgrest.rpc(
                function = "admin_assign_new_slot",
                parameters = buildJsonObject {
                    put("p_activity_id", activityId)
                    userId?.let { put("p_user_id", it) }
                    guestLabel?.let { put("p_guest_label", it) }
                },
            ).data.trim().trim('"')
        }

    // --- Slot Creation (admin) ---

    suspend fun createSlots(activityId: String, count: Int): AppResult<Unit> =
        safeCall {
            val slots = (0 until count).map { index ->
                buildJsonObject {
                    put("activity_id", activityId)
                    put("sort_order", index)
                    put("status", "available")
                }
            }
            postgrest.from("slots").insert(slots)
        }

    suspend fun createSlotGroup(activityId: String, name: String, sortOrder: Int): AppResult<SlotGroup> =
        safeCall {
            postgrest.from("slot_groups")
                .insert(buildJsonObject {
                    put("activity_id", activityId)
                    put("name", name)
                    put("sort_order", sortOrder)
                }) { select() }
                .decodeSingle<SlotGroup>()
        }

    suspend fun createPosition(activityId: String, name: String): AppResult<Position> =
        safeCall {
            postgrest.from("positions")
                .insert(buildJsonObject {
                    put("activity_id", activityId)
                    put("name", name)
                }) { select() }
                .decodeSingle<Position>()
        }

    suspend fun createSlotWithPositions(
        activityId: String,
        groupId: String,
        sortOrder: Int,
        positionIds: List<String>,
    ): AppResult<Slot> =
        safeCall {
            val slot = postgrest.from("slots")
                .insert(buildJsonObject {
                    put("activity_id", activityId)
                    put("group_id", groupId)
                    put("sort_order", sortOrder)
                    put("status", "available")
                }) { select() }
                .decodeSingle<Slot>()

            if (positionIds.isNotEmpty()) {
                val slotPositions = positionIds.map { posId ->
                    buildJsonObject {
                        put("slot_id", slot.id)
                        put("position_id", posId)
                    }
                }
                postgrest.from("slot_positions").insert(slotPositions)
            }

            slot
        }
}
