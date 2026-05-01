package com.app.community.core.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key

/**
 * Override de locale por app. Patrón oficial JetBrains para Compose Multiplatform 1.7+:
 * https://kotlinlang.org/docs/multiplatform/compose-resource-environment.html
 *
 * Pasar `null` como valor restaura el locale por defecto del sistema.
 */
expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

/**
 * Wrapper que aplica el locale custom y fuerza recomposición del árbol completo
 * mediante `key(locale)` cuando el valor cambia.
 */
@Composable
fun AppLocaleProvider(locale: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppLocale provides locale,
    ) {
        key(locale) {
            content()
        }
    }
}
