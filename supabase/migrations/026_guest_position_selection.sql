-- ============================================================================
-- Migration 026: Guest position selection for limited_with_positions
--
-- Guests selecting positions instead of slots. Slot assignment deferred to
-- approval time with greedy "most restrictive first" algorithm.
-- ============================================================================

BEGIN;

-- ============================================================================
-- 1. New column: requested_position_ids on activity_guest_requests
-- ============================================================================
ALTER TABLE activity_guest_requests
    ADD COLUMN IF NOT EXISTS requested_position_ids uuid[] DEFAULT NULL;

-- ============================================================================
-- 2. Update get_activity_guest_preview — include positions with availability
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
    v_positions jsonb;
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

    IF v_activity.slot_mode = 'unlimited' THEN
        v_capacity := NULL;
        v_taken := (SELECT count(*) FROM slots
                    WHERE activity_id = v_activity.id AND status <> 'available');
    ELSE
        v_capacity := (SELECT count(*) FROM slots WHERE activity_id = v_activity.id);
        v_taken := (SELECT count(*) FROM slots
                    WHERE activity_id = v_activity.id AND status <> 'available');
    END IF;

    -- Build positions array for limited_with_positions
    IF v_activity.slot_mode = 'limited_with_positions' THEN
        SELECT COALESCE(jsonb_agg(
            jsonb_build_object(
                'id', p.id,
                'name', p.name,
                'available', COALESCE(avail.cnt, 0)
            ) ORDER BY p.name
        ), '[]'::jsonb)
        INTO v_positions
        FROM positions p
        LEFT JOIN LATERAL (
            SELECT count(*) AS cnt
            FROM slot_positions sp
            JOIN slots s ON s.id = sp.slot_id
            WHERE sp.position_id = p.id
              AND s.activity_id = v_activity.id
              AND s.status = 'available'
        ) avail ON true
        WHERE p.activity_id = v_activity.id
          AND EXISTS (
              SELECT 1 FROM slot_positions sp2
              JOIN slots s2 ON s2.id = sp2.slot_id
              WHERE sp2.position_id = p.id AND s2.activity_id = v_activity.id
          );
    ELSE
        v_positions := NULL;
    END IF;

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
        'positions', v_positions,
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
-- 3. Update request_guest_slot — accept position_ids, skip slot retention
--    for limited_with_positions
-- ============================================================================
CREATE OR REPLACE FUNCTION request_guest_slot(
    p_code text,
    p_name text,
    p_phone text,
    p_position_ids uuid[] DEFAULT NULL
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
            (activity_id, user_id, slot_id, guest_name, guest_phone, requested_position_ids)
        VALUES
            (v_activity.id, v_user_id, NULL, trim(p_name), trim(p_phone), p_position_ids)
        RETURNING id INTO v_request_id;

    -- === unlimited: create slot on the fly ===
    ELSIF v_activity.slot_mode = 'unlimited' THEN
        SELECT COALESCE(max(sort_order), -1) + 1 INTO v_next_sort
        FROM slots WHERE activity_id = v_activity.id;

        INSERT INTO slots (activity_id, sort_order, status, reserved_by, reserved_at, is_guest)
        VALUES (v_activity.id, v_next_sort, 'pending', v_user_id, now(), true)
        RETURNING id INTO v_slot_id;

        INSERT INTO activity_guest_requests (activity_id, user_id, slot_id, guest_name, guest_phone)
        VALUES (v_activity.id, v_user_id, v_slot_id, trim(p_name), trim(p_phone))
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

        INSERT INTO activity_guest_requests (activity_id, user_id, slot_id, guest_name, guest_phone)
        VALUES (v_activity.id, v_user_id, v_slot_id, trim(p_name), trim(p_phone))
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
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ============================================================================
-- 4. Update approve_guest_request — auto-assign slot for position requests
-- ============================================================================
CREATE OR REPLACE FUNCTION approve_guest_request(p_request_id uuid)
RETURNS void AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_req RECORD;
    v_activity RECORD;
    v_slot_id uuid;
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

    -- Position-based requests: find best slot at approval time
    -- Two-step: find candidate, then lock it (FOR UPDATE incompatible with GROUP BY)
    IF v_req.requested_position_ids IS NOT NULL THEN
        SELECT sub.id INTO v_slot_id
        FROM (
            SELECT s.id, s.sort_order, count(sp.position_id) AS pos_count
            FROM slots s
            JOIN slot_positions sp ON sp.slot_id = s.id
            WHERE s.activity_id = v_req.activity_id
              AND s.status = 'available'
              AND sp.position_id = ANY(v_req.requested_position_ids)
            GROUP BY s.id, s.sort_order
            ORDER BY pos_count ASC, s.sort_order ASC
            LIMIT 1
        ) sub
        JOIN slots locked ON locked.id = sub.id AND locked.status = 'available'
        FOR UPDATE OF locked SKIP LOCKED;

        IF v_slot_id IS NULL THEN
            RAISE EXCEPTION 'No available slot for the requested positions';
        END IF;

        UPDATE slots
        SET status = 'reserved', reserved_by = v_req.user_id,
            reserved_at = now(), is_guest = true
        WHERE id = v_slot_id;

        UPDATE activity_guest_requests
        SET status = 'approved', slot_id = v_slot_id,
            resolved_at = now(), resolved_by = v_caller
        WHERE id = p_request_id;
    ELSE
        -- Legacy: slot already retained from request time
        UPDATE slots SET status = 'reserved'
        WHERE id = v_req.slot_id AND status = 'pending';

        UPDATE activity_guest_requests
        SET status = 'approved', resolved_at = now(), resolved_by = v_caller
        WHERE id = p_request_id;
    END IF;

    -- Update guest display name
    UPDATE profiles SET display_name = v_req.guest_name WHERE id = v_req.user_id;

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
-- 5. Update reject_guest_request — handle position-based requests (no slot)
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

    -- Only release slot if one was retained (unlimited/limited modes)
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
-- 6. Update list_pending_guest_requests — include position names
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

    SELECT COALESCE(jsonb_agg(
        jsonb_build_object(
            'id', r.id,
            'activity_id', r.activity_id,
            'slot_id', r.slot_id,
            'guest_name', r.guest_name,
            'guest_phone', r.guest_phone,
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
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ============================================================================
-- 7. Update GRANT for new signature
-- ============================================================================
DROP FUNCTION IF EXISTS request_guest_slot(text, text, text);
GRANT EXECUTE ON FUNCTION request_guest_slot(text, text, text, uuid[]) TO authenticated;

COMMIT;
