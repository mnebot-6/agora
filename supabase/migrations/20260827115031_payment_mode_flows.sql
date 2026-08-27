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
