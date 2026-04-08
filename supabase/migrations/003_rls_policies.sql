-- ============================================================================
-- Migration 003: Row Level Security Policies
-- ============================================================================

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
-- PROFILES
-- ============================================================================
CREATE POLICY "Profiles are viewable by authenticated users"
    ON profiles FOR SELECT
    TO authenticated
    USING (true);

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
    USING (
        id IN (
            SELECT community_id FROM community_members WHERE user_id = auth.uid()
        )
    );

-- Also allow viewing by invite_code (for joining)
CREATE POLICY "Communities are viewable by invite code"
    ON communities FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "Authenticated users can create communities"
    ON communities FOR INSERT
    TO authenticated
    WITH CHECK (created_by = auth.uid());

-- ============================================================================
-- COMMUNITY MEMBERS
-- ============================================================================
CREATE POLICY "Members can view their community's membership"
    ON community_members FOR SELECT
    TO authenticated
    USING (
        community_id IN (
            SELECT community_id FROM community_members WHERE user_id = auth.uid()
        )
    );

CREATE POLICY "Users can join communities"
    ON community_members FOR INSERT
    TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "Admins can update member roles"
    ON community_members FOR UPDATE
    TO authenticated
    USING (
        community_id IN (
            SELECT community_id FROM community_members
            WHERE user_id = auth.uid() AND role = 'admin'
        )
    );

CREATE POLICY "Admins can remove members"
    ON community_members FOR DELETE
    TO authenticated
    USING (
        user_id = auth.uid()
        OR community_id IN (
            SELECT community_id FROM community_members
            WHERE user_id = auth.uid() AND role = 'admin'
        )
    );

-- ============================================================================
-- ACTIVITIES
-- ============================================================================
CREATE POLICY "Activities are viewable by community members"
    ON activities FOR SELECT
    TO authenticated
    USING (
        community_id IN (
            SELECT community_id FROM community_members WHERE user_id = auth.uid()
        )
    );

CREATE POLICY "Community admins can create activities"
    ON activities FOR INSERT
    TO authenticated
    WITH CHECK (
        community_id IN (
            SELECT community_id FROM community_members
            WHERE user_id = auth.uid() AND role = 'admin'
        )
        AND created_by = auth.uid()
    );

CREATE POLICY "Community admins can update activities"
    ON activities FOR UPDATE
    TO authenticated
    USING (
        community_id IN (
            SELECT community_id FROM community_members
            WHERE user_id = auth.uid() AND role = 'admin'
        )
    );

-- ============================================================================
-- SLOT GROUPS, POSITIONS, SLOTS, SLOT_POSITIONS
-- Read-only for members; mutations via RPC functions (SECURITY DEFINER)
-- ============================================================================
CREATE POLICY "Slot groups viewable by community members"
    ON slot_groups FOR SELECT
    TO authenticated
    USING (
        activity_id IN (
            SELECT a.id FROM activities a
            JOIN community_members cm ON cm.community_id = a.community_id
            WHERE cm.user_id = auth.uid()
        )
    );

CREATE POLICY "Positions viewable by community members"
    ON positions FOR SELECT
    TO authenticated
    USING (
        activity_id IN (
            SELECT a.id FROM activities a
            JOIN community_members cm ON cm.community_id = a.community_id
            WHERE cm.user_id = auth.uid()
        )
    );

CREATE POLICY "Slots viewable by community members"
    ON slots FOR SELECT
    TO authenticated
    USING (
        activity_id IN (
            SELECT a.id FROM activities a
            JOIN community_members cm ON cm.community_id = a.community_id
            WHERE cm.user_id = auth.uid()
        )
    );

CREATE POLICY "Slot positions viewable by community members"
    ON slot_positions FOR SELECT
    TO authenticated
    USING (
        slot_id IN (
            SELECT s.id FROM slots s
            JOIN activities a ON a.id = s.activity_id
            JOIN community_members cm ON cm.community_id = a.community_id
            WHERE cm.user_id = auth.uid()
        )
    );

-- Admin INSERT policies for slot creation (needed for activity setup)
CREATE POLICY "Admins can create slot groups"
    ON slot_groups FOR INSERT
    TO authenticated
    WITH CHECK (
        activity_id IN (
            SELECT a.id FROM activities a
            JOIN community_members cm ON cm.community_id = a.community_id
            WHERE cm.user_id = auth.uid() AND cm.role = 'admin'
        )
    );

CREATE POLICY "Admins can create positions"
    ON positions FOR INSERT
    TO authenticated
    WITH CHECK (
        activity_id IN (
            SELECT a.id FROM activities a
            JOIN community_members cm ON cm.community_id = a.community_id
            WHERE cm.user_id = auth.uid() AND cm.role = 'admin'
        )
    );

CREATE POLICY "Admins can create slots"
    ON slots FOR INSERT
    TO authenticated
    WITH CHECK (
        activity_id IN (
            SELECT a.id FROM activities a
            JOIN community_members cm ON cm.community_id = a.community_id
            WHERE cm.user_id = auth.uid() AND cm.role = 'admin'
        )
    );

CREATE POLICY "Admins can create slot positions"
    ON slot_positions FOR INSERT
    TO authenticated
    WITH CHECK (
        slot_id IN (
            SELECT s.id FROM slots s
            JOIN activities a ON a.id = s.activity_id
            JOIN community_members cm ON cm.community_id = a.community_id
            WHERE cm.user_id = auth.uid() AND cm.role = 'admin'
        )
    );

-- ============================================================================
-- SUBSTITUTE QUEUE
-- ============================================================================
CREATE POLICY "Substitute queue viewable by community members"
    ON substitute_queue FOR SELECT
    TO authenticated
    USING (
        activity_id IN (
            SELECT a.id FROM activities a
            JOIN community_members cm ON cm.community_id = a.community_id
            WHERE cm.user_id = auth.uid()
        )
    );

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
