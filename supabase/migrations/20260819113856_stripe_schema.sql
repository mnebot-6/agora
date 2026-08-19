-- ============================================================================
-- Pagos con Stripe: esquema.
--
-- Solo esquema. Ninguna funcion cambia aqui, asi que aplicar esta migracion no
-- altera el comportamiento de nada: sin price_cents las actividades siguen
-- siendo gratuitas y todo el flujo actual queda intacto.
--
-- REQUISITO DE ORDEN: la app con SlotStatus tolerante (commit bb0c8f0) tiene
-- que estar PUBLICADA en Play y en web antes de aplicar esto a produccion. El
-- enum era cerrado y 'pending_payment' tumba la lista entera de plazas en
-- cualquier cliente que no se haya actualizado.
--
-- Diseno: docs/superpowers/specs/2026-08-19-stripe-design.md
-- ============================================================================

BEGIN;

-- ---------- Precio de la actividad -----------------------------------------

-- NULL = gratuita. Es el discriminante de todo el diseno: con NULL no cambia
-- absolutamente nada respecto a hoy.
ALTER TABLE activities ADD COLUMN price_cents integer;

ALTER TABLE activities ADD CONSTRAINT activities_price_positive
  CHECK (price_cents IS NULL OR price_cents > 0);

-- cost_description NO se borra aqui. Se deja de escribir y se sigue leyendo en
-- la pantalla de editar como ayuda para que el admin reescriba el importe. Se
-- elimina en una migracion posterior, cuando no quede nadie usandolo.

-- ---------- Cuenta conectada de la comunidad -------------------------------

ALTER TABLE communities ADD COLUMN stripe_account_id text UNIQUE;
ALTER TABLE communities ADD COLUMN stripe_charges_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE communities ADD COLUMN stripe_details_submitted boolean NOT NULL DEFAULT false;
ALTER TABLE communities ADD COLUMN stripe_onboarded_at timestamptz;

COMMENT ON COLUMN communities.stripe_charges_enabled IS
  'Esta comunidad puede cobrar. Lo mantiene al dia el webhook account.updated.';

-- ---------- Estados nuevos de la plaza -------------------------------------

ALTER TABLE slots DROP CONSTRAINT slots_status_check;
ALTER TABLE slots ADD CONSTRAINT slots_status_check
  CHECK (status IN ('available', 'reserved', 'paid', 'pending', 'pending_payment'));

-- Retencion de pago: alguien tiene el Checkout abierto sobre esta plaza.
-- reserved_by es quien esta pagando. Dura lo mismo que la sesion de Stripe.
ALTER TABLE slots ADD COLUMN hold_expires_at timestamptz;

-- Plaza pagada por su dueno y ofrecida a un sustituto. La plaza SIGUE SIENDO
-- SUYA: puede presentarse el dia de la actividad. Lo unico que cambia es que
-- otra persona puede reclamarla. Invariante del diseno:
--   si pagaste, no pierdes la plaza hasta que otro la pague.
ALTER TABLE slots ADD COLUMN released_at timestamptz;

-- Ventana de 6 h del suplente. No es una retencion de pago: no hay sesion de
-- Checkout todavia ni dinero por medio, es exclusividad para que le de tiempo
-- a enterarse y decidir.
-- FK a profiles, no a auth.users: es la convencion del resto del esquema
-- (slots.reserved_by, substitute_queue.user_id, notifications.user_id...).
-- SET NULL para que borrar la cuenta no falle: delete_my_account() borra
-- auth.users, que cascadea a profiles.
ALTER TABLE slots ADD COLUMN offered_to uuid
  REFERENCES profiles(id) ON DELETE SET NULL;
ALTER TABLE slots ADD COLUMN offer_expires_at timestamptz;

-- Una direccion sola, a proposito. Lo peligroso es una oferta que no caduca
-- nunca; una fecha huerfana sin ofertado es inofensiva (el barrido solo mira
-- filas con offered_to, y sin ofertado la plaza vuelve a ser de cualquiera).
-- La version bidireccional reventaba al borrar la cuenta del ofertado: el SET
-- NULL de arriba dejaba offer_expires_at puesto y violaba el CHECK.
ALTER TABLE slots ADD CONSTRAINT slots_offer_has_expiry
  CHECK (offered_to IS NULL OR offer_expires_at IS NOT NULL);

CREATE INDEX slots_offer_sweep ON slots (offer_expires_at)
  WHERE offered_to IS NOT NULL;

CREATE INDEX slots_hold_sweep ON slots (hold_expires_at)
  WHERE status = 'pending_payment';

-- ---------- payments --------------------------------------------------------

