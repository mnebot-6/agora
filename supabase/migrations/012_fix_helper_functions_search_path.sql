-- ============================================================================
-- Migration 012: Add SET search_path = public to helper functions
--
-- Same fix as migration 010 for handle_new_user. Without explicit search_path,
-- SECURITY DEFINER functions may not resolve table names correctly.
-- ============================================================================

CREATE OR REPLACE FUNCTION get_my_community_ids()
RETURNS SETOF UUID AS $$
    SELECT community_id FROM public.community_members WHERE user_id = auth.uid();
$$ LANGUAGE sql SECURITY DEFINER STABLE SET search_path = public;

CREATE OR REPLACE FUNCTION get_my_admin_community_ids()
RETURNS SETOF UUID AS $$
    SELECT community_id FROM public.community_members WHERE user_id = auth.uid() AND role = 'admin';
$$ LANGUAGE sql SECURITY DEFINER STABLE SET search_path = public;
