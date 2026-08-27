# Tres modos de pago por actividad — diseño

Fecha: **2026-08-27**. Estado: **pendiente de aprobación**.
Antecedente: `docs/superpowers/specs/2026-08-19-stripe-design.md` (en producción).

## Motivación

Hoy una actividad solo tiene dos estados de cobro, y el segundo es ambiguo:

- `price_cents IS NULL` → gratuita.
- `price_cents` con importe → **depende de la comunidad**. Si tiene cobrador de
  Stripe configurado, reservar sale a Checkout. Si no, reservar es instantáneo y
  el admin marca a mano quién ha pagado.

Es decir: el modo de cobro de una actividad **no lo elige quien la crea**, lo
decide un ajuste de la comunidad que puede cambiar por debajo. Un admin que
quiere seguir cobrando por Bizum no tiene forma de decirlo si la comunidad ya
tiene Stripe dado de alta.

Además el modo manual está a medias: el admin puede marcar una plaza como
pagada, pero **no puede desmarcarla**. Un clic por error es irreversible.

## Lo que cambia

Tres modos explícitos, elegidos al crear la actividad:

| Modo | Qué significa |
|---|---|
| **Sin pago** (`free`) | Como hoy. Reservar es instantáneo. |
| **Pago externo** (`external`) | Hay un importe y una explicación de cómo pagar ("Bizum al 601386047 con tu nombre y la fecha como concepto"). Agora **no mueve dinero**: reservar es instantáneo y el admin marca —y desmarca— quién ha pagado. |
| **Pago gestionado por Agora** (`agora`) | Reservar sale a Stripe Checkout. Solo se ofrece si la comunidad tiene cobrador activo. |

## Decisiones cerradas (2026-08-27)

| Decisión | Valor |
|---|---|
| Elección del modo | **Fija al crear.** Editar la actividad muestra el modo en solo lectura |
| Modo `agora` en el selector | **No aparece** si la comunidad no tiene `stripe_charges_enabled` |
| Campos de `external` | Importe numérico **+ descripción libre de cómo pagar** |
| Desmarcar pagado | **Solo cobros manuales.** Un cobro de Stripe nunca se desmarca |
| Si la comunidad pierde el cobrador | La actividad **degrada sola a `external`**, y vuelve sola a `agora` cuando lo recupera |
| Liberar plaza pagada en `external` | La plaza **sigue siendo tuya** hasta que otro la ocupa; ocuparla es instantánea |
| Liberar plaza **no** pagada | Se libera del todo en el acto, en cualquier modo. Sin cambios |
| Invitados | **Siempre pago externo**, aunque la actividad sea `agora`. Nunca pasan por Checkout |
| Deuda al ocupante anterior | Se notifica **a todos los admins de la comunidad**: "devuélvele X € a Fulano" |
| Backfill irreversible | Asumido. Una actividad de pago existente en una comunidad sin cobrador se queda en `external` para siempre |

## Modelo de datos

Una columna nueva:

```sql
ALTER TABLE activities ADD COLUMN payment_mode text NOT NULL DEFAULT 'free'
  CHECK (payment_mode IN ('free', 'external', 'agora'));

-- El invariante que impide los estados incoherentes "de pago sin importe" y
-- "gratuita con importe". Lo garantiza la base de datos, no la app.
ALTER TABLE activities ADD CONSTRAINT activities_mode_matches_price
  CHECK ((payment_mode = 'free') = (price_cents IS NULL));
```

`cost_description` **deja de estar deprecada** y pasa a ser el campo "cómo se
paga" del modo `external`. En `free` y `agora` se guarda NULL.

### Backfill

Conserva exactamente el comportamiento actual de producción:

| Actividad hoy | Modo que recibe |
|---|---|
| `price_cents IS NULL` | `free` |
| Con precio, comunidad con `stripe_charges_enabled` | `agora` |
| Con precio, comunidad sin cobrador | `external` |

**Consecuencia que hay que aceptar:** una actividad anterior a Stripe con
`cost_description` de texto ("Bizum de 6.5 euros por persona") pero sin
`price_cents` queda como `free`, y con el modo fijo al crear ya no se puede pasar
a `external`. No hay forma honesta de evitarlo: inventar un importe parseando ese
texto es exactamente lo que el diseño de Stripe descartó. El admin crea una
actividad nueva. Con esto desaparece también el aviso `price_help_legacy` de la
pantalla de editar, que ya no tiene dónde vivir.

### Modo efectivo

`payment_mode` es la **intención** del admin. Lo que se ejecuta es el modo
efectivo, que añade el estado del cobrador:

```sql
CREATE FUNCTION activity_payment_mode(p_activity_id uuid) RETURNS text
```

- `free` → `free`.
- `agora` **y** la comunidad tiene `stripe_account_id` y `stripe_charges_enabled`
  → `agora`.
