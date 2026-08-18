# Simplificación de navegación y jerarquía visual — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reducir la app de 5 tabs a 3, hacer visible la jerarquía padre/hija de comunidades con icono configurable, y separar visualmente las tarjetas de comunidad de las de actividad.

**Architecture:** Cambio de UI y navegación sobre la estructura existente de Voyager (`TabNavigator` en `App.kt` + un `Navigator` por tab en `AppTabs.kt`). Una única migración aditiva de Supabase añade `icon_key` a `communities`. La lógica pura nueva (catálogo de iconos, color determinista por id) vive en `core/model` donde ya hay tests; los composables compartidos viven en `core/ui` y reciben strings ya formateados para no arrastrar recursos entre módulos.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Voyager (navegación), Koin (DI), Supabase (postgrest + CLI de migraciones), kotlin.test.

**Spec:** [docs/superpowers/specs/2026-08-18-simplificacion-ux-navegacion-design.md](../specs/2026-08-18-simplificacion-ux-navegacion-design.md)

---

## Contexto que el implementador necesita

**Navegación.** `App.kt` monta un `TabNavigator` con 5 tabs. Cada tab en `AppTabs.kt` es un `object : Tab` cuyo `Content()` monta su propio `Navigator` con una pantalla raíz. Empujar una pantalla es `navigator.push(PantallaX())`; el botón atrás la saca. La pestaña seleccionada vive elevada en `App.kt:86` a propósito (los `key(isDarkMode)` y `key(locale)` destruirían el `TabNavigator` y volvería al tab inicial) — no muevas ese estado.

**Deep links.** `DeepLinkHandler` es un singleton con tres `StateFlow` de códigos pendientes. Quien los consume es un `LaunchedEffect` dentro del `Navigator` del tab correspondiente. `DeepLinkTabSwitcher` en `App.kt:193` conmuta al tab que sabe montar la pantalla. Es fontanería: el usuario nunca ve el tab, solo la pantalla destino.

**Tests.** Solo hay dos archivos de test en el repo (`composeApp/src/commonTest/.../WebDeepLinkTest.kt` y `core/model/src/commonTest/.../NotificationTypeTest.kt`). **No hay harness de tests de UI de Compose y este plan no introduce uno** (montarlo cuesta más que el cambio entero). Por eso solo la Task 4 es TDD real: es la única lógica pura nueva. El resto se verifica con compilación de los dos targets y recorrido manual, que es lo que se lista en la Task 9.

**Migraciones.** Se usa el CLI de Supabase: `supabase migration new <nombre>` crea el archivo, `supabase db push` lo aplica. El baseline (`20260625120019_baseline.sql`) NO se edita nunca; las funciones se redefinen con `CREATE OR REPLACE` en la migración nueva.

---

## Estructura de archivos

**Crear:**

| Archivo | Responsabilidad |
|---|---|
| `core/model/src/commonMain/kotlin/com/app/community/core/model/CommunityIcon.kt` | Enum de las 16 claves de icono + color determinista por id. Sin Compose. |
| `core/model/src/commonTest/kotlin/com/app/community/core/model/CommunityIconTest.kt` | Tests de lo anterior. |
| `core/ui/src/commonMain/kotlin/com/app/community/core/ui/components/CommunityAvatar.kt` | Pinta el icono de una comunidad (o su inicial de fallback) a un tamaño dado. |
| `core/ui/src/commonMain/kotlin/com/app/community/core/ui/components/CommunityRow.kt` | Silueta única de "fila de comunidad": avatar + nombre + subtítulo. |
| `core/ui/src/commonMain/kotlin/com/app/community/core/ui/components/ActivityRow.kt` | Silueta única de "fila de actividad": bloque de fecha + nombre + badge. |
| `feature/community/src/commonMain/kotlin/com/app/community/feature/community/presentation/CommunityIconPicker.kt` | Diálogo con grid 4x4 de iconos. |
| `supabase/migrations/<timestamp>_community_icon_key.sql` | Columna `icon_key` + redefinición de las dos funciones que enumeran columnas. |

**Modificar:**

| Archivo | Qué cambia |
|---|---|
| `composeApp/src/commonMain/kotlin/com/app/community/navigation/AppTabs.kt` | Borrar `ActivitiesTab` y `ProfileTab`; mover sus `LaunchedEffect` a `AgoraTab`. |
| `composeApp/src/commonMain/kotlin/com/app/community/App.kt` | 3 items en la barra; `DeepLinkTabSwitcher` apunta a `AgoraTab`. |
| `composeApp/src/commonMain/kotlin/com/app/community/dashboard/DashboardScreen.kt` | Avatar de perfil en `actions` de la topbar; tarjetas con `ActivityRow`. |
| `composeApp/src/commonMain/kotlin/com/app/community/di/AppModule.kt` | Quitar el registro de `ActivityFeedScreenModel`. |
| `core/model/.../Community.kt` | Campo `iconKey`. |
| `core/data/.../CommunityRepository.kt` | `iconKey` en `createCommunity` y `updateCommunity`. |
| `core/domain/.../CreateCommunityUseCase.kt` | Pasa `iconKey`. |
| `feature/community/.../CommunityListScreen.kt` | Árbol indentado con línea de conexión. |
| `feature/community/.../CommunityDetailScreen.kt` + `...ScreenModel.kt` | Icono en el diálogo de edición; tarjetas con las siluetas nuevas. |
| `feature/community/.../CreateCommunityScreen.kt` + `...ScreenModel.kt` | Selector de icono en el formulario. |
| `feature/community/.../ExploreCommunitiesScreen.kt` | Tarjeta con `CommunityRow`. |
| `core/ui/build.gradle.kts` | Añadir `compose.materialIconsExtended`. |

**Borrar:**

