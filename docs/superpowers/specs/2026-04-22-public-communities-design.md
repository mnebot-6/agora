# Public Communities + Tags (Sub-project A of Phase 1) — Design Spec

**Status:** Approved
**Date:** 2026-04-22
**Scope:** Data model + RPCs + RLS + minimal admin UI to enable community discovery. UI de "Explorar" y "Preview" van en sub-proyecto B.

---

## Context

Agora hoy solo permite unirse a comunidades mediante un código de invitación (deep link). Esto es el cuello de botella nº1 para el crecimiento: no hay descubrimiento orgánico. Este sub-proyecto prepara la capa de datos y de servidor para que la UI de descubrimiento (sub-proyecto B) tenga qué mostrar.

La prioridad estratégica acordada (ver `~/.claude/plans/haz-una-revisi-n-entera-sunny-sun.md`) es Crecimiento → Core depth → Engagement, y dentro de crecimiento las features 1.1 y 1.2 del roadmap son el fundamento.

El resultado: comunidades con visibilidad configurable (pública abierta, pública con aprobación, privada), categorizadas por tags de un catálogo curado en BD, con flujo completo de solicitudes de join aprobadas por admin cuando la comunidad lo requiera.

---

## Decisiones acordadas

| Decisión | Valor |
|---|---|
| Estados de visibilidad | `public_open`, `public_approval`, `private` (enum) |
| Default para comunidades existentes | `private` |
| Tags | Catálogo en BD (tabla `tags`), bilingüe es/en |
| Tags por comunidad | Máx 3 (enforced por trigger) |
| Visibilidad para no-miembros | Solo metadata básica (name, description, image, tags, counts) |
| Mensaje en solicitud de join | Opcional, 300 chars |
| Notificaciones de join | Nuevos tipos: `JOIN_REQUEST_RECEIVED`, `JOIN_REQUEST_APPROVED`, `JOIN_REQUEST_REJECTED` |
| Destinatarios de `JOIN_REQUEST_RECEIVED` | Todos los admins de la comunidad |
| Código de invitación | Siempre funciona (bypass de approval) |

---

## Data Model

### 1. Enum `community_visibility`

```sql
CREATE TYPE community_visibility AS ENUM (
  'public_open',      -- aparece en Explorar, join inmediato
  'public_approval',  -- aparece en Explorar, admin aprueba cada solicitud
  'private'           -- oculta; solo por invite_code
);
```

### 2. Columna `communities.visibility`

```sql
ALTER TABLE communities
  ADD COLUMN visibility community_visibility NOT NULL DEFAULT 'private';
CREATE INDEX idx_communities_visibility ON communities(visibility)
  WHERE visibility IN ('public_open', 'public_approval');
```

### 3. Catálogo `tags`

```sql
CREATE TABLE tags (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  slug text UNIQUE NOT NULL,
  name_es text NOT NULL,
  name_en text NOT NULL,
  icon text,                                    -- emoji o material icon name
  sort_order int NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);
```

**Seed inicial (15 categorías):**

| slug | name_es | name_en | icon | sort_order |
|---|---|---|---|---|
| deporte | Deporte | Sports | ⚽ | 10 |
| voluntariado | Voluntariado | Volunteering | 🤝 | 20 |
| educacion | Educación | Education | 📚 | 30 |
| musica | Música | Music | 🎵 | 40 |
| gaming | Gaming | Gaming | 🎮 | 50 |
| arte | Arte | Art | 🎨 | 60 |
| tecnologia | Tecnología | Technology | 💻 | 70 |
| salud | Salud y bienestar | Health & Wellness | 🧘 | 80 |
| idiomas | Idiomas | Languages | 🗣️ | 90 |
| naturaleza | Naturaleza | Nature | 🌳 | 100 |
| profesional | Profesional | Professional | 💼 | 110 |
| familia | Familia | Family | 👨‍👩‍👧 | 120 |
| social | Social | Social | 🎉 | 130 |
| espiritual | Espiritual | Spiritual | 🕊️ | 140 |
| otros | Otros | Other | ✨ | 9999 |

### 4. Tabla N:M `community_tags`