- En cualquier otro caso → `external`.

Ahí vive la degradación automática, y también la recuperación: no hay ningún
proceso que reescriba filas, la actividad vuelve sola a Stripe cuando la
comunidad puede volver a cobrar.

**Consecuencia asumida:** una actividad puede acabar con unos participantes
pagados por Stripe y otros a mano. Ya ocurre hoy con los invitados.

En Kotlin, el mismo cálculo como función pura y testeada:

```kotlin
enum class PaymentMode { FREE, EXTERNAL, AGORA }
fun Activity.effectiveMode(communityChargesEnabled: Boolean): PaymentMode
```

`Community` gana `stripeChargesEnabled`. `ActivityDetailScreenModel` ya llama a
`communityRepository.getCommunity(...)` en `load()`, así que no añade viajes.

## Flujos

### Crear y editar

`ActivityPriceField` pasa de dos tarjetas a tres. La tercera solo se pinta si la
comunidad puede cobrar; si el usuario es admin y no lo tiene configurado, en su
lugar va un enlace a `CommunityPaymentsScreen`.

`external` y `agora` piden importe obligatorio. `external` añade el campo de
texto "Cómo se paga", opcional.

Editar muestra el modo como etiqueta de solo lectura. El importe **sí** es
editable: los pagos ya hechos no se recalculan nunca (`payments.amount_cents` es
una foto del momento del cobro).

### Reservar

| Modo efectivo | Acción |
|---|---|
| `free` | `reserve_slot`. Instantáneo. Sin cambios |
| `external` | `reserve_slot`. Instantáneo. La plaza queda **reservada, no pagada** |
| `agora` | `createCheckout`. Sin cambios |

El cliente ya no intenta el Checkout primero para caer al error: consulta el modo
efectivo y va directo. Se conserva el rescate por si el cobrador cae entre la
carga de la pantalla y el clic: si `createCheckout` responde
`payments_not_enabled`, cae a `reserve_slot` y avisa de que el organizador
cobrará aparte.

`begin_slot_payment` gana una guarda simétrica: si el modo efectivo no es
`agora`, lanza `payments_not_enabled`. Una actividad externa no puede abrir
Checkout ni aunque alguien llame a la RPC a mano.

### Marcar y desmarcar pagado

`mark_slot_paid` no cambia: sigue exigiendo `status = 'reserved'` y sigue
dejando la fila `payments(method='manual', status='succeeded')`.

Nueva RPC `unmark_slot_paid(p_slot_id uuid) RETURNS boolean`:

1. Solo admin de la comunidad. Si no, excepción.
2. **Falla con `paid_with_stripe`** si la plaza tiene algún pago
   `method = 'stripe'` en `succeeded`, `awaiting_substitute`, `refund_pending`,
   `refunded` o `refund_owed`.
3. Borra la fila `payments` manual de esa plaza. Un cobro manual es un apunte del
   admin, no dinero que haya pasado por Agora: no hace falta un estado "anulado"
   que luego haya que filtrar en cinco consultas.
4. Si la plaza estaba liberada esperando sustituto, **cancela también la
   liberación**: `released_at = NULL`, `offered_to = NULL`,
   `offer_expires_at = NULL`, y notifica al suplente que tenía la oferta viva.
5. La plaza vuelve a `status = 'reserved'`.

En la UI va en el menú de overflow de la plaza, no como botón suelto: mueve
dinero y no debe estar a un toque de distancia.

### Liberar una plaza

Estado de partida por modo:

| Modo efectivo | Plaza `reserved` (no pagada) | Plaza `paid` |
|---|---|---|
| `free` | libera y promociona al instante | n/a |
| `external` | libera y promociona al instante | **nuevo**: queda liberada y **sigue siendo suya**; el primero que la ocupa lo hace al instante |
| `agora` | libera y promociona al instante | liberada + oferta de 6 h + el sustituto paga en Checkout. Sin cambios |

`release_slot` **no cambia de lógica**: su rama de plaza pagada ya sirve para los
dos modos de pago, y su rama normal ya cubre "si no está pagada, se libera del
todo en el acto".

#### El bug que esto arregla

Hoy, en una actividad de pago **sin cobrador**, una plaza pagada y liberada no la
puede ocupar nadie. El botón "Ocupar plaza" llama a `createCheckout`, que falla
con `payments_not_enabled`, y el cliente cae a `reserve_slot`, que exige
`status = 'available'` mientras la plaza está en `paid`. Devuelve `false` y el
usuario ve "La plaza ya no está disponible" para siempre. Callejón sin salida.

#### Las dos funciones que cambian

**`promote_substitute`** bifurca hoy por `price_cents IS NULL`. Pasa a bifurcar
por `activity_payment_mode(...) <> 'agora'`, de modo que **`external` entra por la
rama instantánea** igual que las gratuitas. Sobre una plaza liberada, esa rama
tiene que hacer el traspaso completo, no solo asignar el dueño.

