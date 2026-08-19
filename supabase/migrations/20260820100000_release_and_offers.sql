-- ============================================================================
-- Fase 4: liberar una plaza pagada, ofertas a suplentes y cobro manual.
--
-- EL INVARIANTE, que gobierna todo lo de abajo:
--     si pagaste, no pierdes la plaza hasta que otro la pague.
--
-- Liberar una plaza pagada NO la deja libre. La deja marcada con released_at,
-- seguida en manos de su dueno (puede presentarse el dia de la actividad) y
-- reclamable por otro. Solo cuando el pago del sustituto se confirma cambia de
-- dueno y se dispara el reembolso al anterior. Si nadie la reclama nunca, se
-- queda como esta: sigue siendo suya y no recupera el dinero, que es
-- exactamente la regla acordada.
-- ============================================================================

BEGIN;

-- ---------- Tipos de notificacion nuevos ------------------------------------
-- El enum de Kotlin ya los tenia (commit bb0c8f0) pero Postgres NO: hay un CHECK
-- con la lista cerrada. Sin esto, release_slot sobre una plaza pagada revienta
-- ENTERO al intentar avisar al suplente, y la liberacion se deshace con el
-- rollback. Lo cazo el test de la fase 4, no la lectura del codigo.

ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
  CHECK (type = ANY (ARRAY[
      'new_activity', 'slot_released', 'substitute_promoted', 'join_request_received',
      'join_request_approved', 'join_request_rejected', 'activity_reminder',
      'guest_request_received', 'guest_request_approved', 'guest_request_rejected',
      'payment_confirmed', 'slot_removed', 'activity_full', 'activity_cancelled',
      'activity_updated', 'slot_assigned',
      -- Nuevos con los pagos:
      'substitute_offer', 'payment_refunded', 'refund_owed'
  ]));

-- ---------- release_slot ----------------------------------------------------
-- Base: la version viva de 20260731065916_admin_assign_slots.sql. Misma firma,
-- para que CREATE OR REPLACE sustituya en vez de crear una funcion nueva.

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
    v_paid_payment RECORD;
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

    -- Una plaza con el Checkout abierto no se libera: la suelta el retorno, el
    -- webhook o el barrido cuando se sepa si el pago salio adelante.
    IF v_slot.status = 'pending_payment' THEN
        RAISE EXCEPTION 'Cannot release a slot while its payment is in progress';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;

    SELECT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_user_id AND role = 'admin'
    ) INTO v_is_admin;

    IF v_slot.reserved_by IS NULL THEN
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
    ELSE
        IF NOT v_is_admin THEN
            RAISE EXCEPTION 'Only an admin can release a pending guest slot';
        END IF;
    END IF;

    -- ------------------------------------------------------------------
    -- RAMA NUEVA: plaza PAGADA con dueno. No se suelta, se pone en oferta.
    -- ------------------------------------------------------------------
    SELECT * INTO v_paid_payment
    FROM payments
    WHERE slot_id = p_slot_id AND status = 'succeeded'
    ORDER BY created_at DESC LIMIT 1;

    IF v_slot.status = 'paid' AND v_slot.reserved_by IS NOT NULL THEN
        UPDATE slots SET released_at = now() WHERE id = p_slot_id;

        -- Se aplica igual al cobro por Stripe y al marcado a mano. En el manual
        -- Agora no puede devolver nada, pero la plaza se comporta igual y al
        -- llegar el sustituto el anterior acaba en la lista de deudas del admin.
        IF v_paid_payment.id IS NOT NULL THEN
            UPDATE payments SET status = 'awaiting_substitute' WHERE id = v_paid_payment.id;
        END IF;

        v_promoted := promote_substitute(p_slot_id, v_activity.id);

        IF NOT v_promoted THEN
            INSERT INTO notifications (user_id, type, title, body, data)
            SELECT sq.user_id, 'slot_released', 'Plaza disponible',
                   'Se ha liberado una plaza en ' || v_activity.name,
                   jsonb_build_object('activity_id', v_activity.id, 'slot_id', p_slot_id)
            FROM substitute_queue sq WHERE sq.activity_id = v_activity.id;
        END IF;

        RETURN TRUE;
    END IF;

    -- ------------------------------------------------------------------
    -- Comportamiento de siempre para todo lo demas.
    -- ------------------------------------------------------------------
    UPDATE slots
    SET status = 'available', reserved_by = NULL, reserved_at = NULL,
        guest_label = NULL, is_guest = false, released_at = NULL,
        offered_to = NULL, offer_expires_at = NULL
    WHERE id = p_slot_id;

    IF v_slot.reserved_by IS NOT NULL AND v_slot.reserved_by <> v_user_id THEN
        INSERT INTO notifications (user_id, type, title, body, data)
        VALUES (
            v_slot.reserved_by, 'slot_removed', 'Plaza cancelada',
            'Tu plaza en ' || v_activity.name || ' ha sido cancelada por un administrador.',
            jsonb_build_object('activity_id', v_activity.id, 'slot_id', p_slot_id)
        );
    END IF;

    v_promoted := promote_substitute(p_slot_id, v_activity.id);

    IF NOT v_promoted THEN
        INSERT INTO notifications (user_id, type, title, body, data)
        SELECT sq.user_id, 'slot_released', 'Plaza disponible',
               'Se ha liberado una plaza en una actividad',
               jsonb_build_object('activity_id', v_activity.id, 'slot_id', p_slot_id)
        FROM substitute_queue sq WHERE sq.activity_id = v_activity.id;
    END IF;

    RETURN TRUE;
