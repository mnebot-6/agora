-- ============================================================================
-- Migration 021 — lookup_community_by_invite
--
-- Función read-only para resolver un código de invitación a la comunidad
-- correspondiente sin tener efectos secundarios (NO crea pending requests
-- ni añade miembros). El cliente la usa al abrir un deep link de invitación
-- para decidir el flujo:
--   - already_member       → navegar directo a la comunidad
--   - can_join_directly    → llamar join_community_by_invite_v2 sin confirmación
--   - requires_approval    → mostrar diálogo "¿Solicitar unirte?" antes de
--                            llamar join_community_by_invite_v2
--   - not_found            → invitación inválida
-- ============================================================================

CREATE OR REPLACE FUNCTION lookup_community_by_invite(p_invite_code TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
DECLARE
    v_user_id UUID := auth.uid();
    v_community communities%ROWTYPE;
    v_member_count INT;
    v_is_member BOOLEAN;
    v_status TEXT;
BEGIN
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('status', 'unauthenticated');
    END IF;

    SELECT * INTO v_community
    FROM communities
    WHERE invite_code = p_invite_code
    LIMIT 1;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('status', 'not_found');
    END IF;

    SELECT count(*) INTO v_member_count
    FROM community_members
    WHERE community_id = v_community.id;

    SELECT EXISTS(
        SELECT 1 FROM community_members
        WHERE community_id = v_community.id AND user_id = v_user_id
    ) INTO v_is_member;

    v_status := CASE
        WHEN v_is_member THEN 'already_member'
        WHEN v_community.visibility = 'public_open' THEN 'can_join_directly'
        ELSE 'requires_approval'
    END;

    RETURN jsonb_build_object(
        'status', v_status,
        'community', jsonb_build_object(
            'id', v_community.id,
            'name', v_community.name,
            'description', v_community.description,
            'visibility', v_community.visibility,
            'member_count', v_member_count,
            'parent_id', v_community.parent_id,
            'invite_code', v_community.invite_code
        )
    );
END;
$$;

GRANT EXECUTE ON FUNCTION lookup_community_by_invite(TEXT) TO authenticated;
