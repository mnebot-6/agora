-- ============================================================================
-- Migration 011: Ensure ALL RLS policies, helper functions, and triggers exist
--
-- Multiple earlier migrations (003, 004, parts of 005, 007) were not applied
-- to production. This migration idempotently recreates everything needed.
-- Uses DROP POLICY IF EXISTS + CREATE POLICY (PG has no CREATE OR REPLACE POLICY).
-- ============================================================================

-- ============================================================================
-- PHASE 1: Helper functions (CREATE OR REPLACE = idempotent)
-- ============================================================================

CREATE OR REPLACE FUNCTION get_my_community_ids()
RETURNS SETOF UUID AS $$
    SELECT community_id FROM community_members WHERE user_id = auth.uid();
$$ LANGUAGE sql SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION get_my_admin_community_ids()
RETURNS SETOF UUID AS $$
    SELECT community_id FROM community_members WHERE user_id = auth.uid() AND role = 'admin';
$$ LANGUAGE sql SECURITY DEFINER STABLE;

-- ============================================================================
-- PHASE 2: Enable RLS on all tables (idempotent)
-- ============================================================================

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
ALTER TABLE slot_templates ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- PHASE 3: All RLS Policies (DROP IF EXISTS + CREATE)
-- ============================================================================

-- ---------- PROFILES ----------

-- From 007: protected profile policies (replace old permissive ones)
DROP POLICY IF EXISTS "Profiles are viewable by authenticated users" ON profiles;
DROP POLICY IF EXISTS "Users can view own profile" ON profiles;
CREATE POLICY "Users can view own profile"
    ON profiles FOR SELECT TO authenticated
    USING (id = auth.uid());

DROP POLICY IF EXISTS "Users can view community member profiles" ON profiles;
CREATE POLICY "Users can view community member profiles"
    ON profiles FOR SELECT TO authenticated
    USING (id IN (
        SELECT cm2.user_id FROM community_members cm2
        WHERE cm2.community_id IN (SELECT get_my_community_ids())
    ));

DROP POLICY IF EXISTS "Users can update their own profile" ON profiles;
CREATE POLICY "Users can update their own profile"
    ON profiles FOR UPDATE TO authenticated
    USING (id = auth.uid())
    WITH CHECK (id = auth.uid());

-- ---------- COMMUNITIES ----------

DROP POLICY IF EXISTS "Communities are viewable by members" ON communities;
CREATE POLICY "Communities are viewable by members"
    ON communities FOR SELECT TO authenticated
    USING (id IN (SELECT get_my_community_ids()));

-- Drop old policy from 007 if it exists
DROP POLICY IF EXISTS "Communities are viewable by invite code" ON communities;

DROP POLICY IF EXISTS "Authenticated users can create communities" ON communities;
CREATE POLICY "Authenticated users can create communities"
    ON communities FOR INSERT TO authenticated
    WITH CHECK (created_by = auth.uid());

DROP POLICY IF EXISTS "Community admins can update communities" ON communities;
CREATE POLICY "Community admins can update communities"
    ON communities FOR UPDATE TO authenticated
    USING (id IN (SELECT get_my_admin_community_ids()))
    WITH CHECK (id IN (SELECT get_my_admin_community_ids()));

DROP POLICY IF EXISTS "Community creator can delete communities" ON communities;
CREATE POLICY "Community creator can delete communities"
    ON communities FOR DELETE TO authenticated
    USING (created_by = auth.uid());

-- ---------- COMMUNITY MEMBERS ----------

DROP POLICY IF EXISTS "Members can view their community's membership" ON community_members;
CREATE POLICY "Members can view their community's membership"
    ON community_members FOR SELECT TO authenticated
    USING (community_id IN (SELECT get_my_community_ids()));

DROP POLICY IF EXISTS "Users can join communities" ON community_members;
CREATE POLICY "Users can join communities"
    ON community_members FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS "Admins can update member roles" ON community_members;
