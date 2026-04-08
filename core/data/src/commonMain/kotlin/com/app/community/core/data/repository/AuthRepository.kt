package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthRepository {

    private val auth = SupabaseProvider.client.auth

    val isAuthenticated: Flow<Boolean> = auth.sessionStatus.map { status ->
        status is io.github.jan.supabase.auth.status.SessionStatus.Authenticated
    }

    fun currentUserId(): String? = auth.currentUserOrNull()?.id

    suspend fun signUp(email: String, password: String, displayName: String): AppResult<Unit> =
        safeCall {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
                data = kotlinx.serialization.json.buildJsonObject {
                    put("display_name", kotlinx.serialization.json.JsonPrimitive(displayName))
                }
            }
        }

    suspend fun signIn(email: String, password: String): AppResult<Unit> =
        safeCall {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        }

    suspend fun signOut(): AppResult<Unit> =
        safeCall { auth.signOut() }

    suspend fun resetPassword(email: String): AppResult<Unit> =
        safeCall { auth.resetPasswordForEmail(email) }
}
