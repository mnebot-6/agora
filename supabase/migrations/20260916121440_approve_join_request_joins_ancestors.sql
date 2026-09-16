-- ============================================================================
-- Aprobar la solicitud a una comunidad hija une tambien a sus ancestros.
--
-- request_to_join_community crea la solicitud en la hija aunque el usuario no sea
-- miembro de la padre, y al aprobarla el trigger enforce_parent_membership la
-- rechazaba. Cuerpo identico al del baseline salvo el bloque de ancestros; misma
-- firma, asi que CREATE OR REPLACE sustituye la funcion viva.
-- ============================================================================

CREATE OR REPLACE FUNCTION "public"."approve_join_request"("p_request_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_req RECORD;
    v_community_name text;
    v_target_name text;
    v_cascade jsonb;
    v_ancestor uuid;
    v_ancestor_name text;
    v_ancestor_visibility community_visibility;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_req FROM community_join_requests WHERE id = p_request_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Request not found';
    END IF;

    IF v_req.status != 'pending' THEN
        RAISE EXCEPTION 'Request is not pending';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_req.community_id
          AND user_id = v_caller
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can approve';
    END IF;

    -- Unirse a una hija exige ser miembro de la padre (trigger enforce_parent_membership).
    -- Quien pidio entrar directamente en la hija no lo es, y la aprobacion fallaba con
    -- "Must be member of parent community first". Se le une antes a los ancestros que
    -- le falten, de la raiz hacia abajo para que el trigger pase, pero sin saltarse la
    -- puerta de nadie: solo si el ancestro es abierto o quien aprueba tambien lo
    -- administra. Si no, error legible en vez del del trigger.
    FOR v_ancestor, v_ancestor_name, v_ancestor_visibility IN
        SELECT c.id, c.name, c.visibility
        FROM get_ancestor_community_ids(v_req.community_id) a
        JOIN communities c ON c.id = a.community_id
        ORDER BY a.depth DESC
    LOOP
        CONTINUE WHEN EXISTS (
            SELECT 1 FROM community_members
            WHERE community_id = v_ancestor AND user_id = v_req.user_id
        );

        IF v_ancestor_visibility <> 'public_open' AND NOT EXISTS (
            SELECT 1 FROM community_members
            WHERE community_id = v_ancestor AND user_id = v_caller AND role = 'admin'
        ) THEN
            RAISE EXCEPTION 'Esta persona tiene que ser aprobada antes en %', v_ancestor_name;
        END IF;

        INSERT INTO community_members (community_id, user_id, role)
        VALUES (v_ancestor, v_req.user_id, 'user');

        -- Su solicitud pendiente en ese ancestro queda resuelta por esta aprobacion.
        UPDATE community_join_requests
        SET status = 'approved', resolved_at = now(), resolved_by = v_caller
        WHERE community_id = v_ancestor AND user_id = v_req.user_id AND status = 'pending';
    END LOOP;

    INSERT INTO community_members (community_id, user_id, role)
    VALUES (v_req.community_id, v_req.user_id, 'user')
    ON CONFLICT (community_id, user_id) DO NOTHING;

    UPDATE community_join_requests
    SET status = 'approved', resolved_at = now(), resolved_by = v_caller
    WHERE id = p_request_id;

    SELECT name INTO v_community_name FROM communities WHERE id = v_req.community_id;

    -- Cascada si target distinto: intentar materializar siguiente eslabón.
    IF v_req.target_community_id IS NOT NULL
       AND v_req.target_community_id != v_req.community_id THEN
        v_cascade := cascade_join_towards_target(
            v_req.user_id,
            v_req.community_id,
            v_req.target_community_id
        );

        SELECT name INTO v_target_name FROM communities WHERE id = v_req.target_community_id;

        IF v_cascade->>'status' = 'joined' THEN
            -- Llegamos al destino final.
            INSERT INTO notifications (user_id, type, title, body, data)
            VALUES (
                v_req.user_id,
                'join_request_approved',
                'Solicitud aprobada',
                'Ya eres miembro de ' || v_target_name,
                jsonb_build_object(
                    'community_id', v_req.target_community_id,
                    'community_name', v_target_name
                )
            );
        ELSE
            -- Quedó pending en otro eslabón intermedio. Notificar progreso.
            INSERT INTO notifications (user_id, type, title, body, data)
            VALUES (
                v_req.user_id,
                'join_request_approved',
                'Avance en tu solicitud',
                'Has entrado en ' || v_community_name || '. Esperando aprobación para llegar a ' || v_target_name,
                jsonb_build_object(
                    'community_id', v_req.community_id,
                    'community_name', v_community_name,
                    'target_community_id', v_req.target_community_id,
                    'target_community_name', v_target_name
                )
            );
        END IF;
    ELSE
        INSERT INTO notifications (user_id, type, title, body, data)
        VALUES (
            v_req.user_id,
            'join_request_approved',
            'Solicitud aprobada',
            'Ya eres miembro de ' || v_community_name,
            jsonb_build_object('community_id', v_req.community_id, 'community_name', v_community_name)
        );
    END IF;
END;
$$;