- `feature/activity/src/commonMain/kotlin/com/app/community/feature/activity/presentation/ActivityFeedScreen.kt`
- `feature/activity/src/commonMain/kotlin/com/app/community/feature/activity/presentation/ActivityFeedScreenModel.kt`

---

## Fase 1 — Navegación

### Task 1: Mover el enrutado de deep links de actividad a AgoraTab

Esta task va primero y sola porque es el punto de riesgo del cambio: si se rompe, los links que ya circulan dejan de funcionar. Se hace y se verifica antes de tocar nada más.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/app/community/navigation/AppTabs.kt:37-50` y `:79-111`
- Modify: `composeApp/src/commonMain/kotlin/com/app/community/App.kt:204-215`

- [ ] **Step 1: Reemplazar el `Content()` de `AgoraTab`**

En `AppTabs.kt`, sustituye el cuerpo de `AgoraTab.Content()` (hoy es solo `Navigator(DashboardScreen())`) por esto, que es el bloque que hoy vive en `ActivitiesTab`:

```kotlin
    @Composable
    override fun Content() {
        Navigator(DashboardScreen()) { navigator ->
            val pendingActivityCode by DeepLinkHandler.pendingActivityCode.collectAsState()
            val pendingNotificationActivityId by DeepLinkHandler.pendingNotificationActivityId.collectAsState()
            LaunchedEffect(pendingActivityCode) {
                val code = DeepLinkHandler.consumeActivityCode()
                if (code != null) {
                    navigator.push(GuestActivityScreen(code))
                }
            }
            LaunchedEffect(pendingNotificationActivityId) {
                val id = DeepLinkHandler.consumeNotificationActivityId() ?: return@LaunchedEffect
                val current = navigator.lastItem
                if (current is ActivityDetailScreen && current.activityId == id) {
                    navigator.replace(ActivityDetailScreen(id))
                } else {
                    navigator.push(ActivityDetailScreen(id))
                }
            }
            navigator.lastItem.Content()
        }
    }
```

Fíjate en dos detalles que no se pueden simplificar: `navigator.lastItem.Content()` al final es obligatorio (el lambda del `Navigator` sustituye al render por defecto), y la rama `replace` evita apilar dos veces el mismo detalle cuando llega una push de una actividad que ya estás mirando.

- [ ] **Step 2: Borrar el `object ActivitiesTab` entero**

Elimina las líneas 79-111 de `AppTabs.kt`. Los imports de `ActivityFeedScreen` y `Icons.Default.Event` quedan sin usar; bórralos también. `GuestActivityScreen` y `ActivityDetailScreen` siguen usándose desde `AgoraTab`, no los toques.

- [ ] **Step 3: Reapuntar `DeepLinkTabSwitcher`**

En `App.kt`, dentro de `DeepLinkTabSwitcher`, cambia los dos bloques que mencionan `ActivitiesTab`:

```kotlin
    LaunchedEffect(pendingActivityCode) {
        if (pendingActivityCode != null && tabNavigator.current != AgoraTab) {
            tabNavigator.current = AgoraTab
            onSelectTab(AgoraTab)
        }
    }
    LaunchedEffect(pendingNotificationActivityId) {
        if (pendingNotificationActivityId != null && tabNavigator.current != AgoraTab) {
            tabNavigator.current = AgoraTab
            onSelectTab(AgoraTab)
        }
    }
```

Quita `import com.app.community.navigation.ActivitiesTab` y quita `ActivitiesTab` de la `AgoraNavigationBar` (línea 171). Deja `ProfileTab` en la barra de momento: se quita en la Task 3.

- [ ] **Step 4: Compilar**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Esperado: BUILD SUCCESSFUL. Si falla por `Unresolved reference: ActivityFeedScreen`, quedó un import sin borrar en `AppTabs.kt`.

- [ ] **Step 5: Verificar los tests existentes siguen verdes**

```bash
./gradlew :composeApp:allTests
```

Esperado: BUILD SUCCESSFUL. `WebDeepLinkTest` cubre el parseo de URLs, que no ha cambiado — si falla, has tocado algo que no tocaba.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/app/community/navigation/AppTabs.kt composeApp/src/commonMain/kotlin/com/app/community/App.kt
git commit -m "refactor(nav): mover el enrutado de deep links de actividad a AgoraTab"
```

---

