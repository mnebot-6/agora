-- ============================================================================
-- El invitado ve cuanto cuesta la actividad.
--
-- get_activity_guest_preview ya devolvia cost_description -el "como se paga"-,
-- pero no el importe, asi que un invitado abria el enlace de una actividad de
-- 6,50 EUR y se apuntaba sin enterarse de que habia que pagar. Los invitados van
-- SIEMPRE por pago externo, sea cual sea el modo de la actividad, y lo que
-- define ese modo es justamente decirle a la gente cuanto y como pagar.
--
-- Cuerpo identico al del baseline salvo la linea de price_cents. Misma firma
-- exacta: CREATE OR REPLACE con otra distinta crearia una funcion nueva y
-- dejaria la vieja viva.
--
-- Diseno: docs/superpowers/specs/2026-08-27-modos-de-pago-design.md
-- ============================================================================

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
            'price_cents', v_activity.price_cents,
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

