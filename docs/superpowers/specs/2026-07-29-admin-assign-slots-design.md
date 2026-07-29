# Admins apuntan gente en los huecos — diseño

Fecha: **2026-07-29**. Estado: aprobado por el usuario en sesión de brainstorming.

## Motivación

Mientras la comunidad de voleyball se acostumbra a usar la app, el admin necesita poder
**apuntar a otras personas en las plazas de una actividad** sin depender de que cada uno lo
haga por su cuenta. Hoy es imposible: `reserve_slot` usa `auth.uid()` a pelo, así que sólo
puedes escribir tu propia plaza. Lo único que un admin puede hacer sobre plazas ajenas es
**liberarlas** (`release_slot` tiene rama de admin).

## Decisiones tomadas

| Decisión | Elegido | Motivo |
|---|---|---|
| A quién se puede apuntar | Miembros de la comunidad **y** nombres sueltos | Hay gente que juega sin tener la app instalada. |
| Cómo se guarda un nombre suelto | Columna `guest_label` en `slots` | Evita crear usuarios de auth anónimos, que están bloqueados por el tema Turnstile hasta el lanzamiento público. |
| Cola de suplentes | El admin manda, pero avisado | Hay tres vías por las que una plaza queda libre con la cola llena (ver "Fuera de alcance"), así que saltarse la cola sin querer es fácil. El aviso va en la UI, no en el RPC. |
| Modos de actividad | Los tres (`UNLIMITED`, `LIMITED`, `LIMITED_WITH_POSITIONS`) | En ilimitado el RPC crea la plaza y la asigna en la misma transacción. |
| Notificación al apuntado | Sí, tipo nuevo `slot_assigned` | Sin aviso, la gente se entera el día del partido. |
| Persona ya apuntada en la actividad | Rechazar | Apuntar dos veces al mismo casi siempre es un error de dedo. Asimetría deliberada: `reserve_slot` no lo comprueba y se deja como está. |

## Esquema

Una migración nueva (`supabase migration new admin_assign_slots`):

```sql
ALTER TABLE slots ADD COLUMN guest_label text;

ALTER TABLE slots ADD CONSTRAINT slots_no_owner_and_label
  CHECK (guest_label IS NULL OR reserved_by IS NULL);
```

Una plaza pertenece a un usuario **o** a una etiqueta, nunca a los dos.

Esto introduce un estado que hoy no existe: una plaza `reserved` con `reserved_by NULL`.
Todo lo que asuma que una plaza reservada tiene dueño hay que revisarlo — en particular
`loadProfiles` y `loadProfilesWithPositions` en `ActivityDetailScreenModel`.

## Backend

### `admin_assign_slot(p_slot_id uuid, p_user_id uuid DEFAULT NULL, p_guest_label text DEFAULT NULL) RETURNS boolean`

`SECURITY DEFINER`, `search_path = public, pg_temp` (como el resto de RPC del proyecto).

1. `auth.uid()` no nulo, si no `RAISE EXCEPTION 'Not authenticated'`.
2. Exactamente uno de `p_user_id` / `p_guest_label` no nulo; si no, excepción.
3. `SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE`. Si no existe, excepción.
4. El llamante es admin de la comunidad de la actividad; si no, `RAISE EXCEPTION 'Only community admins can assign slots'`.
5. Si `v_slot.status <> 'available'` → `RETURN FALSE` (la plaza se ocupó mientras el diálogo estaba abierto).
6. Si `p_user_id` no nulo:
   - debe ser miembro de la comunidad; si no, excepción.
   - no debe tener ya otra plaza en esta actividad (`EXISTS (SELECT 1 FROM slots WHERE activity_id = ... AND reserved_by = p_user_id)`); si la tiene, `RAISE EXCEPTION 'That person already has a slot in this activity'`.
7. `UPDATE slots SET status = 'reserved', reserved_by = p_user_id, guest_label = p_guest_label, reserved_at = now()`.
8. Si `p_user_id` no nulo: borrar todas sus entradas de `substitute_queue` en esta actividad (mismo comportamiento que `reserve_slot`) e insertar la notificación `slot_assigned`.
9. `RETURN TRUE`.

**No** comprueba la cola de suplentes: es la decisión "el admin manda".

### `admin_assign_new_slot(p_activity_id uuid, p_user_id uuid DEFAULT NULL, p_guest_label text DEFAULT NULL) RETURNS uuid`

Para `slot_mode = 'unlimited'`, donde no hay plazas preexistentes. Mismas validaciones (admin,
xor de parámetros, miembro, no duplicado) y luego, en la misma transacción, `INSERT` de la plaza
con `sort_order = coalesce(max(sort_order), -1) + 1` ya en estado `reserved` con su dueño o
etiqueta. Devuelve el id de la plaza creada.

### Arreglo obligatorio de `release_slot`

La comprobación actual es:

```sql
IF v_slot.reserved_by != v_user_id AND NOT v_is_admin THEN RAISE EXCEPTION ...
```

Con `reserved_by NULL`, `NULL != uuid` evalúa a `NULL` (no a `TRUE`), la condición entera se
vuelve `NULL` y el `IF` **no dispara**. Resultado: cualquier usuario autenticado podría liberar
una plaza de etiqueta, y el agujero es idéntico en la rama de `status = 'paid'`.

Cambios:

- Usar `IS DISTINCT FROM` en vez de `!=` en las dos ramas.
- Cuando `reserved_by IS NULL` (plaza de etiqueta), exigir admin explícitamente.
- Al liberar, limpiar también `guest_label`: `SET status='available', reserved_by=NULL, reserved_at=NULL, guest_label=NULL`.

