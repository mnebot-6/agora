# Public Communities + Tags — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Spec:** [2026-04-22-public-communities-design.md](../specs/2026-04-22-public-communities-design.md)

**Goal:** Añadir capa de visibilidad (public_open / public_approval / private), catálogo de tags en BD, flujo de solicitudes de join con aprobación manual, y cambios mínimos de UI de admin. Dejar el data model + RPCs listos para que sub-proyecto B (pantalla Explorar) los consuma.

**Architecture:** Una migración SQL atómica (`014_public_communities_and_tags.sql`) extiende el schema y añade 6 RPCs + modifica la existente `join_community_by_invite`. Capa Kotlin: nuevos modelos en `:core:model`, nuevo `TagRepository` y extensiones de `CommunityRepository` en `:core:data`, UI ampliada en `:feature:community`. DI registrado en `composeApp/di/AppModule.kt`.

**Tech Stack:** Kotlin Multiplatform · Compose Multiplatform · Voyager (navigation + screen models) · Koin (DI) · Supabase (Postgrest + RPC + RLS) · kotlinx.serialization · PostgreSQL.

**TDD note:** El codebase actual no tiene infraestructura de tests (ni commonTest ni androidTest). El plan sigue el patrón existente: implementación directa + verificación manual E2E al final. Añadir test infra es un proyecto aparte.

**Commit strategy:** Un commit por fase lógica (migración SQL, modelos, repos, UI, wiring final). Mensajes conventional: `feat(community): ...`.

---

## File Structure

### Crear
```
supabase/migrations/014_public_communities_and_tags.sql    — migración atómica
core/model/.../CommunityVisibility.kt                       — enum
core/model/.../Tag.kt                                       — data class
core/model/.../CommunityJoinRequest.kt                      — data class + JoinRequestStatus enum
core/data/.../repository/TagRepository.kt                   — getAllTags con cache
feature/community/.../presentation/JoinRequestsScreen.kt    — lista de solicitudes (admin)
feature/community/.../presentation/JoinRequestsScreenModel.kt
```

### Modificar
```
core/model/.../Community.kt            — añadir visibility, tags, memberCount, activityCountUpcoming
core/model/.../Notification.kt         — añadir 3 nuevos tipos
core/data/.../CommunityRepository.kt   — añadir 9 métodos nuevos
feature/community/.../CreateCommunityScreen.kt               — añadir visibility picker + tags chips
feature/community/.../CreateCommunityScreenModel.kt          — estado adicional + validación
feature/community/.../CommunityDetailScreen.kt               — botón "Solicitudes pendientes"
feature/community/.../CommunityDetailScreenModel.kt          — cargar count pendientes
feature/community/src/commonMain/composeResources/values/strings.xml     — 15+ strings nuevos
feature/community/src/commonMain/composeResources/values-es/strings.xml  — traducciones
composeApp/.../di/AppModule.kt         — registrar TagRepository + JoinRequestsScreenModel
core/domain/.../community/CreateCommunityUseCase.kt          — firma con visibility + tagIds
```

---

## Fase 1 — Backend: Migración SQL

### Task 1: Crear estructura base del archivo de migración

**Files:**
- Create: `supabase/migrations/014_public_communities_and_tags.sql`

- [ ] **Step 1: Crear archivo con header y enum de visibilidad**

```sql
-- ============================================================================
-- Migration 014: Public Communities + Tags + Join Requests
--
-- Adds community visibility states (public_open / public_approval / private),
-- a curated tags catalog with N:M join to communities, and a join-request
-- flow for public_approval communities.
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

COMMIT;
```

- [ ] **Step 2: Verificar sintaxis SQL básica**

