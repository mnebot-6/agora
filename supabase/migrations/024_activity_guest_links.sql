-- ============================================================================
-- Migration 024: Activity Guest Links (invitados a actividad sin cuenta)
--
-- Un admin de una comunidad PÚBLICA (public_open / public_approval) genera un
-- link por actividad. Cualquier dispositivo que lo abre obtiene una identidad
-- anónima (Supabase anonymous auth) y puede solicitar asistencia dando
-- nombre + teléfono. La solicitud RETIENE un slot real al instante
-- (status 'pending', cuenta para el aforo) y requiere aprobación de un admin
-- en orden FIFO. El rechazo libera el hueco. El invitado queda confinado a
-- esa actividad (no se añade a community_members).
-- ============================================================================

BEGIN;

-- ============================================================================
-- 1. Parchear handle_new_user para usuarios anónimos (CRÍTICO)
--    Los anónimos no tienen email ni display_name → el INSERT en profiles
--    violaría NOT NULL y rompería el alta. Default 'Invitado'.
-- ============================================================================
CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, display_name)
    VALUES (
        NEW.id,
        COALESCE(
            NEW.raw_user_meta_data->>'display_name',
            NULLIF(split_part(COALESCE(NEW.email, ''), '@', 1), ''),
            'Invitado'
        )
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION handle_new_user();

-- ============================================================================
-- 2. Marca de invitado + estado 'pending' en slots
-- ============================================================================
ALTER TABLE slots ADD COLUMN IF NOT EXISTS is_guest boolean NOT NULL DEFAULT false;

ALTER TABLE slots DROP CONSTRAINT IF EXISTS slots_status_check;
ALTER TABLE slots ADD CONSTRAINT slots_status_check
    CHECK (status IN ('available', 'reserved', 'paid', 'pending'));

-- ============================================================================
-- 3. activity_guest_links — un link por actividad (solo RPC, RLS sin policies)
-- ============================================================================
CREATE TABLE activity_guest_links (
    activity_id uuid PRIMARY KEY REFERENCES activities(id) ON DELETE CASCADE,
    code text NOT NULL UNIQUE CHECK (length(code) = 8 AND code ~ '^[A-Z0-9]+$'),
    created_by uuid NOT NULL REFERENCES profiles(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    revoked boolean NOT NULL DEFAULT false
);

ALTER TABLE activity_guest_links ENABLE ROW LEVEL SECURITY;
-- Sin policies: toda lectura/escritura pasa por los RPCs SECURITY DEFINER.

-- ============================================================================
-- 4. activity_guest_requests — solicitudes de invitados (nombre + teléfono)
-- ============================================================================
CREATE TABLE activity_guest_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id uuid NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    slot_id uuid REFERENCES slots(id) ON DELETE SET NULL,
    guest_name text NOT NULL CHECK (char_length(guest_name) BETWEEN 1 AND 80),
    guest_phone text NOT NULL CHECK (char_length(guest_phone) BETWEEN 3 AND 30),
    status text NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'approved', 'rejected', 'cancelled')),
    requested_at timestamptz NOT NULL DEFAULT now(),
    resolved_at timestamptz,
    resolved_by uuid REFERENCES profiles(id)
);

-- Estado persistente por dispositivo: una solicitud activa por (actividad, usuario)
CREATE UNIQUE INDEX idx_guest_requests_unique_active
    ON activity_guest_requests(activity_id, user_id)
    WHERE status IN ('pending', 'approved');

-- FIFO: cola de pendientes por actividad ordenada por antigüedad
CREATE INDEX idx_guest_requests_fifo
    ON activity_guest_requests(activity_id, requested_at)
    WHERE status = 'pending';

ALTER TABLE activity_guest_requests ENABLE ROW LEVEL SECURITY;
-- Sin policies: toda lectura/escritura pasa por los RPCs SECURITY DEFINER.

-- ============================================================================
-- 5. Helper: generar código único de 8 chars [A-Z0-9]
-- ============================================================================
CREATE OR REPLACE FUNCTION gen_activity_guest_code()
RETURNS text AS $$
DECLARE
    v_alphabet text := 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    v_code text;
    i int;
BEGIN
    LOOP
        v_code := '';
        FOR i IN 1..8 LOOP
            v_code := v_code || substr(v_alphabet, 1 + floor(random() * 36)::int, 1);
        END LOOP;
        EXIT WHEN NOT EXISTS (SELECT 1 FROM activity_guest_links WHERE code = v_code);
    END LOOP;
    RETURN v_code;
END;
$$ LANGUAGE plpgsql
   SET search_path = public, pg_temp;

