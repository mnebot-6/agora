-- =============================================================
-- 004: Notification triggers
-- Automatically create in-app notifications on key events.
-- Push delivery (FCM) is handled by a Supabase Edge Function
-- that listens to inserts on the notifications table.
-- =============================================================

-- 1) Notify all community members when a new activity is created
CREATE OR REPLACE FUNCTION notify_new_activity()
RETURNS trigger AS $$
BEGIN
    INSERT INTO notifications (user_id, type, title, body, data)
    SELECT
        cm.user_id,
        'new_activity',
        'Nueva actividad',
        NEW.name,
        jsonb_build_object(
            'activity_id', NEW.id,
            'community_id', NEW.community_id
        )
    FROM community_members cm
    WHERE cm.community_id = NEW.community_id
      AND cm.user_id != NEW.created_by;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_notify_new_activity ON activities;
CREATE TRIGGER trg_notify_new_activity
    AFTER INSERT ON activities
    FOR EACH ROW
    EXECUTE FUNCTION notify_new_activity();

-- 2) Notify substitutes when a slot is released (status changes to 'available')
--    The auto-promote logic in release_slot already handles FIFO promotion,
--    but if there are no matching substitutes the slot stays available.
--    This trigger notifies remaining substitutes that a slot opened up.
CREATE OR REPLACE FUNCTION notify_slot_released()
RETURNS trigger AS $$
BEGIN
    IF OLD.status IN ('reserved', 'paid') AND NEW.status = 'available' THEN
        INSERT INTO notifications (user_id, type, title, body, data)
        SELECT
            sq.user_id,
            'slot_released',
            'Plaza disponible',
            'Se ha liberado una plaza en una actividad',
            jsonb_build_object(
                'activity_id', NEW.activity_id,
                'slot_id', NEW.id
            )
        FROM substitute_queue sq
        WHERE sq.activity_id = NEW.activity_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_notify_slot_released ON slots;
CREATE TRIGGER trg_notify_slot_released
    AFTER UPDATE ON slots
    FOR EACH ROW
    EXECUTE FUNCTION notify_slot_released();

-- 3) Notify user when they are promoted from the substitute queue
--    This is called from the promote_substitute function.
CREATE OR REPLACE FUNCTION notify_substitute_promoted(
    p_user_id uuid,
    p_activity_id uuid,
    p_slot_id uuid
)
RETURNS void AS $$
BEGIN
    INSERT INTO notifications (user_id, type, title, body, data)
    VALUES (
        p_user_id,
        'substitute_promoted',
        'Has sido promocionado',
        'Se te ha asignado una plaza desde la cola de suplentes',
        jsonb_build_object(
            'activity_id', p_activity_id,
            'slot_id', p_slot_id
        )
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Update the existing promote_substitute function to call notify
CREATE OR REPLACE FUNCTION promote_substitute(
    p_slot_id uuid,
    p_activity_id uuid
)
RETURNS void AS $$
DECLARE
    v_sub record;
    v_slot_positions uuid[];
BEGIN
    -- Get positions for this slot
    SELECT array_agg(sp.position_id) INTO v_slot_positions
    FROM slot_positions sp
    WHERE sp.slot_id = p_slot_id;

    -- Find first matching substitute (FIFO)
    IF v_slot_positions IS NOT NULL AND array_length(v_slot_positions, 1) > 0 THEN
        -- Slot has positions: match by position or null (any position)
        SELECT * INTO v_sub
        FROM substitute_queue
        WHERE activity_id = p_activity_id
          AND (position_id = ANY(v_slot_positions) OR position_id IS NULL)
        ORDER BY queued_at ASC
        LIMIT 1;
    ELSE
        -- Slot has no positions: take first in queue
        SELECT * INTO v_sub
        FROM substitute_queue
        WHERE activity_id = p_activity_id
        ORDER BY queued_at ASC
        LIMIT 1;
    END IF;

    IF v_sub IS NOT NULL THEN
        -- Reserve the slot for the substitute
        UPDATE slots
        SET status = 'reserved',
            reserved_by = v_sub.user_id,
            reserved_at = now()
        WHERE id = p_slot_id;

        -- Remove from queue
        DELETE FROM substitute_queue WHERE id = v_sub.id;

        -- Notify the promoted user
        PERFORM notify_substitute_promoted(v_sub.user_id, p_activity_id, p_slot_id);
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
