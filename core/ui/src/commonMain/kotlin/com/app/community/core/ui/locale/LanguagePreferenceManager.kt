package com.app.community.core.ui.locale

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tres modos de idioma soportados:
 *  - AUTO: usa el idioma del sistema (default)
 *  - ES: español forzado
 *  - EN: inglés forzado
 */
enum class AppLanguage {
    AUTO,
    ES,
    EN,
    ;

    /**
     * Devuelve el código BCP-47 que entiende [LocalAppLocale].
     * AUTO devuelve null para que LocalAppLocale restaure el default.
     */
    fun toLocaleCode(): String? = when (this) {
        AUTO -> null
        ES -> "es"
        EN -> "en"
    }

    companion object {
        fun fromKey(key: String?): AppLanguage = when (key) {
            "ES" -> ES
            "EN" -> EN
            else -> AUTO
        }
    }
}

/**
 * Almacena la preferencia de idioma actual con persistencia local
 * (SharedPreferences en Android, NSUserDefaults en iOS).
 *
 * Persistir localmente garantiza que el valor inicial en cold start coincide
 * con la preferencia del usuario, evitando flips post-login que disparen
 * recomposiciones destructivas.
 */
class LanguagePreferenceManager(private val settings: Settings) {
    private val _language = MutableStateFlow(
        AppLanguage.fromKey(settings.getStringOrNull(KEY_LANGUAGE)),
    )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        settings.putString(KEY_LANGUAGE, language.name)
        _language.value = language
    }

    private companion object {
        const val KEY_LANGUAGE = "language_preference"
    }
}
