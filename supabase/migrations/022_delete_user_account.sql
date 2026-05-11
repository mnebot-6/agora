-- ============================================================================
-- Migration 022 — delete_my_account
--
-- Permite a un usuario autenticado eliminar su propia cuenta cumpliendo el
-- requisito de Google Play (política obligatoria desde 2024 para apps con
-- cuentas de usuario).
--
-- Estrategia: borramos la fila en auth.users y dejamos que las FK
-- ON DELETE CASCADE existentes propaguen el borrado a profiles y a todas las
-- tablas dependientes (community_members, notifications, slot_templates,
-- substitute_queue, community_join_requests, community_messages, etc.).
--
-- Para slots con reserved_by = uid se hace SET NULL (FK por defecto sin
-- cascada). Para activities/communities con created_by = uid no hay
-- cascada definida explícitamente — la actividad/comunidad sobrevive con
-- created_by apuntando a un usuario inexistente. Esto es aceptable porque
-- el contenido público no se atribuye en UI más allá del display_name del
-- creador (que ya no existirá).
--
-- IMPORTANTE: la función debe ejecutarse como propietario (postgres) para
-- tener permisos sobre auth.users. Por eso SECURITY DEFINER y owner postgres.
-- ============================================================================

CREATE OR REPLACE FUNCTION delete_my_account()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    DELETE FROM auth.users WHERE id = v_user_id;
END;
$$;

ALTER FUNCTION delete_my_account() OWNER TO postgres;

REVOKE ALL ON FUNCTION delete_my_account() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION delete_my_account() TO authenticated;
