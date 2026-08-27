-- ============================================================================
-- Flujos de los tres modos de pago.
--
-- En modo 'external' Agora no mueve dinero, asi que ocupar una plaza liberada es
-- INSTANTANEO: no hay Checkout que abrir. Pero el traspaso contable es el mismo
-- que en 'agora', y esa regla ya existia escrita en TypeScript
-- (supabase/functions/_shared/payments.ts). Duplicarla en SQL era pedir que un
-- dia divergieran y alguien se quedara sin su devolucion, asi que la regla pasa
-- a vivir aqui y la Edge Function la llamara por RPC en una tarea posterior.
-- Hasta entonces conviven las dos implementaciones: la de TypeScript atiende el
-- traspaso de Stripe y esta atiende el del modo externo.
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
    v_name text;
    v_amount text;
BEGIN
    -- Todos los pagos de una plaza son de la misma actividad: se lee una vez, no
    -- una por vuelta del bucle.
    SELECT a.* INTO v_activity
    FROM activities a JOIN slots s ON s.activity_id = a.id
    WHERE s.id = p_slot_id;
    IF NOT FOUND THEN
        RETURN;
    END IF;

    FOR v_prev IN
        SELECT p.id, p.method, p.user_id, p.amount_cents, p.activity_id
        FROM payments p
        WHERE p.slot_id = p_slot_id
          AND p.status = 'awaiting_substitute'
          AND (p_exclude_payment_id IS NULL OR p.id <> p_exclude_payment_id)
        FOR UPDATE
    LOOP
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
            SELECT display_name INTO v_name
            FROM profiles WHERE id = v_prev.user_id;

            INSERT INTO notifications (user_id, type, title, body, data)
            SELECT cm.user_id, 'refund_owed', 'Hay que devolver un pago',
                   'Devuélvele ' || v_amount || ' a ' || coalesce(v_name, 'alguien') ||
                   ': han ocupado su plaza en ' || v_activity.name || '.',
                   jsonb_build_object(
                       'activity_id', v_activity.id,
                       'slot_id', p_slot_id,
                       'payment_id', v_prev.id
                   )
            FROM community_members cm
            WHERE cm.community_id = v_activity.community_id AND cm.role = 'admin';
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
            'La plaza que te habían ofrecido en ' || v_activity.name ||
            ' ya no está disponible.',
            jsonb_build_object('activity_id', v_activity.id, 'slot_id', p_slot_id)
        );
    END IF;

    RETURN TRUE;
END;
$$;

GRANT EXECUTE ON FUNCTION public.unmark_slot_paid(uuid) TO authenticated;

-- ---------- begin_slot_payment ----------------------------------------------
-- Base: la version viva de 20260819190000_begin_slot_payment.sql. Solo cambia
-- el bloque que decide si se puede cobrar; el resto es identico.

