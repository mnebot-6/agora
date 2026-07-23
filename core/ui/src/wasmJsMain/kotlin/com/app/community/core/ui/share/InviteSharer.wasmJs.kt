package com.app.community.core.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** ¿Hay Web Share API? (Safari iOS y Chrome sí; Firefox escritorio no.) */
private fun hasWebShare(): Boolean = js("typeof navigator.share === 'function'")

/**
 * ¿Es un dispositivo móvil? En escritorio `navigator.share` también existe (p. ej.
 * Chrome en Windows), pero abre el diálogo de compartir del sistema, que casi no
 * ofrece destinos y resulta inútil; ahí preferimos copiar al portapapeles.
 *
 * `userAgentData.mobile` es la señal fiable en Chromium; Safari/Firefox no la
 * exponen, así que caemos al user agent. iPadOS se identifica como Macintosh, por
 * eso se distingue por los puntos táctiles.
 */
private fun isMobileDevice(): Boolean =
    js("(navigator.userAgentData && typeof navigator.userAgentData.mobile === 'boolean') ? navigator.userAgentData.mobile : (/Android|iPhone|iPod/i.test(navigator.userAgent) || (/iPad|Macintosh/i.test(navigator.userAgent) && navigator.maxTouchPoints > 1))")

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
    /**
     * Móvil → share-sheet nativo (como en Android/iOS).
     * Escritorio → copiar al portapapeles, que es lo útil de verdad ahí.
     */
    actual fun share(text: String) {
        if (hasWebShare() && isMobileDevice()) webShare(text) else copyToClipboard(text)
    }
}

@Composable
actual fun rememberInviteSharer(): InviteSharer = remember { InviteSharer() }