```sql
CREATE TABLE community_tags (
  community_id uuid NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
  tag_id uuid NOT NULL REFERENCES tags(id) ON DELETE RESTRICT,
  PRIMARY KEY (community_id, tag_id)
);
CREATE INDEX idx_community_tags_tag ON community_tags(tag_id);

-- Trigger: max 3 tags por comunidad
CREATE OR REPLACE FUNCTION check_max_tags_per_community() RETURNS trigger AS $$
BEGIN
  IF (SELECT count(*) FROM community_tags WHERE community_id = NEW.community_id) >= 3 THEN
    RAISE EXCEPTION 'Máximo 3 tags por comunidad';
  END IF;
  RETURN NEW;
END; $$ LANGUAGE plpgsql;

CREATE TRIGGER enforce_max_tags BEFORE INSERT ON community_tags
  FOR EACH ROW EXECUTE FUNCTION check_max_tags_per_community();
```

### 5. Tabla `community_join_requests`

```sql
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

-- Solo puede haber UNA solicitud pending por (community, user)
CREATE UNIQUE INDEX idx_join_requests_unique_pending
  ON community_join_requests(community_id, user_id)
  WHERE status = 'pending';

CREATE INDEX idx_join_requests_community_pending
  ON community_join_requests(community_id)
  WHERE status = 'pending';
```

### 6. Nuevos tipos en `notifications`

Ampliar el enum/type existente con:
- `JOIN_REQUEST_RECEIVED` — destinatarios: todos los admins de la comunidad. `data` jsonb: `{community_id, request_id, requester_id, requester_name}`.
- `JOIN_REQUEST_APPROVED` — destinatario: el solicitante. `data`: `{community_id, community_name}`.
- `JOIN_REQUEST_REJECTED` — destinatario: el solicitante. `data`: `{community_id, community_name}`.

---

## RLS Policies

### `communities`
- **SELECT por miembros**: política existente (ver todos los campos si eres miembro). Se mantiene.
- **SELECT público limitado**: nueva policy `communities_public_preview` que permite SELECT a cualquier autenticado cuando `visibility IN ('public_open','public_approval')`, PERO **solo se exponen campos seguros** via la RPC `search_public_communities` / `get_public_community_preview` (no vía SELECT directo que expondría `invite_code`).
  - **Implementación**: la policy permite SELECT, pero el cliente solo llama las RPCs (no hace `supabase.from('communities').select()` sobre no-miembros). Aplicación-level por convención + las RPCs usan `SECURITY DEFINER` para devolver solo columnas seguras.
  - **Alternativa más estricta (si se necesita)**: vista materializada `public_communities_preview` con solo columnas seguras, y la policy se pone sobre la vista. Dejamos esto como seguimiento si detectamos exposición de `invite_code`.

### `tags`
- SELECT público para cualquier autenticado.
- INSERT/UPDATE/DELETE bloqueado (solo via migración/admin DB).

### `community_tags`
- SELECT: mismo scope que `communities.SELECT` (si ves la comunidad, ves sus tags).
- INSERT/DELETE: solo admins de la comunidad (policy basada en `community_members.role`).

### `community_join_requests`
- SELECT:
  - El propio usuario ve sus propias solicitudes (cualquier status).
  - Admins de la comunidad ven todas las solicitudes de su comunidad.
- INSERT: solo a través de RPC (`request_to_join_community`). Policy deny directo.
- UPDATE: solo a través de RPCs (`approve_`/`reject_`/`cancel_join_request`). Policy deny directo.

---

## RPCs (server-side, `SECURITY DEFINER`)

### `search_public_communities(query text, tag_ids uuid[], limit_n int, offset_n int) returns jsonb`
- Filtros: solo `visibility IN ('public_open','public_approval')`.
- Si `query` no-null: filtro por `name ILIKE '%query%'` o FTS si se añade después.
- Si `tag_ids` no-vacío: filtro por overlap.
- Orden: por `member_count DESC` (calculado), fallback `created_at DESC`.
- Devuelve: array de `{id, name, description, image_url, visibility, tags[], member_count, activity_count_upcoming}`.
- **Excluye** `invite_code` y cualquier dato sensible.

### `get_public_community_preview(p_community_id uuid) returns jsonb`
- Valida que la comunidad existe y es pública.
- Devuelve mismo shape que un elemento de `search_public_communities` + descripción completa.
- Usado por el deep-link y por tap en Explorar (sub-proyecto B).

### `request_to_join_community(p_community_id uuid, p_message text default null) returns jsonb`
Lógica:
1. Leer visibility de la comunidad.
2. Si `private` → error (usa invite code).
3. Si `public_open` → insertar directamente en `community_members` y devolver `{status: 'joined'}`.
4. Si `public_approval`:
   - Verificar que no existe ya un `pending` del mismo user para la misma community.
   - Insertar en `community_join_requests` con status `pending`.
   - Crear notificaciones `JOIN_REQUEST_RECEIVED` para todos los admins.
   - Devolver `{status: 'pending', request_id}`.

