-- ============================================================================
-- Migration 007: Security Hardening for Production
-- Fixes: RPC auth.uid(), RLS policies, community enumeration, performance indexes
-- ============================================================================

-- ============================================================================
-- PHASE 1: Fix RPC functions to use auth.uid() instead of client parameters
-- ============================================================================

-- Drop old function signatures (different param count = different function in PG)
DROP FUNCTION IF EXISTS reserve_slot(UUID, UUID);
DROP FUNCTION IF EXISTS release_slot(UUID, UUID, BOOLEAN);
DROP FUNCTION IF EXISTS mark_slot_paid(UUID, UUID);
DROP FUNCTION IF EXISTS join_substitute_queue(UUID, UUID, UUID);
DROP FUNCTION IF EXISTS leave_substitute_queue(UUID, UUID, UUID);
DROP FUNCTION IF EXISTS get_upcoming_activities(UUID);

-- 1. RESERVE SLOT — uses auth.uid() instead of p_user_id
CREATE OR REPLACE FUNCTION reserve_slot(p_slot_id UUID)
RETURNS BOOLEAN AS $$
DECLARE
    v_slot RECORD;
    v_activity RECORD;
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF v_slot IS NULL THEN
        RAISE EXCEPTION 'Slot not found';
    END IF;

    IF v_slot.status != 'available' THEN
        RETURN FALSE;
    END IF;

    -- Verify user is a member of the activity's community
    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;
    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id AND user_id = v_user_id
    ) THEN
        RAISE EXCEPTION 'User is not a member of this community';
    END IF;

    UPDATE slots
    SET status = 'reserved', reserved_by = v_user_id, reserved_at = now()
    WHERE id = p_slot_id;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 2. RELEASE SLOT — uses auth.uid(), queries admin status from DB
CREATE OR REPLACE FUNCTION release_slot(p_slot_id UUID)
RETURNS BOOLEAN AS $$
DECLARE
    v_slot RECORD;
    v_activity RECORD;
    v_user_id UUID := auth.uid();
    v_is_admin BOOLEAN;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF v_slot IS NULL THEN
        RAISE EXCEPTION 'Slot not found';
    END IF;

    IF v_slot.status = 'available' THEN
        RETURN FALSE;
    END IF;

    -- Query admin status from DB instead of trusting client
    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;
    SELECT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_user_id
          AND role = 'admin'
    ) INTO v_is_admin;

    -- Permission check
    IF v_slot.status = 'paid' THEN
        IF v_slot.reserved_by != v_user_id THEN
            RAISE EXCEPTION 'Only the user who reserved this slot can release a paid reservation';
        END IF;
    ELSIF v_slot.status = 'reserved' THEN
        IF v_slot.reserved_by != v_user_id AND NOT v_is_admin THEN
            RAISE EXCEPTION 'Only the reserved user or an admin can release this slot';
        END IF;
    END IF;

    UPDATE slots
    SET status = 'available', reserved_by = NULL, reserved_at = NULL
    WHERE id = p_slot_id;

    PERFORM promote_substitute(p_slot_id, v_activity.id);

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 3. MARK SLOT PAID — uses auth.uid() instead of p_admin_user_id
CREATE OR REPLACE FUNCTION mark_slot_paid(p_slot_id UUID)
RETURNS BOOLEAN AS $$
DECLARE
    v_slot RECORD;
    v_activity RECORD;
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF v_slot IS NULL THEN
        RAISE EXCEPTION 'Slot not found';
    END IF;

    IF v_slot.status != 'reserved' THEN
        RETURN FALSE;
    END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;
    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = v_user_id
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can mark slots as paid';
    END IF;

    UPDATE slots SET status = 'paid' WHERE id = p_slot_id;
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 4. JOIN SUBSTITUTE QUEUE — uses auth.uid(), validates community membership
CREATE OR REPLACE FUNCTION join_substitute_queue(
    p_activity_id UUID,
    p_position_id UUID DEFAULT NULL
)
RETURNS VOID AS $$
DECLARE
    v_activity RECORD;
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    -- Validate that the activity exists and user is a community member
    SELECT * INTO v_activity FROM activities WHERE id = p_activity_id;
    IF v_activity IS NULL THEN
        RAISE EXCEPTION 'Activity not found';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id AND user_id = v_user_id
    ) THEN
        RAISE EXCEPTION 'User is not a member of this community';
    END IF;

    INSERT INTO substitute_queue (activity_id, user_id, position_id)
    VALUES (p_activity_id, v_user_id, p_position_id)
    ON CONFLICT (activity_id, user_id, position_id) DO NOTHING;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 5. LEAVE SUBSTITUTE QUEUE — uses auth.uid()
