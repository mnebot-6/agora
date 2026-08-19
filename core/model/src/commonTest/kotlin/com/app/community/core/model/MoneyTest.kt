package com.app.community.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {

    // --- formatEuros ---

    @Test fun formats_whole_euros() = assertEquals("6,00 €", formatEuros(600))
    @Test fun formats_cents() = assertEquals("6,50 €", formatEuros(650))
    @Test fun formats_single_digit_cents() = assertEquals("6,05 €", formatEuros(605))
    @Test fun formats_zero() = assertEquals("0,00 €", formatEuros(0))
    @Test fun formats_under_one_euro() = assertEquals("0,99 €", formatEuros(99))
    @Test fun formats_one_cent() = assertEquals("0,01 €", formatEuros(1))
    @Test fun formats_large() = assertEquals("1234,00 €", formatEuros(123400))

    // --- parseEurosToCents: lo que tiene que aceptar ---

    @Test fun parses_comma() = assertEquals(650, parseEurosToCents("6,50"))
    @Test fun parses_dot() = assertEquals(650, parseEurosToCents("6.50"))
    @Test fun parses_integer() = assertEquals(600, parseEurosToCents("6"))
    @Test fun parses_one_decimal() = assertEquals(650, parseEurosToCents("6,5"))
    @Test fun parses_leading_zero() = assertEquals(50, parseEurosToCents("0,50"))
    @Test fun parses_without_leading_zero() = assertEquals(50, parseEurosToCents(",50"))
    @Test fun trims_whitespace() = assertEquals(650, parseEurosToCents("  6,50  "))
    @Test fun accepts_euro_sign() = assertEquals(650, parseEurosToCents("6,50 €"))
    @Test fun accepts_trailing_separator() = assertEquals(600, parseEurosToCents("6,"))

    // --- parseEurosToCents: lo que tiene que rechazar ---

    @Test fun rejects_empty() = assertNull(parseEurosToCents(""))
    @Test fun rejects_blank() = assertNull(parseEurosToCents("   "))
    @Test fun rejects_negative() = assertNull(parseEurosToCents("-6,50"))
    @Test fun rejects_zero() = assertNull(parseEurosToCents("0"))
    @Test fun rejects_zero_with_decimals() = assertNull(parseEurosToCents("0,00"))
    @Test fun rejects_three_decimals() = assertNull(parseEurosToCents("6,555"))
    @Test fun rejects_letters() = assertNull(parseEurosToCents("seis euros"))
    @Test fun rejects_letters_mixed_in() = assertNull(parseEurosToCents("6a,50"))
    @Test fun rejects_two_separators() = assertNull(parseEurosToCents("6,5,0"))
    @Test fun rejects_mixed_separators() = assertNull(parseEurosToCents("1.234,56"))
    @Test fun rejects_only_separator() = assertNull(parseEurosToCents(","))
    @Test fun rejects_plus_sign() = assertNull(parseEurosToCents("+6,50"))
    @Test fun rejects_above_ceiling() = assertNull(parseEurosToCents("100001"))
    @Test fun accepts_exactly_the_ceiling() = assertEquals(MAX_PRICE_CENTS, parseEurosToCents("100000"))

    // --- invariantes ---

    @Test
    fun round_trips_through_format() {
        for (cents in listOf(1, 5, 99, 100, 650, 605, 123456, MAX_PRICE_CENTS)) {
            assertEquals(cents, parseEurosToCents(formatEuros(cents)), "fallo con $cents")
        }
    }

    /**
     * 0.1 + 0.2 != 0.3 en coma flotante. Este test existe para que nadie "simplifique"
     * el parser a toDouble() * 100: con Double, "8,30" da 829 al truncar.
     */
    @Test
    fun parses_the_amounts_that_floating_point_gets_wrong() {
        assertEquals(830, parseEurosToCents("8,30"))
        assertEquals(1110, parseEurosToCents("11,10"))
        assertEquals(2920, parseEurosToCents("29,20"))
        assertEquals(1170, parseEurosToCents("11,70"))
    }
}
