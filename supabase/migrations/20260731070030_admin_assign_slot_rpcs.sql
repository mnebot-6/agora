-- Nuevo tipo de notificacion: sin esto, el CHECK notifications_type_check rechaza cada
-- INSERT de las RPC de abajo. Se repite la lista de 20260630120000 y se anade slot_assigned.
ALTER TABLE public.notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE public.notifications ADD CONSTRAINT notifications_type_check
  CHECK (type = ANY (ARRAY[
    'new_activity','slot_released','substitute_promoted',
    'join_request_received','join_request_approved','join_request_rejected',
    'activity_reminder','guest_request_received','guest_request_approved','guest_request_rejected',
    'payment_confirmed','slot_removed','activity_full','activity_cancelled','activity_updated',
    'slot_assigned'
  ]::text[]));

-- Un admin apunta a un miembro (p_user_id) o a alguien sin cuenta (p_guest_label) en una
-- plaza libre. Devuelve FALSE si la plaza dejo de estar libre entre que se abrio el dialogo
-- y se confirmo. NO comprueba la cola de suplentes: el aviso vive en la UI, la decision es
-- que el admin manda.
CREATE OR REPLACE FUNCTION public.admin_assign_slot(
    p_slot_id uuid,
    p_user_id uuid DEFAULT NULL,
    p_guest_label text DEFAULT NULL
) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_slot RECORD;
    v_activity RECORD;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF (p_user_id IS NULL) = (p_guest_label IS NULL) THEN
        RAISE EXCEPTION 'Provide exactly one of p_user_id or p_guest_label';
    END IF;

    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF v_slot IS NULL THEN
        RAISE EXCEPTION 'Slot not found';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;
    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_caller
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can assign slots';
    END IF;

    IF v_slot.status <> 'available' THEN
        RETURN FALSE;
    END IF;

    IF p_user_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM community_members
            WHERE community_id = v_activity.community_id AND user_id = p_user_id
        ) THEN
            RAISE EXCEPTION 'That person is not a member of this community';
        END IF;

        IF EXISTS (
            SELECT 1 FROM slots
            WHERE activity_id = v_slot.activity_id AND reserved_by = p_user_id
        ) THEN
            RAISE EXCEPTION 'That person already has a slot in this activity';
        END IF;
    END IF;

    UPDATE slots
    SET status = 'reserved',
        reserved_by = p_user_id,
        guest_label = p_guest_label,
        reserved_at = now()
    WHERE id = p_slot_id;

    IF p_user_id IS NOT NULL THEN
        DELETE FROM substitute_queue
        WHERE activity_id = v_slot.activity_id AND user_id = p_user_id;

        INSERT INTO notifications (user_id, type, title, body, data)
        VALUES (
            p_user_id,
            'slot_assigned',
            'Te han apuntado',
            'Un administrador te ha apuntado a ' || v_activity.name,
            jsonb_build_object('activity_id', v_activity.id, 'slot_id', p_slot_id)
        );
    END IF;

    RETURN TRUE;
END;
$$;

ALTER FUNCTION public.admin_assign_slot(uuid, uuid, text) OWNER TO postgres;
GRANT ALL ON FUNCTION public.admin_assign_slot(uuid, uuid, text) TO authenticated;
GRANT ALL ON FUNCTION public.admin_assign_slot(uuid, uuid, text) TO service_role;

-- Modo de aforo ilimitado: no hay plazas preexistentes, asi que se crea y se asigna en la
-- misma transaccion. Devuelve el id de la plaza creada.
CREATE OR REPLACE FUNCTION public.admin_assign_new_slot(
    p_activity_id uuid,
    p_user_id uuid DEFAULT NULL,
    p_guest_label text DEFAULT NULL
) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_activity RECORD;
    v_slot_id uuid;
    v_sort_order int;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF (p_user_id IS NULL) = (p_guest_label IS NULL) THEN
        RAISE EXCEPTION 'Provide exactly one of p_user_id or p_guest_label';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = p_activity_id;
    IF v_activity IS NULL THEN
        RAISE EXCEPTION 'Activity not found';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_caller
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can assign slots';
    END IF;

    IF p_user_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM community_members
            WHERE community_id = v_activity.community_id AND user_id = p_user_id
        ) THEN
            RAISE EXCEPTION 'That person is not a member of this community';
        END IF;

        IF EXISTS (
            SELECT 1 FROM slots
            WHERE activity_id = p_activity_id AND reserved_by = p_user_id
        ) THEN
            RAISE EXCEPTION 'That person already has a slot in this activity';
        END IF;
    END IF;

    SELECT coalesce(max(sort_order), -1) + 1 INTO v_sort_order
    FROM slots WHERE activity_id = p_activity_id;

    INSERT INTO slots (activity_id, sort_order, status, reserved_by, guest_label, reserved_at)
    VALUES (p_activity_id, v_sort_order, 'reserved', p_user_id, p_guest_label, now())
    RETURNING id INTO v_slot_id;

    IF p_user_id IS NOT NULL THEN
        DELETE FROM substitute_queue
        WHERE activity_id = p_activity_id AND user_id = p_user_id;

        INSERT INTO notifications (user_id, type, title, body, data)
        VALUES (
            p_user_id,
            'slot_assigned',
            'Te han apuntado',
            'Un administrador te ha apuntado a ' || v_activity.name,
            jsonb_build_object('activity_id', p_activity_id, 'slot_id', v_slot_id)
        );
    END IF;

    RETURN v_slot_id;
END;
$$;

ALTER FUNCTION public.admin_assign_new_slot(uuid, uuid, text) OWNER TO postgres;
GRANT ALL ON FUNCTION public.admin_assign_new_slot(uuid, uuid, text) TO authenticated;
GRANT ALL ON FUNCTION public.admin_assign_new_slot(uuid, uuid, text) TO service_role;
