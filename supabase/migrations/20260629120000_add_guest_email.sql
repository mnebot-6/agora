-- Guest confirmations por email: sustituye el teléfono por email en el flujo de invitado.
--
-- Contexto: los invitados anónimos (web) no tienen token FCM, así que nunca recibían aviso
-- de aprobación/rechazo. Ahora recogemos su email y la edge function `notify-guest-email`
-- (disparada por el webhook de INSERT en `notifications`) les envía cualquier notificación
-- leyendo `profiles.guest_email`. El email se guarda en el perfil del usuario anónimo para
-- que el envío sea genérico (aprobado, rechazado, pago, expulsión, tipos futuros).

-- 1) Columnas nuevas -----------------------------------------------------------------

-- El email vive en la solicitud (para que el admin lo vea) y en el perfil (para el envío).
ALTER TABLE "public"."activity_guest_requests"
    ADD COLUMN IF NOT EXISTS "guest_email" "text";

-- Ya no pedimos teléfono; lo conservamos para solicitudes antiguas pero deja de ser obligatorio.
ALTER TABLE "public"."activity_guest_requests"
    ALTER COLUMN "guest_phone" DROP NOT NULL;

-- Validación de longitud del email (la CHECK pasa cuando es NULL, p. ej. filas antiguas).
ALTER TABLE "public"."activity_guest_requests"
    DROP CONSTRAINT IF EXISTS "activity_guest_requests_guest_email_check";
ALTER TABLE "public"."activity_guest_requests"
    ADD CONSTRAINT "activity_guest_requests_guest_email_check"
    CHECK ((("char_length"("guest_email") >= 3) AND ("char_length"("guest_email") <= 320)));

-- La edge function de email lee aquí por user_id, igual que push-notification lee fcm_token.
ALTER TABLE "public"."profiles"
    ADD COLUMN IF NOT EXISTS "guest_email" "text";

-- 2) request_guest_slot: p_phone -> p_email ------------------------------------------
-- Cambia el nombre de un parámetro, así que hay que DROP + CREATE (CREATE OR REPLACE no
-- permite renombrar parámetros aunque los tipos coincidan).

DROP FUNCTION IF EXISTS "public"."request_guest_slot"("text", "text", "text", "uuid"[]);