### Task 2: Perfil como avatar en la topbar de Agora

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/app/community/dashboard/DashboardScreen.kt:69-80`

- [ ] **Step 1: Añadir el composable del avatar**

Al final de `DashboardScreen.kt`, añade:

```kotlin
@Composable
private fun ProfileAvatarButton(
    displayName: String?,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        val initial = displayName?.trim()?.firstOrNull()?.uppercaseChar()
        if (initial == null) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = stringResource(Res.string.tab_profile),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
```

El fondo usa `onPrimary` con alfa en vez de un color nuevo porque la topbar tiene `containerColor = primary`: así contrasta en claro y oscuro sin añadir tokens.

Imports nuevos que necesita el archivo:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import com.app.community.feature.auth.presentation.ProfileScreen
```

- [ ] **Step 2: Colgarlo de la topbar**

En `DashboardScreen.Content()`, el `AgoraTopBar` pasa a tener `actions`. `AgoraTopBar` ya expone ese slot (`core/ui/.../AgoraTopBar.kt:25`), no hay que modificarlo. El `displayName` solo existe en el estado `Content`, así que se lee con un cast seguro:

```kotlin
            topBar = {
                AgoraTopBar(
                    title = {
                        Text(
                            "Agora",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    actions = {
                        ProfileAvatarButton(
                            displayName = (uiState as? DashboardScreenModel.UiState.Content)?.displayName,
                            onClick = { navigator.push(ProfileScreen()) },
                        )
                    },
                )
            },
```

- [ ] **Step 3: Comprobar que `composeApp` ve el módulo de auth**

```bash
grep -n "feature.auth" composeApp/build.gradle.kts
```

Esperado: una línea con `implementation(projects.feature.auth)`. Si no aparece, añádela al bloque `commonMain.dependencies`; sin ella el import de `ProfileScreen` no compila.

- [ ] **Step 4: Compilar**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Esperado: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/app/community/dashboard/DashboardScreen.kt composeApp/build.gradle.kts
git commit -m "feat(nav): perfil accesible desde la topbar de Agora"
```

---

### Task 3: Borrar el tab Actividades, el tab Perfil y el feed

**Files:**
- Delete: `feature/activity/src/commonMain/kotlin/com/app/community/feature/activity/presentation/ActivityFeedScreen.kt`
- Delete: `feature/activity/src/commonMain/kotlin/com/app/community/feature/activity/presentation/ActivityFeedScreenModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/app/community/App.kt:168-174`
- Modify: `composeApp/src/commonMain/kotlin/com/app/community/navigation/AppTabs.kt:128-141`
- Modify: `composeApp/src/commonMain/kotlin/com/app/community/di/AppModule.kt:25,125`
- Modify: `feature/activity/src/commonMain/composeResources/values/strings.xml` y `values-es/strings.xml`

- [ ] **Step 1: Dejar 3 items en la barra**

En `App.kt`, el bloque `AgoraNavigationBar` queda exactamente así:

```kotlin
                AgoraNavigationBar {
                    TabNavigationItem(AgoraTab, onSelectTab)
                    TabNavigationItem(CommunitiesTab, onSelectTab)
                    TabNavigationItem(NotificationsTab, onSelectTab)
                }
```

Borra el import de `ProfileTab`.

- [ ] **Step 2: Borrar `ProfileTab`**

Elimina el `object ProfileTab` completo de `AppTabs.kt` (líneas 128-141) y sus imports huérfanos: `ProfileScreen`, `Icons.Default.AccountCircle`. Ojo: `ProfileScreen` se sigue usando, pero desde `DashboardScreen.kt`, no desde aquí.

- [ ] **Step 3: Borrar el feed y su registro en Koin**

```bash
git rm feature/activity/src/commonMain/kotlin/com/app/community/feature/activity/presentation/ActivityFeedScreen.kt feature/activity/src/commonMain/kotlin/com/app/community/feature/activity/presentation/ActivityFeedScreenModel.kt
```

En `AppModule.kt`, borra el import de la línea 25 y el bloque `factory` que empieza en la línea 125 y construye `ActivityFeedScreenModel(...)`.

- [ ] **Step 4: Borrar las cadenas huérfanas**

De `feature/activity/src/commonMain/composeResources/values/strings.xml` y de su gemelo `values-es/strings.xml`, borra las cuatro líneas `feed_title`, `feed_empty`, `feed_create_activity`, `feed_select_community`.

De los recursos de `composeApp` borra `tab_activities`. **`tab_profile` NO se borra**: la Task 2 lo reutiliza como `contentDescription` del avatar.

- [ ] **Step 5: Verificar que no quedan referencias**

```bash
grep -rn "ActivityFeedScreen\|ActivitiesTab\|ProfileTab\|tab_activities\|feed_title" --include=*.kt --include=*.xml . | grep -v "/build/"
```

Esperado: **cero líneas**. Cualquier resultado es una referencia colgando que hay que limpiar antes de seguir.

- [ ] **Step 6: Compilar los dos targets**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinWasmJs
```

Esperado: BUILD SUCCESSFUL en ambos. Los recursos generados de Compose se regeneran solos; si sale `Unresolved reference: feed_title`, quedó un uso de una cadena borrada.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor(nav): 5 tabs a 3, borrar el feed de actividades duplicado"
```

---

## Fase 2 — Icono de comunidad

### Task 4: Catálogo de iconos y color determinista (TDD)

Esta es la única lógica pura del cambio, así que es la única con tests. Vive en `core/model` porque es el módulo que ya tiene `commonTest` configurado y no depende de Compose.

**Files:**
- Create: `core/model/src/commonMain/kotlin/com/app/community/core/model/CommunityIcon.kt`
- Test: `core/model/src/commonTest/kotlin/com/app/community/core/model/CommunityIconTest.kt`

- [ ] **Step 1: Escribir el test que falla**

Crea `core/model/src/commonTest/kotlin/com/app/community/core/model/CommunityIconTest.kt`:

```kotlin
package com.app.community.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommunityIconTest {

    @Test
    fun `hay exactamente 16 iconos`() {
        assertEquals(16, CommunityIcon.entries.size)
    }

    @Test
    fun `las claves son estables y en minusculas`() {
        CommunityIcon.entries.forEach { icon ->
            assertTrue(icon.key.isNotBlank(), "clave vacia en ${icon.name}")
            assertEquals(icon.key.lowercase(), icon.key, "clave no minuscula: ${icon.key}")
        }
        assertEquals(16, CommunityIcon.entries.map { it.key }.toSet().size)
    }

    @Test
    fun `fromKey resuelve una clave conocida`() {
        assertEquals(CommunityIcon.VOLLEYBALL, CommunityIcon.fromKey("volleyball"))
    }

    @Test
    fun `fromKey devuelve null ante clave desconocida o nula`() {
        assertNull(CommunityIcon.fromKey("no-existe"))
        assertNull(CommunityIcon.fromKey(null))
    }

    @Test
    fun `avatarColorIndex es determinista y cae en rango`() {
        val id = "0f8a1c2b-1111-2222-3333-444455556666"
        val first = avatarColorIndex(id)
        assertEquals(first, avatarColorIndex(id))
        assertTrue(first in 0 until AVATAR_COLOR_COUNT)
    }

    @Test
    fun `avatarColorIndex nunca es negativo aunque el hash lo sea`() {
        // Cadenas con hashCode negativo: el modulo ingenuo devolveria un indice negativo
        // y reventaria el acceso a la lista de colores.
        listOf("zzzzzzzzzzzz", "actividad-de-prueba-larga", "😀comunidad").forEach { id ->
            assertTrue(avatarColorIndex(id) >= 0, "indice negativo para $id")
            assertTrue(avatarColorIndex(id) < AVATAR_COLOR_COUNT)
        }
    }

    @Test
    fun `avatarColorIndex tolera cadena vacia`() {
        assertTrue(avatarColorIndex("") in 0 until AVATAR_COLOR_COUNT)
    }
}
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

```bash
./gradlew :core:model:allTests --tests "*CommunityIconTest*"
```

Esperado: FAIL con `Unresolved reference: CommunityIcon`.

- [ ] **Step 3: Implementar**

Crea `core/model/src/commonMain/kotlin/com/app/community/core/model/CommunityIcon.kt`:

```kotlin
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
 * Se pasa por Long antes del módulo: `Int.MIN_VALUE.absoluteValue` sigue siendo
 * negativo en Kotlin, y un índice negativo reventaría el acceso a la paleta.
 */
fun avatarColorIndex(id: String): Int =
    ((id.hashCode().toLong() and 0x7FFFFFFFL) % AVATAR_COLOR_COUNT).toInt()
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

```bash
./gradlew :core:model:allTests --tests "*CommunityIconTest*"
```

Esperado: BUILD SUCCESSFUL, 7 tests verdes.

- [ ] **Step 5: Commit**

```bash
git add core/model/src/commonMain/kotlin/com/app/community/core/model/CommunityIcon.kt core/model/src/commonTest/kotlin/com/app/community/core/model/CommunityIconTest.kt
git commit -m "feat(model): catalogo de 16 iconos de comunidad y color determinista"
```

---

### Task 5: Migración `icon_key` y camino de datos

**Files:**
- Create: `supabase/migrations/<timestamp>_community_icon_key.sql`
- Modify: `core/model/src/commonMain/kotlin/com/app/community/core/model/Community.kt`
- Modify: `core/data/src/commonMain/kotlin/com/app/community/core/data/repository/CommunityRepository.kt:88-107` y `:234-241`
- Modify: `core/domain/src/commonMain/kotlin/com/app/community/core/domain/community/CreateCommunityUseCase.kt`

- [ ] **Step 1: Crear el archivo de migración**

```bash
supabase migration new community_icon_key
```

Esto crea `supabase/migrations/<timestamp>_community_icon_key.sql` vacío.

- [ ] **Step 2: Escribir la migración**

Contenido completo del archivo creado:

```sql
-- Icono configurable por comunidad. Aditivo: una app antigua ignora la columna.
ALTER TABLE "public"."communities" ADD COLUMN IF NOT EXISTS "icon_key" "text";

-- Las dos funciones que enumeran columnas a mano necesitan incluir icon_key,
-- si no el icono no llega ni a explorar ni a la preview de invitacion.

CREATE OR REPLACE FUNCTION "public"."get_public_community_preview"("p_community_id" "uuid") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_community communities%ROWTYPE;
    v_result jsonb;
BEGIN
    SELECT * INTO v_community FROM communities WHERE id = p_community_id;
    IF NOT FOUND OR v_community.visibility <> 'public' THEN
        RAISE EXCEPTION 'Community not found or not public';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM get_ancestor_community_ids(p_community_id) anc
        JOIN communities ac ON ac.id = anc.community_id
        WHERE ac.visibility = 'private'
    ) THEN
        RAISE EXCEPTION 'Community not found or not public';
    END IF;

    SELECT jsonb_build_object(
        'id', v_community.id,
        'name', v_community.name,
        'description', v_community.description,
        'image_url', v_community.image_url,
        'icon_key', v_community.icon_key,
        'visibility', v_community.visibility,
        'parent_id', v_community.parent_id,
        'breadcrumb', community_breadcrumb(v_community.id),
        'member_count', (SELECT count(*) FROM community_members WHERE community_id = v_community.id),
        'activity_count_upcoming', (SELECT count(*) FROM activities
                                    WHERE community_id = v_community.id
                                      AND status = 'active'
                                      AND datetime >= now())
    ) INTO v_result;

    RETURN v_result;
