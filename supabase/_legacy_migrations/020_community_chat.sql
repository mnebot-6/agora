-- ============================================================================
-- Migration 020: Community chat
-- ============================================================================
-- Una sala de chat por comunidad. Todos los miembros pueden leer y escribir.
-- Los autores pueden editar/borrar sus mensajes; los admins pueden borrar
-- mensajes ajenos (moderacion basica).
--
-- Realtime: la tabla se publica via Supabase Realtime para entrega en vivo
-- sin polling.
-- ============================================================================

BEGIN;

-- 1. Tabla de mensajes
CREATE TABLE community_messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id uuid NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    body text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 2000),
    created_at timestamptz NOT NULL DEFAULT now(),
    edited_at timestamptz
);

-- Index para listar mensajes de una comunidad ordenados por fecha (paginacion)
CREATE INDEX idx_messages_community_created
    ON community_messages(community_id, created_at DESC);

-- 2. RLS
ALTER TABLE community_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Members read messages"
    ON community_messages FOR SELECT
    TO authenticated
    USING (community_id IN (SELECT get_my_community_ids()));

CREATE POLICY "Members post own messages"
    ON community_messages FOR INSERT
    TO authenticated
    WITH CHECK (
        community_id IN (SELECT get_my_community_ids())
        AND user_id = auth.uid()
    );

CREATE POLICY "Authors edit own messages"
    ON community_messages FOR UPDATE
    TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "Authors or admins delete messages"
    ON community_messages FOR DELETE
    TO authenticated
    USING (
        user_id = auth.uid()
        OR community_id IN (SELECT get_my_admin_community_ids())
    );

-- 3. Habilitar Realtime broadcast en la tabla
ALTER PUBLICATION supabase_realtime ADD TABLE community_messages;

COMMIT;