CREATE TABLE payments (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    -- RESTRICT, no CASCADE. Las actividades y las comunidades SI se borran en
    -- duro desde la app (ActivityDetailScreen:473, CommunityDetailScreen:363), y
    -- con CASCADE ese boton destruiria en silencio el registro de dinero:
    -- cobrado en Stripe, sin rastro en Agora, y cualquier refund_pending sin
    -- ejecutar perdido para siempre. Con RESTRICT el borrado falla con un error
    -- claro y la via correcta es cancelar la actividad (que reembolsa) y luego
    -- borrarla. La base de datos lo garantiza, asi que ningun camino del codigo
    -- puede saltarselo.
    activity_id           uuid NOT NULL REFERENCES activities(id) ON DELETE RESTRICT,
    slot_id               uuid REFERENCES slots(id) ON DELETE SET NULL,

    -- Nullable y SET NULL a proposito. delete_my_account() borra auth.users, que
    -- cascadea a profiles. Con NOT NULL sin cascada, borrar la cuenta FALLARIA;
    -- con CASCADE se llevaria por delante el registro de dinero, incluido un
    -- refund_pending todavia sin ejecutar, y esa persona perderia su devolucion.
    -- Con SET NULL sobrevive el importe y los ids de Stripe, que es lo que hace
    -- falta para reembolsar y para cuadrar cuentas. Mismo criterio que
    -- slots.reserved_by.
    user_id               uuid REFERENCES profiles(id) ON DELETE SET NULL,

    community_id          uuid NOT NULL REFERENCES communities(id) ON DELETE RESTRICT,

    -- Foto del importe en el momento del cobro. Si el admin cambia el precio
    -- despues, los pagos ya hechos NO se mueven. Un registro de dinero no se
    -- recalcula nunca.
    amount_cents          integer NOT NULL CHECK (amount_cents > 0),
    application_fee_cents integer NOT NULL DEFAULT 0 CHECK (application_fee_cents >= 0),

    -- De esta columna depende si un reembolso se puede automatizar. Los cobros
    -- manuales (Bizum, efectivo) tambien generan fila, para que la lista de
    -- deudas al cancelar salga de una sola consulta.
    method                text NOT NULL CHECK (method IN ('stripe', 'manual')),

    status                text NOT NULL CHECK (status IN (
                              'pending',              -- Checkout abierto
                              'succeeded',            -- cobrado
                              'failed',               -- rechazado
                              'expired',              -- caduco sin pagar
                              'awaiting_substitute',  -- plaza liberada, esperando quien la pague
                              'refund_pending',       -- hay que devolverlo (lo hace el worker)
                              'refunded',             -- devuelto por Stripe
                              'refund_owed'           -- hay que devolverlo A MANO (no paso por Stripe)
                          )),

    connected_account_id  text,
    checkout_session_id   text UNIQUE,
    payment_intent_id     text UNIQUE,
    charge_id             text,

    refund_reason         text CHECK (refund_reason IN ('substitute', 'activity_cancelled')),
    refund_attempts       integer NOT NULL DEFAULT 0,
    failure_code          text,

    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now()
);

-- EL CERROJO. Como mucho un cobro abierto por plaza, garantizado por la base de
-- datos y no por el codigo de aplicacion. Dos personas que salen a pagar la
-- misma plaza a la vez: la segunda INSERT viola este indice y recibe
-- 'slot_being_paid' antes de ver ninguna pantalla de pago.
CREATE UNIQUE INDEX payments_one_pending_per_slot
  ON payments (slot_id) WHERE status = 'pending';

CREATE INDEX payments_activity ON payments (activity_id);
CREATE INDEX payments_user ON payments (user_id);
CREATE INDEX payments_sweep ON payments (status)
  WHERE status IN ('pending', 'refund_pending');

COMMENT ON INDEX payments_one_pending_per_slot IS
  'Cerrojo contra pagos duplicados sobre la misma plaza. No quitar.';

-- Un pago de Stripe siempre sabe a que cuenta conectada fue.
ALTER TABLE payments ADD CONSTRAINT payments_stripe_has_account
  CHECK (method <> 'stripe' OR connected_account_id IS NOT NULL);

-- ---------- stripe_events (deduplicacion de webhooks) -----------------------

-- El manejador INSERTA PRIMERO. Si viola la clave primaria, el evento ya se
-- proceso: responde 200 y no hace nada mas. Es la deduplicacion mas barata que
-- existe y no depende de que el resto del codigo sea perfecto.
CREATE TABLE stripe_events (
    id          text PRIMARY KEY,
    type        text NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now()
);

-- ---------- RLS -------------------------------------------------------------
-- El event trigger rls_auto_enable ya activa RLS en cualquier tabla nueva de
-- public, asi que aqui solo van las politicas.

-- Ni INSERT ni UPDATE para nadie: payments solo lo escribe el service_role
-- desde las Edge Functions y las RPC SECURITY DEFINER.

DROP POLICY IF EXISTS "Users see own payments" ON payments;
CREATE POLICY "Users see own payments"
    ON payments FOR SELECT
    TO authenticated
    USING (user_id = auth.uid());

DROP POLICY IF EXISTS "Admins see community payments" ON payments;
CREATE POLICY "Admins see community payments"
    ON payments FOR SELECT
    TO authenticated
    USING (community_id IN (SELECT get_my_admin_community_ids()));

-- stripe_events se queda sin politicas: solo service_role.

-- ---------- updated_at ------------------------------------------------------

CREATE OR REPLACE FUNCTION public.touch_payments_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'public', 'pg_temp'
    AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER payments_touch_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION public.touch_payments_updated_at();

COMMIT;