END;
$$;
```

**Antes de escribir el cuerpo, léelo del baseline** — el fragmento de arriba reproduce lo que hay en `supabase/migrations/20260625120019_baseline.sql:778-830`, pero debes copiar el cuerpo real de ese archivo y añadirle **solo** la línea `'icon_key', v_community.icon_key,`. Si el baseline tiene más campos de los que aparecen aquí, se conservan todos.

Haz lo mismo con `search_public_communities` (baseline línea 1992 en adelante): copia la definición íntegra y añade `c.icon_key,` justo después de `c.image_url,`.

- [ ] **Step 3: Aplicar la migración**

```bash
supabase db push
```

Esperado: `Finished supabase db push.` Si falla con `function ... does not exist`, has cambiado la firma al copiar: los parámetros y el tipo de retorno deben ser idénticos a los del baseline.

- [ ] **Step 4: Añadir el campo al modelo**

En `Community.kt`, justo debajo de `imageUrl`:

```kotlin
    @SerialName("icon_key") val iconKey: String? = null,
```

Tiene default `null`, así que ninguna llamada existente se rompe.

- [ ] **Step 5: Propagar por repositorio y caso de uso**

En `CommunityRepository.createCommunity`, añade `iconKey: String? = null` a la firma (después de `parentId`) y dentro del `buildJsonObject`:

```kotlin
                    iconKey?.let { put("icon_key", it) }
