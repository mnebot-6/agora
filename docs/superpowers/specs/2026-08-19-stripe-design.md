# Pagos con Stripe — diseño

Fecha: **2026-08-19**. Estado: **aprobado por el usuario** el 2026-08-19.
Origen: `docs/handoff-stripe.md` (fase 1 cerrada el 2026-08-19).

## Motivación

Agora no tiene sistema de pagos. Hoy el coste de una actividad es una columna de
texto libre (`activities.cost_description`, placeholder literal
`"Ej: Bizum de 6.5 euros por persona"`) y el cobro es un check manual que el
admin marca plaza por plaza (`slots.status = 'paid'`, RPC `mark_slot_paid`). No
hay importe, ni moneda, ni registro de quién pagó qué, ni reembolsos.

Este diseño **introduce el primer sistema de cobro**, no sustituye a ninguno. No
hay datos que migrar.

## Decisiones de negocio (ya cerradas, no se reabren)

Vienen del handoff. Se listan para poder leer el diseño sin saltar de documento.

| Decisión | Valor |
|---|---|
| Modelo | **Stripe Connect**: el admin cobra a sus miembros a través de Agora |
| Comisión de Agora | **Configurable, arranca a 0%** (no hay entidad legal detrás de Agora) |
| Unidad de cobro | **Por actividad**, precio por actividad. Sin cuotas periódicas |
| Moneda | **Solo euros**, y sin columna de moneda. Sin facturas ni recibos fiscales |
| Comisión de Stripe | La asume **quien cobra** (el admin) |
| Reservar | **Implica pagar**. Las gratuitas siguen siendo instantáneas |
| Cola de suplentes | **No se le cobra solo.** Se le ofrece la plaza, que le queda **reservada 6 h o hasta que actúe**; entra, confirma y paga en Checkout como todo el mundo *(corregido el 2026-08-19)* |
| Liberar plaza | **No hay dinero de vuelta hasta que hay sustituto**. Mecanismo: **reembolso al anterior**, disparado por el pago del sustituto |
| Reembolso a petición | **No existe**. La única vía es que otro ocupe la plaza |
| Cancelar actividad | Reembolso automático de lo cobrado por Stripe + **lista al admin de a quién debe dinero a mano** |
| Invitados y apuntados por admin | **Cobro manual**, el admin marca "pagado". El check manual no desaparece |
| `cost_description` | **Se sustituye del todo** por importe estructurado |
| Actividades existentes | **Se quedan sin precio**. Nada de parsear el texto viejo |
| Admin sin KYC completado | **Puede crear actividades de pago**; mientras falte el KYC se reserva como hoy y se cobra a mano *(decidido el 2026-08-19)* |

### Sobre la última: qué implica exactamente

Elegido frente a "bloquear la actividad" y "no dejar poner precio". La actividad
se crea normal, con su precio. Mientras la comunidad no tenga `charges_enabled`:

- Reservar sigue siendo **instantáneo**, exactamente como hoy.
- El admin marca "pagado" a mano (Bizum, efectivo), como hoy.
- En cuanto Stripe se activa, **las reservas nuevas** pasan por Checkout sin que
  nadie toque nada. Las plazas ya reservadas se quedan como están.

Consecuencia asumida: "reservar implica pagar" se incumple de forma transitoria
en comunidades sin KYC. Es el precio de no bloquear a los miembros por un
trámite del admin.

## Decisiones técnicas

Estas no venían decididas. Van con el razonamiento porque condicionan todo lo
demás.

### Connect Standard con cargos directos

**Cuentas `standard`, no `express` ni `custom`. Cargos directos
(`Stripe-Account: acct_...`), no cargos de destino.**

| | Por qué |
|---|---|
| `standard` | La cuenta es **del admin**: su relación de KYC, disputas, impuestos y soporte es con Stripe, no con Agora. Con `express` la plataforma responde de saldos negativos y del soporte. Sin entidad legal detrás de Agora, eso no es asumible. |
| Cargos directos | El dinero **nunca pasa por el saldo de Agora**. El comercio de registro es el admin, y su nombre es el que ve el pagador en el extracto. Es exactamente lo que se busca al no tener sociedad. |
| Cargos directos | La comisión de Stripe sale de la cuenta conectada, que es la decisión "la comisión de Stripe la asume el que cobra". Sale gratis, sin código. |
| `application_fee_amount` | Soportado en cargos directos sobre cuentas `standard`. Es el parámetro de la comisión de Agora. **Se omite cuando es 0** (Stripe rechaza `application_fee_amount=0`). |

El alta se hace con **Account Links** (`account_onboarding`), alojado por Stripe.

### Corrección del 2026-08-19: la cuenta se crea con la API v2

Al probarlo contra Stripe de verdad, `POST /v1/accounts` **devuelve error** para
integraciones nuevas de Connect: *"Stripe no longer recommends Accounts v1 for new
Connect integrations. Create connected accounts with POST /v2/core/accounts
instead."* Hay un interruptor en el panel para reactivar la v1, pero es una vía de
compatibilidad en retirada y esto es una integración nueva.

