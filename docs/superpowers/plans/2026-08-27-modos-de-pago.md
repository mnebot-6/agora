# Tres modos de pago por actividad — plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usa `superpowers:subagent-driven-development`
> (recomendado) o `superpowers:executing-plans` para ejecutar este plan tarea a tarea.
> Los pasos usan checkbox (`- [ ]`) para seguimiento.

**Objetivo:** que quien crea una actividad elija explícitamente entre sin pago, pago
externo y pago gestionado por Agora, y que el admin pueda marcar **y desmarcar** cobros
manuales sin romper el flujo de liberar plaza.

**Arquitectura:** una columna `activities.payment_mode` guarda la intención del admin, y
una función SQL `activity_payment_mode()` calcula el modo *efectivo* añadiendo el estado
del cobrador de la comunidad (ahí vive la degradación automática). El traspaso de una plaza
liberada —quién la ocupa, a quién se le debe el dinero, a quién se avisa— se extrae a una
única función SQL que llaman tanto las RPC como la Edge Function de Stripe, para que no
haya dos versiones de la regla del dinero.

**Stack:** Kotlin Multiplatform + Compose Multiplatform, Voyager, Koin, Supabase
(Postgres + RPC SECURITY DEFINER + Edge Functions en Deno/TypeScript), Stripe Connect.

**Diseño aprobado:** `docs/superpowers/specs/2026-08-27-modos-de-pago-design.md`.

---

## Estado de ejecución — actualizado 2026-08-27

Rama: `feat/modos-de-pago`. **Nada aplicado todavía contra la base de datos.**

| Tareas | Estado |
|---|---|
| 1-5 | ✅ SQL escrito, revisado y corregido dos veces |
| 6 | ✅ Verificado contra un Postgres real en Docker: 17 migraciones y 12 escenarios |
| 7-8 | ✅ Modelo y repositorios, 6 tests nuevos |
| 9-11 | ✅ Crear y editar |
| 12-13 | ✅ Detalle de la actividad |
| 14 | ✅ La Edge Function delega el traspaso en la RPC |
| 15 | ⬜ **Pendiente: despliegue y comprobación manual. Lo lanza una persona.** |

**Nada se ha desplegado.** Hay tres migraciones sin aplicar (`20260827113804`,
`20260827115031`, `20260828101500`), las Edge Functions sin desplegar y la app sin publicar.

### Cosas descubiertas por el camino que no estaban en el plan

Todas arregladas dentro de esta rama:

1. **La prioridad de la cola de suplentes lleva rota desde el baseline.** `RECORD IS NOT NULL`
   en Postgres solo es cierto si todas las columnas son no nulas, y `substitute_queue.position_id`
   es nula en cuanto la actividad no usa posiciones. La comprobación no se ha ejecutado nunca.
2. **`joinUnlimited` se saltaba el cobro entero**: creaba plaza y llamaba al RPC sin mirar el
   precio, así que en aforo ilimitado de pago se reservaba gratis.
3. **La restricción nueva habría roto a los clientes ya instalados.** Trigger de compatibilidad.
4. **En aforo ilimitado no había forma de marcar a nadie como pagado**: el diseño lo promete y
   la fila de participante no lo ofrecía.
5. **Los invitados no veían el importe**, ni en la app ni en la landing web del enlace compartido.
6. `unmark_slot_paid` se bloqueaba de por vida por un pago de Stripe ajeno ya reembolsado.

### `deno` no está instalado

`deno test supabase/functions/_shared/payments_test.ts` no se ha podido ejecutar. El cambio de
la tarea 14 es una sustitución sin símbolos nuevos, pero conviene correrlo antes de desplegar
las funciones.

### Agujero preexistente descubierto por el camino

`joinUnlimited()` en `ActivityDetailScreenModel.kt:372` crea una plaza y llama al RPC
`reserve_slot` **directamente, sin mirar el precio ni el modo**. En producción, hoy, eso
permite apuntarse gratis a una actividad de pago de aforo ilimitado. La guarda
`IF v_mode = 'agora' THEN RETURN FALSE` de la migración lo cierra en el servidor. El
arreglo del cliente está en el paso 3 de la tarea 12.

### ORDEN DE DESPLIEGUE: migraciones, funciones, app. En ese orden y sin saltárselo

**La app NO puede salir antes que las migraciones.** `Activity.paymentMode` tiene
`PaymentMode.FREE` por defecto, así que contra una base sin la columna `payment_mode`
**toda** actividad decodifica como gratuita, `effectiveMode()` devuelve `FREE` y reservar
se vuelve instantáneo: cualquiera se quedaría gratis una plaza de una actividad de Stripe
con dinero real detrás.

**Las Edge Functions tampoco pueden salir antes que las migraciones.** `payments.ts` llama
a `settle_previous_occupant` y descarta el error; si la función SQL todavía no existe,
ningún ocupante anterior recibiría su devolución y nada quedaría registrado.

Al revés no duele: con las migraciones puestas, los clientes viejos siguen funcionando
—el Checkout no cambia y el trigger de compatibilidad cubre crear y editar— y el único
daño es el conocido, que `joinUnlimited` falle en silencio en una actividad `agora` de
aforo ilimitado hasta que llegue la app. Por eso la ventana entre los tres pasos debe ser
corta, pero el orden no es negociable.

### Dos hallazgos de la revisión que se DESCARTARON, no los reabras

- *"Sacar `awaiting_substitute` del `DELETE` de `unmark_slot_paid`."* No: deshacer el cobro
  significa que esa persona no pagó, y entonces no se le debe nada. Cancelar la liberación
  es el comportamiento especificado. El riesgo del misclic se cubre con el aviso del punto 3.
- *"Un `refund_pending` sobre una cuenta caída se atasca para siempre."* Falso:
  `stripe-sweep/index.ts:146` lo escala a `refund_owed` a los cinco intentos.

### Política de despliegue de esta rama

Ningún subagente ejecuta `supabase db push` ni `supabase functions deploy`. El proyecto
está enlazado con producción y hay dinero real dentro. Esos dos comandos los lanza una
persona, con el diff delante. Los puntos donde toca son la tarea 6 (migraciones) y la
tarea 14 (Edge Functions).

---

## Estructura de ficheros

**Se crean:**

| Fichero | Responsabilidad |
|---|---|
| `supabase/migrations/<ts>_payment_modes.sql` | Columna, backfill, restricción y `activity_payment_mode()` |
| `supabase/migrations/<ts>_payment_mode_flows.sql` | `settle_previous_occupant`, `promote_substitute`, `reserve_slot`, `unmark_slot_paid`, guarda de `begin_slot_payment` |
| `docs/payment_modes_check.sql` | Script de comprobación manual contra la base de datos |
| `core/model/src/commonMain/kotlin/com/app/community/core/model/PaymentMode.kt` | El enum, su serializer tolerante y `Activity.effectiveMode()` |
| `core/model/src/commonTest/kotlin/com/app/community/core/model/PaymentModeTest.kt` | Tests puros del enum y del modo efectivo |

**Se modifican:**

| Fichero | Cambio |
|---|---|
| `core/model/.../Activity.kt` | Campo `paymentMode` |
| `core/model/.../Community.kt` | Campo `stripeChargesEnabled` |
| `core/data/.../ActivityRepository.kt` | `createActivity` recibe `paymentMode` |
| `core/data/.../SlotRepository.kt` | `unmarkSlotPaid` |
| `feature/activity/.../ActivityPriceField.kt` | Tres tarjetas, campo "cómo se paga", modo de solo lectura |
| `feature/activity/.../CreateActivityScreenModel.kt` + `CreateActivityScreen.kt` | Estado y validación por modo |
| `feature/activity/.../EditActivityScreenModel.kt` + `EditActivityScreen.kt` | Modo en solo lectura |
| `feature/activity/.../ActivityDetailScreenModel.kt` + `ActivityDetailScreen.kt` | Reservar por modo, desmarcar pago, textos |
| `feature/activity/src/commonMain/composeResources/values{,-es}/strings.xml` | Cadenas nuevas |
| `composeApp/.../di/AppModule.kt` | `CreateActivityScreenModel` recibe `communityRepository` |
| `supabase/functions/_shared/payments.ts` | Delega el traspaso en la RPC |

---

## Fase 1 — Esquema

### Tarea 1: Columna `payment_mode`, backfill y modo efectivo

**Ficheros:**
- Crear: `supabase/migrations/<timestamp>_payment_modes.sql`

- [ ] **Paso 1: Crear el fichero de migración**

```bash
supabase migration new payment_modes
```

Anota la ruta que imprime; es la que se edita en el paso siguiente.

- [ ] **Paso 2: Escribir la migración**

Contenido completo del fichero recién creado:

```sql
-- ============================================================================
-- Tres modos de pago por actividad.
--
-- payment_mode es la INTENCION del admin y se fija al crear. Lo que se ejecuta
-- es el modo EFECTIVO, que ademas mira si la comunidad puede cobrar: si el
-- cobrador cae, una actividad 'agora' se comporta como 'external' sin que nadie
-- reescriba ninguna fila, y vuelve sola cuando el cobrador se recupera.
--
-- Solo esquema y datos. Ninguna funcion de flujo cambia aqui.
--
-- Diseno: docs/superpowers/specs/2026-08-27-modos-de-pago-design.md
-- ============================================================================

BEGIN;

ALTER TABLE activities ADD COLUMN payment_mode text NOT NULL DEFAULT 'free'
  CHECK (payment_mode IN ('free', 'external', 'agora'));

COMMENT ON COLUMN activities.payment_mode IS
  'Intencion del admin, fija al crear. El modo real lo da activity_payment_mode().';

-- Backfill ANTES de la restriccion cruzada: con el DEFAULT 'free' y actividades
-- que tienen price_cents, anadirla primero fallaria en la propia migracion.
UPDATE activities a
SET payment_mode = CASE
        WHEN a.price_cents IS NULL THEN 'free'
        WHEN c.stripe_account_id IS NOT NULL AND c.stripe_charges_enabled THEN 'agora'
        ELSE 'external'
    END
FROM communities c
WHERE c.id = a.community_id;

-- Impide los dos estados incoherentes: de pago sin importe, y gratuita con
-- importe. Lo garantiza la base de datos, no la app.
ALTER TABLE activities ADD CONSTRAINT activities_mode_matches_price
  CHECK ((payment_mode = 'free') = (price_cents IS NULL));

-- cost_description DEJA DE ESTAR DEPRECADA: pasa a ser el "como se paga" del
-- modo external ("Bizum al 601386047 con tu nombre y la fecha como concepto").
COMMENT ON COLUMN activities.cost_description IS
  'Modo external: instrucciones de pago para el usuario. La app escribe NULL en free y agora, pero quedan filas antiguas anteriores a Stripe con texto y sin precio: no es un invariante.';

-- ---------- Modo efectivo ---------------------------------------------------

CREATE OR REPLACE FUNCTION public.activity_payment_mode(p_activity_id uuid)
    RETURNS text
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
    SELECT CASE
        WHEN a.payment_mode = 'free' THEN 'free'
        WHEN a.payment_mode = 'agora'
             AND c.stripe_account_id IS NOT NULL
             AND c.stripe_charges_enabled THEN 'agora'
        ELSE 'external'
    END
    FROM activities a
    JOIN communities c ON c.id = a.community_id
    WHERE a.id = p_activity_id;
$$;

-- Nadie la llama desde fuera. Sus consumidores son reserve_slot,
-- promote_substitute y begin_slot_payment, que son SECURITY DEFINER y corren
-- como postgres: no necesitan GRANT. Darsela a anon o a authenticated seria un
-- salto de RLS gratis, porque esta funcion lee activities y communities sin
-- comprobar pertenencia. Mismo criterio que expire_substitute_offers.
REVOKE ALL ON FUNCTION public.activity_payment_mode(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.activity_payment_mode(uuid) FROM anon;
REVOKE ALL ON FUNCTION public.activity_payment_mode(uuid) FROM authenticated;
GRANT EXECUTE ON FUNCTION public.activity_payment_mode(uuid) TO service_role;

COMMIT;
```

- [ ] **Paso 3: Aplicar contra el proyecto de Supabase**

```bash
supabase db push
```

Esperado: la migración se aplica sin error. Si falla en el `ALTER TABLE ... CHECK`,
significa que hay una actividad con `price_cents` que el backfill no alcanzó (una
comunidad borrada, por ejemplo): investígalo, no relajes la restricción.

- [ ] **Paso 4: Comprobar el reparto real**

```bash
psql "$SUPABASE_DB_URL" -c "SELECT payment_mode, count(*) FILTER (WHERE price_cents IS NULL) AS sin_precio, count(*) AS total FROM activities GROUP BY 1 ORDER BY 1;"
```

Esperado: en la fila `free`, `sin_precio = total`; en `external` y `agora`, `sin_precio = 0`.
Cualquier otra cosa significa que la restricción no se aplicó y hay que investigarlo antes
de seguir.

- [ ] **Paso 5: Commit**

```bash
git add supabase/migrations
git commit -m "feat(pagos): modo de pago explicito por actividad"
```

---

## Fase 2 — Flujos en SQL

### Tarea 2: `settle_previous_occupant` — la regla del dinero, en un solo sitio

**Ficheros:**
- Crear: `supabase/migrations/<timestamp>_payment_mode_flows.sql`

Esta tarea escribe la primera mitad del fichero; las tareas 3, 4 y 5 añaden el resto antes
de aplicarlo. **No ejecutes `supabase db push` hasta la tarea 6.**

- [ ] **Paso 1: Crear el fichero de migración**

```bash
supabase migration new payment_mode_flows
```

- [ ] **Paso 2: Escribir la cabecera y la función**

```sql
-- ============================================================================
-- Flujos de los tres modos de pago.
--
-- En modo 'external' Agora no mueve dinero, asi que ocupar una plaza liberada es
-- INSTANTANEO: no hay Checkout que abrir. Pero el traspaso contable es el mismo
-- que en 'agora', y esa regla ya existia escrita en TypeScript
-- (supabase/functions/_shared/payments.ts). Duplicarla en SQL era pedir que un
-- dia divergieran y alguien se quedara sin su devolucion, asi que vive aqui y la
-- Edge Function la llama por RPC.
--
-- Diseno: docs/superpowers/specs/2026-08-27-modos-de-pago-design.md
-- ============================================================================

BEGIN;

-- ---------- settle_previous_occupant ----------------------------------------
-- Cierra las cuentas del ocupante anterior de una plaza liberada. No toca la
-- plaza: de eso se encarga quien la llama, porque el final es distinto en cada
-- caso (en 'agora' la plaza queda 'paid', en 'external' queda 'reserved').
--
-- No-op si no hay ningun pago en 'awaiting_substitute': asi se puede llamar
-- siempre, sin preguntar antes.

CREATE OR REPLACE FUNCTION public.settle_previous_occupant(
        p_slot_id uuid,
        p_exclude_payment_id uuid DEFAULT NULL)
    RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_prev RECORD;
    v_activity RECORD;
    v_admin RECORD;
    v_name text;
    v_amount text;
BEGIN
    FOR v_prev IN
        SELECT p.id, p.method, p.user_id, p.amount_cents, p.activity_id
        FROM payments p
        WHERE p.slot_id = p_slot_id
          AND p.status = 'awaiting_substitute'
          AND (p_exclude_payment_id IS NULL OR p.id <> p_exclude_payment_id)
        FOR UPDATE
    LOOP
        SELECT * INTO v_activity FROM activities WHERE id = v_prev.activity_id;

        -- "650" -> "6,50 €". Mismo formato que formatEuros() en Kotlin.
        v_amount := replace(to_char(v_prev.amount_cents / 100.0, 'FM999999990.00'), '.', ',')
                    || ' €';

        IF v_prev.method = 'stripe' THEN
            UPDATE payments
            SET status = 'refund_pending', refund_reason = 'substitute'
            WHERE id = v_prev.id;

            IF v_prev.user_id IS NOT NULL THEN
                INSERT INTO notifications (user_id, type, title, body, data)
                VALUES (
                    v_prev.user_id, 'payment_refunded', 'Plaza ocupada',
                    'Alguien ha ocupado tu plaza en ' || v_activity.name ||
                    '. Te devolvemos ' || v_amount || '.',
                    jsonb_build_object('activity_id', v_activity.id, 'slot_id', p_slot_id)
                );
            END IF;
        ELSE
            UPDATE payments
            SET status = 'refund_owed', refund_reason = 'substitute'
            WHERE id = v_prev.id;

            IF v_prev.user_id IS NOT NULL THEN
                INSERT INTO notifications (user_id, type, title, body, data)
                VALUES (
                    v_prev.user_id, 'refund_owed', 'Plaza ocupada',
                    'Alguien ha ocupado tu plaza en ' || v_activity.name ||
                    '. El organizador te devolvera ' || v_amount || '.',
                    jsonb_build_object('activity_id', v_activity.id, 'slot_id', p_slot_id)
                );
            END IF;

            -- Agora nunca vio ese dinero, asi que la devolucion la hace una
            -- persona. Va a TODOS los admins: quien crea las actividades no es
            -- necesariamente quien lleva el dinero, y un aviso que llega a la
            -- persona equivocada es un aviso perdido. Mismo fan-out que
            -- guest_request_received.
            SELECT coalesce(display_name, 'alguien') INTO v_name
            FROM profiles WHERE id = v_prev.user_id;

            FOR v_admin IN
                SELECT user_id FROM community_members
                WHERE community_id = v_activity.community_id AND role = 'admin'
            LOOP
                INSERT INTO notifications (user_id, type, title, body, data)
                VALUES (
                    v_admin.user_id, 'refund_owed', 'Hay que devolver un pago',
                    'Devuelvele ' || v_amount || ' a ' || coalesce(v_name, 'alguien') ||
                    ': han ocupado su plaza en ' || v_activity.name || '.',
                    jsonb_build_object(
                        'activity_id', v_activity.id,
                        'slot_id', p_slot_id,
                        'payment_id', v_prev.id
                    )
                );
            END LOOP;
        END IF;
    END LOOP;
END;
$$;

-- Solo la llaman otras funciones SECURITY DEFINER y la Edge Function con
-- service_role. Nunca un cliente: mueve estados de dinero.
REVOKE ALL ON FUNCTION public.settle_previous_occupant(uuid, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.settle_previous_occupant(uuid, uuid) FROM anon;
REVOKE ALL ON FUNCTION public.settle_previous_occupant(uuid, uuid) FROM authenticated;
GRANT EXECUTE ON FUNCTION public.settle_previous_occupant(uuid, uuid) TO service_role;
```