```

Reemplaza `updateCommunity` entera:

```kotlin
    suspend fun updateCommunity(
        id: String,
        name: String,
        description: String?,
        iconKey: String? = null,
    ): AppResult<Unit> =
        safeCall {
            postgrest.from("communities")
                .update({
                    set("name", name)
                    set("description", description)
                    set("icon_key", iconKey)
                }) { filter { eq("id", id) } }
        }
```

En `CreateCommunityUseCase`, añade `iconKey: String? = null` a la firma de `invoke` y pásalo como último argumento de `communityRepository.createCommunity(...)`.

- [ ] **Step 6: Compilar**

```bash
./gradlew :core:data:compileKotlinAndroid :core:domain:compileKotlinAndroid
```

Esperado: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add supabase/migrations core/model core/data core/domain
git commit -m "feat(data): columna icon_key en communities y su camino hasta el repositorio"
```

---

### Task 6: Avatar de comunidad en core/ui

**Files:**
- Modify: `core/ui/build.gradle.kts:27-38`
- Create: `core/ui/src/commonMain/kotlin/com/app/community/core/ui/components/CommunityAvatar.kt`

- [ ] **Step 1: Añadir los iconos extendidos a core/ui**

En `core/ui/build.gradle.kts`, dentro del bloque `commonMain.dependencies` donde ya están `compose.material3` y compañía:

```kotlin
            implementation(compose.materialIconsExtended)
```

`composeApp` y `feature:community` ya la tienen, así que no añade peso nuevo al binario.

- [ ] **Step 2: Crear el composable**

`core/ui/src/commonMain/kotlin/com/app/community/core/ui/components/CommunityAvatar.kt`:

```kotlin
package com.app.community.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsVolleyball
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.community.core.model.CommunityIcon
import com.app.community.core.model.avatarColorIndex

/** Traduce una clave persistida a su dibujo. Único punto de acoplamiento clave-icono. */
fun CommunityIcon.vector(): ImageVector = when (this) {
    CommunityIcon.VOLLEYBALL -> Icons.Default.SportsVolleyball
    CommunityIcon.SOCCER -> Icons.Default.SportsSoccer
    CommunityIcon.BASKETBALL -> Icons.Default.SportsBasketball
    CommunityIcon.RUNNING -> Icons.Default.DirectionsRun
    CommunityIcon.GROUP -> Icons.Default.Groups
    CommunityIcon.HOME -> Icons.Default.Home
    CommunityIcon.COFFEE -> Icons.Default.LocalCafe
    CommunityIcon.PARTY -> Icons.Default.Celebration
    CommunityIcon.MUSIC -> Icons.Default.MusicNote
    CommunityIcon.THEATER -> Icons.Default.TheaterComedy
    CommunityIcon.BOOK -> Icons.Default.MenuBook
    CommunityIcon.ART -> Icons.Default.Palette
    CommunityIcon.WORK -> Icons.Default.Work
    CommunityIcon.TRAVEL -> Icons.Default.Flight
    CommunityIcon.PET -> Icons.Default.Pets
    CommunityIcon.GENERIC -> Icons.Default.Public
}

/**
 * Avatar cuadrado redondeado de una comunidad. Es la firma visual que la
 * distingue de una actividad: una actividad NUNCA lleva este avatar.
 *
 * Sin icono elegido cae a la inicial del nombre sobre un color derivado del id,
 * de forma que dos comunidades distintas nunca se ven iguales.
 */
@Composable
fun CommunityAvatar(
    communityId: String,
    name: String,
    iconKey: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val palette = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.surfaceVariant,
    )
    val onPalette = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val index = avatarColorIndex(communityId)
    val background: Color = palette[index]
    val foreground: Color = onPalette[index]

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        val icon = CommunityIcon.fromKey(iconKey)
        if (icon != null) {
            Icon(
                imageVector = icon.vector(),
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(size * 0.55f),
            )
        } else {
            Text(
                text = name.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = foreground,
            )
        }
    }
}
```

La paleta sale de `MaterialTheme.colorScheme`, no de hexadecimales nuevos: así el modo oscuro funciona sin código extra y el avatar respeta el tema de la app.

- [ ] **Step 3: Compilar**

```bash
./gradlew :core:ui:compileKotlinAndroid
```

Esperado: BUILD SUCCESSFUL. Si sale `Unresolved reference: SportsVolleyball`, falta el paso 1.

- [ ] **Step 4: Commit**

```bash
git add core/ui
git commit -m "feat(ui): componente CommunityAvatar con iconos y fallback de inicial"
```

---

### Task 7: Selector de icono al crear y al editar

**Files:**
- Create: `feature/community/src/commonMain/kotlin/com/app/community/feature/community/presentation/CommunityIconPicker.kt`
- Modify: `feature/community/.../CreateCommunityScreenModel.kt:23-29` y su llamada a `createCommunityUseCase`
- Modify: `feature/community/.../CreateCommunityScreen.kt`
- Modify: `feature/community/.../CommunityDetailScreenModel.kt:196-215`
- Modify: `feature/community/.../CommunityDetailScreen.kt:551-645`

- [ ] **Step 1: Crear el diálogo selector**

`feature/community/src/commonMain/kotlin/com/app/community/feature/community/presentation/CommunityIconPicker.kt`:

```kotlin
package com.app.community.feature.community.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.app.community.core.model.CommunityIcon
import com.app.community.core.ui.components.vector
import com.app.community.core.ui.theme.AgoraSpacing

/**
 * Grid 4x4 de iconos. Un toque elige y cierra: no hay boton de confirmar
 * porque el cambio se guarda con el formulario que lo contiene.
 */
@Composable
fun CommunityIconPickerDialog(
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    title: String,
    clearLabel: String,
    cancelLabel: String,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
            ) {
                items(CommunityIcon.entries.toList(), key = { it.key }) { icon ->
                    val isSelected = icon.key == selectedKey
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            )
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable {
                                onSelect(icon.key)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon.vector(),
                            contentDescription = icon.key,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(26.dp).padding(0.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(null); onDismiss() }) { Text(clearLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelLabel) }
        },
    )
}
```

