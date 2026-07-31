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
    END IF;

    UPDATE slots
    SET status = 'available', reserved_by = NULL, reserved_at = NULL, guest_label = NULL
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
