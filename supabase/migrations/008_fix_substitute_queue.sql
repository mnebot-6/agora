-- ============================================================================
-- Migration 008: Fix Substitute Queue Bugs
--
-- Bugs fixed:
--   1. Auto-promotion failure: if position mismatch, slot stayed available
--      and could be manually reserved by a queue member in wrong position.
--   2. reserve_slot had no queue priority check — anyone could bypass the queue.
--   3. reserve_slot never cleaned up the reserver's substitute_queue entries,
--      causing them to see themselves in queue after manual reservation.
--   4. UNIQUE (activity_id, user_id, position_id) allowed duplicate NULL rows
--      because PostgreSQL treats NULL != NULL for unique constraints.
--   5. trg_notify_slot_released fired before promote_substitute, sending a
--      spurious "plaza disponible" to the user who was about to be promoted.
-- ============================================================================

-- ============================================================================
-- PHASE 1: Fix UNIQUE constraint for NULL position_id
-- ============================================================================

-- Remove duplicate NULL-position entries first (keep the earliest by queued_at)
DELETE FROM substitute_queue
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY activity_id, user_id
                   ORDER BY queued_at ASC
               ) AS rn
        FROM substitute_queue
        WHERE position_id IS NULL
    ) ranked
    WHERE rn > 1
);

-- Recreate constraint with NULLS NOT DISTINCT (PostgreSQL 15)
-- This makes ON CONFLICT correctly fire when position_id IS NULL
ALTER TABLE substitute_queue
    DROP CONSTRAINT substitute_queue_activity_id_user_id_position_id_key;

ALTER TABLE substitute_queue
    ADD CONSTRAINT substitute_queue_activity_id_user_id_position_id_key
    UNIQUE NULLS NOT DISTINCT (activity_id, user_id, position_id);

-- ============================================================================
-- PHASE 2: Replace promote_substitute (VOID → BOOLEAN)
--
-- Changes:
--   - Returns BOOLEAN so release_slot can know if promotion happened
--   - FOR UPDATE SKIP LOCKED on queue SELECT (race-condition safe)
--   - Deletes ALL queue entries for the promoted user in this activity,
--     not just the matched row (user can't hold two slots for same activity)
-- ============================================================================

-- Must DROP first because PostgreSQL forbids changing return type with CREATE OR REPLACE
DROP FUNCTION IF EXISTS promote_substitute(uuid);        -- stale one-arg from 002
DROP FUNCTION IF EXISTS promote_substitute(uuid, uuid);  -- two-arg from 004

CREATE FUNCTION promote_substitute(p_slot_id uuid, p_activity_id uuid)
RETURNS BOOLEAN AS $$
DECLARE
    v_sub record;
    v_slot_positions uuid[];
BEGIN
    -- Get positions for this slot
    SELECT array_agg(sp.position_id) INTO v_slot_positions
    FROM slot_positions sp
    WHERE sp.slot_id = p_slot_id;

    -- Find first matching substitute (FIFO), lock to prevent concurrent promotions
    IF v_slot_positions IS NOT NULL AND array_length(v_slot_positions, 1) > 0 THEN
        -- Slot has specific positions: match by position OR "any position" (NULL)
        SELECT * INTO v_sub
        FROM substitute_queue
        WHERE activity_id = p_activity_id
          AND (position_id = ANY(v_slot_positions) OR position_id IS NULL)
        ORDER BY queued_at ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED;
    ELSE
        -- Slot has no positions: take first in queue
        SELECT * INTO v_sub
        FROM substitute_queue
        WHERE activity_id = p_activity_id
        ORDER BY queued_at ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED;
    END IF;

    IF v_sub IS NULL THEN
        RETURN FALSE;
    END IF;

    -- Reserve the slot for the substitute
    UPDATE slots
    SET status = 'reserved',
        reserved_by = v_sub.user_id,
        reserved_at = now()
    WHERE id = p_slot_id;

    -- Remove ALL queue entries for this user in this activity (not just the matched one)
    DELETE FROM substitute_queue
    WHERE activity_id = p_activity_id
      AND user_id = v_sub.user_id;

    -- Notify the promoted user
    PERFORM notify_substitute_promoted(v_sub.user_id, p_activity_id, p_slot_id);

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- PHASE 3: Replace release_slot — move notification logic here, conditional
--          on whether auto-promotion actually occurred
-- ============================================================================

CREATE OR REPLACE FUNCTION release_slot(p_slot_id UUID)
RETURNS BOOLEAN AS $$
DECLARE
    v_slot RECORD;
    v_activity RECORD;
    v_user_id UUID := auth.uid();
    v_is_admin BOOLEAN;
    v_promoted BOOLEAN;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF v_slot IS NULL THEN
        RAISE EXCEPTION 'Slot not found';
    END IF;

    IF v_slot.status = 'available' THEN
        RETURN FALSE;
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;
    SELECT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_user_id
          AND role = 'admin'
    ) INTO v_is_admin;

    IF v_slot.status = 'paid' THEN
        IF v_slot.reserved_by != v_user_id THEN
            RAISE EXCEPTION 'Only the user who reserved this slot can release a paid reservation';
        END IF;
    ELSIF v_slot.status = 'reserved' THEN
        IF v_slot.reserved_by != v_user_id AND NOT v_is_admin THEN
            RAISE EXCEPTION 'Only the reserved user or an admin can release this slot';
        END IF;
    END IF;

    UPDATE slots
    SET status = 'available', reserved_by = NULL, reserved_at = NULL
    WHERE id = p_slot_id;

    -- Attempt auto-promotion; returns TRUE if someone was promoted
    v_promoted := promote_substitute(p_slot_id, v_activity.id);

    -- Only notify remaining queue members if NO auto-promotion happened.
    -- When promotion occurs the promoted user already received 'substitute_promoted';
    -- notifying all queue members here would also send a spurious 'slot_released'
    -- to other members whose position does not match the now-taken slot.
    IF NOT v_promoted THEN
        INSERT INTO notifications (user_id, type, title, body, data)
        SELECT
            sq.user_id,
            'slot_released',
            'Plaza disponible',
            'Se ha liberado una plaza en una actividad',
            jsonb_build_object('activity_id', v_activity.id, 'slot_id', p_slot_id)
        FROM substitute_queue sq
        WHERE sq.activity_id = v_activity.id;
    END IF;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- PHASE 4: Drop trg_notify_slot_released trigger and its function
