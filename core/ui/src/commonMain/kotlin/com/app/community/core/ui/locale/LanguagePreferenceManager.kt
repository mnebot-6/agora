package com.app.community.core.ui.locale

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
}

/**
 * Almacena la preferencia de idioma actual en memoria. La persistencia (Supabase
 * profile o DataStore) la gestiona el screen model que llama a [setLanguage].
 *
 * Patrón paralelo a [com.app.community.core.ui.theme.ThemeManager].
 */
class LanguagePreferenceManager {
    private val _language = MutableStateFlow(AppLanguage.AUTO)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        _language.value = language
    }
}
