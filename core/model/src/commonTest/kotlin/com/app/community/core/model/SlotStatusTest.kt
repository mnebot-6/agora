package com.app.community.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlotStatusTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun row(status: String) =
        """{"id":"1","activity_id":"a1","sort_order":0,"status":"$status","is_guest":false}"""

    @Test
    fun known_status_decodes_to_its_entry() {
        assertEquals(SlotStatus.PAID, json.decodeFromString<Slot>(row("paid")).status)
    }

    @Test
    fun pending_payment_decodes() {
        assertEquals(
            SlotStatus.PENDING_PAYMENT,
            json.decodeFromString<Slot>(row("pending_payment")).status,
        )
    }

    @Test
    fun unknown_status_falls_back_instead_of_throwing() {
        assertEquals(SlotStatus.UNKNOWN, json.decodeFromString<Slot>(row("some_future")).status)
    }

    @Test
    fun a_list_with_one_unknown_row_still_decodes_the_rest() {
        val payload = "[${row("paid")},${row("some_future")},${row("available")}]"
        val decoded = json.decodeFromString<List<Slot>>(payload)
        assertEquals(3, decoded.size)
        assertEquals(SlotStatus.PAID, decoded[0].status)
        assertEquals(SlotStatus.UNKNOWN, decoded[1].status)
        assertEquals(SlotStatus.AVAILABLE, decoded[2].status)
    }

    @Test
    fun every_entry_has_a_distinct_wire_value() {
        assertEquals(
            SlotStatus.entries.size,
            SlotStatus.entries.map { it.wire }.toSet().size,
        )
    }

    @Test
    fun status_round_trips_through_its_wire_value() {
        for (entry in SlotStatus.entries) {
            assertEquals(entry, json.decodeFromString<Slot>(row(entry.wire)).status)
        }
    }

    /** Una plaza de estado desconocido no es reclamable ni tratable como propia. */
    @Test
    fun unknown_is_never_available() {
        val slot = json.decodeFromString<Slot>(row("some_future"))
        assertFalse(slot.isAvailable)
        assertFalse(slot.isReserved)
        assertFalse(slot.isPaid)
        assertFalse(slot.isPendingGuest)
        assertFalse(slot.isPendingPayment)
    }

    @Test
    fun pending_payment_is_not_available() {
        val slot = json.decodeFromString<Slot>(row("pending_payment"))
        assertTrue(slot.isPendingPayment)
        assertFalse(slot.isAvailable)
    }
}
