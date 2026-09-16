package com.app.community.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneNumberTest {

    @Test
    fun finds_the_phone_inside_free_text() {
        assertEquals(
            PhoneMatch(raw = "612 345 678", normalized = "612345678"),
            findPhoneNumber("6,50 € · Bizum al 612 345 678"),
        )
    }

    @Test
    fun keeps_the_international_prefix() {
        assertEquals(
            PhoneMatch(raw = "+34 612-345-678", normalized = "+34612345678"),
            findPhoneNumber("Bizum a +34 612-345-678, gracias"),
        )
    }

    @Test
    fun ignores_amounts_and_dates() {
        assertNull(findPhoneNumber("6,50 € en efectivo antes del 12/09/2026 a las 20:00"))
    }

    @Test
    fun no_phone_no_match() {
        assertNull(findPhoneNumber("Se paga en mano el dia del partido"))
        assertNull(findPhoneNumber(null))
        assertNull(findPhoneNumber("   "))
    }
}
