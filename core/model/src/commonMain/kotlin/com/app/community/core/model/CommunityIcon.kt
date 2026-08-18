package com.app.community.core.model

/**
 * Repertorio cerrado de iconos que un admin puede elegir para su comunidad.
 *
 * La clave es texto estable y se persiste en `communities.icon_key`. El nombre
 * de la constante de Compose que la pinta vive en core/ui, de forma que cambiar
 * el dibujo de un icono no obliga a migrar datos.
 */
enum class CommunityIcon(val key: String, val family: CommunityIconFamily) {
    VOLLEYBALL("volleyball", CommunityIconFamily.SPORT),
    SOCCER("soccer", CommunityIconFamily.SPORT),
    BASKETBALL("basketball", CommunityIconFamily.SPORT),
    RUNNING("running", CommunityIconFamily.SPORT),

    GROUP("group", CommunityIconFamily.SOCIAL),
    HOME("home", CommunityIconFamily.SOCIAL),
    COFFEE("coffee", CommunityIconFamily.SOCIAL),
    PARTY("party", CommunityIconFamily.SOCIAL),

    MUSIC("music", CommunityIconFamily.CULTURE),
    THEATER("theater", CommunityIconFamily.CULTURE),
    BOOK("book", CommunityIconFamily.CULTURE),
    ART("art", CommunityIconFamily.CULTURE),

    WORK("work", CommunityIconFamily.OTHER),
    TRAVEL("travel", CommunityIconFamily.OTHER),
    PET("pet", CommunityIconFamily.OTHER),
    GENERIC("generic", CommunityIconFamily.OTHER),
    ;

    companion object {
        fun fromKey(key: String?): CommunityIcon? =
            if (key == null) null else entries.firstOrNull { it.key == key }
    }
}

enum class CommunityIconFamily { SPORT, SOCIAL, CULTURE, OTHER }

/** Número de colores de fondo disponibles para el avatar de fallback. */
const val AVATAR_COLOR_COUNT: Int = 4

/**
 * Índice de color estable para una comunidad sin icono elegido.
 *
 * `mod` (no `%`) porque `hashCode()` puede ser negativo y el resto de `%`
 * conservaría el signo: un índice negativo reventaría el acceso a la paleta.
 */
fun avatarColorIndex(id: String): Int = id.hashCode().mod(AVATAR_COLOR_COUNT)
