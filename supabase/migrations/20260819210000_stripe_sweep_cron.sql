-- ============================================================================
-- Programar stripe-sweep cada minuto.
--
-- Es la red que confirma un pago aunque el usuario cierre el navegador y no
-- vuelva nunca a la app. Sin esto el barrido solo corre cuando alguien lo lanza
-- a mano, que es justo como una plaza cobrada se quedo en "Reservandose" la
-- primera vez que se probo el flujo de verdad.
--
-- SIN SECRETO COMPARTIDO, a proposito. El primer intento guardaba una cabecera
-- secreta en Vault y la exponia por RPC para que la Edge Function la leyera.
-- Se descarto al probarlo: llamar a esa funcion como `anon` NO daba error de
-- permisos, tumbaba el backend de Postgres (conexion cerrada, reproducible).
-- Un endpoint que cualquiera puede usar para cerrar conexiones no compensa.
--
-- En su lugar el barrido no expone nada que proteger: responde solo con
-- recuentos, nunca con identificadores, y es idempotente. Lo peor que consigue
-- quien lo invoque a mano es adelantar una reconciliacion que iba a ocurrir
-- igual dentro de un minuto.
-- ============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS pg_cron;

SELECT cron.unschedule('stripe-sweep')
WHERE EXISTS (SELECT 1 FROM cron.job WHERE jobname = 'stripe-sweep');

SELECT cron.schedule(
    'stripe-sweep',
    '* * * * *',
    $cron$
    SELECT net.http_post(
        url := 'https://ckuwetftnkhbndolcnjw.supabase.co/functions/v1/stripe-sweep',
        headers := '{"Content-Type": "application/json"}'::jsonb,
        body := '{}'::jsonb
    );
    $cron$
);

COMMIT;