CREATE OR REPLACE FUNCTION leave_substitute_queue(
    p_activity_id UUID,
    p_position_id UUID DEFAULT NULL
)
RETURNS VOID AS $$
DECLARE
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF p_position_id IS NULL THEN
        DELETE FROM substitute_queue
        WHERE activity_id = p_activity_id
          AND user_id = v_user_id
          AND position_id IS NULL;
    ELSE
        DELETE FROM substitute_queue
        WHERE activity_id = p_activity_id
          AND user_id = v_user_id
          AND position_id = p_position_id;
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 6. GET UPCOMING ACTIVITIES — uses auth.uid()
CREATE OR REPLACE FUNCTION get_upcoming_activities()
RETURNS SETOF activities AS $$
DECLARE
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    RETURN QUERY
    SELECT a.*
    FROM activities a
    INNER JOIN community_members cm ON cm.community_id = a.community_id
    WHERE cm.user_id = v_user_id
      AND a.status = 'active'
      AND a.datetime >= now()
    ORDER BY a.datetime ASC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- PHASE 2: Protect fcm_token in profiles
-- ============================================================================

-- Drop the overly permissive policy
DROP POLICY IF EXISTS "Profiles are viewable by authenticated users" ON profiles;

-- Users can view their own full profile (including fcm_token)
CREATE POLICY "Users can view own profile"
    ON profiles FOR SELECT TO authenticated
    USING (id = auth.uid());

-- Users can view community members' profiles (fcm_token is exposed but only to
-- fellow community members, not all authenticated users)
CREATE POLICY "Users can view community member profiles"
    ON profiles FOR SELECT TO authenticated
    USING (id IN (
        SELECT cm2.user_id FROM community_members cm2
        WHERE cm2.community_id IN (SELECT get_my_community_ids())
    ));

-- ============================================================================
-- PHASE 3: Eliminate community enumeration
-- ============================================================================

-- Drop the policy that allows ANY authenticated user to see ALL communities
DROP POLICY IF EXISTS "Communities are viewable by invite code" ON communities;

-- New RPC function for joining by invite code (SECURITY DEFINER bypasses RLS)
CREATE OR REPLACE FUNCTION join_community_by_invite(p_invite_code TEXT)
RETURNS communities AS $$
DECLARE
    v_community communities%ROWTYPE;
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_community FROM communities WHERE invite_code = p_invite_code;
    IF v_community IS NULL THEN
        RAISE EXCEPTION 'Invalid invite code';
    END IF;

    -- Check if already a member
    IF EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_community.id AND user_id = v_user_id
    ) THEN
        RAISE EXCEPTION 'Already a member of this community';
    END IF;

    -- Insert as member
    INSERT INTO community_members (community_id, user_id, role)
    VALUES (v_community.id, v_user_id, 'user');

    RETURN v_community;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- PHASE 4: Missing RLS policies
-- ============================================================================

-- Activities: admins can delete
CREATE POLICY "Community admins can delete activities"
    ON activities FOR DELETE TO authenticated
    USING (community_id IN (SELECT get_my_admin_community_ids()));

-- Notifications: users can delete their own
CREATE POLICY "Users can delete their own notifications"
    ON notifications FOR DELETE TO authenticated
    USING (user_id = auth.uid());

-- Slot groups: admins can update and delete
CREATE POLICY "Admins can update slot groups"
    ON slot_groups FOR UPDATE TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));

CREATE POLICY "Admins can delete slot groups"
    ON slot_groups FOR DELETE TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));

-- Positions: admins can update and delete
CREATE POLICY "Admins can update positions"
    ON positions FOR UPDATE TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));

CREATE POLICY "Admins can delete positions"
    ON positions FOR DELETE TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));

-- Slot positions: admins can delete
CREATE POLICY "Admins can delete slot positions"
    ON slot_positions FOR DELETE TO authenticated
    USING (slot_id IN (
        SELECT s.id FROM slots s
        WHERE s.activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids()))
    ));

-- Slot templates: users can update their own
CREATE POLICY "Users can update own templates"
    ON slot_templates FOR UPDATE TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

-- ============================================================================
-- PHASE 5: Performance indexes (IF NOT EXISTS for idempotency)
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_community_members_user_id ON community_members(user_id);
CREATE INDEX IF NOT EXISTS idx_community_members_community_id ON community_members(community_id);
CREATE INDEX IF NOT EXISTS idx_activities_community_id ON activities(community_id);
CREATE INDEX IF NOT EXISTS idx_activities_datetime ON activities(datetime);
CREATE INDEX IF NOT EXISTS idx_slots_activity_id ON slots(activity_id);
CREATE INDEX IF NOT EXISTS idx_slot_positions_slot_id ON slot_positions(slot_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_substitute_queue_activity_id ON substitute_queue(activity_id);

-- ============================================================================
-- PHASE 6: Invite code format constraint
-- ============================================================================

ALTER TABLE communities ADD CONSTRAINT invite_code_format
    CHECK (length(invite_code) = 8 AND invite_code ~ '^[A-Z0-9]+$');
