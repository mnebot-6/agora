-- ============================================================================
-- Migración 016: Subcomunidades anidadas
-- ============================================================================
-- Añade soporte para árbol de comunidades (parent_id) con invariantes:
--   1. Para ser miembro de una hija debes ser miembro de su padre.
--   2. Salir de una comunidad cascadea hacia abajo (te saca de descendientes).
--   3. Borrar una comunidad borra sus descendientes (FK ON DELETE CASCADE).
--   4. Profundidad máxima 5 niveles (raíz = nivel 1).
--   5. parent_id es inmutable tras creación.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Schema: parent_id en communities
-- ----------------------------------------------------------------------------
ALTER TABLE communities
    ADD COLUMN parent_id UUID REFERENCES communities(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS communities_parent_id_idx ON communities(parent_id);

-- ----------------------------------------------------------------------------
-- 2. Helpers recursivos
-- ----------------------------------------------------------------------------

-- Devuelve TODOS los IDs descendientes de p_root (sin incluir p_root).
CREATE OR REPLACE FUNCTION get_descendant_community_ids(p_root UUID)
RETURNS TABLE(community_id UUID) AS $$
    WITH RECURSIVE descendants AS (
        SELECT id FROM communities WHERE parent_id = p_root
        UNION ALL
        SELECT c.id
        FROM communities c
        JOIN descendants d ON c.parent_id = d.id
    )
    SELECT id FROM descendants;
$$ LANGUAGE sql STABLE
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- Devuelve TODOS los IDs ancestros de p_id (sin incluir p_id), de hijo a raíz.
CREATE OR REPLACE FUNCTION get_ancestor_community_ids(p_id UUID)
RETURNS TABLE(community_id UUID, depth INT) AS $$
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
$$ LANGUAGE sql STABLE
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- Profundidad de la comunidad (raíz = 1).
CREATE OR REPLACE FUNCTION community_depth(p_id UUID)
RETURNS INT AS $$
    SELECT COALESCE(
        (SELECT MAX(depth) + 1 FROM get_ancestor_community_ids(p_id)),
        1
    );
$$ LANGUAGE sql STABLE
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- Breadcrumb: "Padre › Hijo › Nieto" basado en nombres.
CREATE OR REPLACE FUNCTION community_breadcrumb(p_id UUID)
RETURNS TEXT AS $$
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
$$ LANGUAGE plpgsql STABLE
   SECURITY DEFINER
   SET search_path = public, pg_temp;

-- ----------------------------------------------------------------------------
-- 3. Validación de profundidad máxima (5)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION enforce_community_depth_limit()
RETURNS TRIGGER AS $$
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
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

CREATE TRIGGER communities_enforce_depth
    BEFORE INSERT OR UPDATE OF parent_id ON communities
    FOR EACH ROW EXECUTE FUNCTION enforce_community_depth_limit();

-- ----------------------------------------------------------------------------
-- 4. parent_id inmutable tras creación
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION enforce_parent_id_immutable()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.parent_id IS DISTINCT FROM NEW.parent_id THEN
        RAISE EXCEPTION 'parent_id is immutable after creation'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

CREATE TRIGGER communities_parent_immutable
    BEFORE UPDATE ON communities
    FOR EACH ROW EXECUTE FUNCTION enforce_parent_id_immutable();

-- ----------------------------------------------------------------------------
-- 5. Invariante: para ser miembro de una hija debes serlo de la padre
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION enforce_parent_membership()
RETURNS TRIGGER AS $$
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
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

CREATE TRIGGER community_members_enforce_parent
    BEFORE INSERT ON community_members
    FOR EACH ROW EXECUTE FUNCTION enforce_parent_membership();

-- ----------------------------------------------------------------------------
-- 6. Cascada hacia abajo al borrar membresía
-- ----------------------------------------------------------------------------
-- Si user sale de A, también sale de todas las descendientes en las que estaba.
CREATE OR REPLACE FUNCTION cascade_member_removal()
RETURNS TRIGGER AS $$
BEGIN
    DELETE FROM community_members
    WHERE user_id = OLD.user_id
      AND community_id IN (SELECT community_id FROM get_descendant_community_ids(OLD.community_id));
    RETURN OLD;
END;
$$ LANGUAGE plpgsql
   SECURITY DEFINER
   SET search_path = public, pg_temp;

CREATE TRIGGER community_members_cascade_remove
    AFTER DELETE ON community_members
    FOR EACH ROW EXECUTE FUNCTION cascade_member_removal();

-- ----------------------------------------------------------------------------
-- 7. RLS: solo admins de la padre pueden crear subcomunidad
-- ----------------------------------------------------------------------------
-- La política existente de INSERT en communities permite a cualquier authed user.
-- Refinamos: si parent_id IS NOT NULL, debes ser admin de parent_id.
DROP POLICY IF EXISTS "Authenticated can create communities" ON communities;
DROP POLICY IF EXISTS "Authenticated users can create communities" ON communities;

CREATE POLICY "Authenticated can create communities"
    ON communities FOR INSERT
    TO authenticated
    WITH CHECK (
        created_by = auth.uid()
        AND (
            parent_id IS NULL
            OR EXISTS (
                SELECT 1 FROM community_members
                WHERE community_id = parent_id
                  AND user_id = auth.uid()
                  AND role = 'admin'
            )
        )
    );
