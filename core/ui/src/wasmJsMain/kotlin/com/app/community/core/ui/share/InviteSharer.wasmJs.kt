package com.app.community.core.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** ¿Hay Web Share API? (Safari iOS y Chrome sí; Firefox escritorio no.) */
private fun hasWebShare(): Boolean = js("typeof navigator.share === 'function'")

/** Abre el share-sheet nativo del navegador. */
private fun webShare(text: String): Unit = js("navigator.share({ text: text })")

/** Fallback: copiar al portapapeles y avisar. */
private fun copyToClipboard(text: String): Unit =
    js("navigator.clipboard.writeText(text).then(function () { alert('Enlace copiado al portapapeles') })")

actual class InviteSharer {
    actual fun share(text: String) {
        if (hasWebShare()) webShare(text) else copyToClipboard(text)
    }
}

@Composable
actual fun rememberInviteSharer(): InviteSharer = remember { InviteSharer() }
