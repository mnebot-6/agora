-- ============================================================================
-- Migration 014: Public Communities + Tags + Join Requests
--
-- Adds community visibility states (public_open / public_approval / private),
-- a curated tags catalog with N:M join to communities, and a join-request
-- flow for public_approval communities. Also adds 6 RPCs, modifies the
-- existing join_community_by_invite to auto-cancel pending requests, and
-- extends notifications.type with 3 new types.
-- ============================================================================

BEGIN;

-- ============================================================================
-- 1. COMMUNITY VISIBILITY
-- ============================================================================

CREATE TYPE community_visibility AS ENUM (
    'public_open',      -- aparece en Explorar, join inmediato
    'public_approval',  -- aparece en Explorar, admin aprueba cada solicitud
    'private'           -- oculta, solo por invite_code
);

ALTER TABLE communities
    ADD COLUMN visibility community_visibility NOT NULL DEFAULT 'private';

CREATE INDEX idx_communities_visibility ON communities(visibility)
    WHERE visibility IN ('public_open', 'public_approval');

-- ============================================================================
-- 2. TAGS CATALOG
-- ============================================================================

CREATE TABLE tags (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug text UNIQUE NOT NULL,
    name_es text NOT NULL,
    name_en text NOT NULL,
    icon text,
    sort_order int NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Seed: 15 categorías iniciales
INSERT INTO tags (slug, name_es, name_en, icon, sort_order) VALUES
    ('deporte',      'Deporte',             'Sports',           '⚽',    10),
    ('voluntariado', 'Voluntariado',        'Volunteering',     '🤝',    20),
    ('educacion',    'Educación',           'Education',        '📚',    30),
    ('musica',       'Música',              'Music',            '🎵',    40),
    ('gaming',       'Gaming',              'Gaming',           '🎮',    50),
    ('arte',         'Arte',                'Art',              '🎨',    60),
    ('tecnologia',   'Tecnología',          'Technology',       '💻',    70),
    ('salud',        'Salud y bienestar',   'Health & Wellness','🧘',    80),
    ('idiomas',      'Idiomas',             'Languages',        '🗣️',   90),
    ('naturaleza',   'Naturaleza',          'Nature',           '🌳',   100),
    ('profesional',  'Profesional',         'Professional',     '💼',   110),
    ('familia',      'Familia',             'Family',           '👨‍👩‍👧', 120),
    ('social',       'Social',              'Social',           '🎉',   130),
    ('espiritual',   'Espiritual',          'Spiritual',        '🕊️',  140),
    ('otros',        'Otros',               'Other',            '✨',  9999);

-- ============================================================================
-- 3. COMMUNITY ↔ TAGS (N:M)
-- ============================================================================

CREATE TABLE community_tags (
    community_id uuid NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    tag_id uuid NOT NULL REFERENCES tags(id) ON DELETE RESTRICT,
    PRIMARY KEY (community_id, tag_id)
);

CREATE INDEX idx_community_tags_tag ON community_tags(tag_id);

-- Trigger: máximo 3 tags por comunidad
CREATE OR REPLACE FUNCTION check_max_tags_per_community()
RETURNS trigger AS $$
BEGIN
    IF (SELECT count(*) FROM community_tags WHERE community_id = NEW.community_id) >= 3 THEN
        RAISE EXCEPTION 'Máximo 3 tags por comunidad';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql
SET search_path = public, pg_temp;

CREATE TRIGGER enforce_max_tags
    BEFORE INSERT ON community_tags
    FOR EACH ROW
    EXECUTE FUNCTION check_max_tags_per_community();

-- ============================================================================
-- 4. COMMUNITY JOIN REQUESTS
-- ============================================================================

CREATE TABLE community_join_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id uuid NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    status text NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending','approved','rejected','cancelled')),
    message text CHECK (char_length(message) <= 300),
    requested_at timestamptz NOT NULL DEFAULT now(),
    resolved_at timestamptz,
    resolved_by uuid REFERENCES profiles(id)
);

-- Solo una solicitud pending por (community, user) a la vez
CREATE UNIQUE INDEX idx_join_requests_unique_pending
    ON community_join_requests(community_id, user_id)
    WHERE status = 'pending';

CREATE INDEX idx_join_requests_community_pending
    ON community_join_requests(community_id)
    WHERE status = 'pending';

-- ============================================================================
-- 5. RPC: search_public_communities
-- ============================================================================
CREATE OR REPLACE FUNCTION search_public_communities(
    p_query text DEFAULT NULL,
    p_tag_ids uuid[] DEFAULT NULL,
    p_limit int DEFAULT 20,
    p_offset int DEFAULT 0
)
RETURNS jsonb AS $$
DECLARE
    v_result jsonb;
