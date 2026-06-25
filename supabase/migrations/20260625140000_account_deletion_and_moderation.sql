-- ============================================================================
-- Account deletion (Play requirement) + UGC moderation (reports + user_blocks)
--
-- Fusiona el contenido de los legacy 022 y 023 — no se habían aplicado a prod
-- por error (la app Kotlin ya llama a estos endpoints, ver Profile delete y
-- chat report/block). Sin esta migración, esas funciones fallan en runtime.
-- ============================================================================

BEGIN;

-- ---------- delete_my_account ----------------------------------------------
--
-- Borra auth.users → ON DELETE CASCADE propaga a profiles, community_members,
-- notifications, slot_templates, substitute_queue, community_join_requests,
-- community_messages, etc. slots.reserved_by se pone NULL (SET NULL por
-- defecto). activities/communities.created_by queda apuntando al uid borrado
-- (sin cascada definida) — aceptable porque no hay UI que lo muestre tras el
-- borrado del display_name.
--
-- SECURITY DEFINER + owner postgres para tocar auth.users.

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

-- ---------- reports ---------------------------------------------------------

CREATE TABLE IF NOT EXISTS reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    target_type TEXT NOT NULL CHECK (target_type IN ('message', 'profile')),
    target_id UUID NOT NULL,
    community_id UUID REFERENCES communities(id) ON DELETE SET NULL,
    reason TEXT NOT NULL CHECK (reason IN ('spam', 'harassment', 'inappropriate', 'other')),
    details TEXT,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'reviewed', 'dismissed')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES profiles(id)
);

CREATE INDEX IF NOT EXISTS idx_reports_status ON reports(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reports_target ON reports(target_type, target_id);

ALTER TABLE reports ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Authenticated can create reports" ON reports;
CREATE POLICY "Authenticated can create reports"
    ON reports FOR INSERT
    TO authenticated
    WITH CHECK (reporter_id = auth.uid());

DROP POLICY IF EXISTS "Users see own reports" ON reports;
CREATE POLICY "Users see own reports"
    ON reports FOR SELECT
    TO authenticated
    USING (reporter_id = auth.uid());

-- ---------- user_blocks -----------------------------------------------------

CREATE TABLE IF NOT EXISTS user_blocks (
    blocker_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);

CREATE INDEX IF NOT EXISTS idx_user_blocks_blocker ON user_blocks(blocker_id);

ALTER TABLE user_blocks ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users manage own blocks select" ON user_blocks;
CREATE POLICY "Users manage own blocks select"
    ON user_blocks FOR SELECT
    TO authenticated
    USING (blocker_id = auth.uid());

DROP POLICY IF EXISTS "Users manage own blocks insert" ON user_blocks;
CREATE POLICY "Users manage own blocks insert"
    ON user_blocks FOR INSERT
    TO authenticated
    WITH CHECK (blocker_id = auth.uid());

DROP POLICY IF EXISTS "Users manage own blocks delete" ON user_blocks;
CREATE POLICY "Users manage own blocks delete"
    ON user_blocks FOR DELETE
    TO authenticated
    USING (blocker_id = auth.uid());

COMMIT;