- [ ] **Step 2: Añadir las cadenas**

En `feature/community/src/commonMain/composeResources/values/strings.xml`:

```xml
    <string name="community_icon_picker_title">Choose an icon</string>
    <string name="community_icon_picker_clear">No icon</string>
    <string name="community_icon_label">Icon</string>
```

Y en `values-es/strings.xml`:

```xml
    <string name="community_icon_picker_title">Elige un icono</string>
    <string name="community_icon_picker_clear">Sin icono</string>
    <string name="community_icon_label">Icono</string>
```

- [ ] **Step 3: Estado del icono en el formulario de creación**

En `CreateCommunityScreenModel.FormState` añade el campo:

```kotlin
        val iconKey: String? = null,
```

Añade el setter en la línea 56, justo debajo de `onVisibilityChange`, siguiendo exactamente el estilo de sus vecinos (el flujo se llama `_form`):

```kotlin
    fun onIconKeyChange(value: String?) = _form.update { it.copy(iconKey = value) }
```

En la llamada a `createCommunityUseCase(...)` añade el argumento:

```kotlin
                iconKey = state.iconKey,
```

- [ ] **Step 4: Botón del selector en la pantalla de creación**

En `CreateCommunityScreen`, dentro del `Column` que empieza en la línea 112 y **antes** del primer `OutlinedTextField` (línea 129, el del nombre), añade una fila con el avatar actual y un botón que abre el diálogo. Necesitas un `var showIconPicker by remember { mutableStateOf(false) }` en el `Content()`:

```kotlin
        Row(verticalAlignment = Alignment.CenterVertically) {
            CommunityAvatar(
                communityId = "new",
                name = formState.name.ifBlank { "?" },
                iconKey = formState.iconKey,
                size = 48.dp,
            )
            Spacer(Modifier.width(AgoraSpacing.md))
            TextButton(onClick = { showIconPicker = true }) {
                Text(stringResource(Res.string.community_icon_label))
            }
        }

        if (showIconPicker) {
            CommunityIconPickerDialog(
                selectedKey = formState.iconKey,
                onSelect = screenModel::onIconKeyChange,
                onDismiss = { showIconPicker = false },
                title = stringResource(Res.string.community_icon_picker_title),
                clearLabel = stringResource(Res.string.community_icon_picker_clear),
                cancelLabel = stringResource(Res.string.label_cancel),
            )
        }
```

El `communityId = "new"` es intencionado: la comunidad todavía no tiene id, y el color del fallback solo sirve de vista previa.

- [ ] **Step 5: Lo mismo en el diálogo de edición de admin**

En `CommunityDetailScreenModel`, junto a `editName` y `editDescription` en el estado `Content`, añade `editIconKey: String?` (se inicializa desde `community.iconKey` en el mismo sitio donde hoy se inicializa `editName`), y añade:

```kotlin
    fun onEditIconKeyChange(key: String?) {
        val current = _uiState.value as? UiState.Content ?: return
        _uiState.value = current.copy(editIconKey = key)
    }
```

En `saveCommunity()`, la condición de cambio y la llamada pasan a incluir el icono:

```kotlin
            val nameOrDescChanged = name != current.community.name ||
                current.editDescription.trim().ifBlank { null } != current.community.description ||
                current.editIconKey != current.community.iconKey

            if (nameOrDescChanged) {
                communityRepository.updateCommunity(
                    id = communityId,
                    name = name,
                    description = current.editDescription.trim().ifBlank { null },
                    iconKey = current.editIconKey,
                ).onError { msg, _ -> firstError = firstError ?: msg }
            }
```

En el `AlertDialog` de edición de `CommunityDetailScreen.kt` (empieza en la línea 551), añade la misma fila avatar + botón del paso 4 encima del primer `OutlinedTextField`, usando `state.editIconKey` y `screenModel::onEditIconKeyChange`.

- [ ] **Step 6: Compilar**

```bash
./gradlew :feature:community:compileKotlinAndroid
```

