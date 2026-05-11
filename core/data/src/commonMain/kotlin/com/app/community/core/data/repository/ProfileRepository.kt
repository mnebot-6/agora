package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import com.app.community.core.model.Profile
import io.github.jan.supabase.postgrest.postgrest

class ProfileRepository {

    private val postgrest = SupabaseProvider.client.postgrest

    suspend fun getProfile(userId: String): AppResult<Profile> =
        safeCall {
            postgrest.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingle<Profile>()
        }

    suspend fun updateProfile(userId: String, displayName: String, avatarUrl: String? = null): AppResult<Unit> =
        safeCall {
            postgrest.from("profiles")
                .update({
                    set("display_name", displayName)
                    avatarUrl?.let { set("avatar_url", it) }
                }) { filter { eq("id", userId) } }
        }

    suspend fun updateFcmToken(userId: String, token: String): AppResult<Unit> =
        safeCall {
            postgrest.from("profiles")
                .update({ set("fcm_token", token) }) { filter { eq("id", userId) } }
        }

    suspend fun clearFcmToken(userId: String): AppResult<Unit> =
        safeCall {
            postgrest.from("profiles")
                .update({ set("fcm_token", null as String?) }) { filter { eq("id", userId) } }
        }

    suspend fun updateDarkMode(userId: String, darkMode: Boolean): AppResult<Unit> =
        safeCall {
            postgrest.from("profiles")
                .update({ set("dark_mode", darkMode) }) { filter { eq("id", userId) } }
        }

    suspend fun updateLanguagePreference(userId: String, language: String): AppResult<Unit> =
        safeCall {
            postgrest.from("profiles")
                .update({ set("language_preference", language) }) { filter { eq("id", userId) } }
        }
}