-- ============================================================================
-- 6. RPC: generate_activity_guest_link (admin de comunidad pública)
-- ============================================================================
CREATE OR REPLACE FUNCTION generate_activity_guest_link(p_activity_id uuid)
RETURNS jsonb AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_activity RECORD;
    v_community RECORD;
    v_code text;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = p_activity_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Activity not found';
    END IF;

    SELECT * INTO v_community FROM communities WHERE id = v_activity.community_id;

    IF v_community.visibility NOT IN ('public_open', 'public_approval') THEN
        RAISE EXCEPTION 'Guest links are only allowed for public communities';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_user_id
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can generate guest links';
    END IF;

    SELECT code INTO v_code FROM activity_guest_links
    WHERE activity_id = p_activity_id AND NOT revoked;

    IF v_code IS NULL THEN
        v_code := gen_activity_guest_code();
        INSERT INTO activity_guest_links (activity_id, code, created_by)
        VALUES (p_activity_id, v_code, v_user_id)
        ON CONFLICT (activity_id) DO UPDATE
            SET code = EXCLUDED.code, created_by = EXCLUDED.created_by,
                created_at = now(), revoked = false
        RETURNING code INTO v_code;
    END IF;

    RETURN jsonb_build_object(
        'code', v_code,
        'url', 'https://share-agora.app/a/' || v_code
    );
END;
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ============================================================================
-- 7. RPC: get_activity_guest_preview (read-only; sirve a anónimos)
--    Devuelve datos seguros de la actividad + aforo + estado de la solicitud
--    propia del caller. NO devuelve listas de miembros ni del resto de la
--    comunidad.
-- ============================================================================
CREATE OR REPLACE FUNCTION get_activity_guest_preview(p_code text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
SET search_path = public, pg_temp
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_link RECORD;
    v_activity RECORD;
    v_community RECORD;
    v_capacity int;
    v_taken int;
    v_my_request RECORD;
BEGIN
    SELECT * INTO v_link FROM activity_guest_links
    WHERE code = p_code AND NOT revoked;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('status', 'not_found');
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_link.activity_id;
    IF NOT FOUND OR v_activity.status <> 'active' THEN
        RETURN jsonb_build_object('status', 'not_found');
    END IF;

    SELECT * INTO v_community FROM communities WHERE id = v_activity.community_id;
    IF v_community.visibility NOT IN ('public_open', 'public_approval') THEN
        RETURN jsonb_build_object('status', 'not_found');
    END IF;

    -- Aforo: para unlimited no hay tope; para el resto, contar slots
    IF v_activity.slot_mode = 'unlimited' THEN
        v_capacity := NULL;
        v_taken := (SELECT count(*) FROM slots
                    WHERE activity_id = v_activity.id AND status <> 'available');
    ELSE
        v_capacity := (SELECT count(*) FROM slots WHERE activity_id = v_activity.id);
        v_taken := (SELECT count(*) FROM slots
                    WHERE activity_id = v_activity.id AND status <> 'available');
    END IF;

    -- Estado de la solicitud del propio caller (si la tiene)
    IF v_user_id IS NOT NULL THEN
        SELECT * INTO v_my_request FROM activity_guest_requests
        WHERE activity_id = v_activity.id AND user_id = v_user_id
        ORDER BY requested_at DESC
        LIMIT 1;
    END IF;

    RETURN jsonb_build_object(
        'status', 'ok',
        'activity', jsonb_build_object(
            'id', v_activity.id,
            'name', v_activity.name,
            'description', v_activity.description,
            'datetime', v_activity.datetime,
            'duration_minutes', v_activity.duration_minutes,
            'location_name', v_activity.location_name,
            'cost_description', v_activity.cost_description,
            'slot_mode', v_activity.slot_mode,
            'capacity', v_capacity,
            'taken', v_taken
        ),
        'community', jsonb_build_object(
            'id', v_community.id,
            'name', v_community.name
        ),
        'is_member', (v_user_id IS NOT NULL AND EXISTS (
            SELECT 1 FROM community_members
            WHERE community_id = v_community.id AND user_id = v_user_id
        )),
        'my_request', CASE
            WHEN v_my_request.id IS NULL THEN NULL
            ELSE jsonb_build_object(
                'id', v_my_request.id,
                'status', v_my_request.status,
                'guest_name', v_my_request.guest_name,
                'requested_at', v_my_request.requested_at
            )
        END
    );
END;
$$;

-- ============================================================================
-- 8. RPC: request_guest_slot — retiene un slot (pending) y crea la solicitud
-- ============================================================================
CREATE OR REPLACE FUNCTION request_guest_slot(
    p_code text,
    p_name text,
    p_phone text
)
RETURNS jsonb AS $$
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
    IF p_phone IS NULL OR length(trim(p_phone)) = 0 THEN
        RAISE EXCEPTION 'Phone is required';
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

    -- Los miembros usan el flujo normal, no el de invitado
    IF EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id AND user_id = v_user_id
    ) THEN
        RETURN jsonb_build_object('status', 'already_member');
    END IF;

    -- Idempotente: si ya hay solicitud activa, devolverla
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

    -- Retener un slot
    IF v_activity.slot_mode = 'unlimited' THEN
        SELECT COALESCE(max(sort_order), -1) + 1 INTO v_next_sort
        FROM slots WHERE activity_id = v_activity.id;

        INSERT INTO slots (activity_id, sort_order, status, reserved_by, reserved_at, is_guest)
        VALUES (v_activity.id, v_next_sort, 'pending', v_user_id, now(), true)
        RETURNING id INTO v_slot_id;
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
    END IF;

    INSERT INTO activity_guest_requests (activity_id, user_id, slot_id, guest_name, guest_phone)
    VALUES (v_activity.id, v_user_id, v_slot_id, trim(p_name), trim(p_phone))
    RETURNING id INTO v_request_id;

    -- Notificar a los admins de la comunidad
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
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ============================================================================
-- 9. RPC: approve_guest_request (admin) — confirma el slot
-- ============================================================================
CREATE OR REPLACE FUNCTION approve_guest_request(p_request_id uuid)
RETURNS void AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_req RECORD;
    v_activity RECORD;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_req FROM activity_guest_requests WHERE id = p_request_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Request not found';
    END IF;
    IF v_req.status <> 'pending' THEN
        RAISE EXCEPTION 'Request is not pending';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_req.activity_id;

    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_caller
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can approve';
    END IF;

    UPDATE slots SET status = 'reserved'
    WHERE id = v_req.slot_id AND status = 'pending';

    UPDATE activity_guest_requests
    SET status = 'approved', resolved_at = now(), resolved_by = v_caller
    WHERE id = p_request_id;

    INSERT INTO notifications (user_id, type, title, body, data)
    VALUES (
        v_req.user_id,
        'guest_request_approved',
        'Asistencia aprobada',
        'Tu asistencia a ' || v_activity.name || ' ha sido aprobada',
        jsonb_build_object('activity_id', v_activity.id, 'request_id', p_request_id)
    );