END;
$$;

-- ---------- promote_substitute ----------------------------------------------
-- En actividades GRATUITAS asigna en el acto, como siempre. En las de pago no
-- puede: hay que cobrar antes. Apalabra la plaza 6 h y avisa.

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

    -- Gratuita: comportamiento de siempre, sin ofertas ni ventanas.
    IF v_activity.price_cents IS NULL THEN
        UPDATE slots
        SET status = 'reserved', reserved_by = v_sub.user_id, reserved_at = now()
        WHERE id = p_slot_id;

        DELETE FROM substitute_queue
        WHERE activity_id = p_activity_id AND user_id = v_sub.user_id;

        PERFORM notify_substitute_promoted(v_sub.user_id, p_activity_id, p_slot_id);
        RETURN TRUE;
    END IF;

    -- De pago: se apalabra. El tope en el comienzo de la actividad no es un
    -- detalle: una ventana de 6 h sobre un partido que empieza dentro de dos
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

-- ---------- decline_substitute_offer ----------------------------------------

CREATE OR REPLACE FUNCTION public.decline_substitute_offer(p_slot_id uuid)
    RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_slot RECORD;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF v_slot IS NULL OR v_slot.offered_to IS DISTINCT FROM v_user_id THEN
        RETURN FALSE;
    END IF;

    DELETE FROM substitute_queue
    WHERE activity_id = v_slot.activity_id AND user_id = v_user_id;

    UPDATE slots SET offered_to = NULL, offer_expires_at = NULL WHERE id = p_slot_id;

    PERFORM promote_substitute(p_slot_id, v_slot.activity_id);
    RETURN TRUE;
END;
$$;

-- ---------- expire_substitute_offers ----------------------------------------
-- SQL puro: no hay nada que preguntarle a Stripe, asi que no pasa por el
-- barrido y la ejecuta pg_cron directamente.

CREATE OR REPLACE FUNCTION public.expire_substitute_offers()
    RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_slot RECORD;
    v_count integer := 0;
BEGIN
    FOR v_slot IN
        SELECT id, activity_id, offered_to FROM slots
        WHERE offered_to IS NOT NULL AND offer_expires_at <= now()
        FOR UPDATE SKIP LOCKED
    LOOP
        -- Dejar caducar saca de la cola, igual que rechazar. Sin esto la cola se
        -- atasca: al siguiente barrido se le volveria a ofrecer al mismo, para
        -- siempre.
        DELETE FROM substitute_queue
        WHERE activity_id = v_slot.activity_id AND user_id = v_slot.offered_to;

        UPDATE slots SET offered_to = NULL, offer_expires_at = NULL WHERE id = v_slot.id;

        PERFORM promote_substitute(v_slot.id, v_slot.activity_id);
        v_count := v_count + 1;
    END LOOP;

    RETURN v_count;
END;
$$;

-- ---------- mark_slot_paid --------------------------------------------------
-- Base: la version viva de 20260630120000_more_guest_notifications.sql. Ahora
-- deja rastro en payments para que la lista de deudas al cancelar salga de una
-- sola consulta.

CREATE OR REPLACE FUNCTION public.mark_slot_paid(p_slot_id uuid) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_slot RECORD;
    v_activity RECORD;
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF v_slot IS NULL THEN
        RAISE EXCEPTION 'Slot not found';
    END IF;

    IF v_slot.status != 'reserved' THEN
        RETURN FALSE;
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;
    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_user_id AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can mark slots as paid';
    END IF;

    UPDATE slots SET status = 'paid' WHERE id = p_slot_id;

    -- Rastro del cobro manual. Solo si la actividad tiene precio: sin importe no
    -- hay nada que registrar ni que devolver.
    IF v_activity.price_cents IS NOT NULL AND v_slot.reserved_by IS NOT NULL THEN
        INSERT INTO payments (
            activity_id, slot_id, user_id, community_id,
            amount_cents, method, status
        ) VALUES (
            v_activity.id, p_slot_id, v_slot.reserved_by, v_activity.community_id,
            v_activity.price_cents, 'manual', 'succeeded'
        );
    END IF;

    IF v_slot.reserved_by IS NOT NULL THEN
        INSERT INTO notifications (user_id, type, title, body, data)
        VALUES (
            v_slot.reserved_by, 'payment_confirmed', 'Pago confirmado',
            'Tu plaza en ' || v_activity.name || ' está confirmada. ¡Te esperamos!',
            jsonb_build_object('activity_id', v_activity.id, 'slot_id', p_slot_id)
        );
    END IF;

    RETURN TRUE;
END;
$$;

-- ---------- Permisos y cron -------------------------------------------------

GRANT EXECUTE ON FUNCTION public.decline_substitute_offer(uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.expire_substitute_offers() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.expire_substitute_offers() FROM anon;
REVOKE ALL ON FUNCTION public.expire_substitute_offers() FROM authenticated;

SELECT cron.unschedule('substitute-offers-expiry')
WHERE EXISTS (SELECT 1 FROM cron.job WHERE jobname = 'substitute-offers-expiry');

SELECT cron.schedule(
    'substitute-offers-expiry',
    '* * * * *',
    'SELECT public.expire_substitute_offers()'
);

COMMIT;
