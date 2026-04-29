-- ============================================================================
-- Migración 015: join_community_by_invite respeta visibility
-- ============================================================================
-- Antes (014): join_community_by_invite hacía insert directo en community_members
--   independientemente de la visibility.
-- Ahora: si la comunidad es PUBLIC_APPROVAL, el invite te mete en la cola
--   de solicitudes (pending). Para PUBLIC_OPEN y PRIVATE, sigue siendo insert
--   directo (en PRIVATE el invite es la única forma de entrar; en PUBLIC_OPEN
--   compartir el invite acelera el flujo).
--
-- Estrategia: nuevo RPC `join_community_by_invite_v2` que devuelve jsonb con
-- la forma { "status": "joined" | "pending", "community": {...}, "request_id": ... }
-- Mantenemos la v1 por compatibilidad (la borraremos en una migración futura
-- cuando se confirme que ningún cliente la usa).
-- ============================================================================

CREATE OR REPLACE FUNCTION join_community_by_invite_v2(p_invite_code TEXT)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID := auth.uid();
    v_community communities%ROWTYPE;
    v_request_id UUID;
    v_existing_request_id UUID;
    v_display_name TEXT;
    v_admin RECORD;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_community FROM communities WHERE invite_code = p_invite_code;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Invalid invite code';
    END IF;

    -- Ya soy miembro
    IF EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_community.id AND user_id = v_user_id
    ) THEN
        RETURN jsonb_build_object(
            'status', 'already_member',
            'community', to_jsonb(v_community)
        );
    END IF;

    -- Rama PUBLIC_APPROVAL: crear / reutilizar pending request
    IF v_community.visibility = 'public_approval' THEN
        SELECT id INTO v_existing_request_id
        FROM community_join_requests
        WHERE community_id = v_community.id
          AND user_id = v_user_id
          AND status = 'pending'
        LIMIT 1;

        IF v_existing_request_id IS NOT NULL THEN
            RETURN jsonb_build_object(
                'status', 'pending',
                'community', to_jsonb(v_community),
                'request_id', v_existing_request_id
            );
        END IF;

        INSERT INTO community_join_requests (community_id, user_id, message)
        VALUES (v_community.id, v_user_id, NULL)
        RETURNING id INTO v_request_id;

        SELECT display_name INTO v_display_name FROM profiles WHERE id = v_user_id;

        FOR v_admin IN
            SELECT user_id FROM community_members
            WHERE community_id = v_community.id AND role = 'admin'
        LOOP
            INSERT INTO notifications (user_id, type, title, body, data)
            VALUES (
                v_admin.user_id,
                'join_request_received',
                'Nueva solicitud de unión',
                COALESCE(v_display_name, 'Un usuario') || ' quiere unirse a ' || v_community.name,
                jsonb_build_object(
                    'community_id', v_community.id,
                    'request_id', v_request_id,
                    'requester_id', v_user_id,
                    'requester_name', v_display_name
                )
            );
        END LOOP;

        RETURN jsonb_build_object(
            'status', 'pending',
            'community', to_jsonb(v_community),
            'request_id', v_request_id
        );
    END IF;

    -- Rama PUBLIC_OPEN / PRIVATE: insert directo en community_members
    INSERT INTO community_members (community_id, user_id, role)
    VALUES (v_community.id, v_user_id, 'user');

    -- Auto-cancelar pending propias en esta comunidad (mantiene comportamiento de 014)
    UPDATE community_join_requests
    SET status = 'cancelled', resolved_at = now()
    WHERE community_id = v_community.id
      AND user_id = v_user_id
      AND status = 'pending';

    RETURN jsonb_build_object(
        'status', 'joined',
        'community', to_jsonb(v_community)
    );
END;
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

GRANT EXECUTE ON FUNCTION join_community_by_invite_v2(TEXT) TO authenticated;
