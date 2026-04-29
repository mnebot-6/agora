-- ============================================================================
-- Migración 017: Invite cascada + PRIVATE = approval + breadcrumb en search
-- ============================================================================
-- Cambios:
--   1. join_community_by_invite_v2 ahora cascada de raíz a hoja, respetando la
--      regla "no eres miembro de hija sin serlo de padre". Para PRIVATE el
--      invite se trata como PUBLIC_APPROVAL (necesita aprobación).
--   2. Nueva columna community_join_requests.target_community_id: el destino
--      real del invite cuando hay cascada.
--   3. approve_join_request materializa siguiente eslabón cuando target != self.
--   4. search_public_communities y get_public_community_preview filtran por
--      cadena de ancestros NO privada y devuelven breadcrumb.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Schema: target_community_id
-- ----------------------------------------------------------------------------
ALTER TABLE community_join_requests
    ADD COLUMN target_community_id UUID REFERENCES communities(id) ON DELETE CASCADE;

-- ----------------------------------------------------------------------------
-- 2. Helper interno: progresar la cascada desde un eslabón hacia la hoja
-- ----------------------------------------------------------------------------
-- Asume que p_user_id ya es miembro de p_from_community. Intenta materializar
-- la cadena desde p_from_community (excluido) hasta p_target_community
-- (incluido). Detiene la cascada en el primer eslabón APPROVAL/PRIVATE no
-- accesible y deja una pending request allí. Si todo es PUBLIC_OPEN, materializa
-- en una sola transacción.
CREATE OR REPLACE FUNCTION cascade_join_towards_target(
    p_user_id UUID,
    p_from_community UUID,
    p_target_community UUID
) RETURNS jsonb AS $$
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
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ----------------------------------------------------------------------------
-- 3. join_community_by_invite_v2: ahora con cascada
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION join_community_by_invite_v2(p_invite_code TEXT)
RETURNS JSONB AS $$
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
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ----------------------------------------------------------------------------
-- 4. approve_join_request: ahora cascada hacia el target si lo hay
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION approve_join_request(p_request_id uuid)
RETURNS void AS $$
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
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ----------------------------------------------------------------------------
-- 5. search_public_communities: cadena pública + breadcrumb + parent_id
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION search_public_communities(
    p_query TEXT DEFAULT NULL,
    p_tag_ids UUID[] DEFAULT NULL,
    p_limit INT DEFAULT 30,
    p_offset INT DEFAULT 0
) RETURNS jsonb AS $$
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
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ----------------------------------------------------------------------------
-- 6. get_public_community_preview: breadcrumb + cadena pública
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION get_public_community_preview(p_community_id uuid)
RETURNS jsonb AS $$
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
                                            'icon', t.icon, 'sort_order', t.sort_orderw
                                                     ) ORDER BY t.sort_order)
                                    FROM community_tags ct
                                             JOIN tags t ON t.id = ct.tag_id
                                    WHERE ct.community_id = v_community.id
                                ), '[]'::jsonb)
       ) INTO v_result;

RETURN v_result;
END;
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

GRANT EXECUTE ON FUNCTION cascade_join_towards_target(UUID, UUID, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION community_breadcrumb(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION community_depth(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION get_ancestor_community_ids(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION get_descendant_community_ids(UUID) TO authenticated;

                                                      