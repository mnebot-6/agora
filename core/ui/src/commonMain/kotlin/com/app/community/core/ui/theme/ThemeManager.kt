package com.app.community.core.ui.theme

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemeManager(private val settings: Settings) {
    private val _isDarkMode = MutableStateFlow(settings.getBoolean(KEY_DARK_MODE, false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        settings.putBoolean(KEY_DARK_MODE, enabled)
        _isDarkMode.value = enabled
    }

    private companion object {
        const val KEY_DARK_MODE = "dark_mode"
    }
}
