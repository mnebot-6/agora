


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


CREATE SCHEMA IF NOT EXISTS "public";


ALTER SCHEMA "public" OWNER TO "pg_database_owner";


COMMENT ON SCHEMA "public" IS 'standard public schema';



CREATE TYPE "public"."community_visibility" AS ENUM (
    'public_open',
    'public_approval',
    'private'
);


ALTER TYPE "public"."community_visibility" OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."approve_guest_request"("p_request_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_req RECORD;
    v_activity RECORD;
    v_slot_id uuid;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_req FROM activity_guest_requests WHERE id = p_request_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Request not found';
    END IF;
    IF v_req.status <> 'pending' THEN
        RAISE EXCEPTION 'Request is not pending';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_req.activity_id;

    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_caller
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can approve';
    END IF;

    IF v_req.requested_position_ids IS NOT NULL THEN
        SELECT sub.id INTO v_slot_id
        FROM (
            SELECT s.id, s.sort_order, count(sp.position_id) AS pos_count
            FROM slots s
            JOIN slot_positions sp ON sp.slot_id = s.id
            WHERE s.activity_id = v_req.activity_id
              AND s.status = 'available'
              AND sp.position_id = ANY(v_req.requested_position_ids)
            GROUP BY s.id, s.sort_order
            ORDER BY pos_count ASC, s.sort_order ASC
            LIMIT 1
        ) sub
        JOIN slots locked ON locked.id = sub.id AND locked.status = 'available'
        FOR UPDATE OF locked SKIP LOCKED;

        IF v_slot_id IS NULL THEN
            RAISE EXCEPTION 'No available slot for the requested positions';
        END IF;

        UPDATE slots
        SET status = 'reserved', reserved_by = v_req.user_id,
            reserved_at = now(), is_guest = true
        WHERE id = v_slot_id;

        UPDATE activity_guest_requests
        SET status = 'approved', slot_id = v_slot_id,
            resolved_at = now(), resolved_by = v_caller
        WHERE id = p_request_id;
    ELSE
        UPDATE slots SET status = 'reserved'
        WHERE id = v_req.slot_id AND status = 'pending';

        UPDATE activity_guest_requests
        SET status = 'approved', resolved_at = now(), resolved_by = v_caller
        WHERE id = p_request_id;
    END IF;

    UPDATE profiles SET display_name = v_req.guest_name WHERE id = v_req.user_id;

    INSERT INTO notifications (user_id, type, title, body, data)
    VALUES (
        v_req.user_id,
        'guest_request_approved',
        'Asistencia aprobada',
        'Tu asistencia a ' || v_activity.name || ' ha sido aprobada',
        jsonb_build_object('activity_id', v_activity.id, 'request_id', p_request_id)
    );
END;
$$;


ALTER FUNCTION "public"."approve_guest_request"("p_request_id" "uuid") OWNER TO "postgres";


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


ALTER FUNCTION "public"."approve_join_request"("p_request_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."cancel_join_request"("p_request_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_req RECORD;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_req FROM community_join_requests WHERE id = p_request_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Request not found';
    END IF;

    IF v_req.user_id != v_caller THEN
        RAISE EXCEPTION 'Can only cancel your own requests';
    END IF;

    IF v_req.status != 'pending' THEN
        RAISE EXCEPTION 'Request is not pending';
    END IF;

    UPDATE community_join_requests
    SET status = 'cancelled', resolved_at = now()
    WHERE id = p_request_id;
END;
$$;


ALTER FUNCTION "public"."cancel_join_request"("p_request_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."cascade_join_towards_target"("p_user_id" "uuid", "p_from_community" "uuid", "p_target_community" "uuid") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_chain UUID[];
    v_node UUID;
    v_visibility community_visibility;
    v_request_id UUID;
    v_existing UUID;
    v_target_name TEXT;
    v_node_name TEXT;
    v_display_name TEXT;
    v_admin RECORD;
BEGIN
    IF p_from_community = p_target_community THEN
        RETURN jsonb_build_object('status', 'joined', 'community_id', p_target_community);
    END IF;

    -- Construir cadena de ancestros del target hacia raíz, hasta justo antes de p_from_community.
    -- Luego invertir para iterar en orden raíz -> hoja, comenzando justo por debajo de p_from_community.
    SELECT array_agg(community_id ORDER BY depth DESC) INTO v_chain
    FROM get_ancestor_community_ids(p_target_community);

    -- v_chain ahora está ordenado raíz -> nodos intermedios. Necesitamos los que están debajo de p_from_community.
    -- También añadimos el target al final.
    v_chain := v_chain || p_target_community;

    -- Saltar hasta encontrar el siguiente nodo después de p_from_community.
    DECLARE
        v_skip BOOLEAN := TRUE;
    BEGIN
        FOREACH v_node IN ARRAY v_chain LOOP
            IF v_skip THEN
                IF v_node = p_from_community THEN
                    v_skip := FALSE;
                END IF;
                CONTINUE;
            END IF;

            -- v_node es un nodo descendiente de p_from_community que hay que procesar.
            IF EXISTS (
                SELECT 1 FROM community_members
                WHERE community_id = v_node AND user_id = p_user_id
            ) THEN
                CONTINUE; -- ya es miembro, sigue
            END IF;

            SELECT visibility INTO v_visibility FROM communities WHERE id = v_node;

            IF v_visibility = 'public_open' THEN
                INSERT INTO community_members (community_id, user_id, role)
                VALUES (v_node, p_user_id, 'user');
                CONTINUE;
            END IF;

            -- public_approval o private: deja pending y termina cascada.
            -- Idempotente: si ya hay pending, reusa.
            SELECT id INTO v_existing
            FROM community_join_requests
            WHERE community_id = v_node
              AND user_id = p_user_id
              AND status = 'pending'
            LIMIT 1;

            IF v_existing IS NOT NULL THEN
                -- asegurar que apunte al target correcto si la cascada se reactiva
                UPDATE community_join_requests
                SET target_community_id = p_target_community
                WHERE id = v_existing;
                v_request_id := v_existing;
            ELSE
                INSERT INTO community_join_requests (community_id, user_id, message, target_community_id)
                VALUES (v_node, p_user_id, NULL, p_target_community)
                RETURNING id INTO v_request_id;

                SELECT name INTO v_node_name FROM communities WHERE id = v_node;
                SELECT name INTO v_target_name FROM communities WHERE id = p_target_community;
                SELECT display_name INTO v_display_name FROM profiles WHERE id = p_user_id;

                FOR v_admin IN
                    SELECT user_id FROM community_members
                    WHERE community_id = v_node AND role = 'admin'
                LOOP
                    INSERT INTO notifications (user_id, type, title, body, data)
                    VALUES (
                        v_admin.user_id,
                        'join_request_received',
                        'Nueva solicitud de unión',
                        COALESCE(v_display_name, 'Un usuario') || ' quiere unirse a ' || v_node_name ||
                        CASE WHEN v_node != p_target_community
                             THEN ' (camino hacia ' || v_target_name || ')'
                             ELSE '' END,
                        jsonb_build_object(
                            'community_id', v_node,
                            'request_id', v_request_id,
                            'requester_id', p_user_id,
                            'requester_name', v_display_name,
                            'target_community_id', p_target_community
                        )
                    );
                END LOOP;
            END IF;

            RETURN jsonb_build_object(
                'status', 'pending',
                'pending_at', v_node,
                'request_id', v_request_id
            );
        END LOOP;
    END;

    RETURN jsonb_build_object('status', 'joined', 'community_id', p_target_community);
END;
$$;


ALTER FUNCTION "public"."cascade_join_towards_target"("p_user_id" "uuid", "p_from_community" "uuid", "p_target_community" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."cascade_member_removal"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
BEGIN
    DELETE FROM community_members
    WHERE user_id = OLD.user_id
      AND community_id IN (SELECT community_id FROM get_descendant_community_ids(OLD.community_id));
    RETURN OLD;
END;
$$;


ALTER FUNCTION "public"."cascade_member_removal"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."check_max_tags_per_community"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
BEGIN
    IF (SELECT count(*) FROM community_tags WHERE community_id = NEW.community_id) >= 3 THEN
        RAISE EXCEPTION 'Máximo 3 tags por comunidad';
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."check_max_tags_per_community"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."community_breadcrumb"("p_id" "uuid") RETURNS "text"
    LANGUAGE "plpgsql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_parts TEXT[];
    v_id UUID := p_id;
    v_name TEXT;
    v_parent UUID;
BEGIN
    LOOP
        SELECT name, parent_id INTO v_name, v_parent FROM communities WHERE id = v_id;
        IF NOT FOUND THEN
            EXIT;
        END IF;
        v_parts := ARRAY[v_name] || v_parts;
        IF v_parent IS NULL THEN EXIT; END IF;
        v_id := v_parent;
    END LOOP;
    RETURN array_to_string(v_parts, ' › ');
END;
$$;


ALTER FUNCTION "public"."community_breadcrumb"("p_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."community_depth"("p_id" "uuid") RETURNS integer
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
    SELECT COALESCE(
        (SELECT MAX(depth) + 1 FROM get_ancestor_community_ids(p_id)),
        1
    );
$$;


ALTER FUNCTION "public"."community_depth"("p_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."enforce_community_depth_limit"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_parent_depth INT;
BEGIN
    IF NEW.parent_id IS NULL THEN
        RETURN NEW;
    END IF;
    v_parent_depth := community_depth(NEW.parent_id);
    IF v_parent_depth >= 5 THEN
        RAISE EXCEPTION 'Maximum community nesting depth (5) reached'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."enforce_community_depth_limit"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."enforce_parent_id_immutable"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
BEGIN
    IF OLD.parent_id IS DISTINCT FROM NEW.parent_id THEN
        RAISE EXCEPTION 'parent_id is immutable after creation'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."enforce_parent_id_immutable"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."enforce_parent_membership"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_parent UUID;
BEGIN
    SELECT parent_id INTO v_parent FROM communities WHERE id = NEW.community_id;
    IF v_parent IS NULL THEN
        RETURN NEW; -- raíz, no hay restricción
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_parent AND user_id = NEW.user_id
    ) THEN
        RAISE EXCEPTION 'Must be member of parent community first (parent=%, user=%)',
            v_parent, NEW.user_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."enforce_parent_membership"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."gen_activity_guest_code"() RETURNS "text"
    LANGUAGE "plpgsql"
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_alphabet text := 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    v_code text;
    i int;
BEGIN
    LOOP
        v_code := '';
        FOR i IN 1..8 LOOP
            v_code := v_code || substr(v_alphabet, 1 + floor(random() * 36)::int, 1);
        END LOOP;
        EXIT WHEN NOT EXISTS (SELECT 1 FROM activity_guest_links WHERE code = v_code);
    END LOOP;
    RETURN v_code;
END;
$$;


ALTER FUNCTION "public"."gen_activity_guest_code"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."generate_activity_guest_link"("p_activity_id" "uuid") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_activity RECORD;
    v_community RECORD;
    v_code text;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = p_activity_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Activity not found';
    END IF;

    SELECT * INTO v_community FROM communities WHERE id = v_activity.community_id;

    IF v_community.visibility NOT IN ('public_open', 'public_approval') THEN
        RAISE EXCEPTION 'Guest links are only allowed for public communities';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_user_id
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can generate guest links';
    END IF;

    SELECT code INTO v_code FROM activity_guest_links
    WHERE activity_id = p_activity_id AND NOT revoked;

    IF v_code IS NULL THEN
        v_code := gen_activity_guest_code();
        INSERT INTO activity_guest_links (activity_id, code, created_by)
        VALUES (p_activity_id, v_code, v_user_id)
        ON CONFLICT (activity_id) DO UPDATE
            SET code = EXCLUDED.code, created_by = EXCLUDED.created_by,
                created_at = now(), revoked = false
        RETURNING code INTO v_code;
    END IF;

    RETURN jsonb_build_object(
        'code', v_code,
        'url', 'https://share-agora.app/a/' || v_code
    );
END;
$$;


ALTER FUNCTION "public"."generate_activity_guest_link"("p_activity_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_activity_guest_preview"("p_code" "text") RETURNS "jsonb"
    LANGUAGE "plpgsql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_link RECORD;
    v_activity RECORD;
    v_community RECORD;
    v_capacity int;
    v_taken int;
    v_my_request RECORD;
    v_positions jsonb;
BEGIN
    SELECT * INTO v_link FROM activity_guest_links
    WHERE code = p_code AND NOT revoked;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('status', 'not_found');
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_link.activity_id;
    IF NOT FOUND OR v_activity.status <> 'active' THEN
        RETURN jsonb_build_object('status', 'not_found');
    END IF;

    SELECT * INTO v_community FROM communities WHERE id = v_activity.community_id;
    IF v_community.visibility NOT IN ('public_open', 'public_approval') THEN
        RETURN jsonb_build_object('status', 'not_found');
    END IF;

    IF v_activity.slot_mode = 'unlimited' THEN
        v_capacity := NULL;
        v_taken := (SELECT count(*) FROM slots
                    WHERE activity_id = v_activity.id AND status <> 'available');
    ELSE
        v_capacity := (SELECT count(*) FROM slots WHERE activity_id = v_activity.id);
        v_taken := (SELECT count(*) FROM slots
                    WHERE activity_id = v_activity.id AND status <> 'available');
    END IF;

    -- Build positions array for limited_with_positions
    IF v_activity.slot_mode = 'limited_with_positions' THEN
        SELECT COALESCE(jsonb_agg(
            jsonb_build_object(
                'id', p.id,
                'name', p.name,
                'available', COALESCE(avail.cnt, 0)
            ) ORDER BY p.name
        ), '[]'::jsonb)
        INTO v_positions
        FROM positions p
        LEFT JOIN LATERAL (
            SELECT count(*) AS cnt
            FROM slot_positions sp
            JOIN slots s ON s.id = sp.slot_id
            WHERE sp.position_id = p.id
              AND s.activity_id = v_activity.id
              AND s.status = 'available'
        ) avail ON true
        WHERE p.activity_id = v_activity.id
          AND EXISTS (
              SELECT 1 FROM slot_positions sp2
              JOIN slots s2 ON s2.id = sp2.slot_id
              WHERE sp2.position_id = p.id AND s2.activity_id = v_activity.id
          );
    ELSE
        v_positions := NULL;
    END IF;

    IF v_user_id IS NOT NULL THEN
        SELECT * INTO v_my_request FROM activity_guest_requests
        WHERE activity_id = v_activity.id AND user_id = v_user_id
        ORDER BY requested_at DESC
        LIMIT 1;
    END IF;

    RETURN jsonb_build_object(
        'status', 'ok',
        'activity', jsonb_build_object(
            'id', v_activity.id,
            'name', v_activity.name,
            'description', v_activity.description,
            'datetime', v_activity.datetime,
            'duration_minutes', v_activity.duration_minutes,
            'location_name', v_activity.location_name,
            'cost_description', v_activity.cost_description,
            'slot_mode', v_activity.slot_mode,
            'capacity', v_capacity,
            'taken', v_taken
        ),
        'community', jsonb_build_object(
            'id', v_community.id,
            'name', v_community.name
        ),
        'positions', v_positions,
        'is_member', (v_user_id IS NOT NULL AND EXISTS (
            SELECT 1 FROM community_members
            WHERE community_id = v_community.id AND user_id = v_user_id
        )),
        'my_request', CASE
            WHEN v_my_request.id IS NULL THEN NULL
            ELSE jsonb_build_object(
                'id', v_my_request.id,
                'status', v_my_request.status,
                'guest_name', v_my_request.guest_name,
                'requested_at', v_my_request.requested_at
            )
        END
    );
END;
$$;


ALTER FUNCTION "public"."get_activity_guest_preview"("p_code" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_ancestor_community_ids"("p_id" "uuid") RETURNS TABLE("community_id" "uuid", "depth" integer)
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
    WITH RECURSIVE ancestors AS (
        SELECT c.parent_id AS id, 1 AS d
        FROM communities c
        WHERE c.id = p_id AND c.parent_id IS NOT NULL
        UNION ALL
        SELECT c.parent_id, a.d + 1
        FROM communities c
        JOIN ancestors a ON c.id = a.id
        WHERE c.parent_id IS NOT NULL
    )
    SELECT id, d FROM ancestors;
$$;


ALTER FUNCTION "public"."get_ancestor_community_ids"("p_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_descendant_community_ids"("p_root" "uuid") RETURNS TABLE("community_id" "uuid")
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
    WITH RECURSIVE descendants AS (
        SELECT id FROM communities WHERE parent_id = p_root
        UNION ALL
        SELECT c.id
        FROM communities c
        JOIN descendants d ON c.parent_id = d.id
    )
    SELECT id FROM descendants;
$$;


ALTER FUNCTION "public"."get_descendant_community_ids"("p_root" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_my_admin_community_ids"() RETURNS SETOF "uuid"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
    SELECT community_id FROM public.community_members WHERE user_id = auth.uid() AND role = 'admin';
$$;


ALTER FUNCTION "public"."get_my_admin_community_ids"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_my_community_ids"() RETURNS SETOF "uuid"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
    SELECT community_id FROM public.community_members WHERE user_id = auth.uid();
$$;


ALTER FUNCTION "public"."get_my_community_ids"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_public_community_preview"("p_community_id" "uuid") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_community RECORD;
    v_result jsonb;
BEGIN
    SELECT * INTO v_community
    FROM communities
    WHERE id = p_community_id
      AND visibility IN ('public_open', 'public_approval');

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Community not found or not public';
    END IF;

    -- Si algún ancestro es privado, ocultar (cadena no pública).
    IF EXISTS (
        SELECT 1
        FROM get_ancestor_community_ids(p_community_id) anc
        JOIN communities ac ON ac.id = anc.community_id
        WHERE ac.visibility = 'private'
    ) THEN
        RAISE EXCEPTION 'Community not found or not public';
    END IF;

    SELECT jsonb_build_object(
        'id', v_community.id,
        'name', v_community.name,
        'description', v_community.description,
        'image_url', v_community.image_url,
        'visibility', v_community.visibility,
        'parent_id', v_community.parent_id,
        'breadcrumb', community_breadcrumb(v_community.id),
        'member_count', (SELECT count(*) FROM community_members WHERE community_id = v_community.id),
        'activity_count_upcoming', (SELECT count(*) FROM activities
                                    WHERE community_id = v_community.id
                                      AND status = 'active'
                                      AND datetime >= now()),
        'tags', COALESCE((
            SELECT jsonb_agg(jsonb_build_object(
                'id', t.id, 'slug', t.slug,
                'name_es', t.name_es, 'name_en', t.name_en,
                'icon', t.icon, 'sort_order', t.sort_order
            ) ORDER BY t.sort_order)
            FROM community_tags ct
            JOIN tags t ON t.id = ct.tag_id
            WHERE ct.community_id = v_community.id
        ), '[]'::jsonb)
    ) INTO v_result;

    RETURN v_result;
END;
$$;


ALTER FUNCTION "public"."get_public_community_preview"("p_community_id" "uuid") OWNER TO "postgres";

SET default_tablespace = '';

SET default_table_access_method = "heap";


CREATE TABLE IF NOT EXISTS "public"."activities" (
    "id" "uuid" DEFAULT "extensions"."uuid_generate_v4"() NOT NULL,
    "community_id" "uuid" NOT NULL,
    "name" "text" NOT NULL,
    "description" "text",
    "datetime" timestamp with time zone NOT NULL,
    "duration_minutes" integer NOT NULL,
    "location_name" "text",
    "location_lat" double precision,
    "location_lng" double precision,
    "cost_description" "text",
    "slot_mode" "text" NOT NULL,
    "max_slots" integer,
    "created_by" "uuid" NOT NULL,
    "status" "text" DEFAULT 'active'::"text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "activities_slot_mode_check" CHECK (("slot_mode" = ANY (ARRAY['unlimited'::"text", 'limited'::"text", 'limited_with_positions'::"text"]))),
    CONSTRAINT "activities_status_check" CHECK (("status" = ANY (ARRAY['active'::"text", 'archived'::"text"])))
);


ALTER TABLE "public"."activities" OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_upcoming_activities"() RETURNS SETOF "public"."activities"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    RETURN QUERY
    SELECT a.*
    FROM activities a
    INNER JOIN community_members cm ON cm.community_id = a.community_id
    WHERE cm.user_id = v_user_id
      AND a.status = 'active'
      AND a.datetime >= now()
    ORDER BY a.datetime ASC;
END;
$$;


ALTER FUNCTION "public"."get_upcoming_activities"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_new_community"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
BEGIN
    INSERT INTO public.community_members (community_id, user_id, role)
    VALUES (NEW.id, NEW.created_by, 'admin');
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."handle_new_community"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_new_user"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
BEGIN
    INSERT INTO public.profiles (id, display_name)
    VALUES (
        NEW.id,
        COALESCE(
            NEW.raw_user_meta_data->>'display_name',
            NULLIF(split_part(COALESCE(NEW.email, ''), '@', 1), ''),
            'Invitado'
        )
    );
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."handle_new_user"() OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."communities" (
    "id" "uuid" DEFAULT "extensions"."uuid_generate_v4"() NOT NULL,
    "name" "text" NOT NULL,
    "description" "text",
    "image_url" "text",
    "invite_code" "text" NOT NULL,
    "created_by" "uuid" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "visibility" "public"."community_visibility" DEFAULT 'private'::"public"."community_visibility" NOT NULL,
    "parent_id" "uuid",
    CONSTRAINT "invite_code_format" CHECK ((("length"("invite_code") = 8) AND ("invite_code" ~ '^[A-Z0-9]+$'::"text")))
);


ALTER TABLE "public"."communities" OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."join_community_by_invite"("p_invite_code" "text") RETURNS "public"."communities"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_community communities%ROWTYPE;
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_community FROM communities WHERE invite_code = p_invite_code;
    IF v_community IS NULL THEN
        RAISE EXCEPTION 'Invalid invite code';
    END IF;

    IF EXISTS (SELECT 1 FROM community_members
               WHERE community_id = v_community.id AND user_id = v_user_id) THEN
        RAISE EXCEPTION 'Already a member of this community';
    END IF;

    INSERT INTO community_members (community_id, user_id, role)
    VALUES (v_community.id, v_user_id, 'user');

    -- Si el usuario tenía una solicitud pending en esta comunidad, cancelarla
    UPDATE community_join_requests
    SET status = 'cancelled', resolved_at = now()
    WHERE community_id = v_community.id
      AND user_id = v_user_id
      AND status = 'pending';

    RETURN v_community;
END;
$$;


ALTER FUNCTION "public"."join_community_by_invite"("p_invite_code" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."join_community_by_invite_v2"("p_invite_code" "text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_user_id UUID := auth.uid();
    v_community communities%ROWTYPE;
    v_root UUID;
    v_cascade jsonb;
    v_visibility community_visibility;
    v_request_id UUID;
    v_existing UUID;
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

    -- Encontrar la raíz del árbol del target.
    SELECT community_id INTO v_root
    FROM get_ancestor_community_ids(v_community.id)
    ORDER BY depth DESC
    LIMIT 1;
    IF v_root IS NULL THEN
        v_root := v_community.id; -- el target es la propia raíz
    END IF;

    -- Procesar la raíz primero (si no soy miembro de ella).
    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_root AND user_id = v_user_id
    ) THEN
        SELECT visibility INTO v_visibility FROM communities WHERE id = v_root;

        IF v_visibility = 'public_open' THEN
            INSERT INTO community_members (community_id, user_id, role)
            VALUES (v_root, v_user_id, 'user');
        ELSE
            -- public_approval o private: pending request en raíz con target_community_id
            SELECT id INTO v_existing
            FROM community_join_requests
            WHERE community_id = v_root
              AND user_id = v_user_id
              AND status = 'pending'
            LIMIT 1;

            IF v_existing IS NOT NULL THEN
                UPDATE community_join_requests
                SET target_community_id = v_community.id
                WHERE id = v_existing;
                v_request_id := v_existing;
            ELSE
                INSERT INTO community_join_requests (community_id, user_id, message, target_community_id)
                VALUES (v_root, v_user_id, NULL, v_community.id)
                RETURNING id INTO v_request_id;

                SELECT display_name INTO v_display_name FROM profiles WHERE id = v_user_id;

                FOR v_admin IN
                    SELECT user_id FROM community_members
                    WHERE community_id = v_root AND role = 'admin'
                LOOP
                    INSERT INTO notifications (user_id, type, title, body, data)
                    VALUES (
                        v_admin.user_id,
                        'join_request_received',
                        'Nueva solicitud de unión',
                        COALESCE(v_display_name, 'Un usuario') || ' quiere unirse a ' ||
                            (SELECT name FROM communities WHERE id = v_root) ||
                        CASE WHEN v_root != v_community.id
                             THEN ' (camino hacia ' || v_community.name || ')'
                             ELSE '' END,
                        jsonb_build_object(
                            'community_id', v_root,
                            'request_id', v_request_id,
                            'requester_id', v_user_id,
                            'requester_name', v_display_name,
                            'target_community_id', v_community.id
                        )
                    );
                END LOOP;
            END IF;

            -- Auto-cancelar otras pending propias en esta misma comunidad raíz que no apunten al target.
            -- (no aplicable aquí: ya cubierto)

            RETURN jsonb_build_object(
                'status', 'pending',
                'community', to_jsonb(v_community),
                'request_id', v_request_id,
                'pending_at', v_root
            );
        END IF;
    END IF;

    -- Llegados aquí, ya somos miembro de la raíz. Cascadear hacia el target.
    v_cascade := cascade_join_towards_target(v_user_id, v_root, v_community.id);

    IF v_cascade->>'status' = 'pending' THEN
        RETURN jsonb_build_object(
            'status', 'pending',
            'community', to_jsonb(v_community),
            'request_id', (v_cascade->>'request_id')::uuid,
            'pending_at', (v_cascade->>'pending_at')::uuid
        );
    END IF;

    -- Auto-cancelar pending propias en el target que ya no aplican.
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
$$;


ALTER FUNCTION "public"."join_community_by_invite_v2"("p_invite_code" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."join_substitute_queue"("p_activity_id" "uuid", "p_position_id" "uuid" DEFAULT NULL::"uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_activity RECORD;
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = p_activity_id;
    IF v_activity IS NULL THEN
        RAISE EXCEPTION 'Activity not found';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id AND user_id = v_user_id
    ) THEN
        RAISE EXCEPTION 'User is not a member of this community';
    END IF;

    -- Prevent joining the queue when already holding a slot for this activity
    IF EXISTS (
        SELECT 1 FROM slots
        WHERE activity_id = p_activity_id
          AND reserved_by = v_user_id
          AND status IN ('reserved', 'paid')
    ) THEN
        RAISE EXCEPTION 'Ya tienes una plaza reservada en esta actividad';
    END IF;

    -- ON CONFLICT now works for NULL position_id thanks to NULLS NOT DISTINCT constraint
    INSERT INTO substitute_queue (activity_id, user_id, position_id)
    VALUES (p_activity_id, v_user_id, p_position_id)
    ON CONFLICT (activity_id, user_id, position_id) DO NOTHING;
END;
$$;


ALTER FUNCTION "public"."join_substitute_queue"("p_activity_id" "uuid", "p_position_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."leave_substitute_queue"("p_activity_id" "uuid", "p_position_id" "uuid" DEFAULT NULL::"uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF p_position_id IS NULL THEN
        DELETE FROM substitute_queue
        WHERE activity_id = p_activity_id
          AND user_id = v_user_id
          AND position_id IS NULL;
    ELSE
        DELETE FROM substitute_queue
        WHERE activity_id = p_activity_id
          AND user_id = v_user_id
          AND position_id = p_position_id;
    END IF;
END;
$$;


ALTER FUNCTION "public"."leave_substitute_queue"("p_activity_id" "uuid", "p_position_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."list_pending_guest_requests"("p_activity_id" "uuid") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_activity RECORD;
    v_result jsonb;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = p_activity_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Activity not found';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_caller
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can view guest requests';
    END IF;

    SELECT COALESCE(jsonb_agg(
        jsonb_build_object(
            'id', r.id,
            'activity_id', r.activity_id,
            'slot_id', r.slot_id,
            'guest_name', r.guest_name,
            'guest_phone', r.guest_phone,
            'requested_at', r.requested_at,
            'requested_positions', COALESCE((
                SELECT jsonb_agg(p.name ORDER BY p.name)
                FROM positions p
                WHERE p.id = ANY(r.requested_position_ids)
            ), '[]'::jsonb)
        ) ORDER BY r.requested_at ASC
    ), '[]'::jsonb)
    INTO v_result
    FROM activity_guest_requests r
    WHERE r.activity_id = p_activity_id AND r.status = 'pending';

    RETURN v_result;
END;
$$;


ALTER FUNCTION "public"."list_pending_guest_requests"("p_activity_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."lookup_community_by_invite"("p_invite_code" "text") RETURNS "jsonb"
    LANGUAGE "plpgsql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
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


ALTER FUNCTION "public"."lookup_community_by_invite"("p_invite_code" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."mark_slot_paid"("p_slot_id" "uuid") RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
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
    RETURN TRUE;
END;
$$;


ALTER FUNCTION "public"."mark_slot_paid"("p_slot_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."notify_new_activity"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
BEGIN
    INSERT INTO public.notifications (user_id, type, title, body, data)
    SELECT
        cm.user_id,
        'new_activity',
        'Nueva actividad',
        NEW.name,
        jsonb_build_object(
            'activity_id', NEW.id,
            'community_id', NEW.community_id
        )
    FROM public.community_members cm
    WHERE cm.community_id = NEW.community_id
      AND cm.user_id != NEW.created_by;

    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."notify_new_activity"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."notify_substitute_promoted"("p_user_id" "uuid", "p_activity_id" "uuid", "p_slot_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    INSERT INTO notifications (user_id, type, title, body, data)
    VALUES (
        p_user_id,
        'substitute_promoted',
        'Has sido promocionado',
        'Se te ha asignado una plaza desde la cola de suplentes',
        jsonb_build_object('activity_id', p_activity_id, 'slot_id', p_slot_id)
    );
END;
$$;


ALTER FUNCTION "public"."notify_substitute_promoted"("p_user_id" "uuid", "p_activity_id" "uuid", "p_slot_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."promote_substitute"("p_slot_id" "uuid", "p_activity_id" "uuid") RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_sub record;
    v_slot_positions uuid[];
BEGIN
    -- Get positions for this slot
    SELECT array_agg(sp.position_id) INTO v_slot_positions
    FROM slot_positions sp
    WHERE sp.slot_id = p_slot_id;

    -- Find first matching substitute (FIFO), lock to prevent concurrent promotions
    IF v_slot_positions IS NOT NULL AND array_length(v_slot_positions, 1) > 0 THEN
        -- Slot has specific positions: match by position OR "any position" (NULL)
        SELECT * INTO v_sub
        FROM substitute_queue
        WHERE activity_id = p_activity_id
          AND (position_id = ANY(v_slot_positions) OR position_id IS NULL)
        ORDER BY queued_at ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED;
    ELSE
        -- Slot has no positions: take first in queue
        SELECT * INTO v_sub
        FROM substitute_queue
        WHERE activity_id = p_activity_id
        ORDER BY queued_at ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED;
    END IF;

    IF v_sub IS NULL THEN
        RETURN FALSE;
    END IF;

    -- Reserve the slot for the substitute
    UPDATE slots
    SET status = 'reserved',
        reserved_by = v_sub.user_id,
        reserved_at = now()
    WHERE id = p_slot_id;

    -- Remove ALL queue entries for this user in this activity (not just the matched one)
    DELETE FROM substitute_queue
    WHERE activity_id = p_activity_id
      AND user_id = v_sub.user_id;

    -- Notify the promoted user
    PERFORM notify_substitute_promoted(v_sub.user_id, p_activity_id, p_slot_id);

    RETURN TRUE;
END;
$$;


ALTER FUNCTION "public"."promote_substitute"("p_slot_id" "uuid", "p_activity_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."reject_guest_request"("p_request_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_req RECORD;
    v_activity RECORD;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_req FROM activity_guest_requests WHERE id = p_request_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Request not found';
    END IF;
    IF v_req.status <> 'pending' THEN
        RAISE EXCEPTION 'Request is not pending';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_req.activity_id;

    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_caller
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can reject';
    END IF;

    -- Only release slot if one was retained (unlimited/limited modes)
    IF v_req.slot_id IS NOT NULL THEN
        IF v_activity.slot_mode = 'unlimited' THEN
            DELETE FROM slots WHERE id = v_req.slot_id;
        ELSE
            UPDATE slots
            SET status = 'available', reserved_by = NULL, reserved_at = NULL, is_guest = false
            WHERE id = v_req.slot_id;
        END IF;
    END IF;

    UPDATE activity_guest_requests
    SET status = 'rejected', resolved_at = now(), resolved_by = v_caller
    WHERE id = p_request_id;

    INSERT INTO notifications (user_id, type, title, body, data)
    VALUES (
        v_req.user_id,
        'guest_request_rejected',
        'Asistencia no aprobada',
        'Tu solicitud para asistir a ' || v_activity.name || ' no fue aprobada',
        jsonb_build_object('activity_id', v_activity.id, 'request_id', p_request_id)
    );
END;
$$;


ALTER FUNCTION "public"."reject_guest_request"("p_request_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."reject_join_request"("p_request_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_req RECORD;
    v_community_name text;
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
        RAISE EXCEPTION 'Only community admins can reject';
    END IF;

    UPDATE community_join_requests
    SET status = 'rejected', resolved_at = now(), resolved_by = v_caller
    WHERE id = p_request_id;

    SELECT name INTO v_community_name FROM communities WHERE id = v_req.community_id;

    INSERT INTO notifications (user_id, type, title, body, data)
    VALUES (
        v_req.user_id,
        'join_request_rejected',
        'Solicitud rechazada',
        'Tu solicitud para unirte a ' || v_community_name || ' no fue aprobada',
        jsonb_build_object('community_id', v_req.community_id, 'community_name', v_community_name)
    );
END;
$$;


ALTER FUNCTION "public"."reject_join_request"("p_request_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."release_slot"("p_slot_id" "uuid") RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
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

    -- Attempt auto-promotion; returns TRUE if someone was promoted
    v_promoted := promote_substitute(p_slot_id, v_activity.id);

    -- Only notify remaining queue members if NO auto-promotion happened.
    -- When promotion occurs the promoted user already received 'substitute_promoted';
    -- notifying all queue members here would also send a spurious 'slot_released'
    -- to other members whose position does not match the now-taken slot.
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


ALTER FUNCTION "public"."release_slot"("p_slot_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."request_guest_slot"("p_code" "text", "p_name" "text", "p_phone" "text", "p_position_ids" "uuid"[] DEFAULT NULL::"uuid"[]) RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_link RECORD;
    v_activity RECORD;
    v_community RECORD;
    v_existing RECORD;
    v_slot_id uuid;
    v_next_sort int;
    v_request_id uuid;
    v_admin RECORD;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF p_name IS NULL OR length(trim(p_name)) = 0 THEN
        RAISE EXCEPTION 'Name is required';
    END IF;
    IF p_phone IS NULL OR length(trim(p_phone)) = 0 THEN
        RAISE EXCEPTION 'Phone is required';
    END IF;

    SELECT * INTO v_link FROM activity_guest_links
    WHERE code = p_code AND NOT revoked;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Invalid or revoked link';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_link.activity_id FOR UPDATE;
    IF NOT FOUND OR v_activity.status <> 'active' THEN
        RAISE EXCEPTION 'Activity not available';
    END IF;

    SELECT * INTO v_community FROM communities WHERE id = v_activity.community_id;
    IF v_community.visibility NOT IN ('public_open', 'public_approval') THEN
        RAISE EXCEPTION 'Activity not available';
    END IF;

    IF EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id AND user_id = v_user_id
    ) THEN
        RETURN jsonb_build_object('status', 'already_member');
    END IF;

    SELECT * INTO v_existing FROM activity_guest_requests
    WHERE activity_id = v_activity.id
      AND user_id = v_user_id
      AND status IN ('pending', 'approved');
    IF FOUND THEN
        RETURN jsonb_build_object(
            'status', v_existing.status,
            'request_id', v_existing.id,
            'slot_id', v_existing.slot_id
        );
    END IF;

    -- === limited_with_positions: validate positions, no slot retention ===
    IF v_activity.slot_mode = 'limited_with_positions' THEN
        IF p_position_ids IS NULL OR array_length(p_position_ids, 1) IS NULL THEN
            RAISE EXCEPTION 'At least one position must be selected';
        END IF;

        -- Validate all position IDs belong to this activity
        IF EXISTS (
            SELECT 1 FROM unnest(p_position_ids) pid
            WHERE NOT EXISTS (
                SELECT 1 FROM positions WHERE id = pid AND activity_id = v_activity.id
            )
        ) THEN
            RAISE EXCEPTION 'Invalid position selected';
        END IF;

        -- Check at least one compatible available slot exists
        IF NOT EXISTS (
            SELECT 1
            FROM slots s
            JOIN slot_positions sp ON sp.slot_id = s.id
            WHERE s.activity_id = v_activity.id
              AND s.status = 'available'
              AND sp.position_id = ANY(p_position_ids)
        ) THEN
            RETURN jsonb_build_object('status', 'full');
        END IF;

        INSERT INTO activity_guest_requests
            (activity_id, user_id, slot_id, guest_name, guest_phone, requested_position_ids)
        VALUES
            (v_activity.id, v_user_id, NULL, trim(p_name), trim(p_phone), p_position_ids)
        RETURNING id INTO v_request_id;

    -- === unlimited: create slot on the fly ===
    ELSIF v_activity.slot_mode = 'unlimited' THEN
        SELECT COALESCE(max(sort_order), -1) + 1 INTO v_next_sort
        FROM slots WHERE activity_id = v_activity.id;

        INSERT INTO slots (activity_id, sort_order, status, reserved_by, reserved_at, is_guest)
        VALUES (v_activity.id, v_next_sort, 'pending', v_user_id, now(), true)
        RETURNING id INTO v_slot_id;

        INSERT INTO activity_guest_requests (activity_id, user_id, slot_id, guest_name, guest_phone)
        VALUES (v_activity.id, v_user_id, v_slot_id, trim(p_name), trim(p_phone))
        RETURNING id INTO v_request_id;

    -- === limited (no positions): grab first available slot ===
    ELSE
        SELECT id INTO v_slot_id FROM slots
        WHERE activity_id = v_activity.id AND status = 'available'
        ORDER BY sort_order
        FOR UPDATE SKIP LOCKED
        LIMIT 1;

        IF v_slot_id IS NULL THEN
            RETURN jsonb_build_object('status', 'full');
        END IF;

        UPDATE slots
        SET status = 'pending', reserved_by = v_user_id, reserved_at = now(), is_guest = true
        WHERE id = v_slot_id;

        INSERT INTO activity_guest_requests (activity_id, user_id, slot_id, guest_name, guest_phone)
        VALUES (v_activity.id, v_user_id, v_slot_id, trim(p_name), trim(p_phone))
        RETURNING id INTO v_request_id;
    END IF;

    -- Notify admins
    FOR v_admin IN
        SELECT user_id FROM community_members
        WHERE community_id = v_activity.community_id AND role = 'admin'
    LOOP
        INSERT INTO notifications (user_id, type, title, body, data)
        VALUES (
            v_admin.user_id,
            'guest_request_received',
            'Nueva solicitud de invitado',
            trim(p_name) || ' quiere asistir a ' || v_activity.name,
            jsonb_build_object(
                'activity_id', v_activity.id,
                'request_id', v_request_id,
                'guest_name', trim(p_name)
            )
        );
    END LOOP;

    RETURN jsonb_build_object(
        'status', 'pending',
        'request_id', v_request_id,
        'slot_id', v_slot_id
    );
END;
$$;


ALTER FUNCTION "public"."request_guest_slot"("p_code" "text", "p_name" "text", "p_phone" "text", "p_position_ids" "uuid"[]) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."request_to_join_community"("p_community_id" "uuid", "p_message" "text" DEFAULT NULL::"text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_community RECORD;
    v_request_id uuid;
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

    IF v_community.visibility = 'public_open' THEN
        INSERT INTO community_members (community_id, user_id, role)
        VALUES (p_community_id, v_user_id, 'user');
        RETURN jsonb_build_object('status', 'joined');
    END IF;

    -- public_approval
    IF EXISTS (
        SELECT 1 FROM community_join_requests
        WHERE community_id = p_community_id
          AND user_id = v_user_id
          AND status = 'pending'
    ) THEN
        RAISE EXCEPTION 'Already have a pending request';
    END IF;

    INSERT INTO community_join_requests (community_id, user_id, message)
    VALUES (p_community_id, v_user_id, p_message)
    RETURNING id INTO v_request_id;

    SELECT display_name INTO v_display_name FROM profiles WHERE id = v_user_id;

    FOR v_admin IN
        SELECT user_id FROM community_members
        WHERE community_id = p_community_id AND role = 'admin'
    LOOP
        INSERT INTO notifications (user_id, type, title, body, data)
        VALUES (
            v_admin.user_id,
            'join_request_received',
            'Nueva solicitud de unión',
            COALESCE(v_display_name, 'Un usuario') || ' quiere unirse a ' || v_community.name,
            jsonb_build_object(
                'community_id', p_community_id,
                'request_id', v_request_id,
                'requester_id', v_user_id,
                'requester_name', v_display_name
            )
        );
    END LOOP;

    RETURN jsonb_build_object('status', 'pending', 'request_id', v_request_id);
END;
$$;


ALTER FUNCTION "public"."request_to_join_community"("p_community_id" "uuid", "p_message" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."reserve_slot"("p_slot_id" "uuid") RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_slot RECORD;
    v_activity RECORD;
    v_user_id UUID := auth.uid();
    v_slot_positions uuid[];
    v_first_in_queue RECORD;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF v_slot IS NULL THEN
        RAISE EXCEPTION 'Slot not found';
    END IF;

    IF v_slot.status != 'available' THEN
        RETURN FALSE;
    END IF;

    -- Verify user is a member of the activity's community
    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;
    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id AND user_id = v_user_id
    ) THEN
        RAISE EXCEPTION 'User is not a member of this community';
    END IF;

    -- Check queue priority using the same position-matching logic as promote_substitute
    SELECT array_agg(sp.position_id) INTO v_slot_positions
    FROM slot_positions sp
    WHERE sp.slot_id = p_slot_id;

    IF v_slot_positions IS NOT NULL AND array_length(v_slot_positions, 1) > 0 THEN
        SELECT * INTO v_first_in_queue
        FROM substitute_queue
        WHERE activity_id = v_slot.activity_id
          AND (position_id = ANY(v_slot_positions) OR position_id IS NULL)
        ORDER BY queued_at ASC
        LIMIT 1;
    ELSE
        SELECT * INTO v_first_in_queue
        FROM substitute_queue
        WHERE activity_id = v_slot.activity_id
        ORDER BY queued_at ASC
        LIMIT 1;
    END IF;

    IF v_first_in_queue IS NOT NULL AND v_first_in_queue.user_id != v_user_id THEN
        -- Someone else has priority in the queue.
        -- Re-trigger promote_substitute (handles the case where auto-promotion
        -- was skipped because the released slot had a different position than
        -- the queue member's registered position, but another slot is now free).
        PERFORM promote_substitute(p_slot_id, v_activity.id);
        RETURN FALSE;
    END IF;

    -- Either no one is in queue for this slot, or the caller IS the first in queue
    -- (auto-promotion should have fired but didn't — allow the manual reservation).
    UPDATE slots
    SET status = 'reserved', reserved_by = v_user_id, reserved_at = now()
    WHERE id = p_slot_id;

    -- Clean up ALL of this user's queue entries for this activity
    DELETE FROM substitute_queue
    WHERE activity_id = v_slot.activity_id
      AND user_id = v_user_id;

    RETURN TRUE;
END;
$$;


ALTER FUNCTION "public"."reserve_slot"("p_slot_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."rls_auto_enable"() RETURNS "event_trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'pg_catalog'
    AS $$
DECLARE
  cmd record;
BEGIN
  FOR cmd IN
    SELECT *
    FROM pg_event_trigger_ddl_commands()
    WHERE command_tag IN ('CREATE TABLE', 'CREATE TABLE AS', 'SELECT INTO')
      AND object_type IN ('table','partitioned table')
  LOOP
     IF cmd.schema_name IS NOT NULL AND cmd.schema_name IN ('public') AND cmd.schema_name NOT IN ('pg_catalog','information_schema') AND cmd.schema_name NOT LIKE 'pg_toast%' AND cmd.schema_name NOT LIKE 'pg_temp%' THEN
      BEGIN
        EXECUTE format('alter table if exists %s enable row level security', cmd.object_identity);
        RAISE LOG 'rls_auto_enable: enabled RLS on %', cmd.object_identity;
      EXCEPTION
        WHEN OTHERS THEN
          RAISE LOG 'rls_auto_enable: failed to enable RLS on %', cmd.object_identity;
      END;
     ELSE
        RAISE LOG 'rls_auto_enable: skip % (either system schema or not in enforced list: %.)', cmd.object_identity, cmd.schema_name;
     END IF;
  END LOOP;
END;
$$;


ALTER FUNCTION "public"."rls_auto_enable"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."search_public_communities"("p_query" "text" DEFAULT NULL::"text", "p_tag_ids" "uuid"[] DEFAULT NULL::"uuid"[], "p_limit" integer DEFAULT 30, "p_offset" integer DEFAULT 0) RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_result jsonb;
BEGIN
    SELECT jsonb_agg(row_to_json(sub))
    INTO v_result
    FROM (
        SELECT
            c.id,
            c.name,
            c.description,
            c.image_url,
            c.visibility,
            c.parent_id,
            community_breadcrumb(c.id) AS breadcrumb,
            (SELECT count(*) FROM community_members cm WHERE cm.community_id = c.id) AS member_count,
            (SELECT count(*) FROM activities a
             WHERE a.community_id = c.id
               AND a.status = 'active'
               AND a.datetime >= now()) AS activity_count_upcoming,
            (SELECT jsonb_agg(jsonb_build_object(
                'id', t.id, 'slug', t.slug,
                'name_es', t.name_es, 'name_en', t.name_en,
                'icon', t.icon, 'sort_order', t.sort_order
            ) ORDER BY t.sort_order)
             FROM community_tags ct
             JOIN tags t ON t.id = ct.tag_id
             WHERE ct.community_id = c.id) AS tags
        FROM communities c
        WHERE c.visibility IN ('public_open', 'public_approval')
          -- Cadena de ancestros no debe contener ninguna PRIVATE
          AND NOT EXISTS (
              SELECT 1
              FROM get_ancestor_community_ids(c.id) anc
              JOIN communities ac ON ac.id = anc.community_id
              WHERE ac.visibility = 'private'
          )
          AND (p_query IS NULL OR c.name ILIKE '%' || p_query || '%')
          AND (
              p_tag_ids IS NULL
              OR EXISTS (
                  SELECT 1 FROM community_tags ct
                  WHERE ct.community_id = c.id
                    AND ct.tag_id = ANY(p_tag_ids)
              )
          )
        ORDER BY
            (SELECT count(*) FROM community_members cm WHERE cm.community_id = c.id) DESC,
            c.created_at DESC
        LIMIT p_limit OFFSET p_offset
    ) sub;

    RETURN COALESCE(v_result, '[]'::jsonb);
END;
$$;


ALTER FUNCTION "public"."search_public_communities"("p_query" "text", "p_tag_ids" "uuid"[], "p_limit" integer, "p_offset" integer) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."send_activity_reminders"() RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
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


ALTER FUNCTION "public"."send_activity_reminders"() OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."activity_guest_links" (
    "activity_id" "uuid" NOT NULL,
    "code" "text" NOT NULL,
    "created_by" "uuid" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "revoked" boolean DEFAULT false NOT NULL,
    CONSTRAINT "activity_guest_links_code_check" CHECK ((("length"("code") = 8) AND ("code" ~ '^[A-Z0-9]+$'::"text")))
);


ALTER TABLE "public"."activity_guest_links" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."activity_guest_requests" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "activity_id" "uuid" NOT NULL,
    "user_id" "uuid" NOT NULL,
    "slot_id" "uuid",
    "guest_name" "text" NOT NULL,
    "guest_phone" "text" NOT NULL,
    "status" "text" DEFAULT 'pending'::"text" NOT NULL,
    "requested_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "resolved_at" timestamp with time zone,
    "resolved_by" "uuid",
    "requested_position_ids" "uuid"[],
    CONSTRAINT "activity_guest_requests_guest_name_check" CHECK ((("char_length"("guest_name") >= 1) AND ("char_length"("guest_name") <= 80))),
    CONSTRAINT "activity_guest_requests_guest_phone_check" CHECK ((("char_length"("guest_phone") >= 3) AND ("char_length"("guest_phone") <= 30))),
    CONSTRAINT "activity_guest_requests_status_check" CHECK (("status" = ANY (ARRAY['pending'::"text", 'approved'::"text", 'rejected'::"text", 'cancelled'::"text"])))
);


ALTER TABLE "public"."activity_guest_requests" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."activity_reminders_sent" (
    "activity_id" "uuid" NOT NULL,
    "user_id" "uuid" NOT NULL,
    "sent_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."activity_reminders_sent" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."community_join_requests" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "community_id" "uuid" NOT NULL,
    "user_id" "uuid" NOT NULL,
    "status" "text" DEFAULT 'pending'::"text" NOT NULL,
    "message" "text",
    "requested_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "resolved_at" timestamp with time zone,
    "resolved_by" "uuid",
    "target_community_id" "uuid",
    CONSTRAINT "community_join_requests_message_check" CHECK (("char_length"("message") <= 300)),
    CONSTRAINT "community_join_requests_status_check" CHECK (("status" = ANY (ARRAY['pending'::"text", 'approved'::"text", 'rejected'::"text", 'cancelled'::"text"])))
);


ALTER TABLE "public"."community_join_requests" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."community_members" (
    "community_id" "uuid" NOT NULL,
    "user_id" "uuid" NOT NULL,
    "role" "text" DEFAULT 'user'::"text" NOT NULL,
    "joined_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "community_members_role_check" CHECK (("role" = ANY (ARRAY['admin'::"text", 'user'::"text"])))
);


ALTER TABLE "public"."community_members" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."community_messages" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "community_id" "uuid" NOT NULL,
    "user_id" "uuid" NOT NULL,
    "body" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "edited_at" timestamp with time zone,
    CONSTRAINT "community_messages_body_check" CHECK ((("char_length"("body") >= 1) AND ("char_length"("body") <= 2000)))
);


ALTER TABLE "public"."community_messages" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."community_tags" (
    "community_id" "uuid" NOT NULL,
    "tag_id" "uuid" NOT NULL
);


ALTER TABLE "public"."community_tags" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."notifications" (
    "id" "uuid" DEFAULT "extensions"."uuid_generate_v4"() NOT NULL,
    "user_id" "uuid" NOT NULL,
    "type" "text" NOT NULL,
    "title" "text" NOT NULL,
    "body" "text" NOT NULL,
    "data" "jsonb",
    "read" boolean DEFAULT false NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "notifications_type_check" CHECK (("type" = ANY (ARRAY['new_activity'::"text", 'slot_released'::"text", 'substitute_promoted'::"text", 'join_request_received'::"text", 'join_request_approved'::"text", 'join_request_rejected'::"text", 'activity_reminder'::"text", 'guest_request_received'::"text", 'guest_request_approved'::"text", 'guest_request_rejected'::"text"])))
);


ALTER TABLE "public"."notifications" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."positions" (
    "id" "uuid" DEFAULT "extensions"."uuid_generate_v4"() NOT NULL,
    "activity_id" "uuid" NOT NULL,
    "name" "text" NOT NULL
);


ALTER TABLE "public"."positions" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."profiles" (
    "id" "uuid" NOT NULL,
    "display_name" "text" NOT NULL,
    "avatar_url" "text",
    "fcm_token" "text",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "dark_mode" boolean DEFAULT false,
    "language_preference" "text" DEFAULT 'auto'::"text" NOT NULL,
    CONSTRAINT "profiles_language_preference_check" CHECK (("language_preference" = ANY (ARRAY['auto'::"text", 'es'::"text", 'en'::"text"])))
);


ALTER TABLE "public"."profiles" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."slot_groups" (
    "id" "uuid" DEFAULT "extensions"."uuid_generate_v4"() NOT NULL,
    "activity_id" "uuid" NOT NULL,
    "name" "text" NOT NULL,
    "sort_order" integer DEFAULT 0 NOT NULL
);


ALTER TABLE "public"."slot_groups" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."slot_positions" (
    "slot_id" "uuid" NOT NULL,
    "position_id" "uuid" NOT NULL
);


ALTER TABLE "public"."slot_positions" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."slot_templates" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid" NOT NULL,
    "name" "text" NOT NULL,
    "config" "jsonb" DEFAULT '{}'::"jsonb" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."slot_templates" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."slots" (
    "id" "uuid" DEFAULT "extensions"."uuid_generate_v4"() NOT NULL,
    "activity_id" "uuid" NOT NULL,
    "group_id" "uuid",
    "sort_order" integer DEFAULT 0 NOT NULL,
    "status" "text" DEFAULT 'available'::"text" NOT NULL,
    "reserved_by" "uuid",
    "reserved_at" timestamp with time zone,
    "is_guest" boolean DEFAULT false NOT NULL,
    CONSTRAINT "slots_status_check" CHECK (("status" = ANY (ARRAY['available'::"text", 'reserved'::"text", 'paid'::"text", 'pending'::"text"])))
);


ALTER TABLE "public"."slots" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."substitute_queue" (
    "id" "uuid" DEFAULT "extensions"."uuid_generate_v4"() NOT NULL,
    "activity_id" "uuid" NOT NULL,
    "user_id" "uuid" NOT NULL,
    "position_id" "uuid",
    "queued_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."substitute_queue" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."tags" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "slug" "text" NOT NULL,
    "name_es" "text" NOT NULL,
    "name_en" "text" NOT NULL,
    "icon" "text",
    "sort_order" integer DEFAULT 0 NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."tags" OWNER TO "postgres";


ALTER TABLE ONLY "public"."activities"
    ADD CONSTRAINT "activities_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."activity_guest_links"
    ADD CONSTRAINT "activity_guest_links_code_key" UNIQUE ("code");



ALTER TABLE ONLY "public"."activity_guest_links"
    ADD CONSTRAINT "activity_guest_links_pkey" PRIMARY KEY ("activity_id");



ALTER TABLE ONLY "public"."activity_guest_requests"
    ADD CONSTRAINT "activity_guest_requests_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."activity_reminders_sent"
    ADD CONSTRAINT "activity_reminders_sent_pkey" PRIMARY KEY ("activity_id", "user_id");



ALTER TABLE ONLY "public"."communities"
    ADD CONSTRAINT "communities_invite_code_key" UNIQUE ("invite_code");



ALTER TABLE ONLY "public"."communities"
    ADD CONSTRAINT "communities_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."community_join_requests"
    ADD CONSTRAINT "community_join_requests_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."community_members"
    ADD CONSTRAINT "community_members_pkey" PRIMARY KEY ("community_id", "user_id");



ALTER TABLE ONLY "public"."community_messages"
    ADD CONSTRAINT "community_messages_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."community_tags"
    ADD CONSTRAINT "community_tags_pkey" PRIMARY KEY ("community_id", "tag_id");



ALTER TABLE ONLY "public"."notifications"
    ADD CONSTRAINT "notifications_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."positions"
    ADD CONSTRAINT "positions_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."profiles"
    ADD CONSTRAINT "profiles_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."slot_groups"
    ADD CONSTRAINT "slot_groups_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."slot_positions"
    ADD CONSTRAINT "slot_positions_pkey" PRIMARY KEY ("slot_id", "position_id");



ALTER TABLE ONLY "public"."slot_templates"
    ADD CONSTRAINT "slot_templates_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."slots"
    ADD CONSTRAINT "slots_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."substitute_queue"
    ADD CONSTRAINT "substitute_queue_activity_id_user_id_position_id_key" UNIQUE NULLS NOT DISTINCT ("activity_id", "user_id", "position_id");



ALTER TABLE ONLY "public"."substitute_queue"
    ADD CONSTRAINT "substitute_queue_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."tags"
    ADD CONSTRAINT "tags_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."tags"
    ADD CONSTRAINT "tags_slug_key" UNIQUE ("slug");



CREATE INDEX "communities_parent_id_idx" ON "public"."communities" USING "btree" ("parent_id");



CREATE INDEX "idx_activities_community" ON "public"."activities" USING "btree" ("community_id");



CREATE INDEX "idx_activities_community_id" ON "public"."activities" USING "btree" ("community_id");



CREATE INDEX "idx_activities_datetime" ON "public"."activities" USING "btree" ("datetime");



CREATE INDEX "idx_communities_visibility" ON "public"."communities" USING "btree" ("visibility") WHERE ("visibility" = ANY (ARRAY['public_open'::"public"."community_visibility", 'public_approval'::"public"."community_visibility"]));



CREATE INDEX "idx_community_members_community_id" ON "public"."community_members" USING "btree" ("community_id");



CREATE INDEX "idx_community_members_user" ON "public"."community_members" USING "btree" ("user_id");



CREATE INDEX "idx_community_members_user_id" ON "public"."community_members" USING "btree" ("user_id");



CREATE INDEX "idx_community_tags_tag" ON "public"."community_tags" USING "btree" ("tag_id");



CREATE INDEX "idx_guest_requests_fifo" ON "public"."activity_guest_requests" USING "btree" ("activity_id", "requested_at") WHERE ("status" = 'pending'::"text");



CREATE UNIQUE INDEX "idx_guest_requests_unique_active" ON "public"."activity_guest_requests" USING "btree" ("activity_id", "user_id") WHERE ("status" = ANY (ARRAY['pending'::"text", 'approved'::"text"]));



CREATE INDEX "idx_join_requests_community_pending" ON "public"."community_join_requests" USING "btree" ("community_id") WHERE ("status" = 'pending'::"text");



CREATE UNIQUE INDEX "idx_join_requests_unique_pending" ON "public"."community_join_requests" USING "btree" ("community_id", "user_id") WHERE ("status" = 'pending'::"text");



CREATE INDEX "idx_messages_community_created" ON "public"."community_messages" USING "btree" ("community_id", "created_at" DESC);



CREATE INDEX "idx_notifications_user" ON "public"."notifications" USING "btree" ("user_id");



CREATE INDEX "idx_notifications_user_id" ON "public"."notifications" USING "btree" ("user_id");



CREATE INDEX "idx_notifications_user_unread" ON "public"."notifications" USING "btree" ("user_id") WHERE (NOT "read");



CREATE INDEX "idx_slot_positions_slot" ON "public"."slot_positions" USING "btree" ("slot_id");



CREATE INDEX "idx_slot_positions_slot_id" ON "public"."slot_positions" USING "btree" ("slot_id");



CREATE INDEX "idx_slots_activity" ON "public"."slots" USING "btree" ("activity_id");



CREATE INDEX "idx_slots_activity_id" ON "public"."slots" USING "btree" ("activity_id");



CREATE INDEX "idx_slots_reserved_by" ON "public"."slots" USING "btree" ("reserved_by");



CREATE INDEX "idx_substitute_queue_activity" ON "public"."substitute_queue" USING "btree" ("activity_id");



CREATE INDEX "idx_substitute_queue_activity_id" ON "public"."substitute_queue" USING "btree" ("activity_id");



CREATE OR REPLACE TRIGGER "communities_enforce_depth" BEFORE INSERT OR UPDATE OF "parent_id" ON "public"."communities" FOR EACH ROW EXECUTE FUNCTION "public"."enforce_community_depth_limit"();



CREATE OR REPLACE TRIGGER "communities_parent_immutable" BEFORE UPDATE ON "public"."communities" FOR EACH ROW EXECUTE FUNCTION "public"."enforce_parent_id_immutable"();



CREATE OR REPLACE TRIGGER "community_members_cascade_remove" AFTER DELETE ON "public"."community_members" FOR EACH ROW EXECUTE FUNCTION "public"."cascade_member_removal"();



CREATE OR REPLACE TRIGGER "community_members_enforce_parent" BEFORE INSERT ON "public"."community_members" FOR EACH ROW EXECUTE FUNCTION "public"."enforce_parent_membership"();



CREATE OR REPLACE TRIGGER "enforce_max_tags" BEFORE INSERT ON "public"."community_tags" FOR EACH ROW EXECUTE FUNCTION "public"."check_max_tags_per_community"();



CREATE OR REPLACE TRIGGER "on_community_created" AFTER INSERT ON "public"."communities" FOR EACH ROW EXECUTE FUNCTION "public"."handle_new_community"();



-- Webhook trigger removed from baseline: it embeds a service_role JWT generated
-- by the Supabase Dashboard when the webhook is configured. Keep webhooks as
-- Dashboard configuration (Project → Database → Webhooks), not in migrations.
-- The production trigger remains intact; this comment is just to keep the
-- baseline file safe to commit to a public repo.
-- Webhook details: AFTER INSERT ON public.notifications calls edge function
-- /functions/v1/push-notification (see supabase/functions/push-notification).



CREATE OR REPLACE TRIGGER "trg_notify_new_activity" AFTER INSERT ON "public"."activities" FOR EACH ROW EXECUTE FUNCTION "public"."notify_new_activity"();



ALTER TABLE ONLY "public"."activities"
    ADD CONSTRAINT "activities_community_id_fkey" FOREIGN KEY ("community_id") REFERENCES "public"."communities"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."activities"
    ADD CONSTRAINT "activities_created_by_fkey" FOREIGN KEY ("created_by") REFERENCES "public"."profiles"("id");



ALTER TABLE ONLY "public"."activity_guest_links"
    ADD CONSTRAINT "activity_guest_links_activity_id_fkey" FOREIGN KEY ("activity_id") REFERENCES "public"."activities"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."activity_guest_links"
    ADD CONSTRAINT "activity_guest_links_created_by_fkey" FOREIGN KEY ("created_by") REFERENCES "public"."profiles"("id");



ALTER TABLE ONLY "public"."activity_guest_requests"
    ADD CONSTRAINT "activity_guest_requests_activity_id_fkey" FOREIGN KEY ("activity_id") REFERENCES "public"."activities"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."activity_guest_requests"
    ADD CONSTRAINT "activity_guest_requests_resolved_by_fkey" FOREIGN KEY ("resolved_by") REFERENCES "public"."profiles"("id");



ALTER TABLE ONLY "public"."activity_guest_requests"
    ADD CONSTRAINT "activity_guest_requests_slot_id_fkey" FOREIGN KEY ("slot_id") REFERENCES "public"."slots"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."activity_guest_requests"
    ADD CONSTRAINT "activity_guest_requests_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."activity_reminders_sent"
    ADD CONSTRAINT "activity_reminders_sent_activity_id_fkey" FOREIGN KEY ("activity_id") REFERENCES "public"."activities"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."activity_reminders_sent"
    ADD CONSTRAINT "activity_reminders_sent_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."communities"
    ADD CONSTRAINT "communities_created_by_fkey" FOREIGN KEY ("created_by") REFERENCES "public"."profiles"("id");



ALTER TABLE ONLY "public"."communities"
    ADD CONSTRAINT "communities_parent_id_fkey" FOREIGN KEY ("parent_id") REFERENCES "public"."communities"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."community_join_requests"
    ADD CONSTRAINT "community_join_requests_community_id_fkey" FOREIGN KEY ("community_id") REFERENCES "public"."communities"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."community_join_requests"
    ADD CONSTRAINT "community_join_requests_resolved_by_fkey" FOREIGN KEY ("resolved_by") REFERENCES "public"."profiles"("id");



ALTER TABLE ONLY "public"."community_join_requests"
    ADD CONSTRAINT "community_join_requests_target_community_id_fkey" FOREIGN KEY ("target_community_id") REFERENCES "public"."communities"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."community_join_requests"
    ADD CONSTRAINT "community_join_requests_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."community_members"
    ADD CONSTRAINT "community_members_community_id_fkey" FOREIGN KEY ("community_id") REFERENCES "public"."communities"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."community_members"
    ADD CONSTRAINT "community_members_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."community_messages"
    ADD CONSTRAINT "community_messages_community_id_fkey" FOREIGN KEY ("community_id") REFERENCES "public"."communities"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."community_messages"
    ADD CONSTRAINT "community_messages_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."community_tags"
    ADD CONSTRAINT "community_tags_community_id_fkey" FOREIGN KEY ("community_id") REFERENCES "public"."communities"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."community_tags"
    ADD CONSTRAINT "community_tags_tag_id_fkey" FOREIGN KEY ("tag_id") REFERENCES "public"."tags"("id") ON DELETE RESTRICT;



ALTER TABLE ONLY "public"."notifications"
    ADD CONSTRAINT "notifications_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."positions"
    ADD CONSTRAINT "positions_activity_id_fkey" FOREIGN KEY ("activity_id") REFERENCES "public"."activities"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."profiles"
    ADD CONSTRAINT "profiles_id_fkey" FOREIGN KEY ("id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."slot_groups"
    ADD CONSTRAINT "slot_groups_activity_id_fkey" FOREIGN KEY ("activity_id") REFERENCES "public"."activities"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."slot_positions"
    ADD CONSTRAINT "slot_positions_position_id_fkey" FOREIGN KEY ("position_id") REFERENCES "public"."positions"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."slot_positions"
    ADD CONSTRAINT "slot_positions_slot_id_fkey" FOREIGN KEY ("slot_id") REFERENCES "public"."slots"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."slot_templates"
    ADD CONSTRAINT "slot_templates_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."slots"
    ADD CONSTRAINT "slots_activity_id_fkey" FOREIGN KEY ("activity_id") REFERENCES "public"."activities"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."slots"
    ADD CONSTRAINT "slots_group_id_fkey" FOREIGN KEY ("group_id") REFERENCES "public"."slot_groups"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."slots"
    ADD CONSTRAINT "slots_reserved_by_fkey" FOREIGN KEY ("reserved_by") REFERENCES "public"."profiles"("id");



ALTER TABLE ONLY "public"."substitute_queue"
    ADD CONSTRAINT "substitute_queue_activity_id_fkey" FOREIGN KEY ("activity_id") REFERENCES "public"."activities"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."substitute_queue"
    ADD CONSTRAINT "substitute_queue_position_id_fkey" FOREIGN KEY ("position_id") REFERENCES "public"."positions"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."substitute_queue"
    ADD CONSTRAINT "substitute_queue_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



CREATE POLICY "Activities are viewable by community members" ON "public"."activities" FOR SELECT TO "authenticated" USING (("community_id" IN ( SELECT "public"."get_my_community_ids"() AS "get_my_community_ids")));



CREATE POLICY "Admins can create positions" ON "public"."positions" FOR INSERT TO "authenticated" WITH CHECK (("activity_id" IN ( SELECT "activities"."id"
   FROM "public"."activities"
  WHERE ("activities"."community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids")))));



CREATE POLICY "Admins can create slot groups" ON "public"."slot_groups" FOR INSERT TO "authenticated" WITH CHECK (("activity_id" IN ( SELECT "activities"."id"
   FROM "public"."activities"
  WHERE ("activities"."community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids")))));



CREATE POLICY "Admins can create slot positions" ON "public"."slot_positions" FOR INSERT TO "authenticated" WITH CHECK (("slot_id" IN ( SELECT "s"."id"
   FROM "public"."slots" "s"
  WHERE ("s"."activity_id" IN ( SELECT "activities"."id"
           FROM "public"."activities"
          WHERE ("activities"."community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids")))))));



CREATE POLICY "Admins can create slots" ON "public"."slots" FOR INSERT TO "authenticated" WITH CHECK (("activity_id" IN ( SELECT "activities"."id"
   FROM "public"."activities"
  WHERE ("activities"."community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids")))));



CREATE POLICY "Admins can delete positions" ON "public"."positions" FOR DELETE TO "authenticated" USING (("activity_id" IN ( SELECT "activities"."id"
   FROM "public"."activities"
  WHERE ("activities"."community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids")))));



CREATE POLICY "Admins can delete slot groups" ON "public"."slot_groups" FOR DELETE TO "authenticated" USING (("activity_id" IN ( SELECT "activities"."id"
   FROM "public"."activities"
  WHERE ("activities"."community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids")))));



CREATE POLICY "Admins can delete slot positions" ON "public"."slot_positions" FOR DELETE TO "authenticated" USING (("slot_id" IN ( SELECT "s"."id"
   FROM "public"."slots" "s"
  WHERE ("s"."activity_id" IN ( SELECT "activities"."id"
           FROM "public"."activities"
          WHERE ("activities"."community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids")))))));



CREATE POLICY "Admins can remove members" ON "public"."community_members" FOR DELETE TO "authenticated" USING ((("user_id" = "auth"."uid"()) OR ("community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids"))));



CREATE POLICY "Admins can update member roles" ON "public"."community_members" FOR UPDATE TO "authenticated" USING (("community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids")));



CREATE POLICY "Admins can update positions" ON "public"."positions" FOR UPDATE TO "authenticated" USING (("activity_id" IN ( SELECT "activities"."id"
   FROM "public"."activities"
  WHERE ("activities"."community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids")))));



CREATE POLICY "Admins can update slot groups" ON "public"."slot_groups" FOR UPDATE TO "authenticated" USING (("activity_id" IN ( SELECT "activities"."id"
   FROM "public"."activities"
  WHERE ("activities"."community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids")))));



CREATE POLICY "Admins see join requests for their communities" ON "public"."community_join_requests" FOR SELECT TO "authenticated" USING (("community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids")));



CREATE POLICY "Authenticated can create communities" ON "public"."communities" FOR INSERT TO "authenticated" WITH CHECK ((("created_by" = "auth"."uid"()) AND (("parent_id" IS NULL) OR (EXISTS ( SELECT 1
   FROM "public"."community_members"
  WHERE (("community_members"."community_id" = "communities"."parent_id") AND ("community_members"."user_id" = "auth"."uid"()) AND ("community_members"."role" = 'admin'::"text")))))));



CREATE POLICY "Authors edit own messages" ON "public"."community_messages" FOR UPDATE TO "authenticated" USING (("user_id" = "auth"."uid"())) WITH CHECK (("user_id" = "auth"."uid"()));



CREATE POLICY "Authors or admins delete messages" ON "public"."community_messages" FOR DELETE TO "authenticated" USING ((("user_id" = "auth"."uid"()) OR ("community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids"))));



CREATE POLICY "Communities are viewable by members" ON "public"."communities" FOR SELECT TO "authenticated" USING ((("id" IN ( SELECT "public"."get_my_community_ids"() AS "get_my_community_ids")) OR ("created_by" = "auth"."uid"())));



CREATE POLICY "Community admins can create activities" ON "public"."activities" FOR INSERT TO "authenticated" WITH CHECK ((("community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids")) AND ("created_by" = "auth"."uid"())));



CREATE POLICY "Community admins can delete activities" ON "public"."activities" FOR DELETE TO "authenticated" USING (("community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids")));



CREATE POLICY "Community admins can update activities" ON "public"."activities" FOR UPDATE TO "authenticated" USING (("community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids")));



CREATE POLICY "Community admins can update communities" ON "public"."communities" FOR UPDATE TO "authenticated" USING (("id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids"))) WITH CHECK (("id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids")));



CREATE POLICY "Community admins manage tags" ON "public"."community_tags" TO "authenticated" USING (("community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids"))) WITH CHECK (("community_id" IN ( SELECT "public"."get_my_admin_community_ids"() AS "get_my_admin_community_ids")));



CREATE POLICY "Community creator can delete communities" ON "public"."communities" FOR DELETE TO "authenticated" USING (("created_by" = "auth"."uid"()));



CREATE POLICY "Community members can view guest profiles" ON "public"."profiles" FOR SELECT TO "authenticated" USING (("id" IN ( SELECT "s"."reserved_by"
   FROM (("public"."slots" "s"
     JOIN "public"."activities" "a" ON (("a"."id" = "s"."activity_id")))
     JOIN "public"."community_members" "cm" ON (("cm"."community_id" = "a"."community_id")))
  WHERE (("cm"."user_id" = "auth"."uid"()) AND ("s"."is_guest" = true) AND ("s"."reserved_by" IS NOT NULL)))));



CREATE POLICY "Community tags readable by members or for public communities" ON "public"."community_tags" FOR SELECT TO "authenticated" USING ((("community_id" IN ( SELECT "public"."get_my_community_ids"() AS "get_my_community_ids")) OR ("community_id" IN ( SELECT "communities"."id"
   FROM "public"."communities"
  WHERE ("communities"."visibility" = ANY (ARRAY['public_open'::"public"."community_visibility", 'public_approval'::"public"."community_visibility"]))))));



CREATE POLICY "Members can view their community's membership" ON "public"."community_members" FOR SELECT TO "authenticated" USING (("community_id" IN ( SELECT "public"."get_my_community_ids"() AS "get_my_community_ids")));



CREATE POLICY "Members post own messages" ON "public"."community_messages" FOR INSERT TO "authenticated" WITH CHECK ((("community_id" IN ( SELECT "public"."get_my_community_ids"() AS "get_my_community_ids")) AND ("user_id" = "auth"."uid"())));



CREATE POLICY "Members read messages" ON "public"."community_messages" FOR SELECT TO "authenticated" USING (("community_id" IN ( SELECT "public"."get_my_community_ids"() AS "get_my_community_ids")));



CREATE POLICY "Positions viewable by community members" ON "public"."positions" FOR SELECT TO "authenticated" USING (("activity_id" IN ( SELECT "activities"."id"
   FROM "public"."activities"
  WHERE ("activities"."community_id" IN ( SELECT "public"."get_my_community_ids"() AS "get_my_community_ids")))));



CREATE POLICY "Public communities preview" ON "public"."communities" FOR SELECT TO "authenticated" USING (("visibility" = ANY (ARRAY['public_open'::"public"."community_visibility", 'public_approval'::"public"."community_visibility"])));



CREATE POLICY "Slot groups viewable by community members" ON "public"."slot_groups" FOR SELECT TO "authenticated" USING (("activity_id" IN ( SELECT "activities"."id"
   FROM "public"."activities"
  WHERE ("activities"."community_id" IN ( SELECT "public"."get_my_community_ids"() AS "get_my_community_ids")))));



CREATE POLICY "Slot positions viewable by community members" ON "public"."slot_positions" FOR SELECT TO "authenticated" USING (("slot_id" IN ( SELECT "s"."id"
   FROM "public"."slots" "s"
  WHERE ("s"."activity_id" IN ( SELECT "activities"."id"
           FROM "public"."activities"
          WHERE ("activities"."community_id" IN ( SELECT "public"."get_my_community_ids"() AS "get_my_community_ids")))))));



CREATE POLICY "Slots viewable by community members" ON "public"."slots" FOR SELECT TO "authenticated" USING (("activity_id" IN ( SELECT "activities"."id"
   FROM "public"."activities"
  WHERE ("activities"."community_id" IN ( SELECT "public"."get_my_community_ids"() AS "get_my_community_ids")))));



CREATE POLICY "Substitute queue viewable by community members" ON "public"."substitute_queue" FOR SELECT TO "authenticated" USING (("activity_id" IN ( SELECT "activities"."id"
   FROM "public"."activities"
  WHERE ("activities"."community_id" IN ( SELECT "public"."get_my_community_ids"() AS "get_my_community_ids")))));



CREATE POLICY "Tags are readable by any authenticated user" ON "public"."tags" FOR SELECT TO "authenticated" USING (true);



CREATE POLICY "Users can delete own templates" ON "public"."slot_templates" FOR DELETE USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can delete their own notifications" ON "public"."notifications" FOR DELETE TO "authenticated" USING (("user_id" = "auth"."uid"()));



CREATE POLICY "Users can insert own templates" ON "public"."slot_templates" FOR INSERT WITH CHECK (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can join communities" ON "public"."community_members" FOR INSERT TO "authenticated" WITH CHECK (("user_id" = "auth"."uid"()));



CREATE POLICY "Users can manage their own substitute entries" ON "public"."substitute_queue" FOR INSERT TO "authenticated" WITH CHECK (("user_id" = "auth"."uid"()));



CREATE POLICY "Users can remove their own substitute entries" ON "public"."substitute_queue" FOR DELETE TO "authenticated" USING (("user_id" = "auth"."uid"()));



CREATE POLICY "Users can update own templates" ON "public"."slot_templates" FOR UPDATE TO "authenticated" USING (("user_id" = "auth"."uid"())) WITH CHECK (("user_id" = "auth"."uid"()));



CREATE POLICY "Users can update their own notifications" ON "public"."notifications" FOR UPDATE TO "authenticated" USING (("user_id" = "auth"."uid"())) WITH CHECK (("user_id" = "auth"."uid"()));



CREATE POLICY "Users can update their own profile" ON "public"."profiles" FOR UPDATE TO "authenticated" USING (("id" = "auth"."uid"())) WITH CHECK (("id" = "auth"."uid"()));



CREATE POLICY "Users can view community member profiles" ON "public"."profiles" FOR SELECT TO "authenticated" USING (("id" IN ( SELECT "cm2"."user_id"
   FROM "public"."community_members" "cm2"
  WHERE ("cm2"."community_id" IN ( SELECT "public"."get_my_community_ids"() AS "get_my_community_ids")))));



CREATE POLICY "Users can view own profile" ON "public"."profiles" FOR SELECT TO "authenticated" USING (("id" = "auth"."uid"()));



CREATE POLICY "Users can view own templates" ON "public"."slot_templates" FOR SELECT USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can view their own notifications" ON "public"."notifications" FOR SELECT TO "authenticated" USING (("user_id" = "auth"."uid"()));



CREATE POLICY "Users see their own join requests" ON "public"."community_join_requests" FOR SELECT TO "authenticated" USING (("user_id" = "auth"."uid"()));



ALTER TABLE "public"."activities" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."activity_guest_links" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."activity_guest_requests" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."activity_reminders_sent" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."communities" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."community_join_requests" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."community_members" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."community_messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."community_tags" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."notifications" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."positions" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."profiles" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."slot_groups" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."slot_positions" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."slot_templates" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."slots" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."substitute_queue" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."tags" ENABLE ROW LEVEL SECURITY;


GRANT USAGE ON SCHEMA "public" TO "postgres";
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";
GRANT USAGE ON SCHEMA "public" TO "service_role";



GRANT ALL ON FUNCTION "public"."approve_guest_request"("p_request_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."approve_guest_request"("p_request_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."approve_guest_request"("p_request_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."approve_join_request"("p_request_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."approve_join_request"("p_request_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."approve_join_request"("p_request_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."cancel_join_request"("p_request_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."cancel_join_request"("p_request_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."cancel_join_request"("p_request_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."cascade_join_towards_target"("p_user_id" "uuid", "p_from_community" "uuid", "p_target_community" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."cascade_join_towards_target"("p_user_id" "uuid", "p_from_community" "uuid", "p_target_community" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."cascade_join_towards_target"("p_user_id" "uuid", "p_from_community" "uuid", "p_target_community" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."cascade_member_removal"() TO "anon";
GRANT ALL ON FUNCTION "public"."cascade_member_removal"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."cascade_member_removal"() TO "service_role";



GRANT ALL ON FUNCTION "public"."check_max_tags_per_community"() TO "anon";
GRANT ALL ON FUNCTION "public"."check_max_tags_per_community"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."check_max_tags_per_community"() TO "service_role";



GRANT ALL ON FUNCTION "public"."community_breadcrumb"("p_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."community_breadcrumb"("p_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."community_breadcrumb"("p_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."community_depth"("p_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."community_depth"("p_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."community_depth"("p_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."enforce_community_depth_limit"() TO "anon";
GRANT ALL ON FUNCTION "public"."enforce_community_depth_limit"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."enforce_community_depth_limit"() TO "service_role";



GRANT ALL ON FUNCTION "public"."enforce_parent_id_immutable"() TO "anon";
GRANT ALL ON FUNCTION "public"."enforce_parent_id_immutable"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."enforce_parent_id_immutable"() TO "service_role";



GRANT ALL ON FUNCTION "public"."enforce_parent_membership"() TO "anon";
GRANT ALL ON FUNCTION "public"."enforce_parent_membership"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."enforce_parent_membership"() TO "service_role";



GRANT ALL ON FUNCTION "public"."gen_activity_guest_code"() TO "anon";
GRANT ALL ON FUNCTION "public"."gen_activity_guest_code"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."gen_activity_guest_code"() TO "service_role";



GRANT ALL ON FUNCTION "public"."generate_activity_guest_link"("p_activity_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."generate_activity_guest_link"("p_activity_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."generate_activity_guest_link"("p_activity_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_activity_guest_preview"("p_code" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."get_activity_guest_preview"("p_code" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_activity_guest_preview"("p_code" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_ancestor_community_ids"("p_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."get_ancestor_community_ids"("p_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_ancestor_community_ids"("p_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_descendant_community_ids"("p_root" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."get_descendant_community_ids"("p_root" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_descendant_community_ids"("p_root" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_my_admin_community_ids"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_my_admin_community_ids"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_my_admin_community_ids"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_my_community_ids"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_my_community_ids"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_my_community_ids"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_public_community_preview"("p_community_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."get_public_community_preview"("p_community_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_public_community_preview"("p_community_id" "uuid") TO "service_role";



GRANT ALL ON TABLE "public"."activities" TO "anon";
GRANT ALL ON TABLE "public"."activities" TO "authenticated";
GRANT ALL ON TABLE "public"."activities" TO "service_role";



GRANT ALL ON FUNCTION "public"."get_upcoming_activities"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_upcoming_activities"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_upcoming_activities"() TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_new_community"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_new_community"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_new_community"() TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "service_role";



GRANT ALL ON TABLE "public"."communities" TO "anon";
GRANT ALL ON TABLE "public"."communities" TO "authenticated";
GRANT ALL ON TABLE "public"."communities" TO "service_role";



GRANT ALL ON FUNCTION "public"."join_community_by_invite"("p_invite_code" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."join_community_by_invite"("p_invite_code" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."join_community_by_invite"("p_invite_code" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."join_community_by_invite_v2"("p_invite_code" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."join_community_by_invite_v2"("p_invite_code" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."join_community_by_invite_v2"("p_invite_code" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."join_substitute_queue"("p_activity_id" "uuid", "p_position_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."join_substitute_queue"("p_activity_id" "uuid", "p_position_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."join_substitute_queue"("p_activity_id" "uuid", "p_position_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."leave_substitute_queue"("p_activity_id" "uuid", "p_position_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."leave_substitute_queue"("p_activity_id" "uuid", "p_position_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."leave_substitute_queue"("p_activity_id" "uuid", "p_position_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."list_pending_guest_requests"("p_activity_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."list_pending_guest_requests"("p_activity_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."list_pending_guest_requests"("p_activity_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."lookup_community_by_invite"("p_invite_code" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."lookup_community_by_invite"("p_invite_code" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."lookup_community_by_invite"("p_invite_code" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."mark_slot_paid"("p_slot_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."mark_slot_paid"("p_slot_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."mark_slot_paid"("p_slot_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."notify_new_activity"() TO "anon";
GRANT ALL ON FUNCTION "public"."notify_new_activity"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."notify_new_activity"() TO "service_role";



GRANT ALL ON FUNCTION "public"."notify_substitute_promoted"("p_user_id" "uuid", "p_activity_id" "uuid", "p_slot_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."notify_substitute_promoted"("p_user_id" "uuid", "p_activity_id" "uuid", "p_slot_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."notify_substitute_promoted"("p_user_id" "uuid", "p_activity_id" "uuid", "p_slot_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."promote_substitute"("p_slot_id" "uuid", "p_activity_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."promote_substitute"("p_slot_id" "uuid", "p_activity_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."promote_substitute"("p_slot_id" "uuid", "p_activity_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."reject_guest_request"("p_request_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."reject_guest_request"("p_request_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."reject_guest_request"("p_request_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."reject_join_request"("p_request_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."reject_join_request"("p_request_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."reject_join_request"("p_request_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."release_slot"("p_slot_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."release_slot"("p_slot_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."release_slot"("p_slot_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."request_guest_slot"("p_code" "text", "p_name" "text", "p_phone" "text", "p_position_ids" "uuid"[]) TO "anon";
GRANT ALL ON FUNCTION "public"."request_guest_slot"("p_code" "text", "p_name" "text", "p_phone" "text", "p_position_ids" "uuid"[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."request_guest_slot"("p_code" "text", "p_name" "text", "p_phone" "text", "p_position_ids" "uuid"[]) TO "service_role";



GRANT ALL ON FUNCTION "public"."request_to_join_community"("p_community_id" "uuid", "p_message" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."request_to_join_community"("p_community_id" "uuid", "p_message" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."request_to_join_community"("p_community_id" "uuid", "p_message" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."reserve_slot"("p_slot_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."reserve_slot"("p_slot_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."reserve_slot"("p_slot_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."rls_auto_enable"() TO "anon";
GRANT ALL ON FUNCTION "public"."rls_auto_enable"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."rls_auto_enable"() TO "service_role";



GRANT ALL ON FUNCTION "public"."search_public_communities"("p_query" "text", "p_tag_ids" "uuid"[], "p_limit" integer, "p_offset" integer) TO "anon";
GRANT ALL ON FUNCTION "public"."search_public_communities"("p_query" "text", "p_tag_ids" "uuid"[], "p_limit" integer, "p_offset" integer) TO "authenticated";
GRANT ALL ON FUNCTION "public"."search_public_communities"("p_query" "text", "p_tag_ids" "uuid"[], "p_limit" integer, "p_offset" integer) TO "service_role";



GRANT ALL ON FUNCTION "public"."send_activity_reminders"() TO "anon";
GRANT ALL ON FUNCTION "public"."send_activity_reminders"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."send_activity_reminders"() TO "service_role";



GRANT ALL ON TABLE "public"."activity_guest_links" TO "anon";
GRANT ALL ON TABLE "public"."activity_guest_links" TO "authenticated";
GRANT ALL ON TABLE "public"."activity_guest_links" TO "service_role";



GRANT ALL ON TABLE "public"."activity_guest_requests" TO "anon";
GRANT ALL ON TABLE "public"."activity_guest_requests" TO "authenticated";
GRANT ALL ON TABLE "public"."activity_guest_requests" TO "service_role";



GRANT ALL ON TABLE "public"."activity_reminders_sent" TO "anon";
GRANT ALL ON TABLE "public"."activity_reminders_sent" TO "authenticated";
GRANT ALL ON TABLE "public"."activity_reminders_sent" TO "service_role";



GRANT ALL ON TABLE "public"."community_join_requests" TO "anon";
GRANT ALL ON TABLE "public"."community_join_requests" TO "authenticated";
GRANT ALL ON TABLE "public"."community_join_requests" TO "service_role";



GRANT ALL ON TABLE "public"."community_members" TO "anon";
GRANT ALL ON TABLE "public"."community_members" TO "authenticated";
GRANT ALL ON TABLE "public"."community_members" TO "service_role";



GRANT ALL ON TABLE "public"."community_messages" TO "anon";
GRANT ALL ON TABLE "public"."community_messages" TO "authenticated";
GRANT ALL ON TABLE "public"."community_messages" TO "service_role";



GRANT ALL ON TABLE "public"."community_tags" TO "anon";
GRANT ALL ON TABLE "public"."community_tags" TO "authenticated";
GRANT ALL ON TABLE "public"."community_tags" TO "service_role";



GRANT ALL ON TABLE "public"."notifications" TO "anon";
GRANT ALL ON TABLE "public"."notifications" TO "authenticated";
GRANT ALL ON TABLE "public"."notifications" TO "service_role";



GRANT ALL ON TABLE "public"."positions" TO "anon";
GRANT ALL ON TABLE "public"."positions" TO "authenticated";
GRANT ALL ON TABLE "public"."positions" TO "service_role";



GRANT ALL ON TABLE "public"."profiles" TO "anon";
GRANT ALL ON TABLE "public"."profiles" TO "authenticated";
GRANT ALL ON TABLE "public"."profiles" TO "service_role";



GRANT ALL ON TABLE "public"."slot_groups" TO "anon";
GRANT ALL ON TABLE "public"."slot_groups" TO "authenticated";
GRANT ALL ON TABLE "public"."slot_groups" TO "service_role";



GRANT ALL ON TABLE "public"."slot_positions" TO "anon";
GRANT ALL ON TABLE "public"."slot_positions" TO "authenticated";
GRANT ALL ON TABLE "public"."slot_positions" TO "service_role";



GRANT ALL ON TABLE "public"."slot_templates" TO "anon";
GRANT ALL ON TABLE "public"."slot_templates" TO "authenticated";
GRANT ALL ON TABLE "public"."slot_templates" TO "service_role";



GRANT ALL ON TABLE "public"."slots" TO "anon";
GRANT ALL ON TABLE "public"."slots" TO "authenticated";
GRANT ALL ON TABLE "public"."slots" TO "service_role";



GRANT ALL ON TABLE "public"."substitute_queue" TO "anon";
GRANT ALL ON TABLE "public"."substitute_queue" TO "authenticated";
GRANT ALL ON TABLE "public"."substitute_queue" TO "service_role";



GRANT ALL ON TABLE "public"."tags" TO "anon";
GRANT ALL ON TABLE "public"."tags" TO "authenticated";
GRANT ALL ON TABLE "public"."tags" TO "service_role";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "service_role";