**Todavía no cierres el `COMMIT;`.** Lo añade la tarea 5.

- [ ] **Paso 3: Commit**

```bash
git add supabase/migrations
git commit -m "feat(pagos): cerrar las cuentas del ocupante anterior en una sola funcion"
```

---

### Tarea 3: `promote_substitute` — el modo externo promociona al instante

**Ficheros:**
- Modificar: el fichero `_payment_mode_flows.sql` de la tarea 2 (añadir al final)

La versión viva está en `supabase/migrations/20260820100000_release_and_offers.sql`. Se
copia entera y se cambia **solo** la condición de la bifurcación y el cuerpo de esa rama.
La firma no cambia: si cambiara, `CREATE OR REPLACE` crearía una función nueva en vez de
sustituir la existente.

- [ ] **Paso 1: Añadir la función al fichero de migración**

```sql
-- ---------- promote_substitute ----------------------------------------------
-- Base: la version viva de 20260820100000_release_and_offers.sql.
--
-- CAMBIO: la bifurcacion era `price_cents IS NULL` (solo las gratuitas
-- promocionaban al instante). Ahora es `modo efectivo <> 'agora'`, asi que el
-- modo externo entra tambien por la rama instantanea: no hay Checkout que abrir,
-- de modo que apalabrar la plaza 6 h no protegeria nada y solo la congelaria.
--
-- Y sobre una plaza LIBERADA esa rama tiene que hacer el traspaso completo, no
-- solo asignar dueno: el anterior deja de tenerla y hay que devolverle su dinero.

CREATE OR REPLACE FUNCTION public.promote_substitute(p_slot_id uuid, p_activity_id uuid)
    RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_sub record;
    v_slot_positions uuid[];
    v_activity RECORD;
    v_deadline timestamptz;
BEGIN
    SELECT * INTO v_activity FROM activities WHERE id = p_activity_id;

    SELECT array_agg(sp.position_id) INTO v_slot_positions
    FROM slot_positions sp WHERE sp.slot_id = p_slot_id;

    IF v_slot_positions IS NOT NULL AND array_length(v_slot_positions, 1) > 0 THEN
        SELECT * INTO v_sub FROM substitute_queue
        WHERE activity_id = p_activity_id
          AND (position_id = ANY(v_slot_positions) OR position_id IS NULL)
        ORDER BY queued_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED;
    ELSE
        SELECT * INTO v_sub FROM substitute_queue
        WHERE activity_id = p_activity_id
        ORDER BY queued_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED;
    END IF;

    IF v_sub IS NULL THEN
        UPDATE slots SET offered_to = NULL, offer_expires_at = NULL WHERE id = p_slot_id;
        RETURN FALSE;
    END IF;

    -- Gratuita o de pago externo: se asigna en el acto, sin ofertas ni ventanas.
    IF activity_payment_mode(p_activity_id) <> 'agora' THEN
        -- No-op si la plaza no venia liberada y pagada.
        PERFORM settle_previous_occupant(p_slot_id);

        -- El suplente entra SIN pagar: en externo el cobro lo marca el admin
        -- despues. Por eso 'reserved' y no 'paid'.
        UPDATE slots
        SET status = 'reserved', reserved_by = v_sub.user_id, reserved_at = now(),
            released_at = NULL, offered_to = NULL, offer_expires_at = NULL
        WHERE id = p_slot_id;

        DELETE FROM substitute_queue
        WHERE activity_id = p_activity_id AND user_id = v_sub.user_id;

        PERFORM notify_substitute_promoted(v_sub.user_id, p_activity_id, p_slot_id);
        RETURN TRUE;
    END IF;

    -- Gestionada por Agora: se apalabra. El tope en el comienzo de la actividad no
    -- es un detalle: una ventana de 6 h sobre un partido que empieza dentro de dos
    -- dejaria la plaza congelada hasta despues de jugarse.
    v_deadline := least(now() + interval '6 hours', v_activity.datetime);

    UPDATE slots
    SET offered_to = v_sub.user_id, offer_expires_at = v_deadline
    WHERE id = p_slot_id;

    -- NO se borra de la cola: sale al aceptar (cuando su pago se confirma), al
    -- rechazar, o al caducar la oferta.
    INSERT INTO notifications (user_id, type, title, body, data)
    VALUES (
        v_sub.user_id, 'substitute_offer', 'Tienes plaza',
        'Se ha liberado una plaza en ' || v_activity.name || '. Confirmala antes de que caduque.',
        jsonb_build_object('activity_id', p_activity_id, 'slot_id', p_slot_id)
    );

    RETURN TRUE;
END;
$$;
```

- [ ] **Paso 2: Commit**

```bash
git add supabase/migrations
git commit -m "feat(pagos): el modo externo promociona suplentes al instante"
```

---

### Tarea 4: `reserve_slot` — ocupar una plaza liberada en modo externo

**Ficheros:**
- Modificar: el fichero `_payment_mode_flows.sql` (añadir al final)

Esta es la tarea que arregla el callejón sin salida: hoy una plaza pagada y liberada en una
comunidad sin cobrador no la puede ocupar nadie, porque el botón "Ocupar esta plaza"
termina en `reserve_slot`, que exige `status = 'available'`.

La versión viva es la del baseline (`20260625120019_baseline.sql:1880`). Se copia entera y
se cambia la comprobación de disponibilidad y el `UPDATE` final.

- [ ] **Paso 1: Añadir la función al fichero de migración**

```sql
-- ---------- reserve_slot ----------------------------------------------------
-- Base: la version viva del baseline (20260625120019_baseline.sql:1880).
--
-- CAMBIO: antes exigia status='available' a secas. Ahora acepta tambien una
-- plaza PAGADA Y LIBERADA de otra persona cuando el modo efectivo no es 'agora':
-- ese es el boton "Ocupar esta plaza" del modo externo, que hasta ahora no tenia
-- ninguna funcion que lo atendiera y devolvia siempre "ya no esta disponible".
--
-- En 'agora' ese caso NO pasa por aqui: va a begin_slot_payment y a Checkout.

CREATE OR REPLACE FUNCTION public.reserve_slot(p_slot_id uuid) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_slot RECORD;
    v_activity RECORD;
    v_user_id UUID := auth.uid();
    v_slot_positions uuid[];
    v_first_in_queue RECORD;
    v_mode text;
    v_is_takeover boolean;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF v_slot IS NULL THEN
        RAISE EXCEPTION 'Slot not found';
    END IF;

    v_mode := activity_payment_mode(v_slot.activity_id);

    -- Relevo de una plaza liberada: sigue siendo de su dueno, pero en modo
    -- externo no hay nada que pagar, asi que cambia de manos en el acto.
    v_is_takeover := v_slot.status = 'paid'
        AND v_slot.released_at IS NOT NULL
        AND v_slot.reserved_by IS DISTINCT FROM v_user_id
        AND v_mode <> 'agora';

    IF v_slot.status <> 'available' AND NOT v_is_takeover THEN
        RETURN FALSE;
    END IF;

    -- Verify user is a member of the activity's community
    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;
    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id AND user_id = v_user_id
    ) THEN
        RAISE EXCEPTION 'User is not a member of this community';
    END IF;

    -- Check queue priority using the same position-matching logic as promote_substitute
    SELECT array_agg(sp.position_id) INTO v_slot_positions
    FROM slot_positions sp
    WHERE sp.slot_id = p_slot_id;

    IF v_slot_positions IS NOT NULL AND array_length(v_slot_positions, 1) > 0 THEN
        SELECT * INTO v_first_in_queue
        FROM substitute_queue
        WHERE activity_id = v_slot.activity_id
          AND (position_id = ANY(v_slot_positions) OR position_id IS NULL)
        ORDER BY queued_at ASC
        LIMIT 1;
    ELSE
        SELECT * INTO v_first_in_queue
        FROM substitute_queue
        WHERE activity_id = v_slot.activity_id
        ORDER BY queued_at ASC
        LIMIT 1;
    END IF;

    IF v_first_in_queue IS NOT NULL AND v_first_in_queue.user_id != v_user_id THEN
        -- Someone else has priority in the queue.
        PERFORM promote_substitute(p_slot_id, v_activity.id);
        RETURN FALSE;
    END IF;

    -- En el relevo, el anterior deja de tener la plaza y se le debe el dinero.
    IF v_is_takeover THEN
        PERFORM settle_previous_occupant(p_slot_id);
    END IF;

    -- 'reserved' tambien en el relevo: el nuevo ocupante NO ha pagado nada
    -- todavia, lo marcara el admin. Y se limpian los rastros de la liberacion.
    UPDATE slots
    SET status = 'reserved', reserved_by = v_user_id, reserved_at = now(),
        released_at = NULL, offered_to = NULL, offer_expires_at = NULL
    WHERE id = p_slot_id;

    -- Clean up ALL of this user's queue entries for this activity
    DELETE FROM substitute_queue
    WHERE activity_id = v_slot.activity_id
      AND user_id = v_user_id;

    RETURN TRUE;
END;
$$;
```

