-- =============================================================
-- 027: Allow community members to view guest profiles
--
-- The existing RLS on `profiles` only allows viewing profiles
-- of users who are community_members. Anonymous guest users are
-- NOT community members, so after approval their display_name
-- (updated by approve_guest_request) is invisible to admins.
-- =============================================================

CREATE POLICY "Community members can view guest profiles"
    ON profiles FOR SELECT
    TO authenticated
    USING (id IN (
        SELECT s.reserved_by FROM slots s
        JOIN activities a ON a.id = s.activity_id
        JOIN community_members cm ON cm.community_id = a.community_id
        WHERE cm.user_id = auth.uid()
          AND s.is_guest = true
          AND s.reserved_by IS NOT NULL
    ));
