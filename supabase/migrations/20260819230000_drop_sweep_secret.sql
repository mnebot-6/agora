-- ============================================================================
-- Retirada defensiva de sweep_secret().
--
-- Durante el desarrollo del barrido de pagos se probo una version que guardaba
-- una cabecera secreta en Vault y la exponia con una funcion SECURITY DEFINER
-- en el esquema public. Al probarla en local se vio que llamarla como `anon`
-- NO devolvia error de permisos: tumbaba el backend de Postgres, y en Postgres
-- un backend caido resetea todas las conexiones. Es decir, un endpoint publico
-- con el que cualquiera podia provocar un corte.
--
-- Esa version se reescribio ANTES de subirla, asi que en teoria nunca llego a
-- produccion. Pero "en teoria" no es suficiente para una brecha de seguridad y
-- los sondeos desde fuera no resultaron concluyentes, asi que se garantiza el
-- estado en vez de deducirlo. Este DROP es inofensivo si la funcion no existe
-- y definitivo si existe.
--
-- El barrido definitivo no necesita secreto: responde solo con recuentos.
-- ============================================================================

BEGIN;

DROP FUNCTION IF EXISTS public.sweep_secret();

-- El secreto en si tampoco tiene ya ningun uso.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'supabase_vault')
       AND EXISTS (SELECT 1 FROM vault.secrets WHERE name = 'stripe_sweep_secret') THEN
        DELETE FROM vault.secrets WHERE name = 'stripe_sweep_secret';
    END IF;
END $$;

COMMIT;
