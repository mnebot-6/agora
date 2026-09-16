package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthRepository {

    private val auth = SupabaseProvider.client.auth
    private val postgrest = SupabaseProvider.client.postgrest

    val isAuthenticated: Flow<Boolean> = auth.sessionStatus.map { status ->
        status is io.github.jan.supabase.auth.status.SessionStatus.Authenticated
    }

    /**
     * True cuando la sesión activa es de un invitado anónimo del antiguo formulario
     * web. Las cuentas reales se crean vía Email y siempre tienen email. App.kt
     * cierra estas sesiones: ya no hay UI de invitado.
     */
    val isGuestSession: Flow<Boolean> = auth.sessionStatus.map {
        val user = auth.currentUserOrNull()
        user != null && user.email.isNullOrBlank()
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

    suspend fun deleteAccount(): AppResult<Unit> {
        val deleteResult = safeCall { postgrest.rpc(function = "delete_my_account") }
        // Best-effort local sign-out: si el delete tuvo éxito el token ya está
        // invalidado en backend, así que un fallo aquí no debe enmascarar el
        // resultado real del delete.
        runCatching { auth.signOut() }
        return deleteResult.map { Unit }
    }
}