--          (logic moved to release_slot above)
-- ============================================================================

DROP TRIGGER IF EXISTS trg_notify_slot_released ON slots;
DROP FUNCTION IF EXISTS notify_slot_released();

-- ============================================================================
-- PHASE 5: Replace reserve_slot — enforce queue priority + cleanup on success
--
-- Changes:
--   - Before reserving, check if matching queue members exist for this slot
--   - If the caller is NOT first in matching queue: re-trigger promote_substitute
--     (handles the case where auto-promotion was skipped due to position mismatch
--     at release time) and return FALSE to deny the manual reservation
--   - After any successful reservation: delete ALL of the user's queue entries
--     for this activity (fixes "user sees themselves in queue after reserving")
-- ============================================================================

CREATE OR REPLACE FUNCTION reserve_slot(p_slot_id UUID)
RETURNS BOOLEAN AS $$
DECLARE
    v_slot RECORD;
    v_activity RECORD;
    v_user_id UUID := auth.uid();
    v_slot_positions uuid[];
    v_first_in_queue RECORD;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF v_slot IS NULL THEN
        RAISE EXCEPTION 'Slot not found';
    END IF;

    IF v_slot.status != 'available' THEN
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
        -- Re-trigger promote_substitute (handles the case where auto-promotion
        -- was skipped because the released slot had a different position than
        -- the queue member's registered position, but another slot is now free).
        PERFORM promote_substitute(p_slot_id, v_activity.id);
        RETURN FALSE;
    END IF;

    -- Either no one is in queue for this slot, or the caller IS the first in queue
    -- (auto-promotion should have fired but didn't — allow the manual reservation).
    UPDATE slots
    SET status = 'reserved', reserved_by = v_user_id, reserved_at = now()
    WHERE id = p_slot_id;

    -- Clean up ALL of this user's queue entries for this activity
    DELETE FROM substitute_queue
    WHERE activity_id = v_slot.activity_id
      AND user_id = v_user_id;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- PHASE 6: Update join_substitute_queue — prevent joining queue when user
--          already holds a reserved/paid slot for the same activity
-- ============================================================================

CREATE OR REPLACE FUNCTION join_substitute_queue(
    p_activity_id UUID,
    p_position_id UUID DEFAULT NULL
)
RETURNS VOID AS $$
DECLARE
    v_activity RECORD;
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = p_activity_id;
    IF v_activity IS NULL THEN
        RAISE EXCEPTION 'Activity not found';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id AND user_id = v_user_id
    ) THEN
        RAISE EXCEPTION 'User is not a member of this community';
    END IF;

    -- Prevent joining the queue when already holding a slot for this activity
    IF EXISTS (
        SELECT 1 FROM slots
        WHERE activity_id = p_activity_id
          AND reserved_by = v_user_id
          AND status IN ('reserved', 'paid')
    ) THEN
        RAISE EXCEPTION 'Ya tienes una plaza reservada en esta actividad';
    END IF;

    -- ON CONFLICT now works for NULL position_id thanks to NULLS NOT DISTINCT constraint
    INSERT INTO substitute_queue (activity_id, user_id, position_id)
    VALUES (p_activity_id, v_user_id, p_position_id)
    ON CONFLICT (activity_id, user_id, position_id) DO NOTHING;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