BEGIN
    SELECT jsonb_agg(row_to_json(sub.*))
    INTO v_result
    FROM (
        SELECT
            c.id,
            c.name,
            c.description,
            c.image_url,
            c.visibility,
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

-- ============================================================================
-- 6. RPC: get_public_community_preview
-- ============================================================================
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

    SELECT jsonb_build_object(
        'id', v_community.id,
        'name', v_community.name,
        'description', v_community.description,
        'image_url', v_community.image_url,
        'visibility', v_community.visibility,
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
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ============================================================================
-- 7. RPC: request_to_join_community
-- ============================================================================
CREATE OR REPLACE FUNCTION request_to_join_community(
    p_community_id uuid,
    p_message text DEFAULT NULL
)
RETURNS jsonb AS $$
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
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ============================================================================
-- 8. RPC: approve_join_request
-- ============================================================================
CREATE OR REPLACE FUNCTION approve_join_request(p_request_id uuid)
RETURNS void AS $$
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
        RAISE EXCEPTION 'Only community admins can approve';
    END IF;

    INSERT INTO community_members (community_id, user_id, role)
    VALUES (v_req.community_id, v_req.user_id, 'user')
    ON CONFLICT (community_id, user_id) DO NOTHING;

    UPDATE community_join_requests
    SET status = 'approved', resolved_at = now(), resolved_by = v_caller
    WHERE id = p_request_id;

    SELECT name INTO v_community_name FROM communities WHERE id = v_req.community_id;

    INSERT INTO notifications (user_id, type, title, body, data)
    VALUES (
        v_req.user_id,
        'join_request_approved',
        'Solicitud aprobada',
        'Ya eres miembro de ' || v_community_name,
        jsonb_build_object('community_id', v_req.community_id, 'community_name', v_community_name)
    );
END;
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ============================================================================
-- 9. RPC: reject_join_request
-- ============================================================================
CREATE OR REPLACE FUNCTION reject_join_request(p_request_id uuid)
RETURNS void AS $$
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
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ============================================================================
-- 10. RPC: cancel_join_request
-- ============================================================================
CREATE OR REPLACE FUNCTION cancel_join_request(p_request_id uuid)
RETURNS void AS $$
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
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ============================================================================
-- 11. Modificar join_community_by_invite: auto-cancelar pending al aceptar invite
-- ============================================================================
CREATE OR REPLACE FUNCTION join_community_by_invite(p_invite_code TEXT)
RETURNS communities AS $$
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
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ============================================================================
-- 12. RLS POLICIES
-- ============================================================================

ALTER TABLE tags ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Tags are readable by any authenticated user"
    ON tags FOR SELECT
    TO authenticated
    USING (true);

ALTER TABLE community_tags ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Community tags readable by members or for public communities"
    ON community_tags FOR SELECT
    TO authenticated
    USING (
        community_id IN (SELECT get_my_community_ids())
        OR community_id IN (
            SELECT id FROM communities
            WHERE visibility IN ('public_open', 'public_approval')
        )
    );

CREATE POLICY "Community admins manage tags"
    ON community_tags FOR ALL
    TO authenticated
    USING (community_id IN (SELECT get_my_admin_community_ids()))
    WITH CHECK (community_id IN (SELECT get_my_admin_community_ids()));

ALTER TABLE community_join_requests ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users see their own join requests"
    ON community_join_requests FOR SELECT
    TO authenticated
    USING (user_id = auth.uid());

CREATE POLICY "Admins see join requests for their communities"
    ON community_join_requests FOR SELECT
    TO authenticated
    USING (community_id IN (SELECT get_my_admin_community_ids()));

-- INSERT/UPDATE/DELETE sin políticas: Postgres deniega por defecto con RLS
-- habilitado; toda mutación pasa por los RPCs SECURITY DEFINER definidos arriba.

-- communities: añadir policy para ver públicas (preview)
-- NOTA: Esto expone invite_code en comunidades públicas. El cliente DEBE usar
-- search_public_communities / get_public_community_preview para vistas de
-- descubrimiento. Follow-up: vista materializada con solo columnas seguras
-- si detectamos abuso.
CREATE POLICY "Public communities preview"
    ON communities FOR SELECT
    TO authenticated
    USING (visibility IN ('public_open', 'public_approval'));

-- ============================================================================
-- 13. AMPLIAR TIPOS DE NOTIFICACIÓN
-- ============================================================================
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN (
        'new_activity',
        'slot_released',
        'substitute_promoted',
        'join_request_received',
        'join_request_approved',
        'join_request_rejected'
    ));

COMMIT;
