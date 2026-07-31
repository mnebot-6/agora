# Admins apuntan gente en los huecos — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que un admin de comunidad pueda apuntar a un miembro, o a una persona sin cuenta (nombre suelto), en las plazas de una actividad, en los tres modos de aforo.

**Architecture:** Dos RPC nuevos en Postgres (`admin_assign_slot` para plazas existentes, `admin_assign_new_slot` para el modo ilimitado) más una columna `guest_label` en `slots` que permite ocupar una plaza sin usuario de auth. La app expone la acción con un diálogo en la pantalla de detalle de actividad, reutilizando la lista de miembros que ya se carga para calcular `isAdmin`. Antes de todo eso hay un arreglo obligatorio en el cliente (`NotificationType` tolerante) y otro en el servidor (`release_slot` compara con `!=` y se rompe con `reserved_by NULL`).

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform, Voyager (ScreenModel), Koin, supabase-kt (postgrest RPC), Supabase CLI para migraciones, kotlin-test.

**Spec:** `docs/superpowers/specs/2026-07-29-admin-assign-slots-design.md`

---

## Mapa de archivos

| Archivo | Responsabilidad | Task |
|---|---|---|
| `core/model/build.gradle.kts` | Habilitar `commonTest` en el módulo de modelos | 1 |
| `core/model/src/commonMain/kotlin/com/app/community/core/model/Notification.kt` | Enum tolerante + tipo `slot_assigned` | 1 |
| `core/model/src/commonTest/kotlin/com/app/community/core/model/NotificationTypeTest.kt` | Test de la tolerancia | 1 |
| `feature/notification/.../NotificationListScreen.kt` | Los dos `when` exhaustivos | 1 |
| `feature/notification/.../composeResources/values{,-es}/strings.xml` | Etiquetas de los tipos nuevos | 1 |
| `supabase/migrations/<ts>_admin_assign_slots.sql` | Columna, constraint, arreglo de `release_slot`, los dos RPC | 2, 3 |
| `core/model/src/commonMain/kotlin/com/app/community/core/model/Slot.kt` | Campo `guestLabel` | 4 |
| `core/data/.../repository/SlotRepository.kt` | Envoltorios de los dos RPC | 4 |
| `feature/activity/.../ActivityDetailScreenModel.kt` | Miembros en el estado + acciones de asignar | 5 |
| `feature/activity/.../ActivityDetailScreen.kt` | Botones, diálogo y pintado de la etiqueta | 6 |
| `feature/activity/.../composeResources/values{,-es}/strings.xml` | Textos del diálogo | 6 |
| `docs/admin_assign_slots_smoke_check.sql` | Verificación manual en SQL | 7 |

**Orden obligatorio:** la Task 1 va primero. Si se despliega el RPC que inserta `slot_assigned` antes de que la app tolere tipos desconocidos, la pestaña de notificaciones peta entera en los clientes que no se hayan actualizado.

---

### Task 1: `NotificationType` tolerante a tipos desconocidos

`Notification.type` no es nullable y el enum es cerrado, así que `decodeList<Notification>()` lanza excepción ante un valor que no conoce y **tumba la lista entera**, no sólo la fila mala. Este task lo arregla y de paso añade `slot_assigned`.

**Files:**
- Modify: `core/model/build.gradle.kts`
- Modify: `core/model/src/commonMain/kotlin/com/app/community/core/model/Notification.kt`
- Create: `core/model/src/commonTest/kotlin/com/app/community/core/model/NotificationTypeTest.kt`
- Modify: `feature/notification/src/commonMain/kotlin/com/app/community/feature/notification/presentation/NotificationListScreen.kt:170-190` y `:268-284`
- Modify: `feature/notification/src/commonMain/composeResources/values/strings.xml`
- Modify: `feature/notification/src/commonMain/composeResources/values-es/strings.xml`

- [ ] **Step 1: Habilitar `commonTest` en `core:model`**

En `core/model/build.gradle.kts`, dentro de `sourceSets { ... }`, justo después del bloque `commonMain.dependencies { ... }`:

```kotlin
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
        }
```

- [ ] **Step 2: Escribir el test que falla**

Crear `core/model/src/commonTest/kotlin/com/app/community/core/model/NotificationTypeTest.kt`:

```kotlin
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
```

- [ ] **Step 3: Ejecutar el test y verificar que falla**

Run: `./gradlew :core:model:testDebugUnitTest`
Expected: **FAIL** en compilación, con `Unresolved reference: SLOT_ASSIGNED` y `Unresolved reference: UNKNOWN`. Todavía no existen esas entradas.

- [ ] **Step 4: Reescribir `Notification.kt`**

Contenido completo del archivo:

```kotlin
package com.app.community.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject

/**
 * El servidor puede empezar a emitir tipos nuevos antes de que la app se actualice. Con un enum
 * cerrado, decodeList tumba la lista ENTERA ante un valor desconocido, no sólo la fila mala.
 * UNKNOWN y su serializer son load-bearing: no los quites.
 */
@Serializable(with = NotificationTypeSerializer::class)
enum class NotificationType(val wire: String) {
    NEW_ACTIVITY("new_activity"),
    SLOT_RELEASED("slot_released"),
    SUBSTITUTE_PROMOTED("substitute_promoted"),
    ACTIVITY_REMINDER("activity_reminder"),
    JOIN_REQUEST_RECEIVED("join_request_received"),
    JOIN_REQUEST_APPROVED("join_request_approved"),
    JOIN_REQUEST_REJECTED("join_request_rejected"),
    GUEST_REQUEST_RECEIVED("guest_request_received"),
    GUEST_REQUEST_APPROVED("guest_request_approved"),
    GUEST_REQUEST_REJECTED("guest_request_rejected"),
    PAYMENT_CONFIRMED("payment_confirmed"),
    SLOT_REMOVED("slot_removed"),
    ACTIVITY_FULL("activity_full"),
    ACTIVITY_CANCELLED("activity_cancelled"),
    ACTIVITY_UPDATED("activity_updated"),
    SLOT_ASSIGNED("slot_assigned"),
    UNKNOWN("unknown"),
}

object NotificationTypeSerializer : KSerializer<NotificationType> {
    private val byWire = NotificationType.entries.associateBy { it.wire }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NotificationType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: NotificationType) =
        encoder.encodeString(value.wire)

    override fun deserialize(decoder: Decoder): NotificationType =
        byWire[decoder.decodeString()] ?: NotificationType.UNKNOWN
}

@Serializable
data class Notification(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val data: JsonObject? = null,
    val read: Boolean = false,
    @SerialName("created_at") val createdAt: Instant,
)
```

Las anotaciones `@SerialName` de cada entrada desaparecen: no son legibles en runtime sin reflexión, y el serializer usa `wire` en su lugar.

- [ ] **Step 5: Ejecutar el test y verificar que pasa**

Run: `./gradlew :core:model:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 3 tests verdes.

- [ ] **Step 6: Completar los dos `when` exhaustivos**

En `NotificationListScreen.kt`, en el `when (notification.type)` de la navegación (línea ~170), sustituir la última rama:

```kotlin
                                        NotificationType.NEW_ACTIVITY,
                                        NotificationType.SLOT_RELEASED,
                                        NotificationType.SUBSTITUTE_PROMOTED,
                                        NotificationType.ACTIVITY_REMINDER,
                                        NotificationType.PAYMENT_CONFIRMED,
                                        NotificationType.SLOT_REMOVED,
                                        NotificationType.SLOT_ASSIGNED,
                                        NotificationType.ACTIVITY_FULL,
                                        NotificationType.ACTIVITY_CANCELLED,
                                        NotificationType.ACTIVITY_UPDATED ->
                                            activityId?.let { navigator.push(ActivityDetailScreen(it)) }
                                        NotificationType.UNKNOWN -> Unit
```

Y en el `when (notification.type)` de `typeIcon` (línea ~268), añadir dos ramas antes del cierre:

```kotlin
        NotificationType.SLOT_ASSIGNED -> stringResource(Res.string.type_slot_assigned)
        NotificationType.UNKNOWN -> stringResource(Res.string.type_notification_generic)
```

- [ ] **Step 7: Añadir los textos**

En `feature/notification/src/commonMain/composeResources/values/strings.xml`, antes de `</resources>`:

```xml
    <string name="type_slot_assigned">Added to activity</string>
    <string name="type_notification_generic">Notification</string>
```

En `values-es/strings.xml`, antes de `</resources>`:

```xml
    <string name="type_slot_assigned">Te han apuntado</string>
    <string name="type_notification_generic">Notificación</string>
```

- [ ] **Step 8: Gate Android**

Run: `./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add core/model feature/notification
git commit -m "fix(model): tolerate unknown notification types and add slot_assigned"
```

---

### Task 2: Migración — columna `guest_label` y arreglo de `release_slot`

`release_slot` compara con `!=`. En cuanto exista una plaza con `reserved_by NULL`, `NULL != uuid` evalúa a `NULL`, la condición entera se vuelve `NULL`, el `IF` no dispara y **cualquier usuario autenticado podría liberar esa plaza**, también si está pagada. Se arregla en la misma migración que introduce el estado que lo provoca.

**Files:**
- Create: `supabase/migrations/<timestamp>_admin_assign_slots.sql`

- [ ] **Step 1: Crear el archivo de migración**

Run: `supabase migration new admin_assign_slots`
Expected: imprime la ruta del `.sql` creado en `supabase/migrations/`, vacío.

- [ ] **Step 2: Escribir el esquema y el arreglo**

Contenido del archivo recién creado:

```sql
-- Plazas ocupadas por alguien sin cuenta: reserved_by NULL + guest_label con el nombre.
ALTER TABLE slots ADD COLUMN guest_label text;

-- Una plaza es de un usuario O de una etiqueta, nunca de los dos.
ALTER TABLE slots ADD CONSTRAINT slots_no_owner_and_label
  CHECK (guest_label IS NULL OR reserved_by IS NULL);

