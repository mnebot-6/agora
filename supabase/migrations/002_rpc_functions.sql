-- ============================================================================
-- Migration 002: RPC Functions for Atomic Slot Operations
-- All slot state transitions go through these functions to guarantee atomicity
-- ============================================================================

-- ============================================================================
-- RESERVE SLOT
-- Atomically reserves a slot for a user if it's available
-- Returns true on success, false if slot was already taken
-- ============================================================================
CREATE OR REPLACE FUNCTION reserve_slot(p_slot_id UUID, p_user_id UUID)
RETURNS BOOLEAN AS $$
DECLARE
    v_slot RECORD;
    v_activity RECORD;
BEGIN
    -- Lock the slot row to prevent concurrent reservations
    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;

    IF v_slot IS NULL THEN
        RAISE EXCEPTION 'Slot not found';
    END IF;

    IF v_slot.status != 'available' THEN
        RETURN FALSE; -- Already taken
    END IF;

    -- Verify user is a member of the activity's community
    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;
    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id AND user_id = p_user_id
    ) THEN
        RAISE EXCEPTION 'User is not a member of this community';
    END IF;

    -- Reserve the slot
    UPDATE slots
    SET status = 'reserved', reserved_by = p_user_id, reserved_at = now()
    WHERE id = p_slot_id;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- RELEASE SLOT
-- Releases a slot with permission checks:
--   - PAID slots: only the reserved user can release
--   - RESERVED slots: reserved user OR community admin can release
-- After releasing, auto-promotes first matching substitute
-- ============================================================================
CREATE OR REPLACE FUNCTION release_slot(p_slot_id UUID, p_user_id UUID, p_is_admin BOOLEAN)
RETURNS BOOLEAN AS $$
DECLARE
    v_slot RECORD;
    v_activity RECORD;
BEGIN
    -- Lock the slot
    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;

    IF v_slot IS NULL THEN
        RAISE EXCEPTION 'Slot not found';
    END IF;

    IF v_slot.status = 'available' THEN
        RETURN FALSE; -- Nothing to release
    END IF;

    -- Permission check
    IF v_slot.status = 'paid' THEN
        -- Only the user who reserved can release a PAID slot (admins CANNOT)
        IF v_slot.reserved_by != p_user_id THEN
            RAISE EXCEPTION 'Only the user who reserved this slot can release a paid reservation';
        END IF;
    ELSIF v_slot.status = 'reserved' THEN
        -- Reserved user OR admin can release
        IF v_slot.reserved_by != p_user_id AND NOT p_is_admin THEN
            RAISE EXCEPTION 'Only the reserved user or an admin can release this slot';
        END IF;
    END IF;

    -- Release the slot
    UPDATE slots
    SET status = 'available', reserved_by = NULL, reserved_at = NULL
    WHERE id = p_slot_id;

    -- Try to auto-promote a substitute
    PERFORM promote_substitute(p_slot_id);

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- MARK SLOT PAID
-- Admin marks a reserved slot as paid
-- ============================================================================
CREATE OR REPLACE FUNCTION mark_slot_paid(p_slot_id UUID, p_admin_user_id UUID)
RETURNS BOOLEAN AS $$
DECLARE
    v_slot RECORD;
    v_activity RECORD;
BEGIN
    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;

    IF v_slot IS NULL THEN
        RAISE EXCEPTION 'Slot not found';
    END IF;

    IF v_slot.status != 'reserved' THEN
        RETURN FALSE; -- Can only mark reserved slots as paid
    END IF;

    -- Verify caller is admin of the activity's community
    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;
    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = p_admin_user_id
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can mark slots as paid';
    END IF;

    UPDATE slots SET status = 'paid' WHERE id = p_slot_id;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- PROMOTE SUBSTITUTE (internal)