- [ ] **Paso 2: Commit**

```bash
git add supabase/migrations
git commit -m "fix(pagos): permitir ocupar una plaza liberada sin pasarela"
```

---

### Tarea 5: `unmark_slot_paid` y la guarda de `begin_slot_payment`

**Ficheros:**
- Modificar: el fichero `_payment_mode_flows.sql` (añadir al final y cerrar el `COMMIT`)

- [ ] **Paso 1: Añadir `unmark_slot_paid`**

```sql
-- ---------- unmark_slot_paid ------------------------------------------------
-- Lo simetrico de mark_slot_paid, que no existia: un clic por error era
-- irreversible.
--
-- Solo revierte cobros MANUALES. Un cobro de Stripe no se desmarca: el dinero
-- salio de verdad y la unica via de vuelta es un sustituto o cancelar la
-- actividad. Si se pudiera desmarcar, Agora estaria mintiendo sobre dinero que
-- sigue en la cuenta del admin.

CREATE OR REPLACE FUNCTION public.unmark_slot_paid(p_slot_id uuid) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_slot RECORD;
    v_activity RECORD;
    v_user_id uuid := auth.uid();
    v_offered_to uuid;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF v_slot IS NULL THEN
        RAISE EXCEPTION 'Slot not found';
    END IF;

    IF v_slot.status <> 'paid' THEN
        RETURN FALSE;
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;
    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_user_id AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can unmark slots as paid';
    END IF;

    IF EXISTS (
        SELECT 1 FROM payments
        WHERE slot_id = p_slot_id AND method = 'stripe'
          AND status IN ('succeeded', 'awaiting_substitute',
                         'refund_pending', 'refunded', 'refund_owed')
    ) THEN
        RAISE EXCEPTION 'paid_with_stripe';
    END IF;

    -- Un cobro manual es un apunte del admin, no dinero que haya pasado por
    -- Agora. Se borra la fila en vez de inventar un estado "anulado" que luego
    -- habria que filtrar en la vista previa de cancelacion y en cada consulta.
    DELETE FROM payments
    WHERE slot_id = p_slot_id AND method = 'manual'
      AND status IN ('succeeded', 'awaiting_substitute');

    v_offered_to := v_slot.offered_to;

    -- Desmarcar cancela tambien la liberacion: una plaza que ya no consta pagada
    -- no puede seguir buscando sustituto ni debiendole nada a nadie.
    UPDATE slots
    SET status = 'reserved', released_at = NULL,
        offered_to = NULL, offer_expires_at = NULL
    WHERE id = p_slot_id;

    IF v_offered_to IS NOT NULL THEN
        INSERT INTO notifications (user_id, type, title, body, data)
        VALUES (
            v_offered_to, 'slot_removed', 'Oferta cancelada',
            'La plaza que te habian ofrecido en ' || v_activity.name ||
            ' ya no esta disponible.',
            jsonb_build_object('activity_id', v_activity.id, 'slot_id', p_slot_id)
        );
    END IF;

    RETURN TRUE;
END;
$$;

GRANT EXECUTE ON FUNCTION public.unmark_slot_paid(uuid) TO authenticated;
```

- [ ] **Paso 2: Añadir la guarda de `begin_slot_payment` y cerrar la transacción**

Se copia la versión viva de `20260819190000_begin_slot_payment.sql` cambiando **solo** el
bloque que decide si se puede cobrar. Para no repetir 140 líneas idénticas, aquí va el
bloque exacto que se sustituye y su reemplazo; el resto del cuerpo se copia literal del
fichero original.

Bloque a sustituir (líneas 60-72 del fichero original: el rango llega hasta el `END IF;`
inclusive, o la función se queda sin cerrar):

```sql
    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;

    -- Las gratuitas no pasan por aqui: se reservan con reserve_slot, al instante.
    IF v_activity.price_cents IS NULL THEN
        RAISE EXCEPTION 'activity_is_free';
    END IF;

    SELECT * INTO v_community FROM communities WHERE id = v_activity.community_id;
    IF v_community.stripe_account_id IS NULL OR NOT v_community.stripe_charges_enabled THEN
        -- El cliente cae a reserve_slot y el admin cobra a mano, que es la decision
        -- para comunidades que todavia no han completado su alta.
        RAISE EXCEPTION 'payments_not_enabled';
    END IF;
```

Reemplazo:

```sql
    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;

    -- Las gratuitas no pasan por aqui: se reservan con reserve_slot, al instante.
    IF v_activity.payment_mode = 'free' THEN
        RAISE EXCEPTION 'activity_is_free';
    END IF;

    SELECT * INTO v_community FROM communities WHERE id = v_activity.community_id;

    -- Guarda unica: cubre a la vez la actividad declarada 'external' y la 'agora'
    -- cuyo cobrador se ha caido. En los dos casos el cliente cae a reserve_slot y
    -- el admin cobra a mano. Sin esto, una actividad externa podia abrir Checkout
    -- llamando a la RPC a mano.
    IF activity_payment_mode(v_slot.activity_id) <> 'agora' THEN
        RAISE EXCEPTION 'payments_not_enabled';
    END IF;
```

Al final del fichero, cerrar:

```sql
COMMIT;
```

- [ ] **Paso 3: Commit**

```bash
git add supabase/migrations
git commit -m "feat(pagos): desmarcar un cobro manual y cerrar Checkout al modo externo"
```

---

### Tarea 6: Verificar las migraciones contra un Postgres real ✅ HECHA

**Enunciado cambiado sobre la marcha.** Decía "aplicar con `supabase db push` y comprobar
allí". No: el proyecto está enlazado con producción y hay dinero real dentro. En su lugar
se reconstruye el esquema en un contenedor desechable y se prueba ahí, de modo que cuando
una persona lance el `db push` las migraciones ya se hayan ejecutado de verdad.

**Ficheros:**
- Crear: `docs/payment_modes_check.sql` ← **ese fichero es la versión buena del script**

- [x] **Paso 1: Levantar el esquema real en Docker**

Imagen `public.ecr.aws/supabase/postgres:17.6.1.104` (`supabase/config.toml` declara
`major_version = 17`), en un contenedor aparte, sin tocar el stack local ni el remoto.
Aplicar **en orden** las 17 migraciones de `supabase/migrations/`, baseline incluido.

Con esa imagen no hay que inventar nada del esquema: roles, esquemas `auth`/`extensions`/
`vault` y las extensiones vienen de fábrica. Sí hacen falta tres apaños **solo para el
andamiaje de prueba**, no para la app: añadir `auth.users.email_confirmed_at` (la imagen
trae un `auth.users` viejo y GoTrue la crea en runtime), redefinir `auth.uid()` con la
versión actual de Supabase (la de la imagen solo lee `request.jwt.claim.sub` y sin esto
todo responde `Not authenticated`), y poner `pg_hba.conf` en `trust` para conectar como
`supabase_admin`.

**Lo que esta prueba NO cubre:** RLS tal y como la ve un cliente a través de PostgREST (se
ejecuta con `SET LOCAL role=authenticated`, y las RPC en juego son todas `SECURITY
DEFINER`), nada de Stripe (las filas de `payments` se fabrican a mano), y pg_cron en marcha.

- [x] **Paso 2: Escribir y ejecutar `docs/payment_modes_check.sql`**

El script vive en el repo; no se duplica aquí para que no diverja. Cubre ocho casos: modo
efectivo sin cobrador, reservar y marcar pagado, desmarcar, liberar una plaza no pagada,
liberar una pagada, el relevo con su deuda y sus dos avisos, que una actividad externa no
abre Checkout, y la degradación de `agora` a `external` y vuelta.

**Trampa que costó una tanda de fallos en cascada:** el trigger `handle_new_community()` ya
mete a `created_by` en `community_members` como admin, así que un `INSERT` de montaje que
vuelva a meterlo revienta con `duplicate key` y, al ser un `INSERT` de varias filas, **aborta
el statement entero** y los demás miembros no llegan a existir. El script lleva
`ON CONFLICT (community_id, user_id) DO NOTHING` por eso.

- [x] **Paso 3: Cuatro escenarios que el script no cubre**

1. **Cliente viejo**: `INSERT` con `price_cents` y sin `payment_mode` → `external` sin
   cuenta de Stripe, `agora` con ella; y `UPDATE price_cents = NULL` → `free`.
2. **Prioridad de cola**: el segundo de una cola sin `position_id` llama a `reserve_slot`
   → `FALSE`. Antes de la corrección devolvía `TRUE`.
3. **Relevo con Checkout abierto**: un tercero llama a `reserve_slot` sobre una plaza
   liberada con un pago `pending` → `FALSE`, y nada se toca.
4. **Desmarcar con un pago ajeno ya reembolsado** → `TRUE`, sin `paid_with_stripe`.