`promote_substitute` y `reserve_slot` no necesitan cambios: sólo actúan sobre plazas
`available`, que por definición tienen `guest_label NULL`.

## Prerrequisito de app: `NotificationType` tolerante

`NotificationType` es un enum cerrado y `Notification.type` no es nullable, así que
`decodeList<Notification>()` **lanza excepción ante un valor desconocido y tumba la lista
entera**, no sólo la fila mala. Añadir `slot_assigned` rompería la pestaña de notificaciones
a quien tenga instalada una versión anterior.

Antes de la migración:

1. Añadir `UNKNOWN` al enum y un `KSerializer` que caiga a `UNKNOWN` en vez de lanzar. Requiere
   un mapa de nombre-de-cable → entrada (las anotaciones `@SerialName` no son legibles en
   runtime sin reflexión), así que se le da a cada entrada un `val wire: String`.
2. El compilador señalará los `when` exhaustivos sobre `NotificationType`; en todos ellos
   `UNKNOWN` se trata como notificación genérica (sin icono especial, sin navegación).
3. Añadir `SLOT_ASSIGNED("slot_assigned")`.

Esto arregla un problema latente que ya existía: **cualquier** tipo futuro habría roto a los
clientes viejos igual.

**Nota operativa:** el serializer tolerante protege de aquí en adelante, pero no arregla
retroactivamente lo ya instalado. Hay que avisar a los testers de Prueba Interna de que
actualicen antes de empezar a usar la feature.

## UI

Todo vive en `feature/activity`, pantalla de detalle de actividad.

- **Plazas libres, siendo admin**: botón "Apuntar a…" junto a "Reservar", en las dos variantes
  de fila de `ActivityDetailScreen` (la de `LIMITED` y la de `LIMITED_WITH_POSITIONS`).
- **Modo ilimitado**: botón "Apuntar a alguien" junto a "Apuntarme", visible sólo para admin.
- **Diálogo de asignación** (composable nuevo en el mismo módulo):
  - Lista de miembros de la comunidad, filtrable por nombre. Los miembros **ya se cargan** en
    `load()` para calcular `isAdmin`: se reutiliza esa lista, sin llamadas nuevas.
  - Campo de texto "o escribe un nombre" para el invitado sin cuenta.
  - Confirmar exige una de las dos cosas, no las dos.
  - Si `substituteQueue` no está vacía, el diálogo muestra "Hay N personas en la cola de
    suplentes" y el botón pasa a decir "Apuntar igualmente".
- **Pintado del nombre**: donde hoy se resuelve el perfil por `reservedBy`, si
  `reservedBy == null && guestLabel != null` se muestra la etiqueta con un chip "invitado".
- **Mensajes de error**: el `RETURN FALSE` del RPC se traduce a "Esa plaza ya está ocupada" y
  recarga; la excepción de duplicado a "Esa persona ya tiene plaza en esta actividad".

Todos los textos, en `strings.xml` de `feature/activity` (`values` y `values-es`).

## Capa de datos

`SlotRepository` gana dos métodos que envuelven los RPC, siguiendo el patrón de
`reserveSlot`/`releaseSlot` (`safeCall` + `buildJsonObject`). `Slot` gana
`@SerialName("guest_label") val guestLabel: String? = null`.

## Verificación

La lógica nueva es SQL, así que no hay test unitario de Kotlin que la cubra de forma útil. El
check que queda es un script de smoke al estilo de `docs/guest_links_smoke_check.sql`:

1. Admin apunta a un miembro en plaza libre → plaza `reserved` con ese `reserved_by`.
2. Admin apunta un nombre suelto → `reserved_by NULL`, `guest_label` con el texto.
3. Apuntar sobre plaza ya ocupada → devuelve `FALSE`, no modifica nada.
4. Apuntar a alguien que ya tiene plaza → excepción.
5. No-admin llamando al RPC → excepción.
6. Liberar una plaza de etiqueta siendo no-admin → excepción (regresión del agujero de `NULL`).
7. Liberar una plaza de etiqueta siendo admin → queda `available` y `guest_label` a `NULL`.
8. Modo ilimitado: `admin_assign_new_slot` crea la plaza ya asignada.

Más el gate Android de siempre: `./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest`.

## Fuera de alcance (a propósito)

- **Los tres huecos de promoción automática de suplentes.** `promote_substitute` sólo se llama
  desde `release_slot`, no hay trigger en la tabla `slots`, así que una plaza queda libre con la
  cola llena cuando: (1) la posición de la plaza no casa con la de nadie en cola; (2) se crean
  plazas nuevas por `INSERT` directo, p. ej. al ampliar el aforo; (3) se rechaza a un invitado
  con `reject_guest_request`, que devuelve la plaza a `available` sin promocionar. Decidido
  atacarlo en una tarea propia (probablemente un trigger que sustituya al guardia de una sola
  puerta).
- **La carrera de `joinUnlimited`**, que hoy crea la plaza, recarga y luego reserva en tres
  pasos separados. `admin_assign_new_slot` hace lo correcto en una transacción, pero migrar
  `joinUnlimited` a un RPC equivalente es un cambio aparte.
- **Autocompletar etiquetas usadas antes.** Se escriben cada vez.
- **Prohibir plazas duplicadas en `reserve_slot`.** Cambiaría el comportamiento de la app ya
  publicada.