### `approve_join_request(p_request_id uuid) returns void`
- Verifica que el caller es admin de la comunidad asociada.
- Verifica status `pending`.
- Transacción:
  - Inserta `community_members(community_id, user_id, role='user')`.
  - Marca request `status='approved'`, `resolved_at=now()`, `resolved_by=auth.uid()`.
  - Crea notificación `JOIN_REQUEST_APPROVED` para el user.

### `reject_join_request(p_request_id uuid) returns void`
- Mismo check de admin + `pending`.
- Marca `status='rejected'`, `resolved_at`, `resolved_by`.
- Crea notificación `JOIN_REQUEST_REJECTED`.

### `cancel_join_request(p_request_id uuid) returns void`
- Verifica que `user_id = auth.uid()` y status `pending`.
- Marca `status='cancelled'`, `resolved_at`.
- No genera notificación.

### Modificación de `join_by_invite_code` existente
- Añadir: si la comunidad es `public_approval` y existe un `pending` del mismo user, marcarlo `cancelled` automáticamente al hacer join directo por código (evita solicitud zombie).
- Bypass: el invite code siempre funciona, independiente de `visibility`.

---

## Cambios en modelos Kotlin (common)

### Nuevos
- `CommunityVisibility` (enum serializable): `PUBLIC_OPEN`, `PUBLIC_APPROVAL`, `PRIVATE`
- `Tag` (data class): `id`, `slug`, `nameEs`, `nameEn`, `icon`, `sortOrder`
- `CommunityJoinRequest` (data class): `id`, `communityId`, `userId`, `status`, `message`, `requestedAt`, `resolvedAt`, `resolvedBy`
- `JoinRequestStatus` (enum): `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`

### Modificados
- `Community` → añadir `visibility: CommunityVisibility`, `tags: List<Tag>`, `memberCount: Int?`, `activityCountUpcoming: Int?`
- `NotificationType` → añadir `JOIN_REQUEST_RECEIVED`, `JOIN_REQUEST_APPROVED`, `JOIN_REQUEST_REJECTED`

### Repositorios nuevos / ampliados
- `TagRepository`: `getAllTags(): List<Tag>` (cacheado en memoria; catálogo cambia poco).
- `CommunityRepository`:
  - `searchPublicCommunities(query, tagIds, limit, offset)`
  - `getPublicCommunityPreview(id)`
  - `requestToJoinCommunity(id, message?)`
  - `cancelJoinRequest(requestId)`
  - `getPendingJoinRequests(communityId)` — para admins
  - `approveJoinRequest(requestId)` / `rejectJoinRequest(requestId)`
  - `updateCommunityVisibility(id, visibility)`
  - `updateCommunityTags(id, tagIds)`

---

## Cambios de UI (mínimos — alcance de A)

### `CreateCommunityScreen`
- Añadir sección "Visibilidad" con 3 radio buttons + descripción breve de cada uno.
- Añadir sección "Categorías (1-3)" con chips seleccionables desde el catálogo de tags. Validar min 1, max 3.

### `CommunityDetailScreen` (vista admin)
- Si `visibility = 'public_approval'`: mostrar badge + botón "Solicitudes pendientes (N)".
- Al tap → nueva `JoinRequestsScreen` con lista de requests pending, cada uno con nombre/avatar del solicitante, mensaje (si hay), fecha, y 2 botones (✅ Aprobar / ❌ Rechazar).

### `CommunityDetailScreen` (vista admin, settings)
- Ampliar la sección de edición para permitir cambiar `visibility` y `tags` (reaprovechar la misma UI que CreateCommunityScreen). Si no hay una EditCommunityScreen separada, añadir botón "Editar" en el header.

### Fuera de alcance de A (va en B)
- Pantalla "Explorar".
- Pantalla de preview público.
- Estado "solicitud pendiente" en la vista de join (lo arrancamos aquí a nivel de API pero la UI va en B junto con Explorar).

---

## Plan de migración

Una migración Supabase atómica `supabase/migrations/<timestamp>_public_communities_and_tags.sql` que ejecuta en orden:

1. `CREATE TYPE community_visibility ...`
2. `ALTER TABLE communities ADD COLUMN visibility ... DEFAULT 'private'`
3. `CREATE TABLE tags ...` + seed de los 15 tags
4. `CREATE TABLE community_tags ...` + trigger max-3
5. `CREATE TABLE community_join_requests ...` + índices
6. Ampliar enum/check de `notifications.type` con los 3 nuevos valores
7. Crear las 6 RPCs nuevas
8. Modificar RPC `join_by_invite_code` existente (cancelar pending si aplica)
9. Crear policies RLS nuevas

