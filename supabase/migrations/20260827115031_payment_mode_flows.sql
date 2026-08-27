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
                    '. El organizador te devolverá ' || v_amount || '.',
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
                    'Devuélvele ' || v_amount || ' a ' || coalesce(v_name, 'alguien') ||
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