-- release_slot: comparar con IS DISTINCT FROM (con reserved_by NULL, "!=" da NULL y el IF
-- no dispara, dejando que cualquier autenticado libere una plaza de etiqueta), exigir admin
-- explicitamente para las plazas sin dueno, y limpiar guest_label al liberar.
CREATE OR REPLACE FUNCTION public.release_slot(p_slot_id uuid) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_slot RECORD;
    v_activity RECORD;
    v_user_id UUID := auth.uid();
    v_is_admin BOOLEAN;
    v_promoted BOOLEAN;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF v_slot IS NULL THEN
        RAISE EXCEPTION 'Slot not found';
    END IF;

    IF v_slot.status = 'available' THEN
        RETURN FALSE;
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;
    SELECT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_user_id
          AND role = 'admin'
    ) INTO v_is_admin;

    IF v_slot.reserved_by IS NULL THEN
        -- Plaza de etiqueta: no tiene dueno que la pueda liberar, solo un admin.
        IF NOT v_is_admin THEN
            RAISE EXCEPTION 'Only an admin can release a guest-label slot';
        END IF;
    ELSIF v_slot.status = 'paid' THEN
        IF v_slot.reserved_by IS DISTINCT FROM v_user_id THEN
            RAISE EXCEPTION 'Only the user who reserved this slot can release a paid reservation';
        END IF;
    ELSIF v_slot.status = 'reserved' THEN
        IF v_slot.reserved_by IS DISTINCT FROM v_user_id AND NOT v_is_admin THEN
            RAISE EXCEPTION 'Only the reserved user or an admin can release this slot';
        END IF;
    END IF;

    UPDATE slots
    SET status = 'available', reserved_by = NULL, reserved_at = NULL, guest_label = NULL
    WHERE id = p_slot_id;

    v_promoted := promote_substitute(p_slot_id, v_activity.id);

    IF NOT v_promoted THEN
        INSERT INTO notifications (user_id, type, title, body, data)
        SELECT
            sq.user_id,
            'slot_released',
            'Plaza disponible',
            'Se ha liberado una plaza en una actividad',
            jsonb_build_object('activity_id', v_activity.id, 'slot_id', p_slot_id)
        FROM substitute_queue sq
        WHERE sq.activity_id = v_activity.id;
    END IF;

    RETURN TRUE;
END;
$$;
```

> **CORRECCIÓN aplicada durante la ejecución (2026-07-29).** El cuerpo de arriba se escribió
> desde el baseline, pero la versión viva de `release_slot` es la de
> `supabase/migrations/20260630120000_more_guest_notifications.sql`, que añade un aviso
> `slot_removed` a la persona expulsada cuando un admin le libera la plaza. Escribirlo tal cual
> **habría borrado ese aviso**. La migración real parte de la versión viva y le aplica sólo los
> tres cambios intencionados, más `SET search_path`. Antes de cualquier
> `CREATE OR REPLACE FUNCTION`, comprobar cuál es la definición **más reciente**, no la del
> baseline.

- [ ] **Step 3: Aplicar la migración**

Run: `supabase db push`
Expected: lista la migración pendiente, pide confirmación y termina con `Finished supabase db push`.

- [ ] **Step 4: Verificar que local y remoto coinciden**

Run: `supabase migration list`
Expected: la migración nueva aparece con la misma marca en las columnas Local y Remote.

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations
git commit -m "feat(db): guest_label column and release_slot NULL-safety fix"
```

---

### Task 3: Los dos RPC de asignación

**Files:**
- Create: `supabase/migrations/<timestamp>_admin_assign_slot_rpcs.sql`

- [ ] **Step 1: Crear el archivo de migración**

Run: `supabase migration new admin_assign_slot_rpcs`
Expected: imprime la ruta del `.sql` creado.

- [ ] **Step 2: Escribir los dos RPC**

> **CORRECCIÓN aplicada durante la ejecución (2026-07-29).** `notifications.type` tiene una
> constraint `notifications_type_check` con la lista cerrada de tipos permitidos, definida en
> `supabase/migrations/20260630120000_more_guest_notifications.sql:15-22`. Sin añadir
> `'slot_assigned'` a esa lista, **cada `INSERT INTO notifications` de estos RPC falla en
> runtime**. La migración real empieza con un `DROP CONSTRAINT IF EXISTS` + `ADD CONSTRAINT`
> que reproduce los 15 valores existentes y añade el nuevo.

Contenido del archivo:

```sql
-- Un admin apunta a un miembro (p_user_id) o a alguien sin cuenta (p_guest_label) en una
-- plaza libre. Devuelve FALSE si la plaza dejo de estar libre entre que se abrio el dialogo
-- y se confirmo. NO comprueba la cola de suplentes: el aviso vive en la UI, la decision es
-- que el admin manda.
CREATE OR REPLACE FUNCTION public.admin_assign_slot(
    p_slot_id uuid,
    p_user_id uuid DEFAULT NULL,
    p_guest_label text DEFAULT NULL
) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_slot RECORD;
    v_activity RECORD;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF (p_user_id IS NULL) = (p_guest_label IS NULL) THEN
        RAISE EXCEPTION 'Provide exactly one of p_user_id or p_guest_label';
    END IF;

    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF v_slot IS NULL THEN
        RAISE EXCEPTION 'Slot not found';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;
    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_caller
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can assign slots';
    END IF;

    IF v_slot.status <> 'available' THEN
        RETURN FALSE;
    END IF;

    IF p_user_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM community_members
            WHERE community_id = v_activity.community_id AND user_id = p_user_id
        ) THEN
            RAISE EXCEPTION 'That person is not a member of this community';
        END IF;

        IF EXISTS (
            SELECT 1 FROM slots
            WHERE activity_id = v_slot.activity_id AND reserved_by = p_user_id
        ) THEN
            RAISE EXCEPTION 'That person already has a slot in this activity';
        END IF;
    END IF;

    UPDATE slots
    SET status = 'reserved',
        reserved_by = p_user_id,
        guest_label = p_guest_label,
        reserved_at = now()
    WHERE id = p_slot_id;

    IF p_user_id IS NOT NULL THEN
        DELETE FROM substitute_queue
        WHERE activity_id = v_slot.activity_id AND user_id = p_user_id;

        INSERT INTO notifications (user_id, type, title, body, data)
        VALUES (
            p_user_id,
            'slot_assigned',
            'Te han apuntado',
            'Un administrador te ha apuntado a ' || v_activity.name,
            jsonb_build_object('activity_id', v_activity.id, 'slot_id', p_slot_id)
        );
    END IF;

    RETURN TRUE;
END;
$$;

ALTER FUNCTION public.admin_assign_slot(uuid, uuid, text) OWNER TO postgres;
GRANT ALL ON FUNCTION public.admin_assign_slot(uuid, uuid, text) TO authenticated;
GRANT ALL ON FUNCTION public.admin_assign_slot(uuid, uuid, text) TO service_role;

-- Modo de aforo ilimitado: no hay plazas preexistentes, asi que se crea y se asigna en la
-- misma transaccion. Devuelve el id de la plaza creada.
CREATE OR REPLACE FUNCTION public.admin_assign_new_slot(
    p_activity_id uuid,
    p_user_id uuid DEFAULT NULL,
    p_guest_label text DEFAULT NULL
) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_activity RECORD;
    v_slot_id uuid;
    v_sort_order int;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF (p_user_id IS NULL) = (p_guest_label IS NULL) THEN
        RAISE EXCEPTION 'Provide exactly one of p_user_id or p_guest_label';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = p_activity_id;
    IF v_activity IS NULL THEN
        RAISE EXCEPTION 'Activity not found';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_caller
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can assign slots';
    END IF;

    IF p_user_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM community_members
            WHERE community_id = v_activity.community_id AND user_id = p_user_id
        ) THEN
            RAISE EXCEPTION 'That person is not a member of this community';
        END IF;

        IF EXISTS (
            SELECT 1 FROM slots
            WHERE activity_id = p_activity_id AND reserved_by = p_user_id
        ) THEN
            RAISE EXCEPTION 'That person already has a slot in this activity';
        END IF;
    END IF;

    SELECT coalesce(max(sort_order), -1) + 1 INTO v_sort_order
    FROM slots WHERE activity_id = p_activity_id;

    INSERT INTO slots (activity_id, sort_order, status, reserved_by, guest_label, reserved_at)
    VALUES (p_activity_id, v_sort_order, 'reserved', p_user_id, p_guest_label, now())
    RETURNING id INTO v_slot_id;

    IF p_user_id IS NOT NULL THEN
        DELETE FROM substitute_queue
        WHERE activity_id = p_activity_id AND user_id = p_user_id;

        INSERT INTO notifications (user_id, type, title, body, data)
        VALUES (
            p_user_id,
            'slot_assigned',
            'Te han apuntado',
            'Un administrador te ha apuntado a ' || v_activity.name,
            jsonb_build_object('activity_id', p_activity_id, 'slot_id', v_slot_id)
        );
    END IF;

    RETURN v_slot_id;
END;
$$;

ALTER FUNCTION public.admin_assign_new_slot(uuid, uuid, text) OWNER TO postgres;
GRANT ALL ON FUNCTION public.admin_assign_new_slot(uuid, uuid, text) TO authenticated;
GRANT ALL ON FUNCTION public.admin_assign_new_slot(uuid, uuid, text) TO service_role;
```

- [ ] **Step 3: Aplicar y verificar**

```bash
supabase db push
```
Expected: `Finished supabase db push`.

Después, en el SQL Editor del dashboard (sólo lectura):

```sql
select proname from pg_proc
where proname in ('admin_assign_slot', 'admin_assign_new_slot')
order by proname;
```
Expected: dos filas.

- [ ] **Step 4: Commit**

```bash
git add supabase/migrations
git commit -m "feat(db): admin_assign_slot and admin_assign_new_slot RPCs"
```

---

### Task 4: Modelo y repositorio

**Files:**
- Modify: `core/model/src/commonMain/kotlin/com/app/community/core/model/Slot.kt:31-46`
- Modify: `core/data/src/commonMain/kotlin/com/app/community/core/data/repository/SlotRepository.kt`

- [ ] **Step 1: Añadir `guestLabel` al modelo**

En `Slot.kt`, dentro de `data class Slot`, después de la línea `@SerialName("is_guest") val isGuest: Boolean = false,`:

```kotlin
    @SerialName("guest_label") val guestLabel: String? = null,
```

- [ ] **Step 2: Añadir los dos métodos al repositorio**

En `SlotRepository.kt`, al final del bloque `// --- Atomic RPC Operations ---` (justo antes del comentario `// --- Slot Creation (admin) ---`):

