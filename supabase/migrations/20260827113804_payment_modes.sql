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
        -- Basta con que haya cuenta conectada. Mirar stripe_charges_enabled aqui
        -- seria sellar 'external' para siempre en una comunidad que solo tuviera
        -- el cobro suspendido ese minuto: activity_payment_mode ya degrada en
        -- caliente, y del 'external' no se sale porque el modo es fijo.
        WHEN c.stripe_account_id IS NOT NULL THEN 'agora'
        ELSE 'external'
    END
FROM communities c
WHERE c.id = a.community_id;

-- Impide los dos estados incoherentes: de pago sin importe, y gratuita con
-- importe. Lo garantiza la base de datos, no la app.
ALTER TABLE activities ADD CONSTRAINT activities_mode_matches_price
  CHECK ((payment_mode = 'free') = (price_cents IS NULL));

-- ---------- Compatibilidad con los clientes ya instalados -------------------
-- Las apps que ya estan en Play y en la web no mandan payment_mode: escriben
-- price_cents y punto. Con la restriccion de arriba, crear o editar una
-- actividad de pago desde una de ellas fallaria con un error de Postgres en
-- crudo, y esos clientes no se actualizan el dia que se aplica esto.
--
-- El trigger solo toca pares INCOHERENTES, que es lo unico que produce un
-- cliente viejo. Uno nuevo manda siempre modo e importe de acuerdo, asi que
-- nunca lo dispara y su eleccion se respeta tal cual.
--
-- Consecuencia asumida: desde un cliente viejo se puede seguir pasando una
-- actividad de pago a gratuita y al reves, aunque el modo sea fijo al crear en
-- la app nueva. Es exactamente el comportamiento de hoy, que es lo que se busca.
-- SECURITY DEFINER porque el EXISTS de abajo no puede depender de la RLS del
-- invocante: su resultado es irreversible (el modo es fijo al crear).
CREATE OR REPLACE FUNCTION public.activities_derive_payment_mode()
    RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
BEGIN
    IF NEW.price_cents IS NULL AND NEW.payment_mode <> 'free' THEN
        NEW.payment_mode := 'free';
    ELSIF NEW.price_cents IS NOT NULL AND NEW.payment_mode = 'free' THEN
        -- Solo se mira que haya cuenta conectada, no que pueda cobrar ahora
        -- mismo: activity_payment_mode ya degrada en caliente, y 'external' es
        -- una puerta de un solo sentido porque el modo no se puede cambiar.
        NEW.payment_mode := CASE
            WHEN EXISTS (
                SELECT 1 FROM communities c
                WHERE c.id = NEW.community_id AND c.stripe_account_id IS NOT NULL
            ) THEN 'agora'
            ELSE 'external'
        END;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS activities_derive_payment_mode ON activities;
CREATE TRIGGER activities_derive_payment_mode
    BEFORE INSERT OR UPDATE ON activities
    FOR EACH ROW EXECUTE FUNCTION public.activities_derive_payment_mode();

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
