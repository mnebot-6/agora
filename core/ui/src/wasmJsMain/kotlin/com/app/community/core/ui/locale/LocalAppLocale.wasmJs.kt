package com.app.community.core.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale

/**
 * Patrón oficial JetBrains para web:
 * https://kotlinlang.org/docs/multiplatform/compose-resource-environment.html
 * El script del index.html intercepta `navigator.languages` y devuelve
 * `window.__customLocale` cuando está definido; aquí solo escribimos ese valor.
 */
private fun setCustomLocale(value: String?): Unit =
    js("void (window.__customLocale = value ?? undefined)")

actual object LocalAppLocale {
    private val LocalAppLocale = staticCompositionLocalOf { Locale.current }

    actual val current: String
        @Composable get() = LocalAppLocale.current.toString()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        setCustomLocale(value?.replace('_', '-'))
        return LocalAppLocale.provides(Locale.current)
    }
}
