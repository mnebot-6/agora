package com.app.community.core.model

/**
 * Un telefono escrito a mano dentro de un texto libre ("Bizum al 612 34 56 78").
 * [raw] es tal cual aparece, para poder resaltarlo dentro del texto original;
 * [normalized] es lo que se copia, ya sin separadores, que es lo que aceptan el
 * marcador y las apps de pago.
 */
data class PhoneMatch(val raw: String, val normalized: String)

// Un movil espanol son 9 digitos; con prefijo internacional se va a 11-12. Exigir
// entre 9 y 15 digitos deja fuera lo que tambien lleva numeros en este campo: el
// importe ("6,50 €") y las fechas (8 digitos), y cubre cualquier prefijo del mundo.
private val PHONE_CANDIDATE = Regex("""\+?\d[\d ./-]{7,17}\d""")

/** El primer telefono que aparezca en [text], o null si no hay ninguno. */
fun findPhoneNumber(text: String?): PhoneMatch? {
    if (text.isNullOrBlank()) return null
    return PHONE_CANDIDATE.findAll(text)
        .map { it.value.trimEnd(' ', '.', '-', '/') }
        .firstNotNullOfOrNull { raw ->
            val normalized = raw.filter { it.isDigit() || it == '+' }
            if (normalized.count { it.isDigit() } in 9..15) PhoneMatch(raw, normalized) else null
        }
}
