package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import com.app.community.core.model.Tag
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TagRepository {

    private val postgrest = SupabaseProvider.client.postgrest
    private val mutex = Mutex()
    private var cachedTags: List<Tag>? = null

    suspend fun getAllTags(): AppResult<List<Tag>> {
        cachedTags?.let { return AppResult.Success(it) }
        return mutex.withLock {
            cachedTags?.let { return@withLock AppResult.Success(it) }
            val result = safeCall {
                postgrest.from("tags")
                    .select { order(column = "sort_order", order = Order.ASCENDING) }
                    .decodeList<Tag>()
            }
            result.onSuccess { cachedTags = it }
            result
        }
    }

    fun invalidateCache() {
        cachedTags = null
    }
}
