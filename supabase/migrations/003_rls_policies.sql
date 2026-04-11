-- ============================================================================
-- Migration 003: Row Level Security Policies
-- ============================================================================

-- Helper functions to avoid infinite recursion on community_members policies
-- (SECURITY DEFINER bypasses RLS, preventing self-referencing loops)
CREATE OR REPLACE FUNCTION get_my_community_ids()
RETURNS SETOF UUID AS $$
    SELECT community_id FROM community_members WHERE user_id = auth.uid();
$$ LANGUAGE sql SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION get_my_admin_community_ids()
RETURNS SETOF UUID AS $$
    SELECT community_id FROM community_members WHERE user_id = auth.uid() AND role = 'admin';
$$ LANGUAGE sql SECURITY DEFINER STABLE;

-- Enable RLS on all tables
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE communities ENABLE ROW LEVEL SECURITY;
ALTER TABLE community_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE activities ENABLE ROW LEVEL SECURITY;
ALTER TABLE slot_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE positions ENABLE ROW LEVEL SECURITY;
ALTER TABLE slots ENABLE ROW LEVEL SECURITY;
ALTER TABLE slot_positions ENABLE ROW LEVEL SECURITY;
ALTER TABLE substitute_queue ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- PROFILES (protected: only own profile + community members visible)
-- ============================================================================
CREATE POLICY "Users can view own profile"
    ON profiles FOR SELECT
    TO authenticated
    USING (id = auth.uid());

CREATE POLICY "Users can view community member profiles"
    ON profiles FOR SELECT
    TO authenticated
    USING (id IN (
        SELECT cm2.user_id FROM community_members cm2
        WHERE cm2.community_id IN (SELECT get_my_community_ids())
    ));

CREATE POLICY "Users can update their own profile"
    ON profiles FOR UPDATE
    TO authenticated
    USING (id = auth.uid())
    WITH CHECK (id = auth.uid());

-- ============================================================================
-- COMMUNITIES
-- ============================================================================
CREATE POLICY "Communities are viewable by members"
    ON communities FOR SELECT
    TO authenticated
    USING (id IN (SELECT get_my_community_ids()));

-- NOTE: No global SELECT policy — joining by invite uses RPC join_community_by_invite()

CREATE POLICY "Authenticated users can create communities"
    ON communities FOR INSERT
    TO authenticated
    WITH CHECK (created_by = auth.uid());

CREATE POLICY "Community admins can update communities"
    ON communities FOR UPDATE
    TO authenticated
    USING (id IN (SELECT get_my_admin_community_ids()))
    WITH CHECK (id IN (SELECT get_my_admin_community_ids()));

CREATE POLICY "Community creator can delete communities"
    ON communities FOR DELETE
    TO authenticated
    USING (created_by = auth.uid());

-- ============================================================================
-- COMMUNITY MEMBERS (use helper functions to avoid self-referencing recursion)
-- ============================================================================
CREATE POLICY "Members can view their community's membership"
    ON community_members FOR SELECT
    TO authenticated
    USING (community_id IN (SELECT get_my_community_ids()));

CREATE POLICY "Users can join communities"
    ON community_members FOR INSERT
    TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "Admins can update member roles"
    ON community_members FOR UPDATE
    TO authenticated
    USING (community_id IN (SELECT get_my_admin_community_ids()));

CREATE POLICY "Admins can remove members"
    ON community_members FOR DELETE
    TO authenticated
    USING (
        user_id = auth.uid()
        OR community_id IN (SELECT get_my_admin_community_ids())
    );

-- ============================================================================
-- ACTIVITIES
-- ============================================================================
CREATE POLICY "Activities are viewable by community members"
    ON activities FOR SELECT
    TO authenticated
    USING (community_id IN (SELECT get_my_community_ids()));

CREATE POLICY "Community admins can create activities"
    ON activities FOR INSERT
    TO authenticated
    WITH CHECK (
        community_id IN (SELECT get_my_admin_community_ids())
        AND created_by = auth.uid()
    );

CREATE POLICY "Community admins can update activities"
    ON activities FOR UPDATE
    TO authenticated
    USING (community_id IN (SELECT get_my_admin_community_ids()));

CREATE POLICY "Community admins can delete activities"
    ON activities FOR DELETE
    TO authenticated
    USING (community_id IN (SELECT get_my_admin_community_ids()));

-- ============================================================================
-- SLOT GROUPS, POSITIONS, SLOTS, SLOT_POSITIONS
-- Read for members; INSERT/UPDATE/DELETE for admins; slot state via RPC
-- ============================================================================
CREATE POLICY "Slot groups viewable by community members"
    ON slot_groups FOR SELECT
    TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_community_ids())));

CREATE POLICY "Positions viewable by community members"
    ON positions FOR SELECT
    TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_community_ids())));

CREATE POLICY "Slots viewable by community members"
    ON slots FOR SELECT
    TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_community_ids())));

CREATE POLICY "Slot positions viewable by community members"
    ON slot_positions FOR SELECT
    TO authenticated
    USING (slot_id IN (
        SELECT s.id FROM slots s
        WHERE s.activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_community_ids()))
    ));

-- Admin INSERT/UPDATE/DELETE policies for slot management
CREATE POLICY "Admins can create slot groups"
    ON slot_groups FOR INSERT
    TO authenticated
    WITH CHECK (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));
CREATE POLICY "Admins can update slot groups"
    ON slot_groups FOR UPDATE
    TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));
CREATE POLICY "Admins can delete slot groups"
    ON slot_groups FOR DELETE
    TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));

CREATE POLICY "Admins can create positions"
    ON positions FOR INSERT
    TO authenticated
    WITH CHECK (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));
CREATE POLICY "Admins can update positions"
    ON positions FOR UPDATE
    TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));
CREATE POLICY "Admins can delete positions"
    ON positions FOR DELETE
    TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));

CREATE POLICY "Admins can create slots"
    ON slots FOR INSERT
    TO authenticated
    WITH CHECK (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));

CREATE POLICY "Admins can create slot positions"
    ON slot_positions FOR INSERT
    TO authenticated
    WITH CHECK (slot_id IN (
        SELECT s.id FROM slots s
        WHERE s.activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids()))
    ));
CREATE POLICY "Admins can delete slot positions"
    ON slot_positions FOR DELETE
    TO authenticated
    USING (slot_id IN (
        SELECT s.id FROM slots s
        WHERE s.activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids()))
    ));

-- ============================================================================
-- SUBSTITUTE QUEUE
-- ============================================================================
CREATE POLICY "Substitute queue viewable by community members"
    ON substitute_queue FOR SELECT
    TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_community_ids())));

CREATE POLICY "Users can manage their own substitute entries"
    ON substitute_queue FOR INSERT
    TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "Users can remove their own substitute entries"
    ON substitute_queue FOR DELETE
    TO authenticated
    USING (user_id = auth.uid());

-- ============================================================================
-- NOTIFICATIONS
-- ============================================================================
CREATE POLICY "Users can view their own notifications"
    ON notifications FOR SELECT
    TO authenticated
    USING (user_id = auth.uid());

CREATE POLICY "Users can update their own notifications"
    ON notifications FOR UPDATE
    TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "Users can delete their own notifications"
    ON notifications FOR DELETE
    TO authenticated
    USING (user_id = auth.uid());
