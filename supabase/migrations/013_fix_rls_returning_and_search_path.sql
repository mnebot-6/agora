-- ============================================================================
-- Migration 013: Fix RLS RETURNING issue + helper functions search_path
--
-- Bug 1: Creating communities fails for ALL users because the INSERT uses
--   Prefer: return=representation (from Kotlin SDK's `{ select() }`), which
--   makes PostgreSQL evaluate the SELECT policy on the RETURNING clause.
--   The SELECT policy requires membership via get_my_community_ids(), but the
--   AFTER INSERT trigger (handle_new_community) that creates the membership
--   hasn't committed yet due to snapshot isolation.
--   Fix: Add OR created_by = auth.uid() to the communities SELECT policy.
--
-- Bug 2: Creating activities fails because the helper functions
--   get_my_community_ids() and get_my_admin_community_ids() were defined
--   without SET search_path = public in migration 011. As SECURITY DEFINER
--   functions, they may not resolve unqualified table names correctly.
--   Fix: Recreate both functions with SET search_path = public and
--   explicit public.community_members references.
--
-- This migration absorbs the content of migration 012 (not yet applied).
-- ============================================================================

-- ============================================================================
-- PHASE 1: Fix helper functions (search_path + explicit schema)
-- ============================================================================

CREATE OR REPLACE FUNCTION get_my_community_ids()
RETURNS SETOF UUID AS $$
    SELECT community_id FROM public.community_members WHERE user_id = auth.uid();
$$ LANGUAGE sql SECURITY DEFINER STABLE SET search_path = public;

CREATE OR REPLACE FUNCTION get_my_admin_community_ids()
RETURNS SETOF UUID AS $$
    SELECT community_id FROM public.community_members WHERE user_id = auth.uid() AND role = 'admin';
$$ LANGUAGE sql SECURITY DEFINER STABLE SET search_path = public;

-- ============================================================================
-- PHASE 2: Fix communities SELECT policy for RETURNING compatibility
-- ============================================================================

DROP POLICY IF EXISTS "Communities are viewable by members" ON communities;
CREATE POLICY "Communities are viewable by members"
    ON communities FOR SELECT TO authenticated
    USING (id IN (SELECT get_my_community_ids()) OR created_by = auth.uid());