END;
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ============================================================================
-- 10. RPC: reject_guest_request (admin) — libera el slot
-- ============================================================================
CREATE OR REPLACE FUNCTION reject_guest_request(p_request_id uuid)
RETURNS void AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_req RECORD;
    v_activity RECORD;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_req FROM activity_guest_requests WHERE id = p_request_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Request not found';
    END IF;
    IF v_req.status <> 'pending' THEN
        RAISE EXCEPTION 'Request is not pending';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_req.activity_id;

    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_caller
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can reject';
    END IF;

    -- Liberar el hueco. En unlimited el slot fue creado al vuelo → borrarlo;
    -- en el resto, devolverlo a 'available'. Sin auto-promoción de suplente.
    IF v_req.slot_id IS NOT NULL THEN
        IF v_activity.slot_mode = 'unlimited' THEN
            DELETE FROM slots WHERE id = v_req.slot_id;
        ELSE
            UPDATE slots
            SET status = 'available', reserved_by = NULL, reserved_at = NULL, is_guest = false
            WHERE id = v_req.slot_id;
        END IF;
    END IF;

    UPDATE activity_guest_requests
    SET status = 'rejected', resolved_at = now(), resolved_by = v_caller
    WHERE id = p_request_id;

    INSERT INTO notifications (user_id, type, title, body, data)
    VALUES (
        v_req.user_id,
        'guest_request_rejected',
        'Asistencia no aprobada',
        'Tu solicitud para asistir a ' || v_activity.name || ' no fue aprobada',
        jsonb_build_object('activity_id', v_activity.id, 'request_id', p_request_id)
    );
END;
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ============================================================================
-- 11. RPC: list_pending_guest_requests (admin) — cola FIFO
-- ============================================================================
CREATE OR REPLACE FUNCTION list_pending_guest_requests(p_activity_id uuid)
RETURNS jsonb AS $$
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

    SELECT COALESCE(jsonb_agg(row_to_json(sub.*) ORDER BY sub.requested_at ASC), '[]'::jsonb)
    INTO v_result
    FROM (
        SELECT id, activity_id, slot_id, guest_name, guest_phone, requested_at
        FROM activity_guest_requests
        WHERE activity_id = p_activity_id AND status = 'pending'
    ) sub;

    RETURN v_result;
END;
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ============================================================================
-- 12. Ampliar tipos de notificación
-- ============================================================================
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN (
        'new_activity',
        'slot_released',
        'substitute_promoted',
        'join_request_received',
        'join_request_approved',
        'join_request_rejected',
        'activity_reminder',
        'guest_request_received',
        'guest_request_approved',
        'guest_request_rejected'
    ));

-- ============================================================================
-- 13. GRANTs
-- ============================================================================
GRANT EXECUTE ON FUNCTION generate_activity_guest_link(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION get_activity_guest_preview(text) TO authenticated;
GRANT EXECUTE ON FUNCTION request_guest_slot(text, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION approve_guest_request(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION reject_guest_request(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION list_pending_guest_requests(uuid) TO authenticated;

COMMIT;