Los ocho casos del script y los cuatro escenarios pasaron.

- [x] **Paso 4: Commit** (`e0d595a`)

---

### El `db push` NO va aquí

La migración es compatible hacia atrás gracias al trigger de la tarea 1, pero su guarda
`IF v_mode = 'agora' THEN RETURN FALSE` deja a `joinUnlimited` fallando en silencio hasta
que salga la app con el arreglo del paso 3 de la tarea 12. Así que el orden es **migración
primero y app justo detrás**, las dos en la misma ventana, y eso ocurre al final: antes de
la tarea 15, no aquí.


## Fase 3 — Modelo y repositorios

### Tarea 7: El enum `PaymentMode` y el modo efectivo

**Ficheros:**
- Crear: `core/model/src/commonMain/kotlin/com/app/community/core/model/PaymentMode.kt`
- Crear: `core/model/src/commonTest/kotlin/com/app/community/core/model/PaymentModeTest.kt`
- Modificar: `core/model/src/commonMain/kotlin/com/app/community/core/model/Activity.kt`

- [ ] **Paso 1: Escribir el test que falla**

Crear `core/model/src/commonTest/kotlin/com/app/community/core/model/PaymentModeTest.kt`:

```kotlin
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
```

- [ ] **Paso 2: Ejecutar el test para verificar que falla**

```bash
./gradlew :core:model:allTests
```

Esperado: FALLA con `Unresolved reference: PaymentMode`.

- [ ] **Paso 3: Escribir `PaymentMode.kt`**

```kotlin
package com.app.community.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Como se cobra una actividad. Lo elige quien la crea y no cambia despues.
 *
 * El serializer tolerante es load-bearing, por la misma razon que el de SlotStatus: con un
 * enum cerrado, un modo que el servidor empiece a emitir antes de que la app se actualice
 * tumbaria la lista ENTERA de actividades, no solo la fila mala.
 *
 * Un modo desconocido se lee como EXTERNAL a proposito: es el unico repliegue seguro.
 * Con FREE se regalarian plazas de pago; con AGORA se abriria un Checkout que el servidor
 * va a rechazar. EXTERNAL reserva al instante y deja el cobro en manos del admin, que es
 * como se comportaba la app entera antes de que existiera Stripe.
 */
@Serializable(with = PaymentModeSerializer::class)
enum class PaymentMode(val wire: String) {
    /** Sin coste. Reservar es instantaneo. */
    FREE("free"),

    /** Hay importe, pero Agora no mueve el dinero: lo cobra el admin por su cuenta. */
    EXTERNAL("external"),

    /** Reservar sale a Stripe Checkout. Requiere cobrador dado de alta en la comunidad. */
    AGORA("agora"),
}

object PaymentModeSerializer : KSerializer<PaymentMode> {
    private val byWire = PaymentMode.entries.associateBy { it.wire }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PaymentMode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PaymentMode) = encoder.encodeString(value.wire)

    override fun deserialize(decoder: Decoder): PaymentMode =
        byWire[decoder.decodeString()] ?: PaymentMode.EXTERNAL
}

/**
 * El modo que se ejecuta de verdad. `paymentMode` es la intencion del admin; si la
 * comunidad pierde la capacidad de cobrar, una actividad 'agora' se comporta como externa
 * hasta que la recupere. Espejo exacto de la funcion SQL activity_payment_mode().
 */
fun Activity.effectiveMode(communityChargesEnabled: Boolean): PaymentMode =
    when (paymentMode) {
        PaymentMode.FREE -> PaymentMode.FREE
        PaymentMode.EXTERNAL -> PaymentMode.EXTERNAL
        PaymentMode.AGORA ->
            if (communityChargesEnabled) PaymentMode.AGORA else PaymentMode.EXTERNAL
    }
```

- [ ] **Paso 4: Añadir el campo a `Activity`**

En `core/model/src/commonMain/kotlin/com/app/community/core/model/Activity.kt`, dentro de
`data class Activity`, justo después de `priceCents`:

```kotlin
    /** Como se cobra. Fijo desde que se crea la actividad. */
    @SerialName("payment_mode") val paymentMode: PaymentMode = PaymentMode.FREE,
```

Y sustituir el `isPaid` existente por:

```kotlin
    /** Tiene importe, sea quien sea el que lo cobra. */
    val isPaid: Boolean get() = paymentMode != PaymentMode.FREE
```

- [ ] **Paso 5: Ejecutar los tests**

```bash
./gradlew :core:model:allTests
```

Esperado: PASA, incluidos los tests que ya existían en `SlotStatusTest` y `MoneyTest`.

- [ ] **Paso 6: Commit**

```bash
git add core/model
git commit -m "feat(pagos): modelo de los tres modos de pago"
```

---

### Tarea 8: `Community.stripeChargesEnabled` y `SlotRepository.unmarkSlotPaid`

**Ficheros:**
- Modificar: `core/model/src/commonMain/kotlin/com/app/community/core/model/Community.kt`
- Modificar: `core/data/src/commonMain/kotlin/com/app/community/core/data/repository/SlotRepository.kt`
- Modificar: `core/data/src/commonMain/kotlin/com/app/community/core/data/repository/ActivityRepository.kt`

- [ ] **Paso 1: Añadir el campo a `Community`**

En `data class Community`, después de `parentId`:

```kotlin
    /**
     * Esta comunidad puede cobrar por Stripe. Lo mantiene al dia el webhook account.updated.
     * Decide si una actividad 'agora' se ejecuta como tal o degrada a externa.
     */
    @SerialName("stripe_charges_enabled") val stripeChargesEnabled: Boolean = false,
```

`getCommunity()` hace `select { filter { ... } }`, que es `select(*)`, así que la columna
llega sin tocar la consulta.

- [ ] **Paso 2: Añadir `unmarkSlotPaid` al repositorio**

En `SlotRepository.kt`, justo después de `markSlotPaid`:

```kotlin
    /**
     * Revierte un cobro MANUAL. El servidor rechaza los de Stripe con 'paid_with_stripe':
     * ese dinero salio de verdad y la unica via de vuelta es un sustituto o cancelar.
     */
    suspend fun unmarkSlotPaid(slotId: String): AppResult<Boolean> =
        safeCall {
            val result = postgrest.rpc(
                function = "unmark_slot_paid",
                parameters = buildJsonObject {
                    put("p_slot_id", slotId)
                },
            ).data
            result.trim().toBoolean()
        }
```

- [ ] **Paso 3: Pasar el modo en `createActivity`**

En `ActivityRepository.kt`, en la firma de `createActivity`, sustituir el parámetro
`priceCents: Int?,` por estas dos líneas:

```kotlin
        priceCents: Int?,
        paymentMode: PaymentMode,
```

Y en el `buildJsonObject` del insert, después de `priceCents?.let { put("price_cents", it) }`:

```kotlin
                    put("payment_mode", paymentMode.wire)
```

Añadir el import `com.app.community.core.model.PaymentMode` al principio del fichero.

`updateActivity` **no** se toca: no debe poder cambiar `payment_mode`.

- [ ] **Paso 4: Compilar**

```bash
./gradlew :core:data:compileDebugKotlinAndroid
```

Esperado: FALLA en `CreateActivityScreenModel.kt` por el parámetro nuevo. Es lo previsto;
lo arregla la tarea 9.

- [ ] **Paso 5: Commit**

```bash
git add core/model core/data
git commit -m "feat(pagos): repositorios al dia con el modo de pago"
```

---

## Fase 4 — Crear y editar

### Tarea 9: `ActivityPriceField` con tres tarjetas

**Ficheros:**
- Modificar: `feature/activity/.../ActivityPriceField.kt`
- Modificar: `feature/activity/src/commonMain/composeResources/values/strings.xml`
- Modificar: `feature/activity/src/commonMain/composeResources/values-es/strings.xml`

- [ ] **Paso 1: Añadir las cadenas en castellano**

En `values-es/strings.xml`, sustituir la línea `price_paid` y añadir el resto detrás de
`price_help_legacy`:

```xml
    <string name="price_external">Pago externo</string>
    <string name="price_agora">Pago con Agora</string>
    <string name="price_how_to_pay_label">Cómo se paga</string>
    <string name="price_how_to_pay_placeholder">Bizum al 601386047 con tu nombre y la fecha como concepto</string>
    <string name="price_agora_unavailable">Para cobrar por Agora, configura antes los cobros de la comunidad en sus ajustes.</string>
    <string name="price_mode_locked">El modo de pago se elige al crear la actividad y no se puede cambiar.</string>
```

Borrar `price_paid` y `price_help_legacy`: ya no los usa nadie.

- [ ] **Paso 2: Añadir las mismas cadenas en inglés**

En `values/strings.xml`:

```xml
    <string name="price_external">External payment</string>
    <string name="price_agora">Pay with Agora</string>
    <string name="price_how_to_pay_label">How to pay</string>
    <string name="price_how_to_pay_placeholder">Bizum to 601386047 with your name and the date</string>
    <string name="price_agora_unavailable">To charge through Agora, set up the community payouts first in its settings.</string>
    <string name="price_mode_locked">The payment mode is chosen when the activity is created and cannot be changed.</string>
```