CREATE POLICY "Admins can update member roles"
    ON community_members FOR UPDATE TO authenticated
    USING (community_id IN (SELECT get_my_admin_community_ids()));

DROP POLICY IF EXISTS "Admins can remove members" ON community_members;
CREATE POLICY "Admins can remove members"
    ON community_members FOR DELETE TO authenticated
    USING (
        user_id = auth.uid()
        OR community_id IN (SELECT get_my_admin_community_ids())
    );

-- ---------- ACTIVITIES ----------

DROP POLICY IF EXISTS "Activities are viewable by community members" ON activities;
CREATE POLICY "Activities are viewable by community members"
    ON activities FOR SELECT TO authenticated
    USING (community_id IN (SELECT get_my_community_ids()));

DROP POLICY IF EXISTS "Community admins can create activities" ON activities;
CREATE POLICY "Community admins can create activities"
    ON activities FOR INSERT TO authenticated
    WITH CHECK (
        community_id IN (SELECT get_my_admin_community_ids())
        AND created_by = auth.uid()
    );

DROP POLICY IF EXISTS "Community admins can update activities" ON activities;
CREATE POLICY "Community admins can update activities"
    ON activities FOR UPDATE TO authenticated
    USING (community_id IN (SELECT get_my_admin_community_ids()));

DROP POLICY IF EXISTS "Community admins can delete activities" ON activities;
CREATE POLICY "Community admins can delete activities"
    ON activities FOR DELETE TO authenticated
    USING (community_id IN (SELECT get_my_admin_community_ids()));

-- ---------- SLOT GROUPS ----------

DROP POLICY IF EXISTS "Slot groups viewable by community members" ON slot_groups;
CREATE POLICY "Slot groups viewable by community members"
    ON slot_groups FOR SELECT TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_community_ids())));

DROP POLICY IF EXISTS "Admins can create slot groups" ON slot_groups;
CREATE POLICY "Admins can create slot groups"
    ON slot_groups FOR INSERT TO authenticated
    WITH CHECK (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));

DROP POLICY IF EXISTS "Admins can update slot groups" ON slot_groups;
CREATE POLICY "Admins can update slot groups"
    ON slot_groups FOR UPDATE TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));

DROP POLICY IF EXISTS "Admins can delete slot groups" ON slot_groups;
CREATE POLICY "Admins can delete slot groups"
    ON slot_groups FOR DELETE TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));

-- ---------- POSITIONS ----------

DROP POLICY IF EXISTS "Positions viewable by community members" ON positions;
CREATE POLICY "Positions viewable by community members"
    ON positions FOR SELECT TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_community_ids())));

DROP POLICY IF EXISTS "Admins can create positions" ON positions;
CREATE POLICY "Admins can create positions"
    ON positions FOR INSERT TO authenticated
    WITH CHECK (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));

DROP POLICY IF EXISTS "Admins can update positions" ON positions;
CREATE POLICY "Admins can update positions"
    ON positions FOR UPDATE TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));

DROP POLICY IF EXISTS "Admins can delete positions" ON positions;
CREATE POLICY "Admins can delete positions"
    ON positions FOR DELETE TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));

-- ---------- SLOTS ----------

DROP POLICY IF EXISTS "Slots viewable by community members" ON slots;
CREATE POLICY "Slots viewable by community members"
    ON slots FOR SELECT TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_community_ids())));

DROP POLICY IF EXISTS "Admins can create slots" ON slots;
CREATE POLICY "Admins can create slots"
    ON slots FOR INSERT TO authenticated
    WITH CHECK (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids())));

-- ---------- SLOT POSITIONS ----------

DROP POLICY IF EXISTS "Slot positions viewable by community members" ON slot_positions;
CREATE POLICY "Slot positions viewable by community members"
    ON slot_positions FOR SELECT TO authenticated
    USING (slot_id IN (
        SELECT s.id FROM slots s
        WHERE s.activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_community_ids()))
    ));