```kotlin
    /** Devuelve false si la plaza dejó de estar libre mientras el diálogo estaba abierto. */
    suspend fun adminAssignSlot(
        slotId: String,
        userId: String?,
        guestLabel: String?,
    ): AppResult<Boolean> =
        safeCall {
            val result = postgrest.rpc(
                function = "admin_assign_slot",
                parameters = buildJsonObject {
                    put("p_slot_id", slotId)
                    userId?.let { put("p_user_id", it) }
                    guestLabel?.let { put("p_guest_label", it) }
                },
            ).data
            result.trim().toBoolean()
        }

    /** Modo ilimitado: crea la plaza y la asigna en la misma transacción. Devuelve su id. */
    suspend fun adminAssignNewSlot(
        activityId: String,
        userId: String?,
        guestLabel: String?,
    ): AppResult<String> =
        safeCall {
            postgrest.rpc(
                function = "admin_assign_new_slot",
                parameters = buildJsonObject {
                    put("p_activity_id", activityId)
                    userId?.let { put("p_user_id", it) }
                    guestLabel?.let { put("p_guest_label", it) }
                },
            ).data.trim().trim('"')
        }
```

- [ ] **Step 3: Compilar**

Run: `./gradlew :core:data:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add core/model core/data
git commit -m "feat(data): guestLabel on Slot and admin assign RPC wrappers"
```

---

### Task 5: ScreenModel — miembros en el estado y acciones de asignar

**Files:**
- Modify: `feature/activity/src/commonMain/kotlin/com/app/community/feature/activity/presentation/ActivityDetailScreenModel.kt`

- [ ] **Step 1: Añadir el import y el campo de estado**

En la lista de imports, después de `import com.app.community.core.model.CommunityVisibility`:

```kotlin
import com.app.community.core.model.CommunityMember
```

En `ActivityDetailUiState.Content`, después de `val pendingGuestRequests: List<PendingGuestRequest> = emptyList(),`:

```kotlin
        val members: List<CommunityMember> = emptyList(),
```

- [ ] **Step 2: Guardar los miembros que ya se cargan**

Junto a los otros campos privados de la clase, después de `private var pendingGuestRequests: List<PendingGuestRequest> = emptyList()`:

```kotlin
    private var members: List<CommunityMember> = emptyList()
```

En `load()`, sustituir la línea `val members = membersResult.getOrNull() ?: emptyList()` por:

```kotlin
            members = membersResult.getOrNull() ?: emptyList()
```

(la línea siguiente, `val isAdmin = members.any { ... }`, sigue funcionando sin tocarla).

- [ ] **Step 3: Pasar los miembros al estado**

En `loadSlots`, en las **tres** construcciones de `ActivityDetailUiState.Content` (ramas `UNLIMITED`, `LIMITED` y `LIMITED_WITH_POSITIONS`), añadir como último argumento, después de `pendingGuestRequests = pendingGuestRequests,`:

```kotlin
                    members = members,
```

- [ ] **Step 4: Añadir las dos acciones**

En `ActivityDetailScreenModel`, después de `fun leaveUnlimited() { ... }`:

```kotlin
    fun assignSlot(slotId: String, userId: String?, guestLabel: String?) {
        screenModelScope.launch {
            slotRepository.adminAssignSlot(slotId, userId, guestLabel)
                .onSuccess { assigned ->
                    _actionMessage.value =
                        if (assigned) "Persona apuntada" else "Esa plaza ya está ocupada"
                    load()
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }

    fun assignNewSlot(userId: String?, guestLabel: String?) {
        screenModelScope.launch {
            slotRepository.adminAssignNewSlot(activityId, userId, guestLabel)
                .onSuccess {
                    _actionMessage.value = "Persona apuntada"
                    load()
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }
```

- [ ] **Step 5: Compilar**

Run: `./gradlew :feature:activity:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`.

> `loadProfiles` y `loadProfilesWithPositions` **no necesitan cambios**: ya usan
> `slots.mapNotNull { it.reservedBy }` y `slot.reservedBy?.let { ... }`, así que una plaza con
> `reserved_by NULL` simplemente sale con `profile = null` y la UI pinta la etiqueta. Verificado
> en `ActivityDetailScreenModel.kt:200-232`.

- [ ] **Step 6: Commit**

```bash
git add feature/activity
git commit -m "feat(activity): expose members and admin assign actions in the screen model"
```

---

### Task 6: UI — diálogo, botones y pintado de la etiqueta

**Files:**
- Modify: `feature/activity/src/commonMain/kotlin/com/app/community/feature/activity/presentation/ActivityDetailScreen.kt`
- Modify: `feature/activity/src/commonMain/composeResources/values/strings.xml`
- Modify: `feature/activity/src/commonMain/composeResources/values-es/strings.xml`

- [ ] **Step 1: Añadir los textos**

En `values/strings.xml`, antes de `</resources>`:

```xml
    <string name="assign_button">Add someone</string>
    <string name="assign_dialog_title">Add someone to this slot</string>
    <string name="assign_search_member">Search member</string>
    <string name="assign_guest_name">…or type a name</string>
    <string name="assign_queue_warning">There are %1$d people in the substitute queue.</string>
    <string name="assign_confirm">Add</string>
    <string name="assign_confirm_anyway">Add anyway</string>
```

En `values-es/strings.xml`, antes de `</resources>`:

```xml
    <string name="assign_button">Apuntar a alguien</string>
    <string name="assign_dialog_title">Apuntar a alguien en esta plaza</string>
    <string name="assign_search_member">Buscar miembro</string>
    <string name="assign_guest_name">…o escribe un nombre</string>
    <string name="assign_queue_warning">Hay %1$d personas en la cola de suplentes.</string>
    <string name="assign_confirm">Apuntar</string>
    <string name="assign_confirm_anyway">Apuntar igualmente</string>
```

- [ ] **Step 2: Añadir los imports**

En `ActivityDetailScreen.kt`, añadir a la lista de imports:

```kotlin
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import com.app.community.core.model.CommunityMember
```

- [ ] **Step 3: Declarar el objetivo de la asignación**

Al final de `ActivityDetailScreen.kt`, como declaración de primer nivel:

```kotlin
/** Qué plaza va a recibir la asignación: una existente, o una nueva en modo ilimitado. */
private sealed interface AssignTarget {
    data class ExistingSlot(val slotId: String) : AssignTarget
    data object NewSlot : AssignTarget
}
```

- [ ] **Step 4: Escribir el diálogo**

Al final de `ActivityDetailScreen.kt`:

```kotlin
@Composable
private fun AssignSlotDialog(
    members: List<CommunityMember>,
    queueSize: Int,
    onDismiss: () -> Unit,
    onConfirm: (userId: String?, guestLabel: String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedUserId by remember { mutableStateOf<String?>(null) }
    var guestName by remember { mutableStateOf("") }

    val filtered = remember(members, query) {
        if (query.isBlank()) members
        else members.filter { it.profiles?.displayName?.contains(query, ignoreCase = true) == true }
    }
    val canConfirm = selectedUserId != null || guestName.isNotBlank()
    val unknownUserLabel = stringResource(Res.string.unknown_user)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.assign_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AgoraSpacing.sm)) {
                if (queueSize > 0) {
                    Text(
                        text = stringResource(Res.string.assign_queue_warning, queueSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(Res.string.assign_search_member)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(filtered, key = { it.userId }) { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedUserId = member.userId
                                    guestName = ""
                                }
                                .padding(vertical = AgoraSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedUserId == member.userId,
                                onClick = {
                                    selectedUserId = member.userId
                                    guestName = ""
                                },
                            )
                            Text(
                                text = member.profiles?.displayName ?: unknownUserLabel,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = guestName,
                    onValueChange = {
                        guestName = it
                        if (it.isNotBlank()) selectedUserId = null
                    },
                    label = { Text(stringResource(Res.string.assign_guest_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    onConfirm(selectedUserId, guestName.takeIf { it.isNotBlank() })
                    onDismiss()
                },
            ) {
                Text(
                    stringResource(
                        if (queueSize > 0) Res.string.assign_confirm_anyway
                        else Res.string.assign_confirm
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.label_cancel)) }
        },
    )
}
```

- [ ] **Step 5: Montar el diálogo en la pantalla**

En el composable de contenido, junto a `var showDeleteDialog by remember { mutableStateOf(false) }` (línea ~154):

```kotlin
    var assignTarget by remember { mutableStateOf<AssignTarget?>(null) }
```

Y después del bloque `if (showDeleteDialog) { ... }` (línea ~445), antes del cierre de la función:

```kotlin
    assignTarget?.let { target ->
        AssignSlotDialog(
            members = state.members,
            queueSize = state.substituteQueue.size,
            onDismiss = { assignTarget = null },
            onConfirm = { userId, label ->
                when (target) {
                    is AssignTarget.ExistingSlot -> screenModel.assignSlot(target.slotId, userId, label)
                    AssignTarget.NewSlot -> screenModel.assignNewSlot(userId, label)
                }
            },
        )
    }
```

- [ ] **Step 6: Botón en el modo ilimitado**

En el bloque `if (activity.slotMode == SlotMode.UNLIMITED)` (línea ~314), sustituir el `item { ... }` del botón por:

```kotlin
            item {
                Column(verticalArrangement = Arrangement.spacedBy(AgoraSpacing.xs)) {
                    if (state.isUserJoined) {
                        AgoraButton(
                            text = stringResource(Res.string.detail_leave),
                            onClick = screenModel::leaveUnlimited,
                            variant = AgoraButtonVariant.Danger,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        AgoraButton(
                            text = stringResource(Res.string.detail_join),
                            onClick = screenModel::joinUnlimited,
                            variant = AgoraButtonVariant.Primary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.isAdmin) {
                        AgoraButton(
                            text = stringResource(Res.string.assign_button),
                            onClick = { assignTarget = AssignTarget.NewSlot },
                            variant = AgoraButtonVariant.Secondary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
```

(`AgoraButtonVariant` es `{ Primary, Secondary, Tertiary, Danger }`, verificado en `core/ui/src/commonMain/kotlin/com/app/community/core/ui/components/AgoraButton.kt:18`.)

Y la lista de participantes justo debajo (línea ~334) debe dejar de esconder las plazas de etiqueta:

```kotlin
            items(state.slots.filter { it.slot.reservedBy != null || it.slot.guestLabel != null }) { slotWithProfile ->
                ParticipantRow(slotWithProfile, state.currentUserId)
            }
```

