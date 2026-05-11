-- ============================================================================
-- Migration 023 — Moderación UGC (reportes + bloqueos)
--
-- Cumple el requisito de Google Play para apps con UGC: tooling para que los
-- usuarios reporten contenido y bloqueen a otros usuarios.
--
-- Tablas:
--   - reports: cola de reportes que un humano (admin / dev) revisa offline.
--   - user_blocks: lista de usuarios que el usuario actual no quiere ver.
--
-- El filtrado de mensajes de usuarios bloqueados se hace client-side (la
-- lista típica es de pocas decenas como mucho); RLS recursivo sobre
-- community_messages × user_blocks era posible pero añade complejidad sin
-- ganancia real para el MVP.
-- ============================================================================

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

-- Cualquier autenticado puede crear reportes (siendo el reporter)
CREATE POLICY "Authenticated can create reports"
    ON reports FOR INSERT
    TO authenticated
    WITH CHECK (reporter_id = auth.uid());

-- Cada usuario puede ver sus propios reportes
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

-- El usuario solo puede manejar sus propios bloqueos
CREATE POLICY "Users manage own blocks select"
    ON user_blocks FOR SELECT
    TO authenticated
    USING (blocker_id = auth.uid());

CREATE POLICY "Users manage own blocks insert"
    ON user_blocks FOR INSERT
    TO authenticated
    WITH CHECK (blocker_id = auth.uid());

CREATE POLICY "Users manage own blocks delete"
    ON user_blocks FOR DELETE
    TO authenticated
    USING (blocker_id = auth.uid());
