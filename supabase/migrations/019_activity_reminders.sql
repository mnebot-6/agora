-- ============================================================================
-- Migration 019: Activity reminders (push 1h antes de la actividad)
-- ============================================================================
-- Cada minuto, un cron-job busca actividades que empiezan en 60-65 min y, para
-- cada usuario que tenga un slot reservado, inserta una notificacion en la tabla
-- `notifications`. El webhook existente (push-notification Edge Function)
-- entrega el push automatico via FCM.
--
-- Idempotencia: la tabla `activity_reminders_sent` evita reenviar el mismo aviso.
-- ============================================================================

BEGIN;

-- 1. pg_cron — extension para programar tareas SQL
CREATE EXTENSION IF NOT EXISTS pg_cron;

-- 2. Sincronizar la CHECK constraint con todos los tipos que de hecho se usan
--    (la actual solo lista 3 pero los join_request_* ya se insertan a pelo).
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN (
        'new_activity',
        'slot_released',
        'substitute_promoted',
        'join_request_received',
        'join_request_approved',
        'join_request_rejected',
        'activity_reminder'
    ));

-- 3. Tabla de idempotencia: una fila por (activity, user) cuando ya se envio
CREATE TABLE IF NOT EXISTS activity_reminders_sent (
    activity_id uuid NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    sent_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (activity_id, user_id)
);

-- 4. Funcion que recorre actividades que empiezan en 60-65 min y crea avisos
CREATE OR REPLACE FUNCTION send_activity_reminders()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_record record;
BEGIN
    FOR v_record IN
        SELECT DISTINCT a.id            AS activity_id,
                        a.name          AS activity_name,
                        a.community_id,
                        s.reserved_by   AS user_id,
                        c.name          AS community_name
        FROM activities a
        INNER JOIN slots s ON s.activity_id = a.id
        INNER JOIN communities c ON c.id = a.community_id
        WHERE a.status = 'active'
          AND a.datetime BETWEEN now() + interval '60 minutes'
                              AND now() + interval '65 minutes'
          AND s.reserved_by IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM activity_reminders_sent ars
              WHERE ars.activity_id = a.id
                AND ars.user_id = s.reserved_by
          )
    LOOP
        INSERT INTO notifications (user_id, type, title, body, data)
        VALUES (
            v_record.user_id,
            'activity_reminder',
            'Tu actividad empieza pronto',
            v_record.activity_name || ' en ' || v_record.community_name || ' empieza en una hora',
            jsonb_build_object(
                'activity_id', v_record.activity_id,
                'community_id', v_record.community_id
            )
        );

        INSERT INTO activity_reminders_sent (activity_id, user_id)
        VALUES (v_record.activity_id, v_record.user_id);
    END LOOP;
END;
$$;

-- 5. Programar la funcion cada minuto
--    Si la tarea ya existia (re-aplicar migration), la actualizamos.
SELECT cron.unschedule('activity-reminders-1h')
WHERE EXISTS (SELECT 1 FROM cron.job WHERE jobname = 'activity-reminders-1h');

SELECT cron.schedule(
    'activity-reminders-1h',
    '* * * * *',
    'SELECT send_activity_reminders()'
);

COMMIT;