- [ ] **Step 7: Pasar el callback a las dos tarjetas de plaza**

En la llamada a `SlotCard` (línea ~342) y en la de `PositionSlotCard` (línea ~388), añadir como último argumento:

```kotlin
                    onAssign = { assignTarget = AssignTarget.ExistingSlot(slotWithProfile.slot.id) },
```

- [ ] **Step 8: Botón "Apuntar a alguien" en `SlotCard`**

En la firma de `SlotCard` (línea ~543), añadir tras `onMarkPaid: () -> Unit,`:

```kotlin
    onAssign: () -> Unit,
```

Y sustituir la primera rama del `when` de acciones (línea ~615):

```kotlin
            when {
                slot.status == SlotStatus.AVAILABLE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.xs)) {
                        if (!hasReservation) {
                            AgoraButton(
                                text = stringResource(Res.string.slot_reserve),
                                onClick = onReserve,
                                variant = AgoraButtonVariant.Primary,
                            )
                        }
                        if (isAdmin) {
                            TextButton(onClick = onAssign) {
                                Text(
                                    stringResource(Res.string.assign_button),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                isMySlot -> {
```

(el resto del `when` no cambia).

- [ ] **Step 9: Pintar la etiqueta en `SlotCard`**

En el bloque `SlotStatus.RESERVED, SlotStatus.PAID, SlotStatus.PENDING ->` (línea ~582), sustituir el cálculo del nombre:

```kotlin
                        val name = when {
                            slot.isGuest && slot.status == SlotStatus.PENDING -> guestChip
                            else -> slotWithProfile.profile?.displayName ?: slot.guestLabel ?: guestChip
                        }
```

Y añadir el chip de invitado justo después del `Text` del nombre, dentro del mismo `Row`:

```kotlin
                            if (slot.guestLabel != null) {
                                Spacer(Modifier.width(AgoraSpacing.xs))
                                Text(
                                    text = guestChip,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
```

- [ ] **Step 10: Repetir los pasos 8 y 9 en `PositionSlotCard`**

`PositionSlotCard` (línea ~646) es estructuralmente idéntico: misma firma con `onAssign: () -> Unit,` tras `onMarkPaid`, mismo `when` de acciones (línea ~722) y mismo bloque de nombre (línea ~688). Aplicar exactamente los mismos tres cambios, con el mismo código.

- [ ] **Step 11: Pintar la etiqueta en `ParticipantRow`**

En `ParticipantRow` (línea ~450), sustituir la primera línea:

```kotlin
    val name = slotWithProfile.profile?.displayName
        ?: slotWithProfile.slot.guestLabel
        ?: stringResource(Res.string.unknown_user)
```

- [ ] **Step 12: Gate Android**

Run: `./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 13: Commit**

```bash
git add feature/activity
git commit -m "feat(activity): admins can assign members or guest names to slots"
```

---

### Task 7: Script de smoke y verificación manual

La lógica nueva vive en SQL, donde no llega ningún test de Kotlin. Este script es el check que queda detrás.

**Files:**
- Create: `docs/admin_assign_slots_smoke_check.sql`

- [ ] **Step 1: Escribir el script**

Crear `docs/admin_assign_slots_smoke_check.sql`:

```sql
-- ============================================================================
-- Admins apuntan gente en los huecos — smoke check
-- Pégalo en Supabase Dashboard → SQL Editor. Lee resultados de arriba a abajo.
-- Los bloques 1-3 son SELECTs. Los bloques 4-8 se ejecutan desde la app, no aquí:
-- la comprobación de admin depende de auth.uid(), que en el editor SQL es NULL.
-- ============================================================================

-- 1) Los dos RPC existen
select proname
from pg_proc
where proname in ('admin_assign_slot', 'admin_assign_new_slot')
order by proname;
-- Esperado: 2 filas.

-- 2) La columna y la constraint existen
select column_name, data_type, is_nullable
from information_schema.columns
where table_name = 'slots' and column_name = 'guest_label';
-- Esperado: 1 fila, text, YES.

select conname
from pg_constraint
where conname = 'slots_no_owner_and_label';
-- Esperado: 1 fila.

-- 3) release_slot ya no usa "!=" para comparar el dueño
select position('IS DISTINCT FROM' in prosrc) > 0 as usa_is_distinct_from
from pg_proc where proname = 'release_slot';
-- Esperado: true.

