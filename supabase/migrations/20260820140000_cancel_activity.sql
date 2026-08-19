-- ============================================================================
-- Fase 5: cancelar una actividad devolviendo el dinero.
--
-- Lo que paso por Stripe se devuelve solo. Lo que se cobro a mano (Bizum,
-- efectivo) Agora no puede devolverlo: nunca vio ese dinero. Asi que se marca
-- como deuda y se le da al admin la lista nominal de a quien pagar.
--
-- Los reembolsos NO se ejecutan aqui: se marcan y los hace el barrido. Cancelar
-- una actividad con 20 pagos es asi una transaccion rapida en vez de 20
-- llamadas HTTP dentro de una peticion, y los reintentos salen gratis.
-- ============================================================================

BEGIN;

-- ---------- Vista previa (solo lectura, para el dialogo de confirmacion) ----

CREATE OR REPLACE FUNCTION public.activity_cancellation_preview(p_activity_id uuid)
    RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_activity RECORD;
    v_auto_count integer;
    v_auto_cents integer;
    v_manual jsonb;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'not_authenticated';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = p_activity_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'activity_not_found';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_user_id AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'not_admin';
    END IF;

    SELECT count(*), coalesce(sum(amount_cents), 0)
    INTO v_auto_count, v_auto_cents
    FROM payments
    WHERE activity_id = p_activity_id
      AND method = 'stripe'
      AND status IN ('succeeded', 'awaiting_substitute');

    -- Los manuales van con nombre: el admin tiene que saber A QUIEN paga.
    SELECT coalesce(jsonb_agg(jsonb_build_object(
               'name', coalesce(pr.display_name, 'Sin nombre'),
               'amount_cents', p.amount_cents
           ) ORDER BY pr.display_name), '[]'::jsonb)
    INTO v_manual
    FROM payments p
    LEFT JOIN profiles pr ON pr.id = p.user_id
    WHERE p.activity_id = p_activity_id
      AND p.method = 'manual'
      AND p.status IN ('succeeded', 'awaiting_substitute');

    RETURN jsonb_build_object(
        'auto_count', v_auto_count,
        'auto_cents', v_auto_cents,
        'manual', v_manual
    );
END;
$$;

-- ---------- Cancelar ---------------------------------------------------------

CREATE OR REPLACE FUNCTION public.cancel_activity(p_activity_id uuid)
    RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_activity RECORD;
    v_preview jsonb;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'not_authenticated';
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = p_activity_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'activity_not_found';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_user_id AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'not_admin';
    END IF;

    -- Se calcula ANTES de tocar los estados: despues ya no habria nada que contar.
    v_preview := activity_cancellation_preview(p_activity_id);

    UPDATE activities SET status = 'archived' WHERE id = p_activity_id;

    -- Lo cobrado por Stripe: a la cola del barrido.
    UPDATE payments
    SET status = 'refund_pending', refund_reason = 'activity_cancelled'
    WHERE activity_id = p_activity_id
      AND method = 'stripe'
      AND status IN ('succeeded', 'awaiting_substitute');

    -- Lo cobrado a mano: deuda del admin. Agora nunca vio ese dinero.
    UPDATE payments
    SET status = 'refund_owed', refund_reason = 'activity_cancelled'
    WHERE activity_id = p_activity_id
      AND method = 'manual'
      AND status IN ('succeeded', 'awaiting_substitute');

    -- Los cobros a medio pagar NO se tocan: el barrido los reconcilia contra
    -- Stripe y, si resulta que se cobraron, entran solos en refund_pending.

    -- NO se avisa aqui. El trigger handle_activity_change ya notifica al pasar de
    -- 'active' a 'archived', y ademas mejor: solo lo hace si la actividad es futura.
    -- Duplicarlo mandaba dos avisos identicos a cada participante, lo cazo el test.

    RETURN v_preview;
END;
$$;

GRANT EXECUTE ON FUNCTION public.activity_cancellation_preview(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.cancel_activity(uuid) TO authenticated;

COMMIT;
