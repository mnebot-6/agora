-- ============================================================================
-- Icono configurable por comunidad.
-- Aditivo: una app antigua ignora la columna, y las comunidades existentes
-- se quedan en NULL (la app cae al color determinista por id).
-- ============================================================================

BEGIN;

ALTER TABLE "public"."communities" ADD COLUMN IF NOT EXISTS "icon_key" "text";

-- Las dos funciones de abajo enumeran columnas a mano, asi que sin redefinirlas
-- el icono no llegaria ni a explorar ni a la preview de invitacion. Copiadas
-- integras del baseline (firma identica) con una sola linea anadida.

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
        'icon_key', v_community.icon_key,
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
            c.icon_key,
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

COMMIT;
