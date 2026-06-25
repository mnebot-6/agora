-- ============================================================================
-- Migration 009: Create notify_substitute_promoted function
--
-- This function was defined in migration 004 but was not applied to the
-- production database. Migration 008's promote_substitute calls it,
-- causing "function does not exist" errors during auto-promotion.
-- ============================================================================

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
        jsonb_build_object('activity_id', p_activity_id, 'slot_id', p_slot_id)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
