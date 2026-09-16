-- ============================================================================
-- Fuera el formulario de invitado (web /a/ con sesion anonima).
--
-- El link de actividad sigue existiendo, pero ahora exige iniciar sesion: miembro
-- -> detalle de la actividad; no miembro -> pedir unirse a la comunidad. Apuntar
-- a alguien sin cuenta queda solo en manos de un admin (admin_assign_slot con
-- guest_label).
--
-- 1) Las solicitudes pendientes se convierten en lo que habria hecho un admin:
--    plaza 'reserved' sin dueno y con guest_label = nombre del invitado.
-- 2) request_guest_slot deja de aceptar solicitudes (apps viejas y web cacheada).
-- ============================================================================

BEGIN;

DO $$
DECLARE
    v_req RECORD;
    v_slot_id uuid;
    v_converted boolean;
BEGIN
    FOR v_req IN
        SELECT * FROM activity_guest_requests
        WHERE status = 'pending'
        ORDER BY requested_at
        FOR UPDATE
    LOOP
        v_slot_id := v_req.slot_id;

        -- Con posiciones no se retenia plaza: se busca una libre compatible, con el
        -- mismo criterio que approve_guest_request (la mas especifica primero).
        IF v_slot_id IS NULL AND v_req.requested_position_ids IS NOT NULL THEN
            SELECT s.id INTO v_slot_id
            FROM slots s
            JOIN slot_positions sp ON sp.slot_id = s.id
            WHERE s.activity_id = v_req.activity_id
              AND s.status = 'available'
              AND sp.position_id = ANY(v_req.requested_position_ids)
            GROUP BY s.id, s.sort_order
            ORDER BY count(sp.position_id) ASC, s.sort_order ASC
            LIMIT 1;

            IF v_slot_id IS NULL THEN
                -- Sin hueco compatible no hay nada que convertir.
                UPDATE activity_guest_requests
                SET status = 'cancelled', resolved_at = now()
                WHERE id = v_req.id;
                CONTINUE;
            END IF;
        END IF;

        UPDATE slots
        SET status = 'reserved', reserved_by = NULL, is_guest = false,
            guest_label = v_req.guest_name, reserved_at = now()
        WHERE id = v_slot_id
          AND (status = 'available' OR (status = 'pending' AND reserved_by = v_req.user_id));
        v_converted := FOUND;

        UPDATE activity_guest_requests
        SET status = CASE WHEN v_converted THEN 'approved' ELSE 'cancelled' END,
            slot_id = v_slot_id, resolved_at = now()
        WHERE id = v_req.id;
    END LOOP;
END;
$$;

-- Misma firma exacta que 20260629120000: CREATE OR REPLACE con otra crearia una
-- funcion nueva y dejaria viva la vieja.
CREATE OR REPLACE FUNCTION "public"."request_guest_slot"("p_code" "text", "p_name" "text", "p_email" "text", "p_position_ids" "uuid"[] DEFAULT NULL::"uuid"[]) RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
BEGIN
    RAISE EXCEPTION 'Guest requests are disabled: sign in to join the community';
END;
$$;

COMMIT;