CREATE OR REPLACE FUNCTION public.begin_slot_payment(p_slot_id uuid)
    RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_slot RECORD;
    v_activity RECORD;
    v_community RECORD;
    v_slot_positions uuid[];
    v_first_in_queue RECORD;
    v_payment_id uuid;
    v_claimable boolean;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'not_authenticated';
    END IF;

    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'slot_not_found';
    END IF;

    -- Si esta persona YA tiene un cobro abierto sobre esta plaza, se le devuelve el suyo
    -- en vez de un error. Pasa con el doble clic y al volver a la app tras minimizarla.
    -- Sin esto se quedaba mirando "no disponible" durante 30 minutos por su propia
    -- retencion, que es de las cosas mas absurdas que puede vivir un usuario.
    SELECT p.id INTO v_payment_id
    FROM payments p
    WHERE p.slot_id = p_slot_id AND p.user_id = v_user_id AND p.status = 'pending';

    IF v_payment_id IS NOT NULL THEN
        SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;
        SELECT * INTO v_community FROM communities WHERE id = v_activity.community_id;
        RETURN jsonb_build_object(
            'payment_id', v_payment_id,
            'amount_cents', v_activity.price_cents,
            'connected_account_id', v_community.stripe_account_id,
            'activity_id', v_activity.id,
            'activity_name', v_activity.name,
            'resumed', true
        );
    END IF;

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

    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id AND user_id = v_user_id
    ) THEN
        RAISE EXCEPTION 'not_a_member';
    END IF;

    -- Reclamable: libre, o pagada por OTRO y liberada esperando sustituto.
    v_claimable := v_slot.status = 'available'
        OR (v_slot.status = 'paid'
            AND v_slot.released_at IS NOT NULL
            AND v_slot.reserved_by IS DISTINCT FROM v_user_id);

    IF NOT v_claimable THEN
        RAISE EXCEPTION 'slot_not_claimable';
    END IF;

    -- Oferta viva a nombre de otra persona.
    IF v_slot.offered_to IS NOT NULL
       AND v_slot.offer_expires_at > now()
       AND v_slot.offered_to <> v_user_id THEN
        RAISE EXCEPTION 'slot_offered_to_someone_else';
    END IF;

    -- Prioridad de la cola de suplentes. Se salta si la plaza esta apalabrada para el
    -- llamante: esa oferta ES su turno, y volver a mirar la cola lo bloquearia a el mismo.
    -- Misma logica de posiciones que reserve_slot; si cambia alli, cambia aqui.
    IF v_slot.offered_to IS DISTINCT FROM v_user_id THEN
        SELECT array_agg(sp.position_id) INTO v_slot_positions
        FROM slot_positions sp WHERE sp.slot_id = p_slot_id;

        IF v_slot_positions IS NOT NULL AND array_length(v_slot_positions, 1) > 0 THEN
            SELECT * INTO v_first_in_queue
            FROM substitute_queue
            WHERE activity_id = v_slot.activity_id
              AND (position_id = ANY(v_slot_positions) OR position_id IS NULL)
            ORDER BY queued_at ASC LIMIT 1;
        ELSE
            SELECT * INTO v_first_in_queue
            FROM substitute_queue
            WHERE activity_id = v_slot.activity_id
            ORDER BY queued_at ASC LIMIT 1;
        END IF;

        IF v_first_in_queue IS NOT NULL AND v_first_in_queue.user_id <> v_user_id THEN
            RAISE EXCEPTION 'queue_priority';
        END IF;
    END IF;

    -- EL CERROJO. Si otro tiene el Checkout abierto sobre esta plaza, el indice unico
    -- parcial payments_one_pending_per_slot revienta aqui y nadie paga dos veces.
    BEGIN
        INSERT INTO payments (
            activity_id, slot_id, user_id, community_id,
            amount_cents, method, status, connected_account_id
        ) VALUES (
            v_slot.activity_id, p_slot_id, v_user_id, v_activity.community_id,
            v_activity.price_cents, 'stripe', 'pending', v_community.stripe_account_id
        ) RETURNING id INTO v_payment_id;
    EXCEPTION WHEN unique_violation THEN
        RAISE EXCEPTION 'slot_being_paid';
    END;

    -- La plaza solo se retiene si estaba libre. Si estaba liberada NO se toca: sigue
    -- siendo de su dueno hasta que este pago se confirme. Es el invariante del diseno.
    IF v_slot.status = 'available' THEN
        UPDATE slots
        SET status = 'pending_payment',
            reserved_by = v_user_id,
            reserved_at = now(),
            hold_expires_at = now() + interval '30 minutes'
        WHERE id = p_slot_id;
    END IF;

    RETURN jsonb_build_object(
        'payment_id', v_payment_id,
        'amount_cents', v_activity.price_cents,
        'connected_account_id', v_community.stripe_account_id,
        'activity_id', v_activity.id,
        'activity_name', v_activity.name
    );
END;
$$;

COMMIT;
