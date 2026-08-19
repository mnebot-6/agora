# Pagos con Stripe — diseño

Fecha: **2026-08-19**. Estado: pendiente de revisión del usuario.
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
| Moneda | **Solo euros**. Sin facturas ni recibos fiscales |
| Comisión de Stripe | La asume **quien cobra** (el admin) |
| Reservar | **Implica pagar**. Las gratuitas siguen siendo instantáneas |
| Cola de suplentes | Al suplente **se le cobra directamente** y obtiene la plaza. Si falla, no reserva y **sale de la cola** |
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

### La moneda no lleva columna — desviación consciente

El handoff pide "importe estructurado **y moneda**", y también "solo euros". Una
columna que vale `'eur'` en el 100% de las filas es configuración de un valor que
no cambia. **Se omite.** El euro es una constante en un sitio (`Money.kt` y el
`currency` de la sesión de Checkout). Si algún día hay una segunda moneda, es una
migración de una línea; hasta entonces es una columna que hay que arrastrar en
cada consulta, cada modelo y cada comparación de importes.

Si prefieres la columna, dilo en la revisión: son 10 minutos, no rehace nada.

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

### Tabla de pagos

```sql
CREATE TABLE payments (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id           uuid NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    slot_id               uuid REFERENCES slots(id) ON DELETE SET NULL,
    user_id               uuid NOT NULL REFERENCES auth.users(id),
    community_id          uuid NOT NULL REFERENCES communities(id),
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
         release_slot(U o admin)
              ▼
       paid + released_at = now()        ← sigue siendo de U, U puede asistir
       payment(U) = 'awaiting_substitute'
              │
      ┌───────┴────────────────────────────┐
   otro paga la plaza              nadie la paga nunca
      ▼                                    ▼
   paid, reserved_by = V            se queda asi hasta la actividad.
   released_at = NULL               U conserva la plaza que ya habia pagado.
   payment(U) = 'refund_pending'
   payment(V) = 'succeeded'
      │
   worker de reembolsos
      ▼
   payment(U) = 'refunded'   (o 'refund_owed' si method='manual')
```

**En actividades gratuitas (`price_cents IS NULL`) nada de esto se ejecuta.** La
plaza va de `available` a `reserved` en una transacción, igual que hoy. Es
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
5. Prioridad de cola: si hay alguien por delante en `substitute_queue` para esta
   plaza y no es el llamante → error. Misma comprobación que `reserve_slot`,
   reutilizando su lógica de posiciones.
6. `INSERT INTO payments (... status='pending' ...)`. Si viola
   `payments_one_pending_per_slot` → error `slot_being_paid`.
7. Si la plaza estaba `available`: `status='pending_payment'`,
   `reserved_by=llamante`, `hold_expires_at=now()+30min`. Si estaba liberada, **la
   plaza no se toca**: el cerrojo es la fila de `payments`.

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

**`mark_slot_paid` — se modifica.** Además de poner la plaza en `paid`, inserta
una fila en `payments` con `method='manual'`, `status='succeeded'` y
`amount_cents = activities.price_cents` (si hay precio; si no, no inserta nada).

**`promote_substitute` — se modifica.** Hoy asigna la plaza directamente. En
actividades de pago ya no puede: hay que cobrar antes. Ver la sección siguiente.

**`cancel_activity(p_activity_id)` — nueva.** Admin. Archiva la actividad, pone
todos los `payments` de Stripe en `succeeded`/`awaiting_substitute` a
`refund_pending` con `refund_reason='activity_cancelled'`, y todos los manuales a
`refund_owed`. Devuelve el desglose para el diálogo del admin. Los reembolsos los
ejecuta el worker.

**`claimable_slots(p_activity_id)` — nueva.** Devuelve las plazas reclamables
incluyendo las liberadas. La app la necesita para pintar la lista sin
reimplementar la regla en el cliente.

## Cola de suplentes: la parte que más cuidado pide

La decisión es "**al suplente se le cobra directamente y obtiene la plaza; si su
pago falla, no reserva nada y sale de la cola**". Implementado tal cual significa
un **cobro fuera de sesión**, y eso arrastra dos cosas que conviene ver antes de
aprobarlo:

1. **Apuntarse a la cola de una actividad de pago pasa a exigir guardar una
   tarjeta.** Hay que sacar al usuario a un Checkout en modo `setup`, crear un
   `Customer` **en la cuenta conectada** (los cargos son directos, el cliente vive
   allí) y guardar el `payment_method`. Hoy apuntarse a la cola es pulsar un
   botón. Pasa a ser un formulario de tarjeta.

2. **En Europa, una parte de los cargos fuera de sesión se rechazan por SCA**
   (`authentication_required`), no por falta de fondos. Con la regla tal cual, a
   esa gente se la expulsa de la cola por un requisito bancario, no por no querer
   o no poder pagar. Se mitiga creando el `SetupIntent` con `usage: off_session`
   para que la autenticación inicial cubra los cargos posteriores, pero no
   desaparece.

**No reabro la decisión.** La señalo con una propuesta acotada para la revisión:
que el rechazo por `authentication_required` no expulse de la cola, sino que
avise al suplente con una retención de 30 minutos para pagar en sesión, y solo
entonces lo saque. Los rechazos por fondos o tarjeta caducada siguen expulsando
inmediatamente. Es un caso más en el worker, no un diseño distinto.

Si prefieres la regla literal, se implementa literal.

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

## Cambios en la app

1. **Crear / editar actividad.** El campo de texto libre de coste
   ([CreateActivityScreen.kt:167](feature/activity/src/commonMain/kotlin/com/app/community/feature/activity/presentation/CreateActivityScreen.kt#L167))
   se sustituye por un selector *Gratuita / De pago* + importe en euros. Si falta
   el KYC, aviso **no bloqueante** con botón "Configurar cobros". En editar, si la
   actividad tiene `cost_description` antiguo, se muestra una vez como ayuda.
2. **Detalle de actividad.** Precio visible; "Reservar" pasa a "Reservar ·
   6,50 €"; `SlotStatusBadge` gana "Reservándose…" (`pending_payment`) y "Libre —
   pagada, busca sustituto" (`released_at` no nulo).
3. **Ajustes de comunidad (admin).** Sección "Cobros": conectar con Stripe,
   estado del alta, enlace al panel de Stripe.
4. **Resultado del pago.** Pantalla de vuelta del deep link: spinner mientras
   sincroniza, y resultado.
5. **Cancelar actividad (admin).** Diálogo con el desglose: cuántos se devuelven
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

1. **SCA en la cola de suplentes** (sección propia arriba). Es el único punto
   donde propongo una variación sobre lo decidido.
2. **Guardar tarjeta para entrar en la cola** es fricción nueva sobre una acción
   que hoy es un botón. Puede hundir el uso de la cola en actividades de pago.
3. **El estado liberada-pero-pagada** es nuevo y no se parece a nada de lo que ya
   hay. Los sitios que asumen "plaza `paid` = plaza ocupada y cerrada" hay que
   revisarlos uno a uno.
4. **La comisión de Stripe no se devuelve en los reembolsos**, así que cada
   sustitución cuesta una comisión de más. Asumido en el handoff, se repite aquí
   porque se nota en cuanto haya volumen.
5. **El build de wasm de producción tarda del orden de una hora.** Cualquier
   arreglo de un fallo de pagos en web tiene ese suelo de latencia. Conviene
   probar el flujo entero en Android antes de tocar el despliegue web.
