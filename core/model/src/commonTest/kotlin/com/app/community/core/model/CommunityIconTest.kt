package com.app.community.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommunityIconTest {

    @Test
    fun hay_exactamente_16_iconos() {
        assertEquals(16, CommunityIcon.entries.size)
    }

    @Test
    fun las_claves_son_estables_y_en_minusculas() {
        CommunityIcon.entries.forEach { icon ->
            assertTrue(icon.key.isNotBlank(), "clave vacia en ${icon.name}")
            assertEquals(icon.key.lowercase(), icon.key, "clave no minuscula: ${icon.key}")
        }
        assertEquals(16, CommunityIcon.entries.map { it.key }.toSet().size)
    }

    @Test
    fun from_key_resuelve_una_clave_conocida() {
        assertEquals(CommunityIcon.VOLLEYBALL, CommunityIcon.fromKey("volleyball"))
    }

    @Test
    fun from_key_devuelve_null_ante_clave_desconocida_o_nula() {
        assertNull(CommunityIcon.fromKey("no-existe"))
        assertNull(CommunityIcon.fromKey(null))
    }

    @Test
    fun avatar_color_index_es_determinista_y_cae_en_rango() {
        val id = "0f8a1c2b-1111-2222-3333-444455556666"
        val first = avatarColorIndex(id)
        assertEquals(first, avatarColorIndex(id))
        assertTrue(first in 0 until AVATAR_COLOR_COUNT)
    }

    @Test
    fun avatar_color_index_nunca_es_negativo_aunque_el_hash_lo_sea() {
        // Cadenas con hashCode negativo: el modulo ingenuo devolveria un indice negativo
        // y reventaria el acceso a la lista de colores.
        listOf("zzzzzzzzzzzz", "actividad-de-prueba-larga", "😀comunidad").forEach { id ->
            assertTrue(avatarColorIndex(id) >= 0, "indice negativo para $id")
            assertTrue(avatarColorIndex(id) < AVATAR_COLOR_COUNT)
        }
    }

    @Test
    fun avatar_color_index_tolera_cadena_vacia() {
        assertTrue(avatarColorIndex("") in 0 until AVATAR_COLOR_COUNT)
    }
}