Esperado: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(community): selector de icono al crear y al editar comunidad"
```

---

## Fase 3 — Jerarquía y siluetas

### Task 8: Árbol indentado en la lista de comunidades

**Files:**
- Modify: `feature/community/src/commonMain/kotlin/com/app/community/feature/community/presentation/CommunityListScreen.kt:199-276`

- [ ] **Step 1: Reemplazar `CommunityCard` y `NestedChildRow`**

Sustituye ambos composables (líneas 199 a 276, el final del archivo) por:

```kotlin
@Composable
private fun CommunityCard(
    node: CommunityNode,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val community = node.community
    MarbleCard(
        modifier = modifier,
        elevation = AgoraElevation.subtle,
        onClick = { onClick(community.id) },
    ) {
        Column(modifier = Modifier.padding(AgoraSpacing.cardInternal)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CommunityAvatar(
                    communityId = community.id,
                    name = community.name,
                    iconKey = community.iconKey,
                    size = 40.dp,
                )
                Spacer(Modifier.width(AgoraSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = community.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    community.memberCount?.let { count ->
                        Text(
                            text = stringResource(Res.string.community_detail_members_header, count),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!community.description.isNullOrBlank()) {
                Spacer(Modifier.height(AgoraSpacing.sm))
                Text(
                    text = community.description.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (node.children.isNotEmpty()) {
                Spacer(Modifier.height(AgoraSpacing.md))
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Linea de conexion: la senal que dice "esto cuelga de lo de arriba".
                    Spacer(Modifier.width(AgoraSpacing.lg))
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        node.children.forEach { child ->
                            NestedChildRow(
                                community = child,
                                onClick = { onClick(child.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NestedChildRow(
    community: Community,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = AgoraSpacing.md, top = AgoraSpacing.sm, bottom = AgoraSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CommunityAvatar(
            communityId = community.id,
            name = community.name,
            iconKey = community.iconKey,
            size = 24.dp,
        )
        Spacer(Modifier.width(AgoraSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = community.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            community.memberCount?.let { count ->
                Text(
                    text = stringResource(Res.string.community_detail_members_header, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

Las tres señales de jerarquía quedan así: avatar 40dp vs 24dp, `titleMedium` en negrita vs `bodyMedium` normal, y la línea vertical. Ninguna carga sola con el trabajo.

- [ ] **Step 2: Arreglar imports**

Añade:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import com.app.community.core.ui.components.CommunityAvatar
```

Quita `import androidx.compose.material.icons.filled.SubdirectoryArrowRight` y, si ya no se usa, `import androidx.compose.material3.Icon`. Sustituye los `androidx.compose.ui.Alignment.CenterHorizontally` cualificados a mano del bloque de lista vacía por `Alignment.CenterHorizontally` ahora que el import existe.

- [ ] **Step 3: Compilar los dos targets**

```bash
./gradlew :feature:community:compileKotlinAndroid :feature:community:compileKotlinWasmJs
```

Esperado: BUILD SUCCESSFUL en ambos. `fillMaxHeight()` dentro de un `Row` toma la altura del hermano más alto, que es justo lo que queremos para la línea.

- [ ] **Step 4: Commit**

```bash
git add feature/community/src/commonMain/kotlin/com/app/community/feature/community/presentation/CommunityListScreen.kt
git commit -m "feat(community): arbol indentado con linea de conexion en la lista"
```

---

### Task 9: Dos siluetas de tarjeta

**Files:**
- Create: `core/ui/src/commonMain/kotlin/com/app/community/core/ui/components/ActivityRow.kt`
- Create: `core/ui/src/commonMain/kotlin/com/app/community/core/ui/components/CommunityRow.kt`
- Modify: `composeApp/.../DashboardScreen.kt:275-341` (`CompactActivityCard`)
- Modify: `feature/community/.../CommunityDetailScreen.kt` (lista de actividades y de subcomunidades)
- Modify: `feature/community/.../ExploreCommunitiesScreen.kt:163-...`

Los dos composables reciben **strings ya formateados**. Es deliberado: las abreviaturas de día y mes viven en los recursos de `composeApp` y los nombres de comunidad en los de `feature:community`; pasar texto formateado evita mover recursos entre módulos para un cambio puramente visual.

- [ ] **Step 1: Crear `ActivityRow`**

`core/ui/src/commonMain/kotlin/com/app/community/core/ui/components/ActivityRow.kt`:

```kotlin
package com.app.community.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.community.core.ui.theme.AgoraSpacing

/**
 * Silueta unica de "actividad": bloque de fecha a la izquierda, texto en medio,
 * badge a la derecha. Una comunidad NUNCA usa esta forma.
 *
 * Recibe la fecha ya formateada (dia y mes por separado) porque las
 * abreviaturas viven en los recursos del modulo que llama.
 */
@Composable
fun ActivityRow(
    day: String,
    month: String,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(AgoraSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .width(48.dp)
                .clip(RoundedCornerShape(AgoraSpacing.sm))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(vertical = AgoraSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = day,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
            Text(
                text = month,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.width(AgoraSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(AgoraSpacing.sm))
            trailing()
        }
    }
}
```

- [ ] **Step 2: Crear `CommunityRow`**

`core/ui/src/commonMain/kotlin/com/app/community/core/ui/components/CommunityRow.kt`:

```kotlin
package com.app.community.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.community.core.ui.theme.AgoraSpacing

/**
 * Silueta unica de "comunidad": avatar a la izquierda, nombre, subtitulo.
 * Nunca muestra fecha, que es lo que la separa de una actividad.
 */
@Composable
fun CommunityRow(
    communityId: String,
    name: String,
    iconKey: String?,
    subtitle: String?,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 40.dp,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(AgoraSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CommunityAvatar(
            communityId = communityId,
            name = name,
            iconKey = iconKey,
            size = avatarSize,
        )
        Spacer(Modifier.width(AgoraSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(AgoraSpacing.sm))
            trailing()
        }
    }
}
```

- [ ] **Step 3: Usar `ActivityRow` en el dashboard**

En `DashboardScreen.kt`, el cuerpo de `CompactActivityCard` pasa a delegar. Sustituye todo el `Row` interno (líneas 290-339) por:

```kotlin
        ActivityRow(
            day = localDt.dayOfMonth.toString(),
            month = dayOfWeekAbbr(localDt.dayOfWeek),
            title = activity.name,
            subtitle = activity.locationName,
            trailing = {
                val (chipColorPair, chipText) = when {
                    info.isUserReserved -> slotColors.reservedByMe to stringResource(Res.string.dashboard_status_reserved)
                    info.availableSlots == 0 && activity.slotMode != SlotMode.UNLIMITED -> {
                        slotColors.reservedByOther to stringResource(Res.string.dashboard_status_full)
                    }
                    activity.slotMode == SlotMode.UNLIMITED -> {
                        slotColors.available to stringResource(Res.string.dashboard_status_open)
                    }
                    else -> slotColors.available to stringResource(Res.string.dashboard_status_slots, info.availableSlots)
                }
                SlotStatusBadge(
                    text = chipText,
                    colorPair = chipColorPair,
                    isCompact = true,
                )
            },
        )
```

La hora se pierde de la fila compacta a propósito: la lleva el bloque de fecha y el detalle. Si al verlo prefieres conservarla, pásala en `subtitle` concatenada con la ubicación.

Deja `HeroActivityCard` como está: ya tiene su propia jerarquía y no se confunde con una comunidad.

Añade el import `com.app.community.core.ui.components.ActivityRow`.

- [ ] **Step 4: Usar las siluetas en el detalle de comunidad**

En `CommunityDetailScreen.kt` hay dos composables privados que reemplazar. El cuerpo interno de `ActivityCard` (el que se usa en la pestaña ACTIVITIES, línea 467) pasa a:

```kotlin
        ActivityRow(
            day = localDateTime.dayOfMonth.toString(),
            month = localDateTime.monthNumber.toString().padStart(2, '0'),
            title = activity.name,
            subtitle = activity.locationName,
        )
```

Aquí el mes va como número con cero delante, no como abreviatura: este módulo no tiene las cadenas `day_*_abbr`, que viven en los recursos de `composeApp`. Mantén el `MarbleCard` que lo envuelve y su `onClick`.

El cuerpo interno de `SubcommunityCard` pasa a:

```kotlin
        CommunityRow(
            communityId = community.id,
            name = community.name,
            iconKey = community.iconKey,
            subtitle = community.description,
            avatarSize = 32.dp,
        )
```

Si `SubcommunityCard` hoy muestra algo distinto según `isMember` (una etiqueta o un botón de unirse), pásalo por el parámetro `trailing`; no lo elimines.

- [ ] **Step 5: Usar `CommunityRow` en explorar**

En `ExploreCommunitiesScreen.kt`, dentro de `ExploreCommunityCard` (línea 163), el bloque que hoy pinta el nombre (`Text` con `titleMedium`) y la descripción se sustituye por:

```kotlin
            CommunityRow(
                communityId = community.id,
                name = community.name,
                iconKey = community.iconKey,
                subtitle = community.description,
            )
```

**Conserva intactos** el breadcrumb de arriba y la fila de abajo con el contador de miembros y los tags: son información propia de explorar que `CommunityRow` no cubre. Como `CommunityRow` ya aplica su propio padding, quita el `AgoraSpacing.cardInternal` del `Column` que lo envuelve o el contenido quedará doblemente separado.

`iconKey` sale de `community.iconKey` en los tres casos, que ya existe tras la Task 5.

Nota: `CommunityListScreen` **no** usa `CommunityRow`. Su tarjeta es un nodo de árbol con hijas dentro, una composición distinta; lo que comparte con el resto es `CommunityAvatar`, que es la señal de identidad que importa.

- [ ] **Step 6: Compilar los dos targets**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinWasmJs
```

Esperado: BUILD SUCCESSFUL en ambos.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(ui): siluetas distintas para tarjetas de comunidad y de actividad"
```

---

## Fase 4 — Verificación

### Task 10: Verificación completa

**Files:** ninguno (solo comprobación).

- [ ] **Step 1: Tests y compilación de todo**

```bash
./gradlew :core:model:allTests :composeApp:allTests
```

Esperado: BUILD SUCCESSFUL. Debe incluir los 7 tests de `CommunityIconTest` y los existentes de `NotificationTypeTest` y `WebDeepLinkTest`.

- [ ] **Step 2: Build de release de Android**

```bash
./gradlew :composeApp:assembleDebug
```

Esperado: BUILD SUCCESSFUL.

- [ ] **Step 3: Build de web**

```bash
./gradlew :composeApp:wasmJsBrowserDistribution
```

Esperado: BUILD SUCCESSFUL.

- [ ] **Step 4: Recorrido manual de los tres deep links**

Este paso no se puede automatizar y es el que protege los links que ya circulan. En un dispositivo o emulador con la app instalada:

1. Abrir un link de invitación a comunidad → debe unirte y dejarte en la comunidad, sin pasar por ninguna pantalla de "introducir código".
2. Abrir un link público de actividad estando logueado → debe abrir el detalle de la actividad, con el tab Agora seleccionado.
3. Tocar una notificación push de actividad → debe abrir el detalle de esa actividad. Repetir estando ya en el detalle de esa misma actividad: debe refrescarla, no apilar una segunda copia.

- [ ] **Step 5: Recorrido manual de UI**

1. La barra inferior tiene 3 iconos.
2. El avatar de la topbar de Agora abre Perfil, y atrás vuelve a Agora.
3. Crear una comunidad eligiendo icono: aparece en la lista con ese icono.
4. Una comunidad sin icono muestra su inicial, y dos comunidades distintas muestran colores distintos.
5. Una comunidad con hijas muestra la línea de conexión y las hijas más pequeñas.
6. Editar una comunidad y cambiarle el icono se refleja en la lista al volver.
7. Repetir 1-6 en modo oscuro.
8. Repetir 1-6 en la web.

- [ ] **Step 6: Commit final si hubo arreglos**

```bash
git add -A
git commit -m "fix(ui): ajustes tras la verificacion manual"
```

---

## Notas de riesgo

**El punto delicado es la Task 1.** Los links de actividad compartidos ya circulan; si el enrutado se rompe, un invitado aterriza en el dashboard en vez de en la actividad. Por eso la Task 1 va sola, primero, y se vuelve a verificar a mano en la Task 10.

**La migración es aditiva.** Una versión antigua de la app en el móvil de alguien ignora `icon_key` sin romperse, porque el campo del modelo tiene default `null`. No hace falta coordinar el despliegue de app y base de datos.

**Riesgo de copiar mal las funciones SQL.** La Task 5 pide copiar dos cuerpos de función del baseline. Si al copiar cambias la firma, `CREATE OR REPLACE` crea una función nueva en vez de reemplazar y explorar deja de funcionar. Compara la firma carácter a carácter antes de hacer `db push`.
