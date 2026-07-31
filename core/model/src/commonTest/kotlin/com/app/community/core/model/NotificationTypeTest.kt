package com.app.community.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationTypeTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun row(type: String) =
        """{"id":"1","user_id":"u1","type":"$type","title":"t","body":"b","read":false,"created_at":"2026-07-29T10:00:00Z"}"""

    @Test
    fun known_type_decodes_to_its_entry() {
        val decoded = json.decodeFromString<Notification>(row("slot_assigned"))
        assertEquals(NotificationType.SLOT_ASSIGNED, decoded.type)
    }

    @Test
    fun unknown_type_falls_back_instead_of_throwing() {
        val decoded = json.decodeFromString<Notification>(row("some_future_type"))
        assertEquals(NotificationType.UNKNOWN, decoded.type)
    }

    @Test
    fun a_list_with_one_unknown_row_still_decodes_the_rest() {
        val payload = "[${row("new_activity")},${row("some_future_type")}]"
        val decoded = json.decodeFromString<List<Notification>>(payload)
        assertEquals(2, decoded.size)
        assertEquals(NotificationType.NEW_ACTIVITY, decoded[0].type)
        assertEquals(NotificationType.UNKNOWN, decoded[1].type)
    }
}
