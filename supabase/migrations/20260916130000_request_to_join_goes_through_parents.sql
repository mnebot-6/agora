-- ============================================================================
-- Pedir unirse a una comunidad hija pasa primero por sus padres.
--
-- request_to_join_community solo miraba la comunidad pedida. Si el usuario no era
-- miembro de la padre, el INSERT chocaba con enforce_parent_membership: en una hija
-- abierta fallaba al momento, y en una con aprobacion la solicitud se creaba pero
-- fallaba al aprobarla.
--
-- Ahora recorre la cadena raiz -> hija: entra en los nodos abiertos y se para en el
-- primero que pida aprobacion, con target_community_id apuntando a la hija. Al
-- aprobarse, approve_join_request sigue la cascada hacia el destino
-- (cascade_join_towards_target), igual que con los enlaces de invitacion.
--
-- Misma firma que el baseline: CREATE OR REPLACE sustituye la funcion viva.
-- ============================================================================

CREATE OR REPLACE FUNCTION "public"."request_to_join_community"("p_community_id" "uuid", "p_message" "text" DEFAULT NULL::"text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_community RECORD;
    v_node RECORD;
    v_request_id uuid;
    v_target uuid;
    v_admin RECORD;
    v_display_name text;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_community FROM communities WHERE id = p_community_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Community not found';
    END IF;

    IF EXISTS (SELECT 1 FROM community_members
               WHERE community_id = p_community_id AND user_id = v_user_id) THEN
        RETURN jsonb_build_object('status', 'already_member');
    END IF;

    IF v_community.visibility = 'private' THEN
        RAISE EXCEPTION 'Private community — use invite code';
    END IF;

    -- Ancestros (raiz primero) y al final la comunidad pedida.
    FOR v_node IN
        SELECT c.id, c.name, c.visibility, a.depth
        FROM get_ancestor_community_ids(p_community_id) a
        JOIN communities c ON c.id = a.community_id
        UNION ALL
        SELECT v_community.id, v_community.name, v_community.visibility, 0
        ORDER BY depth DESC
    LOOP
        CONTINUE WHEN EXISTS (
            SELECT 1 FROM community_members
            WHERE community_id = v_node.id AND user_id = v_user_id
        );

        IF v_node.visibility = 'public_open' THEN
            INSERT INTO community_members (community_id, user_id, role)
            VALUES (v_node.id, v_user_id, 'user');
            CONTINUE;
        END IF;

        IF v_node.visibility = 'private' THEN
            RAISE EXCEPTION 'Private community — use invite code';
        END IF;

        -- public_approval: aqui se para la cadena.
        v_target := CASE WHEN v_node.id = p_community_id THEN NULL ELSE p_community_id END;

        SELECT id INTO v_request_id FROM community_join_requests
        WHERE community_id = v_node.id AND user_id = v_user_id AND status = 'pending'
        LIMIT 1;

        IF v_request_id IS NOT NULL THEN
            IF v_target IS NULL THEN
                RAISE EXCEPTION 'Already have a pending request';
            END IF;
            -- Ya esperaba en ese padre: que al aprobarse siga hasta esta hija.
            UPDATE community_join_requests SET target_community_id = v_target
            WHERE id = v_request_id;
            RETURN jsonb_build_object('status', 'pending', 'request_id', v_request_id);
        END IF;

        INSERT INTO community_join_requests (community_id, user_id, message, target_community_id)
        VALUES (v_node.id, v_user_id, p_message, v_target)
        RETURNING id INTO v_request_id;

        SELECT display_name INTO v_display_name FROM profiles WHERE id = v_user_id;

        FOR v_admin IN
            SELECT user_id FROM community_members
            WHERE community_id = v_node.id AND role = 'admin'
        LOOP
            INSERT INTO notifications (user_id, type, title, body, data)
            VALUES (
                v_admin.user_id,
                'join_request_received',
                'Nueva solicitud de unión',
                COALESCE(v_display_name, 'Un usuario') || ' quiere unirse a ' || v_node.name ||
                CASE WHEN v_target IS NOT NULL
                     THEN ' (camino hacia ' || v_community.name || ')'
                     ELSE '' END,
                jsonb_build_object(
                    'community_id', v_node.id,
                    'request_id', v_request_id,
                    'requester_id', v_user_id,
                    'requester_name', v_display_name,
                    'target_community_id', v_target
                )
            );
        END LOOP;

        RETURN jsonb_build_object('status', 'pending', 'request_id', v_request_id);
    END LOOP;

    RETURN jsonb_build_object('status', 'joined');
END;
$$;