Run: `psql --version` (sanity check)
Expected: psql version printed (or note that we'll verify on apply)

- [ ] **Step 3: Añadir tabla tags + seed (continuar editando el mismo archivo antes del COMMIT)**

Reemplazar el `COMMIT;` del paso 1 por:

```sql
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

COMMIT;
```

- [ ] **Step 4: No commit aún — añadiremos RPCs y RLS en el mismo archivo en la Fase 2.**

---

### Task 2: RPCs + RLS policies + modificación de `join_community_by_invite`

**Files:**
- Modify: `supabase/migrations/014_public_communities_and_tags.sql` (añadir antes del `COMMIT;`)

- [ ] **Step 1: Añadir helper function `is_admin_of(community_id)` (si no existe ya)**

Revisar si existe en `003_rls_policies.sql` → `get_my_admin_community_ids()`. Reaprovechar esa. No crear duplicados.

- [ ] **Step 2: Añadir RPC `search_public_communities`**

Antes del `COMMIT;` añadir:

```sql
-- ============================================================================
-- 5. RPC: search_public_communities
-- Devuelve metadata básica de comunidades públicas (ordenada por popularidad).
-- Excluye invite_code y datos sensibles.
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
```

- [ ] **Step 3: Añadir RPC `get_public_community_preview`**

```sql
-- ============================================================================
-- 6. RPC: get_public_community_preview
-- Devuelve preview de una sola comunidad pública.
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
```

- [ ] **Step 4: Añadir RPC `request_to_join_community`**

```sql
-- ============================================================================
-- 7. RPC: request_to_join_community
-- public_open → join directo, devuelve {status: 'joined'}
-- public_approval → inserta en community_join_requests, notifica admins
-- private → error
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

    -- Ya eres miembro
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
    -- Evitar duplicados pending
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

    -- Notificar a todos los admins de la comunidad
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
```

- [ ] **Step 5: Añadir RPCs `approve_join_request`, `reject_join_request`, `cancel_join_request`**

```sql
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

    -- Caller debe ser admin de la comunidad
    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_req.community_id
          AND user_id = v_caller
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can approve';
    END IF;

    -- Añadir como miembro (idempotente)
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
```

- [ ] **Step 6: Modificar `join_community_by_invite` existente para cancelar pending**

Necesitamos leer la versión actual del RPC. Está en `001_initial_schema.sql` o `002_rpc_functions.sql` o en una migración de fix posterior. **Acción:** `grep -rn "join_community_by_invite" supabase/migrations/` para localizar la última definición, copiarla, y añadir al final de la transacción (antes del success RETURN) el bloque:

```sql
    -- Si el usuario tenía una solicitud pending en esta comunidad, cancelarla
    UPDATE community_join_requests
    SET status = 'cancelled', resolved_at = now()
    WHERE community_id = v_community.id
      AND user_id = v_user_id
      AND status = 'pending';
```

Pegar el `CREATE OR REPLACE FUNCTION join_community_by_invite(...)` completo actualizado en la migración 014 (al ser CREATE OR REPLACE sobreescribe limpiamente).

- [ ] **Step 7: Añadir RLS policies para tablas nuevas**

```sql
-- ============================================================================
-- 11. RLS POLICIES
-- ============================================================================

-- tags: lectura pública para cualquier autenticado
ALTER TABLE tags ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Tags are readable by any authenticated user"
    ON tags FOR SELECT
    TO authenticated
    USING (true);

-- community_tags: readable si la comunidad es visible (miembro o pública)
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

-- community_join_requests
ALTER TABLE community_join_requests ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users see their own join requests"
    ON community_join_requests FOR SELECT
    TO authenticated
    USING (user_id = auth.uid());

CREATE POLICY "Admins see join requests for their communities"
    ON community_join_requests FOR SELECT
    TO authenticated
    USING (community_id IN (SELECT get_my_admin_community_ids()));

-- INSERT/UPDATE/DELETE deny directo — solo via RPCs
-- (Sin políticas de INSERT/UPDATE/DELETE, Postgres deniega por defecto con RLS habilitado)

-- communities: añadir policy para ver públicas
CREATE POLICY "Public communities preview"
    ON communities FOR SELECT
    TO authenticated
    USING (visibility IN ('public_open', 'public_approval'));
-- NOTA: Esto expone TODAS las columnas incluyendo invite_code. El cliente
-- DEBE usar search_public_communities/get_public_community_preview RPCs
-- para vistas públicas. Follow-up futuro: vista materializada con solo
-- columnas seguras si detectamos abuso.
```

- [ ] **Step 8: Ampliar `notifications.type` check constraint con los 3 nuevos tipos**

Primero identificar cómo está declarado el tipo actual. Buscar en migraciones:

Run: `grep -n "CREATE TABLE notifications" supabase/migrations/*.sql`

Si es una CHECK constraint sobre columna text, la migración debe hacer DROP/ADD:

```sql
-- ============================================================================
-- 12. AMPLIAR TIPOS DE NOTIFICACIÓN
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
```

Si es un ENUM type, usar `ALTER TYPE ... ADD VALUE` (por cada uno). **Verificar** en el grep antes de elegir una vía.

- [ ] **Step 9: Cerrar la transacción**

El archivo termina con:
```sql
COMMIT;
```
(Ya escrito en task 1 step 3. Asegurar que sigue presente al final.)

- [ ] **Step 10: Aplicar migración en Supabase local/staging y verificar**

Según cómo el proyecto aplique migraciones (Supabase CLI o deploy manual):

Run (si hay supabase CLI):
```
supabase db reset
# o
supabase migration up
```

Expected: sin errores; tablas `tags`, `community_tags`, `community_join_requests` creadas; 15 filas en `tags`; `communities.visibility` presente con todos los valores = `'private'`.

Verificación SQL manual:
```sql
SELECT count(*) FROM tags;           -- 15
SELECT visibility, count(*) FROM communities GROUP BY visibility;  -- todas private
SELECT proname FROM pg_proc WHERE proname IN (
    'search_public_communities','get_public_community_preview',
    'request_to_join_community','approve_join_request',
    'reject_join_request','cancel_join_request'
);  -- 6 filas
```

- [ ] **Step 11: Commit**

```bash
git add supabase/migrations/014_public_communities_and_tags.sql
git commit -m "feat(community): add public communities, tags catalog, and join request flow (SQL)"
```

---

## Fase 2 — Modelos Kotlin (`:core:model`)

### Task 3: Enum `CommunityVisibility`

**Files:**
- Create: `core/model/src/commonMain/kotlin/com/app/community/core/model/CommunityVisibility.kt`

- [ ] **Step 1: Crear el archivo**

```kotlin
package com.app.community.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CommunityVisibility {
    @SerialName("public_open") PUBLIC_OPEN,
    @SerialName("public_approval") PUBLIC_APPROVAL,
    @SerialName("private") PRIVATE,
}
```

---

### Task 4: Data class `Tag`

**Files:**
- Create: `core/model/src/commonMain/kotlin/com/app/community/core/model/Tag.kt`

- [ ] **Step 1: Crear el archivo**

```kotlin
package com.app.community.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tag(
    val id: String,
    val slug: String,
    @SerialName("name_es") val nameEs: String,
    @SerialName("name_en") val nameEn: String,
    val icon: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
)
```

---

### Task 5: Data class `CommunityJoinRequest` + enum `JoinRequestStatus`

**Files:**
- Create: `core/model/src/commonMain/kotlin/com/app/community/core/model/CommunityJoinRequest.kt`

- [ ] **Step 1: Crear el archivo**

```kotlin
package com.app.community.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class JoinRequestStatus {
    @SerialName("pending") PENDING,
    @SerialName("approved") APPROVED,
    @SerialName("rejected") REJECTED,
    @SerialName("cancelled") CANCELLED,
}

@Serializable
data class CommunityJoinRequest(
    val id: String,
    @SerialName("community_id") val communityId: String,
    @SerialName("user_id") val userId: String,
    val status: JoinRequestStatus,
    val message: String? = null,
    @SerialName("requested_at") val requestedAt: Instant,
    @SerialName("resolved_at") val resolvedAt: Instant? = null,
    @SerialName("resolved_by") val resolvedBy: String? = null,
)
```

---

### Task 6: Modificar `Community` para incluir visibility, tags y counts

**Files:**
- Modify: `core/model/src/commonMain/kotlin/com/app/community/core/model/Community.kt`

- [ ] **Step 1: Sustituir contenido completo del archivo**

```kotlin
package com.app.community.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Community(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    // invite_code solo poblado cuando eres miembro (RLS filtra para no-miembros, pero
    // aun así marcamos nullable por seguridad)
    @SerialName("invite_code") val inviteCode: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    val visibility: CommunityVisibility = CommunityVisibility.PRIVATE,
    val tags: List<Tag> = emptyList(),
    @SerialName("member_count") val memberCount: Int? = null,
    @SerialName("activity_count_upcoming") val activityCountUpcoming: Int? = null,
)
```

- [ ] **Step 2: Revisar usos de `inviteCode` y `createdBy` en el código**

Run: `grep -rn "inviteCode\|createdBy" core feature composeApp --include="*.kt"`

Expected: encontrar llamadas que antes asumían no-null. Ajustar con `!!` (si es contexto donde seguro tenemos el campo — comunidad propia) o safe calls (`?.`).

Actualizar cualquier uso problemático con el operador adecuado. Sin cambiar lógica existente.

---

### Task 7: Añadir tipos de notificación

**Files:**
- Modify: `core/model/src/commonMain/kotlin/com/app/community/core/model/Notification.kt`

- [ ] **Step 1: Añadir 3 valores nuevos al enum `NotificationType`**

Sustituir el bloque enum por:

```kotlin
@Serializable
enum class NotificationType {
    @SerialName("new_activity") NEW_ACTIVITY,
    @SerialName("slot_released") SLOT_RELEASED,
    @SerialName("substitute_promoted") SUBSTITUTE_PROMOTED,
    @SerialName("join_request_received") JOIN_REQUEST_RECEIVED,
    @SerialName("join_request_approved") JOIN_REQUEST_APPROVED,
    @SerialName("join_request_rejected") JOIN_REQUEST_REJECTED,
}
```

- [ ] **Step 2: Buscar `when` exhaustivos sobre `NotificationType` y completar ramas**

Run: `grep -rn "NotificationType\." core feature composeApp --include="*.kt" | grep -v ".Companion\|::" | head -30`

Completar cualquier `when (type)` que no sea exhaustivo con las 3 ramas nuevas. Probable ubicación: `feature/notification/.../NotificationListScreen.kt` (iconos y textos por tipo).

---

### Task 8: Commit fase 2

- [ ] **Step 1: Commit**

```bash
git add core/model
git commit -m "feat(community): add visibility/tag/join-request models"
```

---

## Fase 3 — Repositorios y DI (`:core:data`, `composeApp`)

### Task 9: `TagRepository` con cache en memoria

**Files:**
- Create: `core/data/src/commonMain/kotlin/com/app/community/core/data/repository/TagRepository.kt`

- [ ] **Step 1: Crear el archivo**

```kotlin
package com.app.community.core.data.repository

import com.app.community.core.common.AppResult
import com.app.community.core.common.safeCall
import com.app.community.core.data.SupabaseProvider
import com.app.community.core.model.Tag
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TagRepository {

    private val postgrest = SupabaseProvider.client.postgrest
    private val mutex = Mutex()
    private var cachedTags: List<Tag>? = null

    suspend fun getAllTags(): AppResult<List<Tag>> {
        cachedTags?.let { return AppResult.Success(it) }
        return mutex.withLock {
            cachedTags?.let { return@withLock AppResult.Success(it) }
            val result = safeCall {
                postgrest.from("tags")
                    .select { order(column = "sort_order", order = io.github.jan.supabase.postgrest.query.Order.ASCENDING) }
                    .decodeList<Tag>()
            }
            result.onSuccess { cachedTags = it }
            result
        }
    }

    fun invalidateCache() {
        cachedTags = null
    }
}
```

---

### Task 10: Ampliar `CommunityRepository` — visibility/tags al crear + métodos públicos

**Files:**
- Modify: `core/data/src/commonMain/kotlin/com/app/community/core/data/repository/CommunityRepository.kt`

- [ ] **Step 1: Cambiar firma de `createCommunity` para aceptar visibility + tags**

Reemplazar el método actual:

```kotlin
suspend fun createCommunity(
    name: String,
    description: String?,
    createdBy: String,
    visibility: CommunityVisibility = CommunityVisibility.PRIVATE,
    tagIds: List<String> = emptyList(),
): AppResult<Community> =
    safeCall {
        val inviteCode = generateInviteCode()
        val created = postgrest.from("communities")
            .insert(buildJsonObject {
                put("name", name)
                description?.let { put("description", it) }
                put("invite_code", inviteCode)
                put("created_by", createdBy)
                put("visibility", when (visibility) {
                    CommunityVisibility.PUBLIC_OPEN -> "public_open"
                    CommunityVisibility.PUBLIC_APPROVAL -> "public_approval"
                    CommunityVisibility.PRIVATE -> "private"
                })
            }) { select() }
            .decodeSingle<Community>()

        // Asociar tags (máx 3, enforced por trigger)
        if (tagIds.isNotEmpty()) {
            val limited = tagIds.distinct().take(3)
            val rows = limited.map { tagId ->
                buildJsonObject {
                    put("community_id", created.id)
                    put("tag_id", tagId)
                }
            }
            postgrest.from("community_tags").insert(rows)
        }

        created
    }
```

Añadir imports necesarios:
```kotlin
import com.app.community.core.model.CommunityVisibility
```

- [ ] **Step 2: Añadir método `searchPublicCommunities`**

Al final de la clase (antes del cierre `}`):

```kotlin
suspend fun searchPublicCommunities(
    query: String? = null,
    tagIds: List<String> = emptyList(),
    limit: Int = 20,
    offset: Int = 0,
): AppResult<List<Community>> = safeCall {
    val result = postgrest.rpc(
        function = "search_public_communities",
        parameters = buildJsonObject {
            query?.let { put("p_query", it) }
            if (tagIds.isNotEmpty()) {
                put("p_tag_ids", kotlinx.serialization.json.JsonArray(
                    tagIds.map { kotlinx.serialization.json.JsonPrimitive(it) }
                ))
            }
            put("p_limit", limit)
            put("p_offset", offset)
        },
    )
    lenientJson.decodeFromString<List<Community>>(result.data)
}
```

- [ ] **Step 3: Añadir método `getPublicCommunityPreview`**

```kotlin
suspend fun getPublicCommunityPreview(communityId: String): AppResult<Community> = safeCall {
    val result = postgrest.rpc(
        function = "get_public_community_preview",
        parameters = buildJsonObject { put("p_community_id", communityId) },
    )
    lenientJson.decodeFromString<Community>(result.data)
}
```

- [ ] **Step 4: Añadir método `requestToJoinCommunity`**

```kotlin
/**
 * Returns one of:
 *  - {"status":"joined"}          — era public_open; ya eres miembro
 *  - {"status":"already_member"}  — ya eras miembro
 *  - {"status":"pending","request_id":"..."} — solicitud creada (public_approval)
 */
suspend fun requestToJoinCommunity(
    communityId: String,
    message: String? = null,
): AppResult<JoinRequestResult> = safeCall {
    val result = postgrest.rpc(
        function = "request_to_join_community",
        parameters = buildJsonObject {
            put("p_community_id", communityId)
            message?.let { put("p_message", it) }
        },
    )
    lenientJson.decodeFromString<JoinRequestResult>(result.data)
}
```

Añadir al final del archivo (fuera de la clase pero en el mismo fichero):

```kotlin
@kotlinx.serialization.Serializable
data class JoinRequestResult(
    val status: String,                          // "joined"/"already_member"/"pending"
    @kotlinx.serialization.SerialName("request_id")
    val requestId: String? = null,
)
```

- [ ] **Step 5: Añadir métodos `cancelJoinRequest`, `approveJoinRequest`, `rejectJoinRequest`**

```kotlin
suspend fun cancelJoinRequest(requestId: String): AppResult<Unit> = safeCall {
    postgrest.rpc(
        function = "cancel_join_request",
        parameters = buildJsonObject { put("p_request_id", requestId) },
    )
    Unit
}

suspend fun approveJoinRequest(requestId: String): AppResult<Unit> = safeCall {
    postgrest.rpc(
        function = "approve_join_request",
        parameters = buildJsonObject { put("p_request_id", requestId) },
    )
    Unit
}

suspend fun rejectJoinRequest(requestId: String): AppResult<Unit> = safeCall {
    postgrest.rpc(
        function = "reject_join_request",
        parameters = buildJsonObject { put("p_request_id", requestId) },
    )
    Unit
}
```

- [ ] **Step 6: Añadir método `getPendingJoinRequests` — admin ve solicitudes pending de su comunidad**

```kotlin
suspend fun getPendingJoinRequests(communityId: String): AppResult<List<PendingJoinRequest>> = safeCall {
    postgrest.from("community_join_requests")
        .select(columns = io.github.jan.supabase.postgrest.query.Columns.raw(
            "id, community_id, user_id, status, message, requested_at, resolved_at, resolved_by, profiles(id, display_name, avatar_url)"
        )) {
            filter {
                eq("community_id", communityId)
                eq("status", "pending")
            }
            order(column = "requested_at", order = io.github.jan.supabase.postgrest.query.Order.ASCENDING)
        }
        .decodeList<PendingJoinRequest>()
}
```

Añadir al final del fichero:
```kotlin
@kotlinx.serialization.Serializable
data class PendingJoinRequest(
    val id: String,
    @kotlinx.serialization.SerialName("community_id") val communityId: String,
    @kotlinx.serialization.SerialName("user_id") val userId: String,
    val status: com.app.community.core.model.JoinRequestStatus,
    val message: String? = null,
    @kotlinx.serialization.SerialName("requested_at") val requestedAt: kotlinx.datetime.Instant,
    val profiles: RequesterProfile? = null,
)

@kotlinx.serialization.Serializable
data class RequesterProfile(
    val id: String,
    @kotlinx.serialization.SerialName("display_name") val displayName: String? = null,
    @kotlinx.serialization.SerialName("avatar_url") val avatarUrl: String? = null,
)
```

- [ ] **Step 7: Añadir método `getPendingJoinRequestForUser` — user ve su propia solicitud pending**

```kotlin
suspend fun getPendingJoinRequestForUser(
    communityId: String,
    userId: String,
): AppResult<com.app.community.core.model.CommunityJoinRequest?> = safeCall {
    postgrest.from("community_join_requests")
        .select {
            filter {
                eq("community_id", communityId)
                eq("user_id", userId)
                eq("status", "pending")
            }
            limit(1)
        }
        .decodeList<com.app.community.core.model.CommunityJoinRequest>()
        .firstOrNull()
}
```

- [ ] **Step 8: Añadir método `updateCommunityVisibility` y `updateCommunityTags`**

```kotlin
suspend fun updateCommunityVisibility(
    communityId: String,
    visibility: CommunityVisibility,
): AppResult<Unit> = safeCall {
    postgrest.from("communities")
        .update({
            set("visibility", when (visibility) {
                CommunityVisibility.PUBLIC_OPEN -> "public_open"
                CommunityVisibility.PUBLIC_APPROVAL -> "public_approval"
                CommunityVisibility.PRIVATE -> "private"
            })
        }) { filter { eq("id", communityId) } }
}

suspend fun updateCommunityTags(
    communityId: String,
    tagIds: List<String>,
): AppResult<Unit> = safeCall {
    // Delete + reinsert (atomic via single transaction not available here,
    // best-effort: delete then insert; trigger enforces max 3 on insert)
    postgrest.from("community_tags")
        .delete { filter { eq("community_id", communityId) } }

    if (tagIds.isNotEmpty()) {
        val limited = tagIds.distinct().take(3)
        val rows = limited.map { tagId ->
            buildJsonObject {
                put("community_id", communityId)
                put("tag_id", tagId)
            }
        }
        postgrest.from("community_tags").insert(rows)
    }
}
```

---

### Task 11: Adaptar `CreateCommunityUseCase` para propagar los nuevos parámetros

**Files:**
- Modify: `core/domain/src/commonMain/kotlin/com/app/community/core/domain/community/CreateCommunityUseCase.kt`

- [ ] **Step 1: Leer archivo actual y añadir parámetros**

Leer el fichero. Probable firma actual:
```kotlin
class CreateCommunityUseCase(
    private val communityRepository: CommunityRepository,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(name: String, description: String?): AppResult<Community> {
        val userId = authRepository.currentUserId() ?: return AppResult.Error("Not authenticated")
        return communityRepository.createCommunity(name, description, userId)
    }
}
```

Sustituir por:
```kotlin
class CreateCommunityUseCase(
    private val communityRepository: CommunityRepository,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        name: String,
        description: String?,
        visibility: com.app.community.core.model.CommunityVisibility,
        tagIds: List<String>,
    ): AppResult<com.app.community.core.model.Community> {
        val userId = authRepository.currentUserId()
            ?: return AppResult.Error("Not authenticated")
        return communityRepository.createCommunity(name, description, userId, visibility, tagIds)
    }
}
```

---

### Task 12: Registrar nuevos componentes en DI

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/app/community/di/AppModule.kt`

- [ ] **Step 1: Añadir `TagRepository` al `repositoryModule`**

Antes:
```kotlin
single { SlotTemplateRepository() }
```
Insertar:
```kotlin
single { TagRepository() }
```

Añadir import:
```kotlin
import com.app.community.core.data.repository.TagRepository
```

- [ ] **Step 2: (Se completa en Fase 5) Placeholder para registrar `JoinRequestsScreenModel`**

Nota: lo registramos cuando exista la ScreenModel en Fase 5.

---

### Task 13: Commit fase 3

- [ ] **Step 1: Commit**

```bash
git add core/data core/domain composeApp
git commit -m "feat(community): public community RPCs, tag repo, use case wiring"
```

---

## Fase 4 — UI: CreateCommunityScreen ampliado

### Task 14: String resources (es + en)

**Files:**
- Modify: `feature/community/src/commonMain/composeResources/values/strings.xml`
- Modify: `feature/community/src/commonMain/composeResources/values-es/strings.xml`

- [ ] **Step 1: Añadir las siguientes keys a ambos ficheros**

**En `values-es/strings.xml`:**
```xml
<string name="create_community_visibility_label">Visibilidad</string>
<string name="create_community_visibility_public_open">Pública</string>
<string name="create_community_visibility_public_open_desc">Aparece en Explorar. Cualquiera puede unirse.</string>
<string name="create_community_visibility_public_approval">Pública con aprobación</string>
<string name="create_community_visibility_public_approval_desc">Aparece en Explorar. Tú apruebas cada solicitud.</string>
<string name="create_community_visibility_private">Privada</string>
<string name="create_community_visibility_private_desc">Oculta. Solo por código de invitación.</string>
<string name="create_community_tags_label">Categorías (1 a 3)</string>
<string name="create_community_tags_error_min">Selecciona al menos 1 categoría</string>
<string name="create_community_tags_error_max">Máximo 3 categorías</string>
<string name="community_pending_requests_badge">Solicitudes pendientes</string>
<string name="community_pending_requests_count">%1$d pendientes</string>
<string name="join_requests_title">Solicitudes de unión</string>
<string name="join_requests_empty">No hay solicitudes pendientes</string>
<string name="join_requests_approve">Aprobar</string>
<string name="join_requests_reject">Rechazar</string>
<string name="join_request_message_placeholder">Mensaje para el admin (opcional)</string>
```

**En `values/strings.xml`** (inglés — si el proyecto soporta bilingüe; si solo es ES, saltarse este paso):
```xml
<string name="create_community_visibility_label">Visibility</string>
<string name="create_community_visibility_public_open">Public</string>
<string name="create_community_visibility_public_open_desc">Shown in Explore. Anyone can join.</string>
<string name="create_community_visibility_public_approval">Public with approval</string>
<string name="create_community_visibility_public_approval_desc">Shown in Explore. You approve each request.</string>
<string name="create_community_visibility_private">Private</string>
<string name="create_community_visibility_private_desc">Hidden. Invite code only.</string>
<string name="create_community_tags_label">Categories (1 to 3)</string>
<string name="create_community_tags_error_min">Select at least 1 category</string>
<string name="create_community_tags_error_max">Maximum 3 categories</string>
<string name="community_pending_requests_badge">Pending requests</string>
<string name="community_pending_requests_count">%1$d pending</string>
<string name="join_requests_title">Join requests</string>
<string name="join_requests_empty">No pending requests</string>
<string name="join_requests_approve">Approve</string>
<string name="join_requests_reject">Reject</string>
<string name="join_request_message_placeholder">Message for admin (optional)</string>
```

**Nota:** antes de este task, correr `git ls-files feature/community/src/commonMain/composeResources/ | head` para confirmar qué idiomas existen.

---

### Task 15: Actualizar `CreateCommunityScreenModel` con visibility + tags

**Files:**
- Modify: `feature/community/src/commonMain/kotlin/com/app/community/feature/community/presentation/CreateCommunityScreenModel.kt`

- [ ] **Step 1: Leer el archivo actual y reescribirlo**

Objetivo: añadir `StateFlow<FormState>` con `name`, `description`, `visibility`, `selectedTagIds`. Al crear la comunidad, llamar al use case con los 4 campos. Cargar tags disponibles al inicio.

Nueva estructura (placeholder de firma — el código exacto depende de la versión actual):

```kotlin
package com.app.community.feature.community.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.AppResult
import com.app.community.core.data.repository.TagRepository
import com.app.community.core.domain.community.CreateCommunityUseCase
import com.app.community.core.model.CommunityVisibility
import com.app.community.core.model.Tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateCommunityScreenModel(
    private val createCommunityUseCase: CreateCommunityUseCase,
    private val tagRepository: TagRepository,
) : ScreenModel {

    data class FormState(
        val name: String = "",
        val description: String = "",
        val visibility: CommunityVisibility = CommunityVisibility.PRIVATE,
        val selectedTagIds: Set<String> = emptySet(),
        val availableTags: List<Tag> = emptyList(),
    )

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data object Success : UiState
        data class Error(val message: String) : UiState
    }

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        screenModelScope.launch {
            when (val result = tagRepository.getAllTags()) {
                is AppResult.Success -> _form.update { it.copy(availableTags = result.data) }
                is AppResult.Error -> { /* no bloquear UI si falla catálogo */ }
            }
        }
    }

    fun onNameChange(value: String) = _form.update { it.copy(name = value) }
    fun onDescriptionChange(value: String) = _form.update { it.copy(description = value) }
    fun onVisibilityChange(value: CommunityVisibility) = _form.update { it.copy(visibility = value) }

    fun onTagToggle(tagId: String) = _form.update { state ->
        val newSet = if (state.selectedTagIds.contains(tagId)) {
            state.selectedTagIds - tagId
        } else {
            if (state.selectedTagIds.size >= 3) state.selectedTagIds // no superar 3
            else state.selectedTagIds + tagId
        }
        state.copy(selectedTagIds = newSet)
    }

    fun create() {
        val state = _form.value
        if (state.name.isBlank()) return
        if (state.selectedTagIds.isEmpty()) {
            _uiState.value = UiState.Error("Selecciona al menos 1 categoría")
            return
        }
        screenModelScope.launch {
            _uiState.value = UiState.Loading
            val result = createCommunityUseCase(
                name = state.name,
                description = state.description.ifBlank { null },
                visibility = state.visibility,
                tagIds = state.selectedTagIds.toList(),
            )
            _uiState.value = when (result) {
                is AppResult.Success -> UiState.Success
                is AppResult.Error -> UiState.Error(result.message)
            }
        }
    }
}
```

**Nota:** si la firma/estructura actual difiere (uiState con forma distinta), mantener compatibilidad con `CreateCommunityScreen` (se ajusta en Task 16).

- [ ] **Step 2: Actualizar registro en DI**

En `composeApp/.../AppModule.kt` reemplazar:
```kotlin
factory { CreateCommunityScreenModel(createCommunityUseCase = get()) }
```
por:
```kotlin
factory { CreateCommunityScreenModel(createCommunityUseCase = get(), tagRepository = get()) }
```

---

### Task 16: Actualizar `CreateCommunityScreen` UI

**Files:**
- Modify: `feature/community/src/commonMain/kotlin/com/app/community/feature/community/presentation/CreateCommunityScreen.kt`

- [ ] **Step 1: Leer archivo actual e insertar sección "Visibilidad" + "Categorías" entre el campo descripción y el botón submit**

Añadir imports:
```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import com.app.community.core.model.CommunityVisibility
```

Sustituir el `Column` content entre el campo `description` y el bloque `errorState`/`AgoraButton` por (insertar después del `OutlinedTextField` de description y su `Spacer`):

```kotlin
val form by screenModel.form.collectAsState()