**Rollback**: la migración tiene su correspondiente `down.sql` que hace DROP en orden inverso. Todas las comunidades existentes quedan `private` así que no hay riesgo de exposición inesperada.

---

## Verification (end-to-end)

Una vez implementado, comprobar manualmente en Android:

1. **Migración**: aplicar migración en Supabase local/staging; verificar que todas las comunidades existentes siguen accesibles como privadas.
2. **Crear comunidad pública abierta**: crear con visibility=`public_open` + 2 tags. Verificar en BD que se guardó correctamente con sus tags.
3. **Crear comunidad pública con aprobación**: igual pero con `public_approval`.
4. **Solicitar unirse (public_approval)**: con otro user, llamar a `request_to_join_community`. Verificar:
   - Aparece la request en la tabla con status `pending`.
   - Admin recibe notificación `JOIN_REQUEST_RECEIVED`.
5. **Aprobar**: admin llama `approve_join_request`. Verificar:
   - User aparece en `community_members`.
   - Request status=`approved`.
   - User recibe notificación `JOIN_REQUEST_APPROVED`.
6. **Rechazar**: repetir con otra request y rechazar. Verificar status + notificación.
7. **Cancelar**: user cancela su propia request pending. Verificar status + sin notificación.
8. **Invite code bypass**: comunidad `public_approval` + user con request pendiente. Usa invite code → join directo; request pending queda `cancelled`.
9. **RLS**: desde user no-miembro intentar `SELECT * FROM communities WHERE id=<public_id>` directo y verificar que `invite_code` no aparece (o mejor, que todo falla salvo lo previsto).
10. **Límite de tags**: intentar asignar 4 tags a una comunidad; el trigger debe rechazar.
11. **`search_public_communities`**: llamar con distintos filtros (query, tag_ids vacío/con items) y verificar que devuelve lo esperado y NO incluye `invite_code`.

Tests automatizados a nivel repositorio (KMP common) en `composeApp/src/commonTest/` cubriendo:
- `CommunityRepository.searchPublicCommunities` parsea respuesta
- `CommunityRepository.requestToJoinCommunity` maneja los 3 casos (joined/pending/error)
- `TagRepository.getAllTags` cachea correctamente

Tests de integración SQL (si existen pgTAP o similar — verificar en el repo) para los triggers y RPCs.

---

## Archivos a modificar / crear

### Backend (Supabase)
- `supabase/migrations/<ts>_public_communities_and_tags.sql` (nuevo)

### Kotlin common (modelos)
- `composeApp/src/commonMain/kotlin/.../core/model/Community.kt` (modificar)
- `composeApp/src/commonMain/kotlin/.../core/model/CommunityVisibility.kt` (nuevo)
- `composeApp/src/commonMain/kotlin/.../core/model/Tag.kt` (nuevo)
- `composeApp/src/commonMain/kotlin/.../core/model/CommunityJoinRequest.kt` (nuevo)
- `composeApp/src/commonMain/kotlin/.../core/model/Notification.kt` (modificar — añadir 3 tipos)

### Repositorios
- `composeApp/src/commonMain/kotlin/.../core/data/repository/CommunityRepository.kt` (ampliar)
- `composeApp/src/commonMain/kotlin/.../core/data/repository/TagRepository.kt` (nuevo)

### UI (mínima)
- `composeApp/src/commonMain/kotlin/.../feature/community/CreateCommunityScreen.kt` (ampliar con visibility + tags)
- `composeApp/src/commonMain/kotlin/.../feature/community/CommunityDetailScreen.kt` (badge + botón solicitudes si admin)
- `composeApp/src/commonMain/kotlin/.../feature/community/JoinRequestsScreen.kt` (nueva)
- Edición de comunidad (ampliar pantalla existente o nueva `EditCommunityScreen.kt`)

### DI (Koin)
- Registrar `TagRepository` en el módulo de data.

---

## Siguientes pasos

1. Aprobar este spec (tú).
2. Invocar `superpowers:writing-plans` para generar el plan de implementación detallado con TDD.
3. Ejecutar el plan con `superpowers:executing-plans` o subagents (Fase 1 es un bloque grande, probable que se descomponga en varios commits: migración SQL primero, luego modelos, luego repos, luego UI).
4. Verificación end-to-end según la sección anterior.
5. Al completar A, arrancar B (pantalla Explorar + preview) que ya tiene toda la infra lista.
