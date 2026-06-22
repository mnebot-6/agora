-- Hotfix: FOR UPDATE is not allowed with GROUP BY in PostgreSQL.
-- Split into subquery (find candidate) + join (lock it).

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
        UPDATE slots SET status = 'reserved'
        WHERE id = v_req.slot_id AND status = 'pending';

        UPDATE activity_guest_requests
        SET status = 'approved', resolved_at = now(), resolved_by = v_caller
        WHERE id = p_request_id;
    END IF;

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