DROP POLICY IF EXISTS "Admins can create slot positions" ON slot_positions;
CREATE POLICY "Admins can create slot positions"
    ON slot_positions FOR INSERT TO authenticated
    WITH CHECK (slot_id IN (
        SELECT s.id FROM slots s
        WHERE s.activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids()))
    ));

DROP POLICY IF EXISTS "Admins can delete slot positions" ON slot_positions;
CREATE POLICY "Admins can delete slot positions"
    ON slot_positions FOR DELETE TO authenticated
    USING (slot_id IN (
        SELECT s.id FROM slots s
        WHERE s.activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_admin_community_ids()))
    ));

-- ---------- SUBSTITUTE QUEUE ----------

DROP POLICY IF EXISTS "Substitute queue viewable by community members" ON substitute_queue;
CREATE POLICY "Substitute queue viewable by community members"
    ON substitute_queue FOR SELECT TO authenticated
    USING (activity_id IN (SELECT id FROM activities WHERE community_id IN (SELECT get_my_community_ids())));

DROP POLICY IF EXISTS "Users can manage their own substitute entries" ON substitute_queue;
CREATE POLICY "Users can manage their own substitute entries"
    ON substitute_queue FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS "Users can remove their own substitute entries" ON substitute_queue;
CREATE POLICY "Users can remove their own substitute entries"
    ON substitute_queue FOR DELETE TO authenticated
    USING (user_id = auth.uid());

-- ---------- NOTIFICATIONS ----------

DROP POLICY IF EXISTS "Users can view their own notifications" ON notifications;
CREATE POLICY "Users can view their own notifications"
    ON notifications FOR SELECT TO authenticated
    USING (user_id = auth.uid());

DROP POLICY IF EXISTS "Users can update their own notifications" ON notifications;
CREATE POLICY "Users can update their own notifications"
    ON notifications FOR UPDATE TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS "Users can delete their own notifications" ON notifications;
CREATE POLICY "Users can delete their own notifications"
    ON notifications FOR DELETE TO authenticated
    USING (user_id = auth.uid());

-- ---------- SLOT TEMPLATES ----------

DROP POLICY IF EXISTS "Users can view own templates" ON slot_templates;
CREATE POLICY "Users can view own templates"
    ON slot_templates FOR SELECT
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can insert own templates" ON slot_templates;
CREATE POLICY "Users can insert own templates"
    ON slot_templates FOR INSERT
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can update own templates" ON slot_templates;
CREATE POLICY "Users can update own templates"
    ON slot_templates FOR UPDATE TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS "Users can delete own templates" ON slot_templates;
CREATE POLICY "Users can delete own templates"
    ON slot_templates FOR DELETE
    USING (auth.uid() = user_id);

-- ============================================================================
-- PHASE 4: Triggers (idempotent: DROP IF EXISTS + CREATE)
-- ============================================================================

-- handle_new_community: auto-add creator as admin
CREATE OR REPLACE FUNCTION handle_new_community()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.community_members (community_id, user_id, role)
    VALUES (NEW.id, NEW.created_by, 'admin');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS on_community_created ON communities;
CREATE TRIGGER on_community_created
    AFTER INSERT ON communities
    FOR EACH ROW EXECUTE FUNCTION handle_new_community();

-- notify_new_activity: notify community members when activity is created
CREATE OR REPLACE FUNCTION notify_new_activity()
RETURNS trigger AS $$
BEGIN
    INSERT INTO public.notifications (user_id, type, title, body, data)
    SELECT
        cm.user_id,
        'new_activity',
        'Nueva actividad',
        NEW.name,
        jsonb_build_object(
            'activity_id', NEW.id,
            'community_id', NEW.community_id
        )
    FROM public.community_members cm
    WHERE cm.community_id = NEW.community_id
      AND cm.user_id != NEW.created_by;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS trg_notify_new_activity ON activities;
CREATE TRIGGER trg_notify_new_activity
    AFTER INSERT ON activities
    FOR EACH ROW
    EXECUTE FUNCTION notify_new_activity();
