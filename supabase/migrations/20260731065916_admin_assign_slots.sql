-- ============================================================================
-- guest_label: apuntar en un hueco a alguien que no tiene cuenta.
-- ============================================================================

BEGIN;

-- Plazas ocupadas por alguien sin cuenta: reserved_by NULL + guest_label con el nombre.
ALTER TABLE slots ADD COLUMN guest_label text;

-- Una plaza es de un usuario O de una etiqueta, nunca de los dos.
ALTER TABLE slots ADD CONSTRAINT slots_no_owner_and_label
  CHECK (guest_label IS NULL OR reserved_by IS NULL);

-- release_slot: comparar con IS DISTINCT FROM (con reserved_by NULL, "!=" da NULL y el IF
-- no dispara, dejando que cualquier autenticado libere una plaza de etiqueta), exigir admin
-- explicitamente para las plazas sin dueno, y limpiar guest_label al liberar.
-- Base: la version viva de 20260630120000_more_guest_notifications.sql (incluye el aviso
-- slot_removed a la persona expulsada por un admin), no la del baseline.
CREATE OR REPLACE FUNCTION public.release_slot(p_slot_id uuid) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
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

    IF v_slot.reserved_by IS NULL THEN
        -- Plaza de etiqueta: no tiene dueno que la pueda liberar, solo un admin.
        IF NOT v_is_admin THEN
            RAISE EXCEPTION 'Only an admin can release a guest-label slot';
        END IF;
    ELSIF v_slot.status = 'paid' THEN
        IF v_slot.reserved_by IS DISTINCT FROM v_user_id THEN
            RAISE EXCEPTION 'Only the user who reserved this slot can release a paid reservation';
        END IF;
    ELSIF v_slot.status = 'reserved' THEN
        IF v_slot.reserved_by IS DISTINCT FROM v_user_id AND NOT v_is_admin THEN
            RAISE EXCEPTION 'Only the reserved user or an admin can release this slot';
        END IF;
    ELSE
        -- status 'pending': plaza retenida por un invitado a la espera de aprobacion.
        -- Sin esta rama cualquier autenticado podia liberarla, porque no casaba con
        -- ninguna de las anteriores y el IF caia directo al UPDATE.
        IF NOT v_is_admin THEN
            RAISE EXCEPTION 'Only an admin can release a pending guest slot';
        END IF;
    END IF;

    -- is_guest se queda pegado si no se limpia aqui: approve_guest_request lo pone a true
    -- y solo reject_guest_request lo reseteaba, asi que el siguiente ocupante de la plaza
    -- aparecia marcado como invitado.
    UPDATE slots
    SET status = 'available', reserved_by = NULL, reserved_at = NULL,
        guest_label = NULL, is_guest = false
    WHERE id = p_slot_id;

    -- Avisar a la persona expulsada si fue un admin quien libero su plaza (no auto-liberacion).
    IF v_slot.reserved_by IS NOT NULL AND v_slot.reserved_by <> v_user_id THEN
        INSERT INTO notifications (user_id, type, title, body, data)
        VALUES (
            v_slot.reserved_by,
            'slot_removed',
            'Plaza cancelada',
            'Tu plaza en ' || v_activity.name || ' ha sido cancelada por un administrador.',
            jsonb_build_object('activity_id', v_activity.id, 'slot_id', p_slot_id)
        );
    END IF;

    v_promoted := promote_substitute(p_slot_id, v_activity.id);

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
$$;

-- reject_guest_request es el otro sitio (aparte de release_slot) que devuelve una plaza a
-- 'available', y no limpiaba guest_label. Una plaza libre con etiqueta viola
-- slots_no_owner_and_label en cuanto alguien la reserve: reserve_slot,
-- approve_guest_request y promote_substitute escriben reserved_by sin tocar guest_label, y
-- como no hay policy de DELETE sobre slots la fila quedaria inservible para siempre. Cuerpo
-- copiado tal cual del baseline (no hay redefinicion posterior) salvo ese guest_label.
CREATE OR REPLACE FUNCTION public.reject_guest_request(p_request_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
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
            SET status = 'available', reserved_by = NULL, reserved_at = NULL,
                is_guest = false, guest_label = NULL
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
$$;

COMMIT;