**`reserve_slot`** exige hoy `status = 'available'`. Se amplía a:

```
status = 'available'
  OR (status = 'paid' AND released_at IS NOT NULL
      AND reserved_by IS DISTINCT FROM auth.uid()
      AND activity_payment_mode(activity_id) <> 'agora')
```

con el mismo traspaso. Ése es el botón "Ocupar plaza" del modo externo. La
comprobación de prioridad de la cola de suplentes que ya tiene se mantiene
intacta.

#### El traspaso, en un solo sitio

La regla de qué pasa con el dinero del ocupante anterior ya existe, escrita en
TypeScript, en `supabase/functions/_shared/payments.ts:93`. Duplicarla en SQL es
pedir que un día divierjan y alguien se quede sin su devolución — el propio
fichero abre con esa advertencia.

Se extrae a una función SQL única:

```sql
CREATE FUNCTION settle_previous_occupant(
    p_slot_id uuid,
    p_exclude_payment_id uuid DEFAULT NULL
) RETURNS void
```

Para cada pago de esa plaza en `awaiting_substitute` distinto del excluido:

| `method` | Nuevo estado | Aviso al anterior | Aviso a los admins |
|---|---|---|---|
| `stripe` | `refund_pending` (lo devuelve el barrido) | `payment_refunded` — "Plaza ocupada, te devolvemos el dinero" | ninguno: se devuelve solo |
| `manual` | `refund_owed` | `refund_owed` — "Plaza ocupada. El organizador te devolverá el dinero" | `refund_owed` — "Devuélvele 6,50 € a Fulano" |

`refund_reason = 'substitute'` en ambos casos.

El tipo de notificación `refund_owed` **ya existe** en el enum de Kotlin, en el
CHECK de Postgres y en los strings (`"Pendiente de devolver"`), y hoy no lo emite
nadie. Se creó para esto.

Llamantes de `settle_previous_occupant`:

- `promote_substitute` y `reserve_slot`, en el traspaso instantáneo del modo
  externo.
- `applyPaymentSucceeded` en `_shared/payments.ts`, que sustituye su bucle
  actual por una llamada `rpc("settle_previous_occupant", ...)`. Con esto el
  aviso al creador llega también en modo `agora` cuando el ocupante anterior era
  un invitado que había pagado a mano, que hoy se pierde.

El aviso de deuda va a **todos los admins de la comunidad**, no solo a quien creó
la actividad: quien crea las actividades no es necesariamente quien lleva el
dinero, y un aviso que llega a la persona equivocada es un aviso perdido. Una
fila de `notifications` por admin, del mismo modo que ya hace
`guest_request_received`.

### Invitados

Un invitado nunca pasa por Checkout, sea cual sea el modo de la actividad. Su
plaza la aprueba un admin y la marca —y ahora desmarca— pagada a mano. Ya es el
comportamiento actual; queda escrito para que no se "arregle" por error.

### Cancelar actividad

**Sin cambios.** `activity_cancellation_preview` ya separa cobros de Stripe de
cobros a mano. En modo `external` todos son `manual`, así que la lista entera
sale como deuda nominal del admin, que es lo correcto.

### Barrido y retenciones

Solo aplican al modo `agora`. Sin cambios.

## Qué se prueba

**Puro, en `core/model/commonTest`:**

- `effectiveMode()`: la matriz completa de `payment_mode` × `chargesEnabled`.
- La decodificación tolerante del enum: un modo desconocido cae a `external` y no
  tumba la lista entera de actividades, igual que `SlotStatus`.

**En SQL, script de comprobación `docs/payment_modes_check.sql`,** al estilo de
los que ya hay en `docs/`:

1. Externo: reservar, marcar pagado, desmarcar; comprobar que la fila de
   `payments` desaparece y la plaza vuelve a `reserved`.
2. Externo: liberar una plaza pagada, comprobar que sigue en `paid` con
   `released_at`; ocuparla desde otra cuenta y comprobar el traspaso, el
   `refund_owed` y las **dos** notificaciones.
3. Externo: liberar una plaza **no** pagada y comprobar que queda `available` en
   el acto.
4. Desmarcar una plaza liberada: comprobar que la liberación se cancela.
5. Desmarcar una plaza pagada por Stripe: comprobar que falla con
   `paid_with_stripe`.
6. Degradación: quitar `stripe_charges_enabled` a la comunidad y comprobar que
   una actividad `agora` deja de abrir Checkout y se comporta como externa.

## Lo que este diseño NO hace

- No permite cambiar el modo de una actividad ya creada.
- No permite desmarcar un cobro de Stripe.
- No permite que un invitado pague por Stripe.
- No toca el barrido, los webhooks ni el alta de Connect.
