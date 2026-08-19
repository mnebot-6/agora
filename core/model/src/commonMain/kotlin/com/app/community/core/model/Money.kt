package com.app.community.core.model

/**
 * Importes en centimos enteros. Nunca Double: 8.30 * 100 da 829.9999... y truncar eso cobra
 * un centimo de menos. Toda la app habla de dinero a traves de estas dos funciones.
 *
 * Solo euros, decidido el 2026-08-19. Por eso no hay parametro de moneda ni columna en la
 * base de datos: el simbolo esta aqui y en la sesion de Checkout, y ya.
 */

/** Techo por cobro: 100 000 €. No es regla de negocio, es un cortafuegos contra el dedo gordo. */
const val MAX_PRICE_CENTS: Int = 10_000_000

/** 650 -> "6,50 €". */
fun formatEuros(cents: Int): String {
    val euros = cents / 100
    val remainder = cents % 100
    return "$euros,${remainder.toString().padStart(2, '0')} €"
}

/**
 * "6,50" | "6.50" | "6,5" | "6" | "6,50 €" -> 650. Devuelve null si no es un importe cobrable:
 * vacio, cero, negativo, mas de dos decimales, separadores de millar, o por encima del techo.
 */
fun parseEurosToCents(input: String): Int? {
    val cleaned = input.trim().removeSuffix("€").trim()
    if (cleaned.isEmpty()) return null

    val separators = cleaned.count { it == ',' || it == '.' }
    if (separators > 1) return null

    val separatorIndex = cleaned.indexOfFirst { it == ',' || it == '.' }
    val wholePart = if (separatorIndex < 0) cleaned else cleaned.substring(0, separatorIndex)
    val decimalPart = if (separatorIndex < 0) "" else cleaned.substring(separatorIndex + 1)

    if (decimalPart.length > 2) return null
    if (wholePart.isEmpty() && decimalPart.isEmpty()) return null
    if (!wholePart.all { it.isDigit() } || !decimalPart.all { it.isDigit() }) return null

    val euros = if (wholePart.isEmpty()) 0 else wholePart.toIntOrNull() ?: return null
    // "6,5" son 50 centimos, no 5. Se rellena por la derecha.
    val cents = when (decimalPart.length) {
        0 -> 0
        1 -> decimalPart.toInt() * 10
        else -> decimalPart.toInt()
    }

    if (euros > MAX_PRICE_CENTS / 100) return null
    val total = euros * 100 + cents
    if (total <= 0 || total > MAX_PRICE_CENTS) return null
    return total
}