CREATE OR REPLACE FUNCTION "public"."request_guest_slot"("p_code" "text", "p_name" "text", "p_email" "text", "p_position_ids" "uuid"[] DEFAULT NULL::"uuid"[]) RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_link RECORD;
    v_activity RECORD;
    v_community RECORD;
    v_existing RECORD;
    v_slot_id uuid;
    v_next_sort int;
    v_request_id uuid;
    v_admin RECORD;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF p_name IS NULL OR length(trim(p_name)) = 0 THEN
        RAISE EXCEPTION 'Name is required';
    END IF;
    IF p_email IS NULL OR length(trim(p_email)) = 0 THEN
        RAISE EXCEPTION 'Email is required';
    END IF;

    SELECT * INTO v_link FROM activity_guest_links
    WHERE code = p_code AND NOT revoked;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Invalid or revoked link';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_link.activity_id FOR UPDATE;
    IF NOT FOUND OR v_activity.status <> 'active' THEN
        RAISE EXCEPTION 'Activity not available';
    END IF;

    SELECT * INTO v_community FROM communities WHERE id = v_activity.community_id;
    IF v_community.visibility NOT IN ('public_open', 'public_approval') THEN
        RAISE EXCEPTION 'Activity not available';
    END IF;

    IF EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id AND user_id = v_user_id
    ) THEN
        RETURN jsonb_build_object('status', 'already_member');
    END IF;

    SELECT * INTO v_existing FROM activity_guest_requests
    WHERE activity_id = v_activity.id
      AND user_id = v_user_id
      AND status IN ('pending', 'approved');
    IF FOUND THEN
        RETURN jsonb_build_object(
            'status', v_existing.status,
            'request_id', v_existing.id,
            'slot_id', v_existing.slot_id
        );
    END IF;

    -- Guarda el email en el perfil del invitado para que la edge function pueda enviarle
    -- cualquier notificación posterior (aprobación, rechazo, pago, expulsión, ...).
    UPDATE profiles SET guest_email = trim(p_email) WHERE id = v_user_id;

    -- === limited_with_positions: validate positions, no slot retention ===
    IF v_activity.slot_mode = 'limited_with_positions' THEN
        IF p_position_ids IS NULL OR array_length(p_position_ids, 1) IS NULL THEN
            RAISE EXCEPTION 'At least one position must be selected';
        END IF;

        -- Validate all position IDs belong to this activity
        IF EXISTS (
            SELECT 1 FROM unnest(p_position_ids) pid
            WHERE NOT EXISTS (
                SELECT 1 FROM positions WHERE id = pid AND activity_id = v_activity.id
            )
        ) THEN
            RAISE EXCEPTION 'Invalid position selected';
        END IF;

        -- Check at least one compatible available slot exists
        IF NOT EXISTS (
            SELECT 1
            FROM slots s
            JOIN slot_positions sp ON sp.slot_id = s.id
            WHERE s.activity_id = v_activity.id
              AND s.status = 'available'
              AND sp.position_id = ANY(p_position_ids)
        ) THEN
            RETURN jsonb_build_object('status', 'full');
        END IF;

        INSERT INTO activity_guest_requests
            (activity_id, user_id, slot_id, guest_name, guest_email, requested_position_ids)
        VALUES
            (v_activity.id, v_user_id, NULL, trim(p_name), trim(p_email), p_position_ids)
        RETURNING id INTO v_request_id;

    -- === unlimited: create slot on the fly ===
    ELSIF v_activity.slot_mode = 'unlimited' THEN
        SELECT COALESCE(max(sort_order), -1) + 1 INTO v_next_sort
        FROM slots WHERE activity_id = v_activity.id;

        INSERT INTO slots (activity_id, sort_order, status, reserved_by, reserved_at, is_guest)
        VALUES (v_activity.id, v_next_sort, 'pending', v_user_id, now(), true)
        RETURNING id INTO v_slot_id;

        INSERT INTO activity_guest_requests (activity_id, user_id, slot_id, guest_name, guest_email)
        VALUES (v_activity.id, v_user_id, v_slot_id, trim(p_name), trim(p_email))
        RETURNING id INTO v_request_id;

    -- === limited (no positions): grab first available slot ===
    ELSE
        SELECT id INTO v_slot_id FROM slots
        WHERE activity_id = v_activity.id AND status = 'available'
        ORDER BY sort_order
        FOR UPDATE SKIP LOCKED
        LIMIT 1;

        IF v_slot_id IS NULL THEN
            RETURN jsonb_build_object('status', 'full');
        END IF;

        UPDATE slots
        SET status = 'pending', reserved_by = v_user_id, reserved_at = now(), is_guest = true
        WHERE id = v_slot_id;

        INSERT INTO activity_guest_requests (activity_id, user_id, slot_id, guest_name, guest_email)
        VALUES (v_activity.id, v_user_id, v_slot_id, trim(p_name), trim(p_email))
        RETURNING id INTO v_request_id;
    END IF;

    -- Notify admins
    FOR v_admin IN
        SELECT user_id FROM community_members
        WHERE community_id = v_activity.community_id AND role = 'admin'
    LOOP
        INSERT INTO notifications (user_id, type, title, body, data)
        VALUES (
            v_admin.user_id,
            'guest_request_received',
            'Nueva solicitud de invitado',
            trim(p_name) || ' quiere asistir a ' || v_activity.name,
            jsonb_build_object(
                'activity_id', v_activity.id,
                'request_id', v_request_id,
                'guest_name', trim(p_name)
            )
        );
    END LOOP;

    RETURN jsonb_build_object(
        'status', 'pending',
        'request_id', v_request_id,
        'slot_id', v_slot_id
    );
END;
$$;

ALTER FUNCTION "public"."request_guest_slot"("p_code" "text", "p_name" "text", "p_email" "text", "p_position_ids" "uuid"[]) OWNER TO "postgres";

-- El DROP elimina los grants previos; los invitados llaman con el rol anon, así que hay
-- que reconcederlos (igual que en el baseline para la firma con p_phone).
GRANT ALL ON FUNCTION "public"."request_guest_slot"("p_code" "text", "p_name" "text", "p_email" "text", "p_position_ids" "uuid"[]) TO "anon";
GRANT ALL ON FUNCTION "public"."request_guest_slot"("p_code" "text", "p_name" "text", "p_email" "text", "p_position_ids" "uuid"[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."request_guest_slot"("p_code" "text", "p_name" "text", "p_email" "text", "p_position_ids" "uuid"[]) TO "service_role";

-- 3) list_pending_guest_requests: devuelve guest_email en vez de guest_phone -----------

CREATE OR REPLACE FUNCTION "public"."list_pending_guest_requests"("p_activity_id" "uuid") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_activity RECORD;
    v_result jsonb;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = p_activity_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Activity not found';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_caller
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can view guest requests';
    END IF;

    SELECT COALESCE(jsonb_agg(
        jsonb_build_object(
            'id', r.id,
            'activity_id', r.activity_id,
            'slot_id', r.slot_id,
            'guest_name', r.guest_name,
            'guest_email', r.guest_email,
            'requested_at', r.requested_at,
            'requested_positions', COALESCE((
                SELECT jsonb_agg(p.name ORDER BY p.name)
                FROM positions p
                WHERE p.id = ANY(r.requested_position_ids)
            ), '[]'::jsonb)
        ) ORDER BY r.requested_at ASC
    ), '[]'::jsonb)
    INTO v_result
    FROM activity_guest_requests r
    WHERE r.activity_id = p_activity_id AND r.status = 'pending';

    RETURN v_result;
END;
$$;

ALTER FUNCTION "public"."list_pending_guest_requests"("p_activity_id" "uuid") OWNER TO "postgres";