-- ============================================================================
-- Los siguientes se comprueban DESDE LA APP, con un admin logueado.
-- Tras cada uno, releer la fila con:
--   select id, status, reserved_by, guest_label from slots where id = '<slot_id>';
-- ============================================================================
-- 4) Apuntar a un miembro en plaza libre  → status 'reserved', reserved_by = ese usuario,
--    guest_label NULL, y le llega la notificación "Te han apuntado".
-- 5) Apuntar un nombre suelto             → status 'reserved', reserved_by NULL,
--    guest_label con el texto. La plaza se ve en la app con el nombre y el chip de invitado.
-- 6) Apuntar sobre plaza ya ocupada       → snackbar "Esa plaza ya está ocupada", sin cambios.
-- 7) Apuntar a alguien que ya tiene plaza → error "already has a slot in this activity".
-- 8) Liberar la plaza de etiqueta (admin) → status 'available', guest_label NULL.
```

- [ ] **Step 2: Ejecutar los bloques 1-3**

Pegar los bloques 1-3 en el SQL Editor del dashboard de Supabase.
Expected: 2 filas de RPC, la columna `guest_label` presente, la constraint presente, `usa_is_distinct_from` = `true`.

- [ ] **Step 3: Ejecutar los casos 4-8 desde la app**

Compilar e instalar en un dispositivo o emulador Android, entrar como admin de una comunidad de prueba y recorrer los cinco casos, comprobando la fila de `slots` tras cada uno con el `select` indicado.
Expected: los cinco comportamientos descritos.

- [ ] **Step 4: Commit**

```bash
git add docs/admin_assign_slots_smoke_check.sql
git commit -m "docs: smoke check for admin slot assignment"
```

---

### Task 8: Cierre

- [ ] **Step 1: Registrar el resultado**

Añadir al final de este plan una sección `## Resultado` con una línea por caso del smoke (o "sin hallazgos") y cualquier desviación aplicada durante la ejecución.

- [ ] **Step 2: Gate Android final**

Run: `./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Avisar al usuario**

Recordarle que los testers de Prueba Interna deben actualizar la app: el serializer tolerante protege de aquí en adelante, pero no arregla retroactivamente lo ya instalado, y el tipo `slot_assigned` ya circula por la base de datos.

- [ ] **Step 4: Commit y push**

```bash
git add docs/superpowers/plans
git commit -m "docs: record admin slot assignment results"
git push origin main
```

---

## Resultado

**2026-07-31 — migraciones aplicadas a producción y feature probada en Android.**

Las dos migraciones se aplicaron con `supabase db push` (`supabase migration list` da Local =
Remote en las 7). Verificado **contra el esquema real** volcado con `supabase db dump`, no
contra la salida del CLI: los 2 RPC existen, la columna `guest_label` y la constraint
`slots_no_owner_and_label` están, `notifications_type_check` incluye `slot_assigned` junto a
los 15 anteriores, `release_slot` lleva los cuatro arreglos (`IS DISTINCT FROM` ×2, limpia
`guest_label`, resetea `is_guest`, protege `pending`), `reject_guest_request` limpia la
etiqueta, y `admin_assign_new_slot` tiene el guardia de `slot_mode`, el `FOR UPDATE` y la
validación de etiqueta vacía.

Probado por el usuario en Android, modo de aforo limitado:

| Caso | Resultado |
|---|---|
| Apuntar a un miembro de la comunidad | ✅ y le llegó la notificación |
| Marcar esa plaza como pagada | ✅ y le llegó la notificación |
| Apuntar un nombre suelto (invitado sin cuenta) | ✅ |
| Liberar esa plaza de etiqueta siendo admin | ✅ |

**No verificado desde la app** (ni por el usuario ni por nadie): los caminos negativos —
no-admin liberando una plaza de etiqueta, persona que ya tiene plaza, nombre vacío, aviso de
cola de suplentes— y los modos `unlimited` y `limited_with_positions`. Están cubiertos por
código y por la revisión adversarial del SQL, pero no ejecutados. Los casos están escritos en
`docs/admin_assign_slots_smoke_check.sql` por si se quieren pasar más adelante.

### Hallazgos de la revisión adversarial del SQL (los 7, todos arreglados antes del push)

Tres eran bugs **preexistentes**, no introducidos por esta feature:

1. `reject_guest_request` no limpiaba `guest_label` → plaza libre con etiqueta pegada, que
   viola la constraint nueva en cuanto alguien la reserve, y sin policy de DELETE para limpiarla.
2. `release_slot` no comprobaba permisos para `status='pending'`: la cadena
   `IF reserved_by IS NULL / ELSIF paid / ELSIF reserved` no casaba, el `IF` caía directo al
   UPDATE y **cualquier autenticado podía liberar la plaza retenida por un invitado**.
3. `release_slot` no reseteaba `is_guest`, así que el siguiente ocupante de una plaza que tuvo
   invitado salía marcado como invitado.
4. `admin_assign_new_slot` no comprobaba `slot_mode` → podía crear plazas por encima del aforo.
5. `p_guest_label` sin validar aceptaba `''` y `'   '`.
6. `admin_assign_new_slot` sin `FOR UPDATE` sobre la actividad → `sort_order` duplicado y
   plazas que se reordenan solas entre refrescos.
7. Migraciones sin `BEGIN`/`COMMIT`, a diferencia de sus hermanas.

## Qué NO hace este plan (a propósito)

- **Los tres huecos de promoción automática de suplentes.** `promote_substitute` sólo se llama desde `release_slot` y no hay trigger en `slots`, así que una plaza queda libre con la cola llena cuando la posición no casa, cuando se crean plazas por `INSERT` directo, o cuando se rechaza a un invitado. Tarea aparte, ya acordada.
- **Migrar `joinUnlimited` a un RPC.** Hoy crea la plaza, recarga y reserva en tres pasos; `admin_assign_new_slot` hace lo correcto en una transacción, pero cambiar el camino del usuario normal es otro cambio.
- **Autocompletar etiquetas usadas antes.** Se escriben cada vez.
- **Prohibir plazas duplicadas en `reserve_slot`.** Cambiaría el comportamiento de la app ya publicada.