La creación pasa a `POST /v2/core/accounts`, que habla **JSON** en vez de
form-encoded y usa su propia versión de API. El equivalente de la vieja cuenta
`standard` es:

| v1 | v2 |
|---|---|
| `type: standard` | `dashboard: "full"` |
| El conectado paga comisiones | `defaults.responsibilities.fees_collector: "stripe"` |
| El conectado responde de pérdidas | `defaults.responsibilities.losses_collector: "stripe"` |
| — | `configuration.merchant.capabilities.card_payments.requested: true` |

**El resto del diseño no cambia.** Verificado ejecutándolo, no leyéndolo: una
cuenta creada con la v2 aparece en `GET /v1/accounts` con su `metadata`, y
`POST /v1/account_links` la acepta y devuelve una URL de alta real. La
interoperabilidad que promete la documentación se comprobó punto por punto, así
que los cargos directos, Checkout y `application_fee_amount` siguen valiendo.

### Stripe Checkout, no SDK nativo

No hay SDK de Stripe para Compose Multiplatform: el nativo es de Android y wasmJs
no tiene ninguno. **Checkout alojado por Stripe**, abierto en el navegador, con
vuelta por deep link. Funciona igual en los dos targets y mantiene la app fuera
del alcance de PCI: ningún dato de tarjeta toca Agora.

### El servidor son Edge Functions de Supabase

La clave secreta no puede vivir en el cliente. Las dos opciones eran las Edge
Functions y el Worker de Cloudflare. **Edge Functions**, porque ya hay dos
(`notify-guest-email`, `push-notification`), están en el mismo repo, y tienen
`SUPABASE_SERVICE_ROLE_KEY` en el entorno — que es lo que hace falta para tocar
`payments` y `slots` saltándose RLS. El Worker sirve estáticos y no habla con la
base de datos.

### El dinero es entero, siempre

`price_cents integer`. Nunca `numeric`, nunca coma flotante, nunca un `Double` en
Kotlin. El formateo y el parseo viven en `core/model` como funciones puras con
tests, que es el único módulo del repo con `commonTest` configurado.

### La moneda no lleva columna

Confirmado por el usuario el 2026-08-19: **solo euros, y el importe depende de la
actividad**. Una columna que valdría `'eur'` en el 100% de las filas es
configuración de un valor que no cambia, así que **no existe**. El euro es una
constante en un sitio (`Money.kt` y el `currency` de la sesión de Checkout). Si
algún día hay una segunda moneda, es una migración de una línea.

Lo que sí es por actividad es el importe: `activities.price_cents`.

### Verificado: Google Play no obliga a Play Billing aquí

Restricción #1 del handoff, comprobada. Una plaza en un entrenamiento es un
**servicio físico consumido fuera de la app**, la misma categoría que gimnasios y
transporte, explícitamente **no soportada** por el sistema de facturación de
Google Play. Se puede cobrar con pasarela externa sin infringir la política. No
condiciona ninguna decisión de negocio.