Spacer(Modifier.height(AgoraSpacing.lg))

// Sección Visibilidad
Text(
    text = stringResource(Res.string.create_community_visibility_label),
    style = MaterialTheme.typography.labelLarge,
)
Spacer(Modifier.height(AgoraSpacing.sm))
CommunityVisibility.entries.forEach { visibility ->
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading) { screenModel.onVisibilityChange(visibility) }
            .padding(vertical = AgoraSpacing.xs),
    ) {
        RadioButton(
            selected = form.visibility == visibility,
            onClick = { screenModel.onVisibilityChange(visibility) },
            enabled = !isLoading,
        )
        Spacer(Modifier.width(AgoraSpacing.sm))
        Column {
            Text(
                text = stringResource(visibility.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(visibility.descRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

Spacer(Modifier.height(AgoraSpacing.lg))

// Sección Categorías
Text(
    text = stringResource(Res.string.create_community_tags_label),
    style = MaterialTheme.typography.labelLarge,
)
Spacer(Modifier.height(AgoraSpacing.sm))

@OptIn(ExperimentalLayoutApi::class)
FlowRow(
    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
    verticalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
) {
    form.availableTags.forEach { tag ->
        val selected = form.selectedTagIds.contains(tag.id)
        FilterChip(
            selected = selected,
            onClick = { screenModel.onTagToggle(tag.id) },
            label = {
                val icon = tag.icon?.let { "$it " } ?: ""
                Text(icon + tag.nameEs)
            },
            enabled = !isLoading && (selected || form.selectedTagIds.size < 3),
        )
    }
}

Spacer(Modifier.height(AgoraSpacing.xl))
```

Añadir al final del archivo helpers:

```kotlin
@Composable
private fun CommunityVisibility.labelRes() = when (this) {
    CommunityVisibility.PUBLIC_OPEN -> Res.string.create_community_visibility_public_open
    CommunityVisibility.PUBLIC_APPROVAL -> Res.string.create_community_visibility_public_approval
    CommunityVisibility.PRIVATE -> Res.string.create_community_visibility_private
}

@Composable
private fun CommunityVisibility.descRes() = when (this) {
    CommunityVisibility.PUBLIC_OPEN -> Res.string.create_community_visibility_public_open_desc
    CommunityVisibility.PUBLIC_APPROVAL -> Res.string.create_community_visibility_public_approval_desc
    CommunityVisibility.PRIVATE -> Res.string.create_community_visibility_private_desc
}
```

Nota: helpers `@Composable` para retornar el `StringResource`; si `.labelRes()` no es compile-friendly en el target, sustituir por expresión `when` inline en el punto de llamada.

- [ ] **Step 2: Actualizar bloqueo de botón Submit**

Cambiar:
```kotlin
enabled = !isLoading && name.isNotBlank(),
```
por:
```kotlin
enabled = !isLoading && form.name.isNotBlank() && form.selectedTagIds.isNotEmpty(),
```

- [ ] **Step 3: Asegurar que los campos name/description ahora sincronizan con el state del ScreenModel**

Ya que el ScreenModel mantiene el estado autoritativo:
```kotlin
var name by remember { mutableStateOf("") }
```
→ puede quedarse como buffer local; basta con asegurar que `screenModel.onNameChange(...)` se llama en cada update (ya lo hace). OK.

- [ ] **Step 4: Build**

Run: `./gradlew :feature:community:compileKotlinMetadata :composeApp:compileDebugKotlinAndroid`
Expected: sin errores.

---

### Task 17: Commit fase 4

- [ ] **Step 1: Commit**

```bash
git add feature/community composeApp
git commit -m "feat(community): visibility + tags picker in CreateCommunityScreen"
```

---

## Fase 5 — UI: JoinRequestsScreen + badge en CommunityDetailScreen

### Task 18: Crear `JoinRequestsScreenModel`

**Files:**
- Create: `feature/community/src/commonMain/kotlin/com/app/community/feature/community/presentation/JoinRequestsScreenModel.kt`

- [ ] **Step 1: Crear el archivo**

```kotlin
package com.app.community.feature.community.presentation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.app.community.core.common.AppResult
import com.app.community.core.data.repository.CommunityRepository
import com.app.community.core.data.repository.PendingJoinRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class JoinRequestsScreenModel(
    private val communityId: String,
    private val communityRepository: CommunityRepository,
) : ScreenModel {

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val requests: List<PendingJoinRequest>) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _actionInProgress = MutableStateFlow<String?>(null)  // requestId actualmente procesándose
    val actionInProgress: StateFlow<String?> = _actionInProgress.asStateFlow()

    init { load() }

    fun load() {
        screenModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = when (val r = communityRepository.getPendingJoinRequests(communityId)) {
                is AppResult.Success -> UiState.Loaded(r.data)
                is AppResult.Error -> UiState.Error(r.message)
            }
        }
    }

    fun approve(requestId: String) = handleAction(requestId) {
        communityRepository.approveJoinRequest(requestId)
    }

    fun reject(requestId: String) = handleAction(requestId) {
        communityRepository.rejectJoinRequest(requestId)
    }

    private fun handleAction(requestId: String, action: suspend () -> AppResult<Unit>) {
        screenModelScope.launch {
            _actionInProgress.value = requestId
            val result = action()
            _actionInProgress.value = null
            if (result is AppResult.Success) load()
            else if (result is AppResult.Error) _uiState.value = UiState.Error(result.message)
        }
    }
}
```

---

### Task 19: Crear `JoinRequestsScreen` (UI)

**Files:**
- Create: `feature/community/src/commonMain/kotlin/com/app/community/feature/community/presentation/JoinRequestsScreen.kt`

- [ ] **Step 1: Crear el archivo**

```kotlin
package com.app.community.feature.community.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.theme.AgoraSpacing
import agora.feature.community.generated.resources.Res
import agora.feature.community.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf

data class JoinRequestsScreen(val communityId: String) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<JoinRequestsScreenModel> { parametersOf(communityId) }
        val state by screenModel.uiState.collectAsState()
        val inProgress by screenModel.actionInProgress.collectAsState()

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = { Text(stringResource(Res.string.join_requests_title)) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val s = state) {
                    is JoinRequestsScreenModel.UiState.Loading -> {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    is JoinRequestsScreenModel.UiState.Error -> {
                        Text(s.message, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
                    }
                    is JoinRequestsScreenModel.UiState.Loaded -> {
                        if (s.requests.isEmpty()) {
                            Text(
                                stringResource(Res.string.join_requests_empty),
                                modifier = Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(AgoraSpacing.screenHorizontal),
                                verticalArrangement = Arrangement.spacedBy(AgoraSpacing.md),
                            ) {
                                items(s.requests, key = { it.id }) { req ->
                                    RequestCard(
                                        name = req.profiles?.displayName ?: "—",
                                        message = req.message,
                                        isProcessing = inProgress == req.id,
                                        onApprove = { screenModel.approve(req.id) },
                                        onReject = { screenModel.reject(req.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(
    name: String,
    message: String?,
    isProcessing: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AgoraSpacing.md)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(AgoraSpacing.sm))
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(AgoraSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm)) {
                AgoraButton(
                    text = stringResource(Res.string.join_requests_reject),
                    onClick = onReject,
                    variant = AgoraButtonVariant.Secondary,
                    enabled = !isProcessing,
                    isLoading = isProcessing,
                    modifier = Modifier.weight(1f),
                )
                AgoraButton(
                    text = stringResource(Res.string.join_requests_approve),
                    onClick = onApprove,
                    variant = AgoraButtonVariant.Primary,
                    enabled = !isProcessing,
                    isLoading = isProcessing,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
```

---

### Task 20: Registrar `JoinRequestsScreenModel` en DI

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/app/community/di/AppModule.kt`

- [ ] **Step 1: Añadir al `screenModelModule`**

```kotlin
factory { params ->
    JoinRequestsScreenModel(
        communityId = params.get(),
        communityRepository = get(),
    )
}
```

Añadir import:
```kotlin
import com.app.community.feature.community.presentation.JoinRequestsScreenModel
```

---

### Task 21: Añadir badge "Solicitudes pendientes" en `CommunityDetailScreen`

**Files:**
- Modify: `feature/community/src/commonMain/kotlin/com/app/community/feature/community/presentation/CommunityDetailScreenModel.kt`
- Modify: `feature/community/src/commonMain/kotlin/com/app/community/feature/community/presentation/CommunityDetailScreen.kt`

- [ ] **Step 1: Añadir estado `pendingRequestsCount` al ScreenModel**

En `CommunityDetailScreenModel.kt` añadir en la state/data class principal:

```kotlin
val pendingRequestsCount: Int = 0,
```

Y en el `init { }` o `load()` después de cargar la comunidad + rol, si el usuario es admin y `community.visibility == CommunityVisibility.PUBLIC_APPROVAL`:

```kotlin
if (isAdmin && community.visibility == CommunityVisibility.PUBLIC_APPROVAL) {
    when (val r = communityRepository.getPendingJoinRequests(communityId)) {
        is AppResult.Success -> /* update state con r.data.size */
        is AppResult.Error -> { /* ignorar silenciosamente */ }
    }
}
```

**Nota:** la estructura exacta del ScreenModel actual hay que leerla. Adaptar las llamadas de actualización de state según el patrón existente (`_uiState.update { ... }` o similar).

- [ ] **Step 2: Añadir botón/badge en la UI**

En `CommunityDetailScreen.kt`, dentro del área que muestra acciones de admin (detectar la zona actual inspeccionando el archivo), añadir:

```kotlin
if (isAdmin && pendingRequestsCount > 0) {
    Spacer(Modifier.height(AgoraSpacing.md))
    AssistChip(
        onClick = { navigator.push(JoinRequestsScreen(communityId)) },
        label = {
            Text(
                stringResource(
                    Res.string.community_pending_requests_count,
                    pendingRequestsCount
                )
            )
        },
        leadingIcon = {
            BadgedBox(badge = { Badge { Text("$pendingRequestsCount") } }) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
            }
        },
    )
}
```

Añadir imports: `BadgedBox`, `Badge`, `AssistChip`, `Icons.Default.PersonAdd`, `JoinRequestsScreen`.

Si `PersonAdd` no está disponible en la versión de Material Icons que usa el proyecto, usar `Icons.Default.Group` o `Icons.AutoMirrored.Filled.List`.

- [ ] **Step 3: Build**

Run: `./gradlew :feature:community:compileKotlinMetadata :composeApp:assembleDebug`
Expected: sin errores.

---

### Task 22: Commit fase 5

- [ ] **Step 1: Commit**

```bash
git add feature/community composeApp
git commit -m "feat(community): JoinRequestsScreen + pending badge in detail"
```

---

## Fase 6 — Verificación end-to-end

### Task 23: Aplicar migración y testear manualmente en Android

- [ ] **Step 1: Aplicar migración al Supabase de desarrollo**

Según workflow del proyecto. Si hay Supabase CLI:
```
supabase db push
```
O manualmente copiar `014_public_communities_and_tags.sql` al dashboard SQL editor.

Expected: sin errores. Tablas `tags`, `community_tags`, `community_join_requests` creadas. 15 tags poblados.

- [ ] **Step 2: Build Android**

Run: `./gradlew :composeApp:installDebug`
Expected: build exitoso, app instalada.

- [ ] **Step 3: Checks funcionales (seguir en orden)**

1. **Login** con usuario existente.
2. **Crear comunidad "Test Pública Abierta"** con visibility `public_open` + 2 tags. Verificar que se crea sin error y aparece en "Mis comunidades".
3. **Crear comunidad "Test con Aprobación"** con visibility `public_approval` + 3 tags.
4. **Validación tags**: intentar seleccionar una 4ª tag → debe quedar deshabilitada.
5. **SQL check**: en Supabase dashboard verificar `SELECT visibility, tags FROM communities ...`.
6. **Solicitar unión (otro user)**: con segundo user, llamar directo a RPC `request_to_join_community` con la comunidad `public_approval`. Verificar:
   - Respuesta `{status: "pending", request_id: ...}`
   - Fila en `community_join_requests` con `status='pending'`
   - Notificación `join_request_received` para el admin en tabla `notifications`
7. **Ver solicitudes (admin)**: abrir "Test con Aprobación" en CommunityDetailScreen → ver el badge "1 pendiente" → tap → ver tarjeta con el solicitante.
8. **Aprobar**: tap "Aprobar". Verificar:
   - Request status=`approved` en BD
   - User aparece en `community_members`
   - Notificación `join_request_approved` para el user
   - La tarjeta desaparece de la lista
9. **Repetir con Rechazar** (nuevo request de otro user).
10. **Cancelar (user)**: con segundo user + tercera comunidad, crear request pending → llamar `cancel_join_request`. Verificar status=`cancelled`.
11. **Invite code bypass**: crear request pending en comunidad `public_approval` → usar invite code. Verificar join directo + request status=`cancelled`.
12. **RLS invite_code leak**: con user no-miembro ejecutar desde cliente `supabase.from('communities').select('invite_code').eq('id', <public_id>).single()`. Debería devolver el código (porque la policy "Public communities preview" es permisiva en columnas). **Documentar como follow-up**: migrar a vista sin `invite_code` cuando sea prioridad (antes de sub-proyecto B).

- [ ] **Step 4: Si algo falla, fix inline y re-verificar. Cuando todo pase, continuar.**

- [ ] **Step 5: Verificar que `search_public_communities` funciona**

Desde Supabase SQL editor:
```sql
SELECT * FROM search_public_communities(NULL, NULL, 10, 0);
SELECT * FROM search_public_communities('Test', NULL, 10, 0);
SELECT * FROM search_public_communities(NULL, ARRAY[(SELECT id FROM tags WHERE slug='deporte')]::uuid[], 10, 0);
```
Expected: resultados coherentes; sin `invite_code` en el JSON.

---

## Self-Review Checklist

Antes de cerrar:

- [ ] Cada task tiene archivos con rutas absolutas/relativas correctas
- [ ] Cada step de código tiene el código completo (sin "añadir validación")
- [ ] Firmas de métodos entre tasks son consistentes (`searchPublicCommunities`, `requestToJoinCommunity`, etc.)
- [ ] La migración SQL está dentro de `BEGIN; ... COMMIT;`
- [ ] Los 3 nuevos tipos de notificación están en el check constraint (Step 8 de Task 2) **Y** en el enum Kotlin (Task 7)
- [ ] `join_community_by_invite` se modifica y queda con `CREATE OR REPLACE` para sobreescribir
- [ ] Default de `visibility` es `private` en todas las comunidades existentes
- [ ] El follow-up de "RLS expone `invite_code` en comunidades públicas" está documentado en Task 23 Step 3 check 12

## Follow-ups explícitos (no en scope de este plan)

1. **RLS tight-column**: migrar policy `Public communities preview` a vista materializada `public_communities_view` que excluya `invite_code`. Hacer antes de lanzar sub-proyecto B si hay miedo a scraping.
2. **Test infrastructure**: añadir commonTest en `:core:data` con mocks de Postgrest, o integration tests contra un Supabase de test.
3. **Sub-proyecto B**: pantalla Explorar + preview — consume las 2 RPCs nuevas ya dejadas listas aquí.
4. **Profile reputation**: opcional, para el futuro: campo `reliability_score` en profiles cuando existan check-ins (Fase 2 del roadmap general).
