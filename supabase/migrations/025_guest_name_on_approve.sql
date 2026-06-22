-- ============================================================================
-- Migration 025: Al aprobar un invitado, copiar guest_name → profiles.display_name
--
-- Antes de esta migración, los slots de invitados aprobados mostraban el
-- display_name genérico 'Invitado' (asignado al crear la cuenta anónima).
-- Con este cambio, approve_guest_request actualiza el perfil con el nombre
-- real que el invitado introdujo en la solicitud, de modo que el SlotCard
-- de la app muestra el nombre correcto sin cambios en la capa Kotlin.
-- ============================================================================

BEGIN;

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

    -- Actualizar el nombre visible del invitado con el que dio en la solicitud.
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

COMMIT;
