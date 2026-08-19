-- ============================================================================
-- begin_slot_payment: reclamar una plaza de pago y abrir el cobro, atomico.
--
-- Todo el trabajo peligroso (validar, comprobar la cola, retener la plaza y
-- abrir el pago) ocurre en UNA transaccion de Postgres. La Edge Function solo
-- crea despues la sesion de Stripe con lo que esta funcion le devuelve.
--
-- Hacerlo al reves — leer y escribir por pasos desde la Edge Function — dejaria
-- huecos entre lectura y escritura por los que se cuelan dos personas sobre la
-- misma plaza. Aqui el FOR UPDATE y el indice unico parcial lo impiden.
-- ============================================================================

BEGIN;

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
    IF v_activity.price_cents IS NULL THEN
        RAISE EXCEPTION 'activity_is_free';
    END IF;

    SELECT * INTO v_community FROM communities WHERE id = v_activity.community_id;
    IF v_community.stripe_account_id IS NULL OR NOT v_community.stripe_charges_enabled THEN
        -- El cliente cae a reserve_slot y el admin cobra a mano, que es la decision
        -- para comunidades que todavia no han completado su alta.
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

ALTER FUNCTION public.begin_slot_payment(uuid) OWNER TO postgres;
REVOKE ALL ON FUNCTION public.begin_slot_payment(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.begin_slot_payment(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.begin_slot_payment(uuid) TO service_role;

COMMIT;
