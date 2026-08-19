package com.app.community

/**
 * Deep link llegado por URL en el target web:
 * /app/?pay={payment} | /app/?c={invite} | /app/?a={activity}.
 */
sealed interface WebDeepLink {
    data class Invite(val code: String) : WebDeepLink
    data class Activity(val code: String) : WebDeepLink

    /** Vuelta de Stripe Checkout. worker.js redirige /pay/ok?p=... aqui. */
    data class Payment(val paymentId: String) : WebDeepLink

    /** Vuelta del alta de Connect. worker.js redirige /pay/connect?community=... aqui. */
    data class ConnectReturn(val communityId: String) : WebDeepLink
}

/**
 * Parsea el query string de la URL (p. ej. "?c=ABC123") a un deep link.
 * Precedencia si viene mas de uno: pago > invitación > actividad. El pago va primero
 * porque volver de un cobro es lo mas urgente que puede traer una URL: si se pierde,
 * el usuario se queda mirando una plaza sin confirmar despues de haber pagado.
 */
fun parseWebDeepLink(search: String): WebDeepLink? {
    val params = search.removePrefix("?")
        .split('&')
        .mapNotNull { param ->
            val separator = param.indexOf('=')
            if (separator <= 0) null
            else param.take(separator) to percentDecode(param.substring(separator + 1))
        }
        .toMap()
    params["pay"]?.takeIf { it.isNotEmpty() }?.let { return WebDeepLink.Payment(it) }
    params["connect"]?.takeIf { it.isNotEmpty() }?.let { return WebDeepLink.ConnectReturn(it) }
    params["c"]?.takeIf { it.isNotEmpty() }?.let { return WebDeepLink.Invite(it) }
    params["a"]?.takeIf { it.isNotEmpty() }?.let { return WebDeepLink.Activity(it) }
    return null
}

/** Decodificación percent-encoding mínima (los códigos son alfanuméricos; esto cubre el caso raro). */
private fun percentDecode(value: String): String {
    val sb = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        val ch = value[i]
        if (ch == '%' && i + 2 < value.length) {
            val code = value.substring(i + 1, i + 3).toIntOrNull(16)
            if (code != null) {
                sb.append(code.toChar())
                i += 3
                continue
            }
        }
        sb.append(if (ch == '+') ' ' else ch)
        i++
    }
    return sb.toString()
}
