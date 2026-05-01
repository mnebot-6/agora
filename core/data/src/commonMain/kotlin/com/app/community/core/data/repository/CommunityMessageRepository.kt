package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import com.app.community.core.model.CommunityMessage
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transform
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed class MessageEvent {
    data class Inserted(val message: CommunityMessage) : MessageEvent()
    data class Updated(val message: CommunityMessage) : MessageEvent()
    data class Deleted(val messageId: String) : MessageEvent()
}

class CommunityMessageRepository {

    private val supabase = SupabaseProvider.client
    private val postgrest = supabase.postgrest
    private val realtime = supabase.realtime
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Carga mensajes de una comunidad ordenados de mas reciente a mas antiguo.
     * `before` permite paginar: pasa el `createdAt` del mensaje mas antiguo
     * cargado para traer los anteriores.
     */
    suspend fun getMessages(
        communityId: String,
        before: Instant? = null,
        limit: Int = 50,
    ): AppResult<List<CommunityMessage>> = safeCall {
        postgrest.from("community_messages")
            .select(columns = io.github.jan.supabase.postgrest.query.Columns.raw(
                "id, community_id, user_id, body, created_at, edited_at, profiles(*)"
            )) {
                filter {
                    eq("community_id", communityId)
                    if (before != null) {
                        lt("created_at", before)
                    }
                }
                order(column = "created_at", order = Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<CommunityMessage>()
    }

    suspend fun sendMessage(
        communityId: String,
        body: String,
        userId: String,
    ): AppResult<CommunityMessage> = safeCall {
        postgrest.from("community_messages")
            .insert(buildJsonObject {
                put("community_id", communityId)
                put("user_id", userId)
                put("body", body.trim())
            }) {
                select(columns = io.github.jan.supabase.postgrest.query.Columns.raw(
                    "id, community_id, user_id, body, created_at, edited_at, profiles(*)"
                ))
            }
            .decodeSingle<CommunityMessage>()
    }

    suspend fun editMessage(messageId: String, newBody: String): AppResult<Unit> = safeCall {
        postgrest.from("community_messages")
            .update({
                set("body", newBody.trim())
                // Usamos el timestamp del cliente — Supabase interpreta "now()" como
                // string literal, no como función SQL.
                set("edited_at", Clock.System.now().toString())
            }) {
                filter { eq("id", messageId) }
            }
        Unit
    }

    suspend fun deleteMessage(messageId: String): AppResult<Unit> = safeCall {
        postgrest.from("community_messages")
            .delete { filter { eq("id", messageId) } }
        Unit
    }

    /**
     * Subscribe a cambios en tiempo real de los mensajes de una comunidad.
     * Emite eventos Inserted/Updated/Deleted segun ocurren en la BBDD.
     *
     * El channel se mantiene activo mientras el caller colecte el Flow.
     * Al cancelar la coleccion, hace unsubscribe.
     */
    fun observeMessages(communityId: String): Flow<MessageEvent> = flow {
        val channel = realtime.channel("community-messages-$communityId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "community_messages"
            filter(column = "community_id", operator = FilterOperator.EQ, value = communityId)
        }
        channel.subscribe()
        emitAll(
            changes
                .transform { action ->
                    when (action) {
                        is PostgresAction.Insert -> {
                            runCatching {
                                json.decodeFromString<CommunityMessage>(action.record.toString())
                            }.getOrNull()?.let { emit(MessageEvent.Inserted(it)) }
                        }
                        is PostgresAction.Update -> {
                            runCatching {
                                json.decodeFromString<CommunityMessage>(action.record.toString())
                            }.getOrNull()?.let { emit(MessageEvent.Updated(it)) }
                        }
                        is PostgresAction.Delete -> {
                            val id = action.oldRecord["id"]?.toString()?.trim('"')
                            id?.let { emit(MessageEvent.Deleted(it)) }
                        }
                        else -> Unit
                    }
                }
                .onCompletion { channel.unsubscribe() }
        )
    }
}
