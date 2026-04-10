package com.app.community.core.data.repository

import com.app.community.core.model.GroupTemplate
import com.app.community.core.model.SlotTemplate
import com.app.community.core.model.SlotTemplateEntry
import com.app.community.core.model.TemplateConfig

object DefaultTemplates {

    fun getAll(): List<SlotTemplate> = listOf(
        volleyball6v6(),
        basketball5v5(),
        football7(),
        football11(),
        padel(),
    )

    // Voley 6v6: Colocador(0), Opuesto(1), Central(2), Receptor(3)
    private fun volleyball6v6() = SlotTemplate(
        id = "default_volleyball_6v6",
        name = "Voley 6v6",
        config = TemplateConfig(
            positions = listOf("Colocador", "Opuesto", "Central", "Receptor"),
            groups = listOf(
                teamGroup("Equipo 1", allPositionSlots(4, 6)),
                teamGroup("Equipo 2", allPositionSlots(4, 6)),
            ),
        ),
    )

    // Basketball 5v5: Base(0), Escolta(1), Alero(2), Ala-Pivot(3), Pivot(4)
    private fun basketball5v5() = SlotTemplate(
        id = "default_basketball_5v5",
        name = "Basketball 5v5",
        config = TemplateConfig(
            positions = listOf("Base", "Escolta", "Alero", "Ala-Pivot", "Pivot"),
            groups = listOf(
                teamGroup("Equipo 1", onePerPosition(5)),
                teamGroup("Equipo 2", onePerPosition(5)),
            ),
        ),
    )

    // Futbol 7: Portero(0), Defensa(1), Centrocampista(2), Delantero(3) -> 1+2+2+2
    private fun football7() = SlotTemplate(
        id = "default_football_7",
        name = "Futbol 7",
        config = TemplateConfig(
            positions = listOf("Portero", "Defensa", "Centrocampista", "Delantero"),
            groups = listOf(
                teamGroup("Equipo 1", football7Slots()),
                teamGroup("Equipo 2", football7Slots()),
            ),
        ),
    )

    // Futbol 11: Portero(0), Defensa(1), Centrocampista(2), Delantero(3) -> 1+4+3+3
    private fun football11() = SlotTemplate(
        id = "default_football_11",
        name = "Futbol 11",
        config = TemplateConfig(
            positions = listOf("Portero", "Defensa", "Centrocampista", "Delantero"),
            groups = listOf(
                teamGroup("Equipo 1", football11Slots()),
                teamGroup("Equipo 2", football11Slots()),
            ),
        ),
    )

    // Padel: no positions, 2 per pair
    private fun padel() = SlotTemplate(
        id = "default_padel",
        name = "Padel",
        config = TemplateConfig(
            positions = emptyList(),
            groups = listOf(
                GroupTemplate("Pareja 1", List(2) { SlotTemplateEntry() }),
                GroupTemplate("Pareja 2", List(2) { SlotTemplateEntry() }),
            ),
        ),
    )

    // Helpers
    private fun teamGroup(name: String, slots: List<SlotTemplateEntry>) =
        GroupTemplate(name, slots)

    // 6 slots each accepting all positions
    private fun allPositionSlots(positionCount: Int, slotCount: Int) =
        List(slotCount) { SlotTemplateEntry(positionIndices = (0 until positionCount).toSet()) }

    // 1 slot per position
    private fun onePerPosition(count: Int) =
        List(count) { i -> SlotTemplateEntry(positionIndices = setOf(i)) }

    // 1 GK + 2 DEF + 2 MID + 2 FWD
    private fun football7Slots() = buildList {
        add(SlotTemplateEntry(positionIndices = setOf(0)))
        repeat(2) { add(SlotTemplateEntry(positionIndices = setOf(1))) }
        repeat(2) { add(SlotTemplateEntry(positionIndices = setOf(2))) }
        repeat(2) { add(SlotTemplateEntry(positionIndices = setOf(3))) }
    }

    // 1 GK + 4 DEF + 3 MID + 3 FWD
    private fun football11Slots() = buildList {
        add(SlotTemplateEntry(positionIndices = setOf(0)))
        repeat(4) { add(SlotTemplateEntry(positionIndices = setOf(1))) }
        repeat(3) { add(SlotTemplateEntry(positionIndices = setOf(2))) }
        repeat(3) { add(SlotTemplateEntry(positionIndices = setOf(3))) }
    }
}