Borrar también aquí `price_paid` y `price_help_legacy`.

- [ ] **Paso 3: Reescribir `ActivityPriceField.kt`**

```kotlin
package com.app.community.feature.activity.presentation

import agora.feature.activity.generated.resources.Res
import agora.feature.activity.generated.resources.price_agora
import agora.feature.activity.generated.resources.price_agora_unavailable
import agora.feature.activity.generated.resources.price_external
import agora.feature.activity.generated.resources.price_free
import agora.feature.activity.generated.resources.price_how_to_pay_label
import agora.feature.activity.generated.resources.price_how_to_pay_placeholder
import agora.feature.activity.generated.resources.price_invalid
import agora.feature.activity.generated.resources.price_label
import agora.feature.activity.generated.resources.price_mode_locked
import agora.feature.activity.generated.resources.price_per_slot_label
import agora.feature.activity.generated.resources.price_placeholder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.app.community.core.model.PaymentMode
import com.app.community.core.model.parseEurosToCents
import com.app.community.core.ui.theme.AgoraSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * Coste de la actividad: sin pago, pago externo o pago gestionado por Agora.
 *
 * "Pago con Agora" solo aparece si la comunidad tiene cobrador dado de alta. Ensenarla
 * apagada seria peor: el admin la pulsaria, no pasaria nada y no sabria por que.
 *
 * Lo comparten crear y editar para que las dos pantallas validen igual. En editar el modo
 * va en solo lectura (`readOnly`): se decide al crear y no cambia, porque cambiarlo con
 * pagos hechos dejaria cobros de Stripe vivos en una actividad que ya no los usa.
 */
@Composable
fun ActivityPriceField(
    mode: PaymentMode,
    priceInput: String,
    howToPayInput: String,
    canUseAgoraPayments: Boolean,
    onModeChange: (PaymentMode) -> Unit,
    onPriceInputChange: (String) -> Unit,
    onHowToPayInputChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
    ) {
        Text(
            text = stringResource(Res.string.price_label),
            style = MaterialTheme.typography.labelLarge,
        )

        if (readOnly) {
            Text(
                text = when (mode) {
                    PaymentMode.FREE -> stringResource(Res.string.price_free)
                    PaymentMode.EXTERNAL -> stringResource(Res.string.price_external)
                    PaymentMode.AGORA -> stringResource(Res.string.price_agora)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(Res.string.price_mode_locked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
            ) {
                SlotModeCard(
                    label = stringResource(Res.string.price_free),
                    isSelected = mode == PaymentMode.FREE,
                    onClick = { onModeChange(PaymentMode.FREE) },
                    modifier = Modifier.weight(1f),
                )
                SlotModeCard(
                    label = stringResource(Res.string.price_external),
                    isSelected = mode == PaymentMode.EXTERNAL,
                    onClick = { onModeChange(PaymentMode.EXTERNAL) },
                    modifier = Modifier.weight(1f),
                )
                if (canUseAgoraPayments) {
                    SlotModeCard(
                        label = stringResource(Res.string.price_agora),
                        isSelected = mode == PaymentMode.AGORA,
                        onClick = { onModeChange(PaymentMode.AGORA) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (!canUseAgoraPayments) {
                Text(
                    text = stringResource(Res.string.price_agora_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (mode != PaymentMode.FREE) {
            // Solo se marca en rojo cuando hay algo escrito: un campo vacio recien
            // abierto no es un error todavia, es que aun no has escrito.
            val isInvalid = priceInput.isNotBlank() && parseEurosToCents(priceInput) == null

            OutlinedTextField(
                value = priceInput,
                onValueChange = onPriceInputChange,
                label = { Text(stringResource(Res.string.price_per_slot_label)) },
                placeholder = { Text(stringResource(Res.string.price_placeholder)) },
                suffix = { Text("€") },
                singleLine = true,
                isError = isInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = if (isInvalid) {
                    { Text(stringResource(Res.string.price_invalid)) }
                } else {
                    null
                },
                modifier = Modifier.width(200.dp),
            )
        }

        // Solo en externo: en 'agora' el usuario paga en Checkout y no hay nada que
        // explicarle, y en 'free' no hay nada que cobrar.
        if (mode == PaymentMode.EXTERNAL) {
            OutlinedTextField(
                value = howToPayInput,
                onValueChange = onHowToPayInputChange,
                label = { Text(stringResource(Res.string.price_how_to_pay_label)) },
                placeholder = { Text(stringResource(Res.string.price_how_to_pay_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```

- [ ] **Paso 4: Commit**

```bash
git add feature/activity
git commit -m "feat(pagos): selector de los tres modos de pago"
```

---

### Tarea 10: Crear actividad

**Ficheros:**
- Modificar: `feature/activity/.../CreateActivityScreenModel.kt`
- Modificar: `feature/activity/.../CreateActivityScreen.kt:168-173`
- Modificar: `composeApp/src/commonMain/kotlin/com/app/community/di/AppModule.kt:96-102`

- [ ] **Paso 1: Cambiar el estado y el constructor del screen model**

En `CreateActivityUiState`, sustituir las dos propiedades de pago:

```kotlin
    /** Como se cobra esta actividad. Se fija al crearla. */
    val paymentMode: PaymentMode = PaymentMode.FREE,
    /** Texto crudo del campo de importe: "6,50". Se valida con parseEurosToCents. */
    val priceInput: String = "",
    /** Instrucciones de cobro del modo externo. Va a activities.cost_description. */
    val howToPayInput: String = "",
    /** La comunidad tiene cobrador: sin esto no se ofrece el modo Agora. */
    val canUseAgoraPayments: Boolean = false,
```

Añadir el import `com.app.community.core.model.PaymentMode` y
`com.app.community.core.data.repository.CommunityRepository`.

En el constructor de la clase, añadir tras `slotTemplateRepository`:

```kotlin
    private val communityRepository: CommunityRepository,
```

En `init`, tras `loadTemplates()`:

```kotlin
        loadPaymentCapability()
```

Y el método, junto a `loadTemplates()`:

```kotlin
    /**
     * Si la comunidad no puede cobrar por Stripe, el modo Agora ni siquiera se ofrece.
     * Ante un fallo de red se deja en false: mejor no ofrecer un modo que luego rechaza
     * el servidor que ofrecerlo y dejar al admin con una actividad que no cobra.
     */
    private fun loadPaymentCapability() {
        screenModelScope.launch {
            val enabled = communityRepository.getCommunity(communityId)
                .getOrNull()?.stripeChargesEnabled ?: false
            _state.update { it.copy(canUseAgoraPayments = enabled) }
        }
    }
```

- [ ] **Paso 2: Cambiar los callbacks**

Sustituir `onIsPaidChange` por:

```kotlin
    fun onPaymentModeChange(value: PaymentMode) = _state.update { it.copy(paymentMode = value) }
    fun onHowToPayInputChange(value: String) = _state.update { it.copy(howToPayInput = value) }
```

Dejar `onPriceInputChange` como está.

- [ ] **Paso 3: Cambiar la validación y el guardado**

En `save()`, sustituir el bloque

```kotlin
        val priceCents = if (s.isPaid) parseEurosToCents(s.priceInput) else null
        if (s.isPaid && priceCents == null) {
            _state.update { it.copy(status = CreateActivityStatus.Error("Introduce un importe válido, por ejemplo 6,50")) }
            return
        }
```

por

```kotlin
        // Defensa por si el estado llega con Agora sin cobrador: el selector no lo
        // ofrece, pero el estado sobrevive a que la comunidad pierda el alta mientras
        // la pantalla esta abierta.
        if (s.paymentMode == PaymentMode.AGORA && !s.canUseAgoraPayments) {
            _state.update { it.copy(status = CreateActivityStatus.Error("Esta comunidad todavía no puede cobrar por Agora")) }
            return
        }

        val priceCents =
            if (s.paymentMode != PaymentMode.FREE) parseEurosToCents(s.priceInput) else null
        if (s.paymentMode != PaymentMode.FREE && priceCents == null) {
            _state.update { it.copy(status = CreateActivityStatus.Error("Introduce un importe válido, por ejemplo 6,50")) }
            return
        }
```

Y en la llamada a `activityRepository.createActivity(...)`, sustituir las dos líneas
`costDescription = null,` y `priceCents = priceCents,` por:

```kotlin
                costDescription = if (s.paymentMode == PaymentMode.EXTERNAL) {
                    s.howToPayInput.ifBlank { null }
                } else null,
                priceCents = priceCents,
                paymentMode = s.paymentMode,
```

- [ ] **Paso 4: Cambiar la llamada en la pantalla**

En `CreateActivityScreen.kt:168`, sustituir la llamada entera:

```kotlin
                ActivityPriceField(
                    mode = state.paymentMode,
                    priceInput = state.priceInput,
                    howToPayInput = state.howToPayInput,
                    canUseAgoraPayments = state.canUseAgoraPayments,
                    onModeChange = screenModel::onPaymentModeChange,
                    onPriceInputChange = screenModel::onPriceInputChange,
                    onHowToPayInputChange = screenModel::onHowToPayInputChange,
                )
```

- [ ] **Paso 5: Actualizar Koin**

