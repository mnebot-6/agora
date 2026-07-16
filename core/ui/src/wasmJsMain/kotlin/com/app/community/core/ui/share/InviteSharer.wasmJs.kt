package com.app.community.core.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** ¿Hay Web Share API? (Safari iOS y Chrome sí; Firefox escritorio no.) */
private fun hasWebShare(): Boolean = js("typeof navigator.share === 'function'")

/** Abre el share-sheet nativo del navegador. Silencia el rechazo (p. ej. el usuario cancela). */
private fun webShare(text: String): Unit =
    js("navigator.share({ text: text }).catch(function () {})")

/**
 * Fallback: copiar al portapapeles y avisar. Si no hay Clipboard API (contexto
 * no seguro) o falla la escritura, mostrar el enlace en el alert como último recurso.
 */
private fun copyToClipboard(text: String): Unit =
    js("navigator.clipboard ? navigator.clipboard.writeText(text).then(function () { alert('Enlace copiado al portapapeles') }).catch(function () { alert(text) }) : alert(text)")

actual class InviteSharer {
    actual fun share(text: String) {
        if (hasWebShare()) webShare(text) else copyToClipboard(text)
    }
}

@Composable
actual fun rememberInviteSharer(): InviteSharer = remember { InviteSharer() }
