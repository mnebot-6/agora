package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import com.app.community.core.model.Notification
import io.github.jan.supabase.postgrest.postgrest

class NotificationRepository {

    private val postgrest = SupabaseProvider.client.postgrest

    suspend fun getNotifications(userId: String): AppResult<List<Notification>> =
        safeCall {
            postgrest.from("notifications")
                .select {
                    filter { eq("user_id", userId) }
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(50)
                }
                .decodeList<Notification>()
        }

    suspend fun markAsRead(notificationId: String): AppResult<Unit> =
        safeCall {
            postgrest.from("notifications")
                .update({ set("read", true) }) {
                    filter { eq("id", notificationId) }
                }
        }

    suspend fun markAllAsRead(userId: String): AppResult<Unit> =
        safeCall {
            postgrest.from("notifications")
                .update({ set("read", true) }) {
                    filter {
                        eq("user_id", userId)
                        eq("read", false)
                    }
                }
        }
}