En `AppModule.kt:96`, dentro de `CreateActivityScreenModel(...)`, añadir tras
`slotTemplateRepository = get(),`:

```kotlin
            communityRepository = get(),
```

- [ ] **Paso 6: Compilar**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Esperado: FALLA solo en `EditActivityScreenModel.kt` / `EditActivityScreen.kt` por la firma
nueva de `ActivityPriceField`. Lo arregla la tarea 11.

- [ ] **Paso 7: Commit**

```bash
git add feature/activity composeApp
git commit -m "feat(pagos): elegir modo de pago al crear una actividad"
```

---

### Tarea 11: Editar actividad

**Ficheros:**
- Modificar: `feature/activity/.../EditActivityScreenModel.kt`
- Modificar: `feature/activity/.../EditActivityScreen.kt:146-151`

- [ ] **Paso 1: Cambiar el estado**

En `EditActivityUiState`, sustituir `isPaid`, `priceInput` y `legacyCostDescription` por:

```kotlin
    /** Solo lectura: el modo se fija al crear la actividad. */
    val paymentMode: PaymentMode = PaymentMode.FREE,
    val priceInput: String = "",
    val howToPayInput: String = "",
```

Añadir el import `com.app.community.core.model.PaymentMode`.

- [ ] **Paso 2: Cambiar la carga**

En el bloque que rellena el estado desde la actividad (líneas 76-82), sustituir las
asignaciones de pago por:

```kotlin
                        paymentMode = activity.paymentMode,
                        priceInput = activity.priceCents
                            ?.let { formatEuros(it).removeSuffix(" €") } ?: "",
                        howToPayInput = activity.costDescription.orEmpty(),
```

- [ ] **Paso 3: Cambiar los callbacks**

Sustituir `onIsPaidChange` por:

```kotlin
    fun onHowToPayInputChange(value: String) = _state.update { it.copy(howToPayInput = value) }
```

El modo no tiene callback: es de solo lectura.

- [ ] **Paso 4: Cambiar la validación y el guardado**

Sustituir

```kotlin
        val priceCents = if (s.isPaid) parseEurosToCents(s.priceInput) else null
        if (s.isPaid && priceCents == null) {
```

por

```kotlin
        val priceCents =
            if (s.paymentMode != PaymentMode.FREE) parseEurosToCents(s.priceInput) else null
        // Una actividad de pago no puede quedarse sin importe: la restriccion
        // activities_mode_matches_price lo rechazaria con un error ilegible.
        if (s.paymentMode != PaymentMode.FREE && priceCents == null) {
```

Y en la llamada a `activityRepository.updateActivity(...)`, sustituir la línea de
`costDescription` por:

```kotlin
                // Nunca se borra al editar. En 'external' este es el campo "como se
                // paga"; en 'free' puede ser el texto de coste antiguo de una actividad
                // anterior a Stripe, que el detalle sigue enseñando. Editar el nombre no
                // puede hacerlo desaparecer en silencio.
                costDescription = s.howToPayInput.ifBlank { null },
```

Ojo a la asimetría con la tarea 10: al **crear** sí se escribe solo en `EXTERNAL`, porque
una actividad nueva no tiene texto heredado que conservar.

- [ ] **Paso 5: Cambiar la llamada en la pantalla**

En `EditActivityScreen.kt:146`:

```kotlin
                    ActivityPriceField(
                        mode = state.paymentMode,
                        priceInput = state.priceInput,
                        howToPayInput = state.howToPayInput,
                        canUseAgoraPayments = false,
                        onModeChange = {},
                        onPriceInputChange = screenModel::onPriceInputChange,
                        onHowToPayInputChange = screenModel::onHowToPayInputChange,
                        readOnly = true,
                    )
```

- [ ] **Paso 6: Compilar**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinWasmJs
```

Esperado: compila sin errores en los dos targets.

- [ ] **Paso 7: Commit**

```bash
git add feature/activity
git commit -m "feat(pagos): el modo de pago no se cambia al editar"
```

---

## Fase 5 — Detalle de la actividad

### Tarea 12: Reservar según el modo, y desmarcar el pago

**Ficheros:**
- Modificar: `feature/activity/.../ActivityDetailScreenModel.kt`

- [ ] **Paso 1: Guardar el modo efectivo al cargar**

En `ActivityDetailScreenModel`, junto a `isPublicCommunity`, añadir:

```kotlin
    /** Modo que se ejecuta de verdad: 'agora' degrada a externo si la comunidad no cobra. */
    private var effectiveMode: PaymentMode = PaymentMode.FREE
```

Añadir los imports `com.app.community.core.model.PaymentMode` y
`com.app.community.core.model.effectiveMode`.

En `load()`, sustituir el bloque que resuelve `isPublicCommunity` por:

```kotlin
            // Comunidad pública → habilita compartir/invitados; carga la cola FIFO si soy admin
            val community = communityRepository.getCommunity(activity.communityId).getOrNull()
            isPublicCommunity = community?.visibility?.let { it != CommunityVisibility.PRIVATE } ?: false
            effectiveMode = activity.effectiveMode(community?.stripeChargesEnabled ?: false)
```

- [ ] **Paso 2: Reservar por modo**

Sustituir el cuerpo de `reserveSlot` por:

```kotlin
    fun reserveSlot(slotId: String) {
        // En externo no hay nada que cobrar por aqui: reservar es instantaneo, igual que
        // en las gratuitas. Salir a Checkout para que el servidor lo rechace era un viaje
        // de red de mas en la accion mas usada de la app.
        if (effectiveMode != PaymentMode.AGORA) {
            reserveFree(slotId)
            return
        }
        screenModelScope.launch {
            paymentRepository.createCheckout(slotId)
                .onSuccess { link -> _checkoutUrl.value = link.url }
                .onError { msg, _ ->
                    when (PaymentError.from(msg)) {
                        // El cobrador se ha caido entre que se cargo la pantalla y el
                        // clic. Se reserva como en externo y el admin cobra a mano.
                        PaymentError.PAYMENTS_NOT_ENABLED,
                        PaymentError.ACTIVITY_IS_FREE -> reserveFree(slotId)

                        PaymentError.SLOT_BEING_PAID ->
                            _actionMessage.value = "Alguien está pagando esta plaza ahora mismo"
                        PaymentError.SLOT_NOT_CLAIMABLE ->
                            _actionMessage.value = "La plaza ya no está disponible"
                        PaymentError.SLOT_OFFERED_TO_SOMEONE_ELSE ->
                            _actionMessage.value = "La plaza está reservada para un suplente"
                        PaymentError.QUEUE_PRIORITY ->
                            _actionMessage.value = "Hay alguien por delante en la cola"
                        PaymentError.NOT_A_MEMBER ->
                            _actionMessage.value = "No eres miembro de esta comunidad"
                        PaymentError.UNKNOWN ->
                            _actionMessage.value = "Error: $msg"
                    }
                }
        }
    }
```

- [ ] **Paso 3: Arreglar `joinUnlimited`, que se salta el cobro entero**

`joinUnlimited()` crea una plaza y llama al **repositorio** directamente, sin mirar el
precio ni el modo. Es un agujero que ya existe en producción: en una actividad de pago de
aforo ilimitado, "Apuntarme" reserva gratis y nunca sale a Checkout. La guarda
`IF v_mode = 'agora' THEN RETURN FALSE` que la migración añade a `reserve_slot` lo cierra
en el servidor, pero sin este cambio el cliente se queda fallando en silencio: plaza
huérfana creada y ni un mensaje.

Sustituir el cuerpo entero de `joinUnlimited` por:

```kotlin
    fun joinUnlimited() {
        screenModelScope.launch {
            // For unlimited mode, create a new slot and reserve it
            slotRepository.createSlots(activityId, 1)
                .onSuccess {
                    // Reload to get the new slot, then reserve it
                    val slots = slotRepository.getSlots(activityId).getOrNull() ?: return@onSuccess
                    val availableSlot = slots.lastOrNull { it.isAvailable }
                    if (availableSlot != null) {
                        // Por reserveSlot del modelo, NO por el repositorio: en modo agora
                        // hay que salir a Checkout. Llamar al RPC a pelo era apuntarse
                        // gratis a una actividad de pago de aforo ilimitado, y el servidor
                        // ahora lo rechaza sin que el usuario vea nada.
                        reserveSlot(availableSlot.id)
                    } else {
                        load()
                    }
                }
                .onError { msg, _ ->
                    _actionMessage.value = "Error: $msg"
                }
        }
    }
```

`reserveSlot` ya se encarga del `load()` en los caminos gratuito y externo, y en `agora`
deja la URL de Checkout puesta, así que el `load()` incondicional de antes sobra.

- [ ] **Paso 4: Añadir `unmarkSlotPaid`**

Justo después de `markSlotPaid`:

```kotlin
    fun unmarkSlotPaid(slotId: String) {
        screenModelScope.launch {
            slotRepository.unmarkSlotPaid(slotId)
                .onSuccess { success ->
                    _actionMessage.value =
                        if (success) "Pago desmarcado" else "Esta plaza no consta como pagada"
                    load()
                }
                .onError { msg, _ ->
                    // El servidor es la autoridad: la pantalla no sabe si una plaza se
                    // pago por Stripe sin cargar los pagos de cada una, asi que ofrece el
                    // boton y traduce el rechazo.
                    _actionMessage.value = if (msg.contains("paid_with_stripe")) {
                        "Esta plaza se pagó por Stripe: no se puede desmarcar"
                    } else {
                        "Error: $msg"
                    }
                }
        }
    }
