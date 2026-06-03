package com.app.community

import com.russhwolf.settings.Settings

/**
 * Persiste el código de la actividad para la que el dispositivo entró como
 * invitado, de modo que la sesión anónima sobreviva a un cold-start sin perder
 * el contexto (y por tanto sin perder la solicitud ya enviada).
 */
class GuestSessionStore(private val settings: Settings) {

    fun setActivityCode(code: String?) {
        if (code == null) settings.remove(KEY) else settings.putString(KEY, code)
    }

    fun activityCode(): String? = settings.getStringOrNull(KEY)

    private companion object {
        const val KEY = "guest_activity_code"
    }
}