Fuente: [Understanding Google Play's Payments policy](https://support.google.com/googleplay/android-developer/answer/10281818?hl=en).

## Esquema

Una migración: `supabase migration new stripe_payments`.

### Precio en la actividad

```sql
ALTER TABLE activities ADD COLUMN price_cents integer;
ALTER TABLE activities ADD CONSTRAINT activities_price_positive
  CHECK (price_cents IS NULL OR price_cents > 0);
```

`NULL` = gratuita. Es el discriminante de todo el diseño: **si `price_cents` es
`NULL`, no cambia absolutamente nada respecto a hoy.**

`cost_description` **no se borra en esta migración**. Se deja de escribir y se
deja de mostrar en el detalle, pero se sigue leyendo en la pantalla de editar
para enseñárselo una vez al admin ("antes ponía: *Bizum de 6.5 euros*") mientras
introduce el precio real. Se elimina en una migración posterior, cuando ya no
quede nadie usándolo.

### Estado de Stripe en la comunidad

```sql
ALTER TABLE communities ADD COLUMN stripe_account_id text UNIQUE;
ALTER TABLE communities ADD COLUMN stripe_charges_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE communities ADD COLUMN stripe_details_submitted boolean NOT NULL DEFAULT false;
ALTER TABLE communities ADD COLUMN stripe_onboarded_at timestamptz;
```

"¿Esta comunidad puede cobrar?" es
`stripe_account_id IS NOT NULL AND stripe_charges_enabled`. Lo mantiene al día el
webhook `account.updated` y una sincronización explícita al volver del alta.

Los miembros pueden leer estas columnas: necesitan saber si al reservar van a
salir a Checkout o no. `acct_...` no es un secreto (aparece del lado del cliente
en cualquier integración de Connect), así que no se monta RLS por columna.

### Marca de plaza liberada

```sql
ALTER TABLE slots ADD COLUMN released_at timestamptz;
```

Esta columna es **el estado intermedio que el handoff avisaba que iba a
concentrar los bugs**: plaza pagada por A, ofrecida a un sustituto, todavía de A.

Invariante, y merece leerse dos veces:

> **Si pagaste, no pierdes la plaza hasta que otro la pague.**

Con `released_at` no nulo la plaza sigue en `status = 'paid'` y con
`reserved_by = A`. A puede presentarse el día de la actividad. Lo único que
cambia es que otra persona **puede reclamarla**. Si nadie la reclama nunca, no
pasa nada: sigue siendo de A y A no recupera el dinero, que es exactamente la
regla acordada.

Se aplica igual a las plazas pagadas por Stripe y a las marcadas a mano. En las
manuales Agora no puede reembolsar nada, pero la plaza se comporta igual y, al
llegar el sustituto, el anterior aparece en la lista de deudas del admin. Una
sola ruta en vez de dos.

### Nuevo estado de plaza

```sql
ALTER TABLE slots DROP CONSTRAINT slots_status_check;
ALTER TABLE slots ADD CONSTRAINT slots_status_check
  CHECK (status IN ('available','reserved','paid','pending','pending_payment'));
ALTER TABLE slots ADD COLUMN hold_expires_at timestamptz;
```

`pending_payment` = alguien tiene el Checkout abierto sobre esta plaza.
`reserved_by` es quien está pagando, `hold_expires_at` es cuándo caduca la
retención.

Esto tiene una propiedad que ahorra media implementación: `reserve_slot`,
`admin_assign_slot` y `approve_guest_request` ya rechazan cualquier plaza cuyo
`status` no sea `available`. Con `pending_payment` **no hay que tocarlas**.

### Oferta al suplente

```sql
ALTER TABLE slots ADD COLUMN offered_to uuid
  REFERENCES profiles(id) ON DELETE SET NULL;
ALTER TABLE slots ADD COLUMN offer_expires_at timestamptz;
ALTER TABLE slots ADD CONSTRAINT slots_offer_has_expiry
  CHECK (offered_to IS NULL OR offer_expires_at IS NOT NULL);
CREATE INDEX slots_offer_sweep ON slots (offer_expires_at)
  WHERE offered_to IS NOT NULL;
```

Las referencias a personas van a `profiles`, no a `auth.users`: es la convención
del resto del esquema (`slots.reserved_by`, `substitute_queue.user_id`,
`notifications.user_id`). Y con `ON DELETE SET NULL`, porque `delete_my_account()`
borra `auth.users` y eso cascadea a `profiles`.

El `CHECK` va **en una sola dirección** por esa misma razón. La versión
bidireccional (`(offered_to IS NULL) = (offer_expires_at IS NULL)`) reventaba al
borrar la cuenta del ofertado: el `SET NULL` dejaba `offer_expires_at` puesto y
violaba la restricción, así que borrar la cuenta fallaba. Lo peligroso de verdad
es una oferta que no caduca nunca, y eso sí lo sigue impidiendo.

"Esta plaza está apalabrada para esta persona hasta esta hora." Es la **ventana
de 6 h** del suplente. No es una retención de pago: no hay sesión de Checkout
todavía, ni dinero por medio. Es exclusividad para que le dé tiempo a enterarse y
decidir.

Son dos retenciones distintas y conviene no confundirlas:

| | Qué significa | Cuánto dura |
|---|---|---|
| `offered_to` / `offer_expires_at` | "la plaza es tuya si la quieres, ven a por ella" | **6 h** |
| `pending_payment` / `hold_expires_at` | "estás pagando ahora mismo, nadie te la quita" | **30 min** |

Un suplente pasa por las dos, en ese orden. Un miembro cualquiera que reserva una
plaza libre solo pasa por la segunda.

**Solo existen en actividades de pago.** Si `price_cents IS NULL`,
`promote_substitute` sigue asignando la plaza en el acto, como hoy.

### Tabla de pagos

```sql
CREATE TABLE payments (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id           uuid NOT NULL REFERENCES activities(id) ON DELETE RESTRICT,
    slot_id               uuid REFERENCES slots(id) ON DELETE SET NULL,
    user_id               uuid REFERENCES profiles(id) ON DELETE SET NULL,
    community_id          uuid NOT NULL REFERENCES communities(id) ON DELETE RESTRICT,
    amount_cents          integer NOT NULL CHECK (amount_cents > 0),
    application_fee_cents integer NOT NULL DEFAULT 0,
    method                text NOT NULL CHECK (method IN ('stripe','manual')),
    status                text NOT NULL CHECK (status IN (
                              'pending','succeeded','failed','expired',
                              'awaiting_substitute','refund_pending','refunded','refund_owed')),
    connected_account_id  text,
    checkout_session_id   text UNIQUE,
    payment_intent_id     text UNIQUE,
    charge_id             text,
    refund_reason         text CHECK (refund_reason IN ('substitute','activity_cancelled')),
    refund_attempts       integer NOT NULL DEFAULT 0,
    failure_code          text,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now()
);

-- EL CERROJO. Como mucho un cobro abierto por plaza, garantizado por la base
-- de datos y no por el codigo de aplicacion.
CREATE UNIQUE INDEX payments_one_pending_per_slot
  ON payments (slot_id) WHERE status = 'pending';

CREATE INDEX payments_activity ON payments (activity_id);
CREATE INDEX payments_sweep ON payments (status)
  WHERE status IN ('pending','refund_pending');
```

`amount_cents` es una **foto del importe en el momento del cobro**. Si el admin
cambia el precio de la actividad después, los pagos ya hechos no se mueven. Un
registro de dinero no se recalcula nunca.

`method` es la columna que el handoff pedía: de ella depende si un reembolso se
puede automatizar. Los cobros manuales también generan fila (`method='manual'`,
`status='succeeded'`), para que la lista de deudas al cancelar y el historial
salgan de una sola consulta.

`activity_id` y `community_id` van con **`ON DELETE RESTRICT`**, no `CASCADE`.
Las actividades y las comunidades se borran en duro desde la app
(`ActivityDetailScreen.kt:473`, `CommunityDetailScreen.kt:363`), y con `CASCADE`
ese botón destruiría en silencio el registro de dinero: cobrado en Stripe, sin
rastro en Agora, y cualquier `refund_pending` sin ejecutar perdido para siempre.
Con `RESTRICT`, borrar falla con un error claro y la vía correcta pasa a ser
**cancelar la actividad primero** — que reembolsa a todo el mundo — y borrarla
después. Lo garantiza la base de datos, así que ningún camino del código puede
saltárselo.

Consecuencia en la UI: el botón de borrar necesita un mensaje que explique esto,
en vez de enseñar un error de base de datos en crudo.

`user_id` es **nullable y `ON DELETE SET NULL`**, y esto no es descuido. Con
`NOT NULL` y sin cascada, borrar la cuenta fallaría y se rompería
`delete_my_account()`. Con `ON DELETE CASCADE`, borrar la cuenta se llevaría por
delante el registro de dinero — incluido un `refund_pending` todavía sin
ejecutar, y esa persona perdería su devolución. Con `SET NULL` sobreviven el
importe y los identificadores de Stripe, que es lo que hace falta para reembolsar
y para cuadrar cuentas. Mismo criterio que `slots.reserved_by`.

`payments_one_pending_per_slot` es el cerrojo real. Dos personas que salen a
pagar la misma plaza a la vez: la segunda `INSERT` viola el índice y recibe
"alguien está pagando esta plaza ahora mismo". No hace falta lógica de bloqueo,
lo garantiza el índice.

### Deduplicación de webhooks

```sql
CREATE TABLE stripe_events (
    id          text PRIMARY KEY,
    type        text NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now()
);
```

El manejador **inserta primero**. Si viola la clave primaria, el evento ya se
procesó: responde 200 y no hace nada más. Es la deduplicación más barata que
existe y no depende de que el resto del código sea perfecto.

### RLS

- `payments`: `SELECT` para el dueño (`user_id = auth.uid()`) y para los admins
  de la comunidad. **Ni `INSERT` ni `UPDATE` para nadie**: solo escribe el
  `service_role` desde las Edge Functions, y `mark_slot_paid` (que es
  `SECURITY DEFINER`).
- `stripe_events`: sin políticas. Solo `service_role`.

**`ALTER TABLE ... ENABLE ROW LEVEL SECURITY` explícito en las dos.** El baseline
define la *función* `rls_auto_enable()` pero **nunca llega a crear el event
trigger que la dispara** — comprobado contra `pg_event_trigger` en una base de
datos real: no está. Las demás tablas tienen RLS porque sus migraciones la
activaron a mano (`ALTER TABLE reports ENABLE ROW LEVEL SECURITY` y compañía).

Sin esas dos líneas las políticas existen pero **no se aplican**, y cualquiera
con la clave anónima podría leer todos los pagos de todo el mundo. Se detectó
ejecutando la migración, no leyéndola: `relrowsecurity` salía `f`.

## La máquina de estados

Es la parte a leer despacio. Todo lo demás es fontanería.

```
                        available
                            │
              crear sesion de Checkout (INSERT payment 'pending')
                            ▼
                    pending_payment              ┌──────────────────────┐
                 reserved_by = U                 │ hold_expires_at      │
                 hold_expires_at = +30min        │ caduca, o el pago    │
                            │                    │ falla, o se cancela  │
              ┌─────────────┴─────────────┐      └──────────┬───────────┘
       pago confirmado              pago no confirmado      │
              ▼                                             ▼
            paid  ◄──────────────────────────────────── available
       reserved_by = U                                 (payment 'expired')
              │
         release_slot(solo U; el admin NO puede liberar plaza pagada ajena)
              ▼
       paid + released_at = now()        ← sigue siendo de U, U puede asistir
       payment(U) = 'awaiting_substitute'
              │
              │  promote_substitute: hay cola
              ▼
       + offered_to = V, offer_expires_at = +6h      ← apalabrada para V
              │
      ┌───────┴──────────────┬─────────────────────────┐
   V confirma          V rechaza, o pasan 6h      nadie en la cola
      ▼                      ▼                          ▼
  pasa por Checkout    se ofrece al siguiente      offered_to = NULL
  (payment pending)    y V sale de la cola.        cualquiera puede
      │                Sin cola -> sin oferta      reclamarla
      ▼
   pago de V confirmado
      ▼
   paid, reserved_by = V, released_at = NULL, offered_to = NULL
   payment(U) = 'refund_pending'
   payment(V) = 'succeeded'
      │
   worker de reembolsos
      ▼
   payment(U) = 'refunded'   (o 'refund_owed' si method='manual')
```

Si nadie llega a pagarla nunca, la plaza se queda como está hasta el día de la
actividad: sigue siendo de U, que conserva la plaza que ya había pagado. Correcto
y deliberado.

**En actividades gratuitas (`price_cents IS NULL`) nada de esto se ejecuta.** La
plaza va de `available` a `reserved` en una transacción, igual que hoy, y
`promote_substitute` asigna al suplente en el acto sin ofertas ni ventanas. Es
requisito explícito y hay que protegerlo con tests.

## Backend

### `stripe-connect` (Edge Function, JWT requerido)

- `POST {action:'onboard', community_id}` — el llamante debe ser admin. Si la
  comunidad no tiene `stripe_account_id`, crea la cuenta `standard` y la guarda.
  Devuelve un Account Link (`account_onboarding`) con `return_url` y
  `refresh_url` apuntando a `share-agora.app/pay/connect`.
- `POST {action:'status', community_id}` — recupera la cuenta de Stripe y
  refresca `stripe_charges_enabled` / `stripe_details_submitted`. Se llama al
  volver del alta, sin esperar al webhook.

### `stripe-checkout` (Edge Function, JWT requerido)

`POST {action:'create', slot_id}` — el corazón. En una transacción:

1. Carga plaza y actividad. Si `price_cents IS NULL` → error: esta ruta no es
   para gratuitas (el cliente no debería haber llegado aquí).
2. La comunidad debe tener `stripe_charges_enabled`; si no → error
   `payments_not_enabled` (el cliente cae a la reserva manual de hoy).
3. El llamante es miembro de la comunidad.
4. La plaza es reclamable: `status = 'available'`, **o** `status = 'paid'` con
   `released_at IS NOT NULL` y `reserved_by <> llamante`.
5. Oferta viva: si `offered_to` no es nulo, `offer_expires_at` no ha pasado y
   `offered_to <> llamante` → error `slot_offered_to_someone_else`. Si el
   llamante **es** el ofertado, pasa: la oferta es precisamente su derecho a
   reclamarla.
6. Prioridad de cola: si hay alguien por delante en `substitute_queue` para esta
   plaza y no es el llamante → error. Misma comprobación que `reserve_slot`,
   reutilizando su lógica de posiciones.
7. `INSERT INTO payments (... status='pending' ...)`. Si viola
   `payments_one_pending_per_slot` → error `slot_being_paid`.
8. Si la plaza estaba `available`: `status='pending_payment'`,
   `reserved_by=llamante`, `hold_expires_at=now()+30min`. Si estaba liberada, **la
   plaza no se toca**: el cerrojo es la fila de `payments`.

La oferta al suplente **no se borra aquí**. Si el pago se queda a medias, V sigue
teniendo su ventana de 6 h para volver a intentarlo. Se borra al confirmarse el
pago.

Después del commit, crea la sesión de Checkout con
`Stripe-Account: <connected>`, `Idempotency-Key: <payment.id>`,
`expires_at = +30min`, `client_reference_id = <payment.id>`,
`metadata.payment_id`, y `application_fee_amount` **solo si el fee es > 0**.
Devuelve la URL.

`POST {action:'sync', payment_id}` — recupera la sesión de Stripe y aplica
**exactamente la misma transición que el webhook**, llamando a la misma función.
Se invoca al volver por deep link. Esto hace que el webhook sea la red de
seguridad y no el camino crítico: el usuario ve el resultado al instante.

### `stripe-webhook` (Edge Function, `verify_jwt = false`)

1. `constructEvent` con `STRIPE_WEBHOOK_SECRET`. Firma inválida → 400.
2. `INSERT INTO stripe_events`. Conflicto → 200 y fuera.
3. Despacha:
   - `checkout.session.completed` (+ `async_payment_succeeded`) → transición de éxito.
   - `checkout.session.expired`, `async_payment_failed` → liberar retención.
   - `charge.refunded` → confirmar `refunded`.
   - `account.updated` → refrescar el estado de la comunidad.
4. Siempre 200 salvo error de firma o fallo interno (para que Stripe reintente).

Los eventos de cuentas conectadas llegan al mismo endpoint con `event.account`
poblado.

### `stripe-sweep` (Edge Function, sin JWT, protegida por cabecera secreta)

Un `pg_cron` cada minuto la invoca vía `net.http_post`. El repo ya usa `pg_cron`
para `send_activity_reminders` (ver `_legacy_migrations/019_activity_reminders.sql`),
así que el patrón existe. Hace dos cosas:

1. **Reconciliar retenciones caducadas.** Para cada `payment` en `pending` con
   `hold_expires_at` pasado: recupera la sesión de Stripe. Si salió pagada,
   aplica la transición de éxito; si no, libera la plaza y marca `expired`.
   Consultar a Stripe antes de liberar **no es opcional**: es el caso "el webhook
   no llegó nunca y el usuario tampoco volvió". Liberar a ciegas dejaría a
   alguien cobrado y sin plaza.
2. **Reintentar reembolsos.** Para cada `refund_pending` con más de 5 minutos:
   `POST /v1/refunds` con `Idempotency-Key = payment.id` sobre la cuenta
   conectada. Éxito → `refunded`. Fallo → `refund_attempts + 1`; a partir de 5
   intentos, notificar al admin para que lo resuelva a mano.

Los reembolsos se hacen **siempre** desde este worker, nunca en línea, tanto en
la sustitución como en la cancelación de una actividad. Así una cancelación con
20 pagos es una transacción de base de datos rápida y los reembolsos ocurren
después, con reintentos gratis. Es un patrón outbox de quince líneas.

**La caducidad de las ofertas a suplentes no pasa por aquí.** No hay nada que
preguntarle a Stripe: es `expire_substitute_offers()`, SQL puro invocado
directamente por `pg_cron`, sin salir de la base de datos.

### RPCs nuevas y modificadas

**`release_slot` — se modifica.** Rama nueva antes de la actual:

```
si la plaza esta 'paid':
    released_at = now()                     -- la plaza NO cambia de dueno
    payment de esa plaza -> 'awaiting_substitute'
    notificar a la cola de suplentes
    RETURN TRUE
si no:
    comportamiento de hoy (la plaza vuelve a 'available')
```

Ojo con la advertencia del handoff: `CREATE OR REPLACE` copiando el cuerpo real
de la última migración que la definió (`20260731065916_admin_assign_slots.sql`),
sin tocar la firma.

**Quién puede liberar una plaza pagada: solo su dueño.** Decidido el 2026-08-19.
Es la regla que la app ya aplica hoy — `isAdminReleasable` en
`ActivityDetailScreen.kt:96` deja fuera explícitamente las plazas pagadas con
dueño, con su comentario diciendo que "sigue siendo cosa suya" — y se mantiene al
llegar el dinero de verdad, con más razón: el admin no mueve el dinero de otro.

El admin conserva lo que ya tenía: liberar reservas sin pagar y plazas de etiqueta
(las de `guest_label`, sin `reserved_by`).

Consecuencia asumida: si alguien paga y desaparece sin liberar, esa plaza queda
bloqueada hasta el día de la actividad y el suplente se queda fuera. La salida es
manual y fuera de la app — que el admin le escriba y la libere él.

**`mark_slot_paid` — se modifica.** Además de poner la plaza en `paid`, inserta
una fila en `payments` con `method='manual'`, `status='succeeded'` y
`amount_cents = activities.price_cents` (si hay precio; si no, no inserta nada).

**`promote_substitute` — se modifica.** Hoy asigna la plaza directamente. Sigue
haciéndolo **si la actividad es gratuita**. Si tiene precio, en vez de asignar:

```
buscar al primero de la cola (logica de posiciones actual, sin tocar)
si no hay nadie:
    offered_to = NULL, offer_expires_at = NULL
    RETURN FALSE
si hay:
    offered_to = ese usuario
    offer_expires_at = min(now() + interval '6 hours', activities.datetime)
    notificar 'substitute_offer'
    RETURN TRUE
```

No borra al ofertado de `substitute_queue`: sale de la cola al aceptar (cuando su
pago se confirma), al rechazar, o al caducar la oferta.

**`decline_substitute_offer(p_slot_id)` — nueva.** El ofertado renuncia. Lo saca
de la cola y llama a `promote_substitute` para pasar al siguiente.

**`expire_substitute_offers()` — nueva.** SQL puro, sin Stripe de por medio. Para
cada plaza con `offer_expires_at` pasado: saca al ofertado de la cola, limpia la
oferta y llama a `promote_substitute`. La invoca `pg_cron` cada minuto, igual que
`send_activity_reminders`.

**`cancel_activity(p_activity_id)` — nueva.** Admin. Archiva la actividad, pone
todos los `payments` de Stripe en `succeeded`/`awaiting_substitute` a
`refund_pending` con `refund_reason='activity_cancelled'`, y todos los manuales a
`refund_owed`. Devuelve el desglose para el diálogo del admin. Los reembolsos los
ejecuta el worker.

**`claimable_slots(p_activity_id)` — nueva.** Devuelve las plazas reclamables
incluyendo las liberadas. La app la necesita para pintar la lista sin
reimplementar la regla en el cliente.

## Cola de suplentes

**Corregido el 2026-08-19.** La decisión original era cobrar al suplente
directamente. Se sustituye por: **al suplente se le ofrece la plaza, le queda
reservada 6 h o hasta que actúe, y paga en Checkout como todo el mundo.**

El cambio elimina de un plumazo toda la parte cara del diseño anterior: no hacen
falta tarjetas guardadas, ni `SetupIntent`, ni objetos `Customer` en la cuenta
conectada, ni cobros fuera de sesión. Y de paso desaparece el problema de SCA en
Europa, donde una parte de los cargos fuera de sesión se rechazan por
autenticación y no por falta de fondos — con el mecanismo anterior, a esa gente
la habría expulsado de la cola el banco. Ahora el suplente autentica en el
momento, en sesión, como cualquier otro pagador.

También conserva algo que se perdía: apuntarse a la cola vuelve a ser **pulsar un
botón**, no rellenar un formulario de tarjeta por si acaso.

### Cómo funciona

1. Se libera una plaza en una actividad de pago (alguien la libera, o el admin
   añade huecos). `promote_substitute` busca al primero de la cola con la lógica
   de posiciones que ya existe.
2. En vez de asignársela, la **apalabra**: `offered_to = V`,
   `offer_expires_at = min(now() + 6h, comienzo de la actividad)`. Notificación a
   V ("tienes plaza en X, confírmala antes de las 14:30").
3. Mientras la oferta viva, **solo V puede reclamarla**. Al resto se le muestra
   apalabrada, no libre.
4. V entra, confirma, y sale a Checkout por el camino normal: retención de pago de
   30 min, sesión, deep link de vuelta. Si paga, la plaza es suya.
5. Si V rechaza explícitamente, o pasan las 6 h sin que actúe: **V sale de la
   cola** y la oferta pasa al siguiente. Si no queda nadie, `offered_to = NULL` y
   la plaza queda reclamable por cualquiera.

El tope en el comienzo de la actividad no es un detalle: una ventana de 6 h sobre
un partido que empieza dentro de tres horas dejaría la plaza congelada hasta
después de jugarse.

### Lo que asumo y puedes corregir en la revisión

**Dejar caducar la oferta saca de la cola**, igual que rechazarla. Es la lectura
natural de la regla original ("si su pago falla, sale de la cola") y sin ella la
cola se atasca: al siguiente barrido se le volvería a ofrecer al mismo, para
siempre. Si prefieres que vuelva al final de la cola en vez de salir, es una línea.

**El fallo de pago sigue sacando de la cola.** Si V confirma, sale a Checkout y no
paga, pierde la oferta y sale. Eso sí es literal a lo decidido.

## Reservar: el camino que más se usa

La acción más usada de la app deja de ser instantánea en actividades de pago.
Secuencia completa:

1. Pulsar "Reservar · 6,50 €".
2. La app llama a `stripe-checkout` y recibe una URL. Si contesta
   `payments_not_enabled` (comunidad sin KYC), **cae a `reserve_slot` de toda la
   vida** y termina aquí, instantáneo.
3. Abre la URL en el navegador. La plaza ya está retenida: nadie más la ve libre.
4. El usuario paga en Stripe.
5. Stripe redirige a `https://share-agora.app/pay/ok?p={payment_id}`.
   - **Android**: App Link con `pathPattern="/pay/.*"` y `autoVerify`. Abre la
     app directamente.
   - **Web**: `worker.js` redirige `/pay/*` a `/app/?pay={id}`, igual que ya hace
     con `/a/*`.

   Una sola URL para los dos targets.
6. La app llama a `{action:'sync'}` y enseña el resultado.
7. El webhook llega en paralelo y es idempotente. Si el paso 6 ya lo aplicó, no
   hace nada.

`WebDeepLink` gana `Payment(paymentId)` y `parseWebDeepLink` el parámetro `pay`.
Ya hay `WebDeepLinkTest.kt`: se amplía.

## Qué pasa cuando algo va mal

| Fallo | Comportamiento |
|---|---|
| El webhook llega dos veces | `stripe_events` lo deduplica por clave primaria. 200 sin efecto. |
| El webhook no llega nunca | El retorno por deep link (`sync`) aplica la misma transición. Si el usuario tampoco vuelve, el barrido consulta a Stripe al caducar la retención. |
| Dos personas pagan la misma plaza | Imposible: `payments_one_pending_per_slot`. La segunda recibe `slot_being_paid` antes de ver ninguna pantalla de pago. |
| El usuario cierra el navegador tras pagar | El webhook confirma. Al abrir la app, la plaza está `paid`. |
| El usuario abandona el Checkout | `checkout.session.expired` libera la retención; si el evento no llega, el barrido a los 30 min. |
| Se cobró pero la plaza ya no está | No puede ocurrir: la plaza se retiene **antes** de crear la sesión, y la retención dura lo mismo que la sesión. Si aun así ocurriera, el barrido lo detecta y deja el pago `succeeded` con la plaza asignada. |
| El reembolso falla en Stripe | Queda `refund_pending`. El worker reintenta con la misma `Idempotency-Key`. Tras 5 intentos, aviso al admin. |
| El admin desconecta Stripe con pagos vivos | `charges_enabled=false`: no se crean sesiones nuevas. Los reembolsos pendientes fallan y acaban en la lista manual del admin. |
| La actividad se cancela mientras alguien paga | El pago pendiente se reconcilia: si se cobró, `refund_pending`; si no, se libera. |
| El admin cambia el precio con gente ya pagada | Los pagos existentes no se tocan (`amount_cents` es una foto). La UI avisa al editar. |
| El suplente ignora la oferta 6 h | `expire_substitute_offers()` lo saca de la cola y ofrece al siguiente. Sin cola, la plaza queda libre para cualquiera. |
| La actividad empieza antes de que caduque la oferta | `offer_expires_at` se topa en `activities.datetime`. Nunca se congela una plaza más allá del comienzo. |
| El suplente empieza a pagar y abandona | Pierde la retención de 30 min, **no la oferta**: le quedan sus 6 h para reintentar. |
| El admin apunta a alguien en una plaza apalabrada | `admin_assign_slot` sigue exigiendo `status='available'`. Una plaza liberada está en `paid`, así que ya la rechaza; sobre una `available` con oferta viva hay que añadir la comprobación de `offered_to`. **Es el único sitio del código antiguo que hay que tocar por esto.** |

## Cambios en la app

1. **Crear / editar actividad.** El campo de texto libre de coste
   ([CreateActivityScreen.kt:167](feature/activity/src/commonMain/kotlin/com/app/community/feature/activity/presentation/CreateActivityScreen.kt#L167))
   se sustituye por un selector *Gratuita / De pago* + importe en euros. Si falta
   el KYC, aviso **no bloqueante** con botón "Configurar cobros". En editar, si la
   actividad tiene `cost_description` antiguo, se muestra una vez como ayuda.
2. **Detalle de actividad.** Precio visible; "Reservar" pasa a "Reservar ·
   6,50 €"; `SlotStatusBadge` gana "Reservándose…" (`pending_payment`), "Libre —
   pagada, busca sustituto" (`released_at` no nulo) y "Apalabrada hasta las 14:30"
   (`offered_to` de otro).
3. **Oferta al suplente.** Al ofertado se le enseña la plaza con cuenta atrás y
   dos botones: *Confirmar y pagar · 6,50 €* y *Renunciar*. Es la pantalla que
   sustituye al cobro automático, así que tiene que dejar clarísimo que la plaza
   se pierde a la hora que marca.
4. **Ajustes de comunidad (admin).** Sección "Cobros": conectar con Stripe,
   estado del alta, enlace al panel de Stripe.
5. **Resultado del pago.** Pantalla de vuelta del deep link: spinner mientras
   sincroniza, y resultado.
6. **Cancelar actividad (admin).** Diálogo con el desglose: cuántos se devuelven
   solos y la lista nominal de a quién hay que devolverle a mano.

### Código puro con tests

En `core/model`, `Money.kt`:

- `formatEuros(cents: Int): String` → `"6,50 €"`.
- `parseEurosToCents(input: String): Int?` → acepta `"6,50"`, `"6.50"`, `"6"`;
  rechaza vacío, negativos, más de dos decimales y basura.

Con su `MoneyTest.kt` en `commonTest`. Es el único sitio del repo donde hay
harness de tests y es dinero: aquí no se improvisa.

## Fuera de alcance

- Facturas y recibos fiscales (decidido).
- Multi-moneda (decidido).
- Cobro a invitados sin cuenta (decidido: manual).
- Cuotas periódicas o suscripciones (decidido).
- Reembolso a petición del usuario (decidido).
- **Precios distintos por posición o por grupo dentro de una actividad.** No se
  habló. Se asume precio único por actividad, que es lo que pide el caso de
  voleibol. Si hace falta, es un `price_cents` en `slot_groups` más adelante.
- Pagos parciales, propinas, descuentos, códigos promocionales.

## Riesgos

1. **El estado liberada-pero-pagada** es nuevo y no se parece a nada de lo que ya
   hay. Los sitios que asumen "plaza `paid` = plaza ocupada y cerrada" hay que
   revisarlos uno a uno.
2. **La ventana de 6 h ralentiza la rotación de plazas.** Con una cola de tres
   personas que pasan de la notificación, una plaza puede tardar 18 h en volver a
   estar libre para cualquiera. En una actividad que se anuncia con una semana de
   antelación da igual; en una de mañana, no. El tope en `activities.datetime` lo
   acota, pero no lo arregla del todo. Si en la práctica molesta, la ventana es un
   número en un sitio.
3. **La comisión de Stripe no se devuelve en los reembolsos**, así que cada
   sustitución cuesta una comisión de más. Asumido en el handoff, se repite aquí
   porque se nota en cuanto haya volumen.
4. **El build de wasm de producción tarda del orden de una hora.** Cualquier
   arreglo de un fallo de pagos en web tiene ese suelo de latencia. Conviene
   probar el flujo entero en Android antes de tocar el despliegue web.

Los dos riesgos que tenía este documento en su primera versión — SCA en los cobros
fuera de sesión y la fricción de guardar tarjeta para entrar en la cola —
**desaparecen** con el mecanismo de oferta. Era la parte más cara del diseño.