```

- [ ] **Paso 5: Compilar**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Esperado: compila.

- [ ] **Paso 6: Commit**

```bash
git add feature/activity
git commit -m "feat(pagos): reservar segun el modo y desmarcar cobros manuales"
```

---

### Tarea 13: El botón de desmarcar y las instrucciones de pago

**Ficheros:**
- Modificar: `feature/activity/.../ActivityDetailScreen.kt`
- Modificar: `feature/activity/src/commonMain/composeResources/values{,-es}/strings.xml`

El botón "Ocupar esta plaza" del modo externo **no necesita cambios**: ya se pinta para
`slot.isAwaitingSubstitute`, llama a `onReserve` y ahora el servidor lo atiende.

- [ ] **Paso 1: Añadir las cadenas**

En `values-es/strings.xml`:

```xml
    <string name="slot_unmark_paid">Desmarcar pago</string>
```

En `values/strings.xml`:

```xml
    <string name="slot_unmark_paid">Unmark payment</string>
```

- [ ] **Paso 2: Mostrar el importe y las instrucciones juntos**

En `ActivityDetailScreen.kt:243`, sustituir

```kotlin
                    (activity.priceCents?.let { formatEuros(it) } ?: activity.costDescription)?.let { cost ->
```

por

```kotlin
                    // El importe y el "como se paga" son dos cosas distintas en el modo
                    // externo: 6,50 € no le dice a nadie a que numero hacer el Bizum.
                    val cost = listOfNotNull(
                        activity.priceCents?.let { formatEuros(it) },
                        activity.costDescription,
                    ).joinToString(" · ").ifBlank { null }
                    cost?.let { cost ->
```

- [ ] **Paso 3: Añadir la opción al menú de la plaza**

En `SlotActions`, en la rama `isAdmin && slot.isAdminReleasable`, el `SlotOverflowMenu` ya
existe para plazas `RESERVED`. Una plaza `PAID` con dueño no entra por ahí
(`isAdminReleasable` es `reservedBy == null || status == RESERVED`), así que hay que añadir
una rama nueva **justo antes** de `isAdmin && slot.isAdminReleasable`:

```kotlin
        // Plaza pagada de otra persona: el admin puede deshacer el marcado. El servidor
        // rechaza los cobros de Stripe; aqui no se puede saber sin cargar los pagos de
        // cada plaza, asi que se ofrece y se traduce el rechazo.
        isAdmin && hasCost && slot.status == SlotStatus.PAID -> {
            SlotOverflowMenu(slotLabel) { dismiss ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.slot_unmark_paid)) },
                    onClick = { dismiss(); onUnmarkPaid() },
                )
            }
        }
```

Añadir el parámetro `onUnmarkPaid: () -> Unit,` en tres sitios, justo detrás de
`onMarkPaid` en cada uno:

1. La firma de `SlotActions` (`ActivityDetailScreen.kt:888`).
2. La firma de `SlotCard` (`ActivityDetailScreen.kt:721`), que es quien la envuelve.
3. La llamada a `SlotActions` dentro de `SlotCard` (`ActivityDetailScreen.kt:856`), pasando
   `onUnmarkPaid = onUnmarkPaid,`.

Y el import `agora.feature.activity.generated.resources.slot_unmark_paid`.

- [ ] **Paso 4: Pasar el callback desde los dos puntos de llamada**

En las dos llamadas a `SlotCard` (líneas 409 y 458), junto a `onMarkPaid = ...`, añadir:

```kotlin
                    onUnmarkPaid = { screenModel.unmarkSlotPaid(slotWithProfile.slot.id) },
```

- [ ] **Paso 5: Compilar los dos targets**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinWasmJs
```

Esperado: compila sin errores.

- [ ] **Paso 6: Commit**

```bash
git add feature/activity
git commit -m "feat(pagos): desmarcar el pago desde el detalle de la actividad"
```

---

## Fase 6 — Edge Function y despliegue

### Tarea 14: La Edge Function delega el traspaso en la RPC

**Ficheros:**
- Modificar: `supabase/functions/_shared/payments.ts:56-99`

- [ ] **Paso 1: Sustituir el bucle por la llamada a la RPC**

En `applyPaymentSucceeded`, borrar el bloque que empieza en
`// Quien ocupaba la plaza antes...` (la consulta `previous`) y el `for (const prev of
previous ?? [])` entero, dejando el `UPDATE` de la plaza. El resultado:

```ts
  const payment = updated[0] as PaymentRow;
  if (!payment.slot_id) return "applied";

  await supabase
    .from("slots")
    .update({
      status: "paid",
      reserved_by: payment.user_id,
      reserved_at: new Date().toISOString(),
      hold_expires_at: null,
      released_at: null,
      offered_to: null,
      offer_expires_at: null,
    })
    .eq("id", payment.slot_id);

  // Quien paga deja de ser suplente de esta actividad.
  if (payment.user_id) {
    await supabase
      .from("substitute_queue")
      .delete()
      .eq("activity_id", payment.activity_id)
      .eq("user_id", payment.user_id);
  }

  // Cerrar las cuentas del ocupante anterior, si esta plaza venia liberada. La regla
  // vive en SQL porque el modo externo la necesita tambien, y dos copias de la regla
  // del dinero acaban divergiendo. No-op si no habia nadie esperando sustituto.
  await supabase.rpc("settle_previous_occupant", {
    p_slot_id: payment.slot_id,
    p_exclude_payment_id: paymentId,
  });

  if (payment.user_id) {
    await notify(supabase, payment.user_id, "payment_confirmed", "Pago confirmado", {
      activity_id: payment.activity_id,
    });
  }

  return "applied";
}
```

- [ ] **Paso 2: Ejecutar los tests de las funciones**

```bash
deno test supabase/functions/_shared/payments_test.ts
```

Esperado: PASA. `refundStatusToPayment` no cambia; el test solo confirma que el fichero
sigue compilando.

- [ ] **Paso 3: Desplegar**

```bash
supabase functions deploy stripe-webhook stripe-checkout stripe-sweep
```

Esperado: las tres despliegan sin error.

- [ ] **Paso 4: Commit**

```bash
git add supabase/functions
git commit -m "refactor(pagos): una sola regla para el traspaso de una plaza liberada"
```

---

### Tarea 15: Comprobación manual de punta a punta

**Ficheros:** ninguno. Es la red de seguridad antes de publicar.

- [ ] **Paso 1: Modo externo, ciclo completo**

En una comunidad **sin** cobrador de Stripe:

1. Crear una actividad con "Pago externo", 6,50 €, "Bizum al 601386047".
2. Comprobar que el detalle muestra `6,50 € · Bizum al 601386047`.
3. Reservar con otra cuenta: la plaza queda reservada al instante, sin pantalla de pago.
4. Como admin, marcar pagada. Desmarcar. Volver a marcar.

- [ ] **Paso 2: Modo externo, liberar y ocupar**

5. Con la cuenta que tiene la plaza pagada, liberarla. Comprobar que **sigue apareciendo
   como suya** y marcada como liberada.
6. Con una tercera cuenta, pulsar "Ocupar esta plaza". Comprobar que la ocupa al instante.
7. Comprobar las tres notificaciones: la anterior ocupante recibe "Plaza ocupada", **todos
   los admins** reciben "Devuélvele 6,50 € a …", y no hay ningún reembolso automático.

- [ ] **Paso 3: Modo Agora sigue intacto**

En una comunidad **con** cobrador:

8. Crear una actividad "Pago con Agora". Reservar: sale a Checkout y vuelve confirmada.
9. Intentar "Desmarcar pago" sobre esa plaza: tiene que salir "Esta plaza se pagó por
   Stripe: no se puede desmarcar".

- [ ] **Paso 4: Degradación**

10. En el panel de Supabase, poner `stripe_charges_enabled = false` en esa comunidad.
11. Reservar una plaza de la actividad "Pago con Agora": tiene que reservarse al instante,
    sin Checkout.
12. Devolver `stripe_charges_enabled = true` y comprobar que vuelve a salir a Checkout.

- [ ] **Paso 5: El selector respeta el alta**

13. En la comunidad sin cobrador, abrir "Crear actividad": solo salen dos tarjetas y el
    aviso de que hay que configurar los cobros.
14. Abrir "Editar" sobre cualquier actividad: el modo sale como texto, no como tarjetas.

- [ ] **Paso 6: Desplegar la web**

```bash
./gradlew :composeApp:syncWebApp
```

Después, `wrangler deploy` desde `web/`. El build de wasm de producción tarda del orden de
una hora: cuéntalo y no lo encadenes con otra cosa.

- [ ] **Paso 7: Commit final**

```bash
git add docs
git commit -m "docs(pagos): comprobacion manual de los tres modos"
```
