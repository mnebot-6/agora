package com.app.community.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentModeTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun activity(mode: String, priceCents: Int?) = json.decodeFromString<Activity>(
        """{"id":"a1","community_id":"c1","name":"Entreno",
           "datetime":"2026-09-01T20:00:00Z","duration_minutes":90,
           "slot_mode":"limited","created_by":"u1",
           "payment_mode":"$mode"${priceCents?.let { ""","price_cents":$it""" } ?: ""}}""",
    )

    @Test
    fun free_is_free_whatever_the_community_does() {
        val a = activity("free", null)
        assertEquals(PaymentMode.FREE, a.effectiveMode(communityChargesEnabled = true))
        assertEquals(PaymentMode.FREE, a.effectiveMode(communityChargesEnabled = false))
    }

    @Test
    fun external_never_becomes_agora() {
        val a = activity("external", 650)
        assertEquals(PaymentMode.EXTERNAL, a.effectiveMode(communityChargesEnabled = true))
    }

    @Test
    fun agora_degrades_to_external_without_a_collector() {
        val a = activity("agora", 650)
        assertEquals(PaymentMode.AGORA, a.effectiveMode(communityChargesEnabled = true))
        assertEquals(PaymentMode.EXTERNAL, a.effectiveMode(communityChargesEnabled = false))
    }

    @Test
    fun an_unknown_mode_falls_back_to_external_instead_of_throwing() {
        val a = activity("some_future_mode", 650)
        assertEquals(PaymentMode.EXTERNAL, a.paymentMode)
    }

    @Test
    fun a_list_with_one_unknown_mode_still_decodes_the_rest() {
        val payload = """[
            {"id":"a1","community_id":"c1","name":"A","datetime":"2026-09-01T20:00:00Z",
             "duration_minutes":90,"slot_mode":"limited","created_by":"u1","payment_mode":"free"},
            {"id":"a2","community_id":"c1","name":"B","datetime":"2026-09-01T20:00:00Z",
             "duration_minutes":90,"slot_mode":"limited","created_by":"u1",
             "payment_mode":"some_future_mode","price_cents":650}
        ]"""
        val decoded = json.decodeFromString<List<Activity>>(payload)
        assertEquals(2, decoded.size)
        assertEquals(PaymentMode.FREE, decoded[0].paymentMode)
        assertEquals(PaymentMode.EXTERNAL, decoded[1].paymentMode)
    }

    @Test
    fun a_missing_mode_defaults_to_free() {
        val a = json.decodeFromString<Activity>(
            """{"id":"a1","community_id":"c1","name":"Entreno",
               "datetime":"2026-09-01T20:00:00Z","duration_minutes":90,
               "slot_mode":"limited","created_by":"u1"}""",
        )
        assertEquals(PaymentMode.FREE, a.paymentMode)
    }
}