-- Finds the earliest-queued substitute matching the slot's positions
-- and auto-reserves the slot for them
-- ============================================================================
CREATE OR REPLACE FUNCTION promote_substitute(p_slot_id UUID)
RETURNS VOID AS $$
DECLARE
    v_slot RECORD;
    v_substitute RECORD;
    v_slot_position_ids UUID[];
BEGIN
    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id;

    IF v_slot IS NULL OR v_slot.status != 'available' THEN
        RETURN; -- Slot not available, nothing to do
    END IF;

    -- Get positions this slot accepts
    SELECT array_agg(position_id) INTO v_slot_position_ids
    FROM slot_positions WHERE slot_id = p_slot_id;

    -- Find earliest substitute that matches
    IF v_slot_position_ids IS NOT NULL AND array_length(v_slot_position_ids, 1) > 0 THEN
        -- Slot has specific positions: find substitute for any matching position
        SELECT * INTO v_substitute
        FROM substitute_queue
        WHERE activity_id = v_slot.activity_id
          AND (position_id = ANY(v_slot_position_ids) OR position_id IS NULL)
        ORDER BY queued_at ASC
        LIMIT 1
        FOR UPDATE;
    ELSE
        -- Slot has no positions: find any substitute
        SELECT * INTO v_substitute
        FROM substitute_queue
        WHERE activity_id = v_slot.activity_id
        ORDER BY queued_at ASC
        LIMIT 1
        FOR UPDATE;
    END IF;

    IF v_substitute IS NULL THEN
        RETURN; -- No substitute waiting
    END IF;

    -- Reserve the slot for the substitute
    UPDATE slots
    SET status = 'reserved', reserved_by = v_substitute.user_id, reserved_at = now()
    WHERE id = p_slot_id;

    -- Remove from queue
    DELETE FROM substitute_queue WHERE id = v_substitute.id;

    -- Create notification for promoted user
    INSERT INTO notifications (user_id, type, title, body, data)
    SELECT
        v_substitute.user_id,
        'substitute_promoted',
        'Plaza disponible',
        'Se te ha asignado automáticamente una plaza en ' || a.name,
        jsonb_build_object('activity_id', v_slot.activity_id, 'slot_id', p_slot_id)
    FROM activities a WHERE a.id = v_slot.activity_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- JOIN SUBSTITUTE QUEUE
-- ============================================================================
CREATE OR REPLACE FUNCTION join_substitute_queue(
    p_activity_id UUID,
    p_user_id UUID,
    p_position_id UUID DEFAULT NULL
)
RETURNS VOID AS $$
BEGIN
    INSERT INTO substitute_queue (activity_id, user_id, position_id)
    VALUES (p_activity_id, p_user_id, p_position_id)
    ON CONFLICT (activity_id, user_id, position_id) DO NOTHING;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- LEAVE SUBSTITUTE QUEUE
-- ============================================================================
CREATE OR REPLACE FUNCTION leave_substitute_queue(
    p_activity_id UUID,
    p_user_id UUID,
    p_position_id UUID DEFAULT NULL
)
RETURNS VOID AS $$
BEGIN
    IF p_position_id IS NULL THEN
        DELETE FROM substitute_queue
        WHERE activity_id = p_activity_id
          AND user_id = p_user_id
          AND position_id IS NULL;
    ELSE
        DELETE FROM substitute_queue
        WHERE activity_id = p_activity_id
          AND user_id = p_user_id
          AND position_id = p_position_id;
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- GET UPCOMING ACTIVITIES (for a user across all communities)
-- ============================================================================
CREATE OR REPLACE FUNCTION get_upcoming_activities(p_user_id UUID)
RETURNS SETOF activities AS $$
BEGIN
    RETURN QUERY
    SELECT a.*
    FROM activities a
    INNER JOIN community_members cm ON cm.community_id = a.community_id
    WHERE cm.user_id = p_user_id
      AND a.status = 'active'
      AND a.datetime >= now()
    ORDER BY a.datetime ASC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
