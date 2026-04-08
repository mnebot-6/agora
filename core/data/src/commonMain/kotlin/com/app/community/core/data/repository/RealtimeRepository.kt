package com.app.community.core.data.repository

import com.app.community.core.data.SupabaseProvider
import io.github.jan.supabase.realtime.realtime

class RealtimeRepository {
    private val realtime = SupabaseProvider.client.realtime
    // Realtime subscriptions will be implemented once Supabase project is configured
}
