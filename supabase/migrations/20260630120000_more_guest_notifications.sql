-- Más avisos de ciclo de vida para invitados (y miembros) por email.
--
-- El pipeline `notify-guest-email` es genérico: cualquier fila en `notifications` para un
-- usuario con `profiles.guest_email` se envía por correo. Aquí solo insertamos las filas que
-- faltaban. Los miembros normales también reciben estas notificaciones (push FCM in-app).
--
-- Eventos añadidos:
--   payment_confirmed  -> al marcar la plaza como pagada
--   slot_removed       -> cuando un admin libera/expulsa la plaza de alguien
--   activity_full      -> cuando se llenan todas las plazas (la actividad "se hace")
--   activity_cancelled -> cuando un admin archiva (cancela) una actividad futura
--   activity_updated   -> cuando cambia fecha/hora o lugar de una actividad futura

-- 1) Permitir los nuevos tipos -------------------------------------------------------
ALTER TABLE public.notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE public.notifications ADD CONSTRAINT notifications_type_check
  CHECK (type = ANY (ARRAY[
    'new_activity','slot_released','substitute_promoted',
    'join_request_received','join_request_approved','join_request_rejected',
    'activity_reminder','guest_request_received','guest_request_approved','guest_request_rejected',
    'payment_confirmed','slot_removed','activity_full','activity_cancelled','activity_updated'
  ]::text[]));

-- Marca para enviar "actividad llena" una sola vez por cada vez que se llena.
ALTER TABLE public.activities ADD COLUMN IF NOT EXISTS full_notified_at timestamptz;

-- 2) mark_slot_paid: avisar al usuario de la plaza que el pago está confirmado ---------
CREATE OR REPLACE FUNCTION public.mark_slot_paid(p_slot_id uuid) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    AS $$
DECLARE
    v_slot RECORD;
    v_activity RECORD;
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF v_slot IS NULL THEN
        RAISE EXCEPTION 'Slot not found';
    END IF;

    IF v_slot.status != 'reserved' THEN
        RETURN FALSE;
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;
    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_user_id
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can mark slots as paid';
    END IF;

    UPDATE slots SET status = 'paid' WHERE id = p_slot_id;

    IF v_slot.reserved_by IS NOT NULL THEN
        INSERT INTO notifications (user_id, type, title, body, data)
        VALUES (
            v_slot.reserved_by,
            'payment_confirmed',
            'Pago confirmado',
            'Tu plaza en ' || v_activity.name || ' está confirmada. ¡Te esperamos!',
            jsonb_build_object('activity_id', v_activity.id, 'slot_id', p_slot_id)
        );
    END IF;

    RETURN TRUE;
END;
$$;

-- 3) release_slot: avisar a la persona expulsada cuando un ADMIN libera su plaza -------
--    (no se avisa cuando el propio usuario libera su plaza).
CREATE OR REPLACE FUNCTION public.release_slot(p_slot_id uuid) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
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

    -- Avisar a la persona expulsada si fue un admin quien liberó su plaza (no auto-liberación).
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

    -- Attempt auto-promotion; returns TRUE if someone was promoted
    v_promoted := promote_substitute(p_slot_id, v_activity.id);

    -- Only notify remaining queue members if NO auto-promotion happened.
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

-- 4) activity_full: avisar a todos los que tienen plaza cuando se llena la actividad ---
--    "Lleno" = no quedan plazas libres ni pendientes (todas reserved/paid = confirmadas).
--    No aplica a modo unlimited. Se resetea cuando vuelve a quedar hueco.
CREATE OR REPLACE FUNCTION public.handle_activity_fullness() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public','pg_temp'
    AS $$
DECLARE
    v_activity RECORD;
    v_open int;
BEGIN
    SELECT * INTO v_activity FROM activities WHERE id = NEW.activity_id;
    IF v_activity IS NULL OR v_activity.slot_mode = 'unlimited' OR v_activity.status <> 'active' THEN
        RETURN NEW;
    END IF;

    SELECT count(*) INTO v_open
    FROM slots
    WHERE activity_id = NEW.activity_id AND status IN ('available', 'pending');

    IF v_open = 0 AND v_activity.full_notified_at IS NULL THEN
        UPDATE activities SET full_notified_at = now() WHERE id = NEW.activity_id;

        INSERT INTO notifications (user_id, type, title, body, data)
        SELECT DISTINCT s.reserved_by,
            'activity_full',
            'Actividad confirmada',
            v_activity.name || ' ha completado todas las plazas. ¡Se hace!',
            jsonb_build_object('activity_id', v_activity.id)
        FROM slots s
        WHERE s.activity_id = NEW.activity_id AND s.reserved_by IS NOT NULL;
    ELSIF v_open > 0 AND v_activity.full_notified_at IS NOT NULL THEN
        UPDATE activities SET full_notified_at = NULL WHERE id = NEW.activity_id;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_activity_fullness ON public.slots;
CREATE TRIGGER trg_activity_fullness
    AFTER INSERT OR UPDATE OF status ON public.slots
    FOR EACH ROW EXECUTE FUNCTION public.handle_activity_fullness();

-- 5) activity_cancelled / activity_updated: trigger sobre activities ------------------
--    Solo para actividades futuras (datetime > now()), para no avisar al archivar pasadas.
CREATE OR REPLACE FUNCTION public.handle_activity_change() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public','pg_temp'
    AS $$
BEGIN
    -- Cancelación: active -> archived (solo futuras)
    IF OLD.status = 'active' AND NEW.status = 'archived' AND NEW.datetime > now() THEN
        INSERT INTO notifications (user_id, type, title, body, data)
        SELECT DISTINCT s.reserved_by,
            'activity_cancelled',
            'Actividad cancelada',
            NEW.name || ' ha sido cancelada.',
            jsonb_build_object('activity_id', NEW.id)
        FROM slots s
        WHERE s.activity_id = NEW.id AND s.reserved_by IS NOT NULL;
        RETURN NEW;
    END IF;

    -- Cambio de fecha/hora o lugar (solo mientras está activa y es futura)
    IF NEW.status = 'active' AND NEW.datetime > now() AND (
         NEW.datetime      IS DISTINCT FROM OLD.datetime
      OR NEW.location_name  IS DISTINCT FROM OLD.location_name
      OR NEW.location_lat   IS DISTINCT FROM OLD.location_lat
      OR NEW.location_lng   IS DISTINCT FROM OLD.location_lng
    ) THEN
        INSERT INTO notifications (user_id, type, title, body, data)
        SELECT DISTINCT s.reserved_by,
            'activity_updated',
            'Cambio en la actividad',
            NEW.name || ' ha cambiado de fecha/hora o lugar. Revisa los detalles.',
            jsonb_build_object('activity_id', NEW.id)
        FROM slots s
        WHERE s.activity_id = NEW.id AND s.reserved_by IS NOT NULL;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_activity_change ON public.activities;
CREATE TRIGGER trg_activity_change
    AFTER UPDATE ON public.activities
    FOR EACH ROW EXECUTE FUNCTION public.handle_activity_change();
