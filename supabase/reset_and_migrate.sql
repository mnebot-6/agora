-- ============================================================================
-- NUCLEAR RESET: Drop everything in public schema
-- ============================================================================

-- Drop all triggers first
DROP TRIGGER IF EXISTS trg_notify_new_activity ON activities;
DROP TRIGGER IF EXISTS trg_notify_slot_released ON slots;
DROP TRIGGER IF EXISTS on_community_created ON communities;

-- Drop all functions
DROP FUNCTION IF EXISTS notify_new_activity() CASCADE;
DROP FUNCTION IF EXISTS notify_slot_released() CASCADE;
DROP FUNCTION IF EXISTS notify_substitute_promoted(uuid, uuid, uuid) CASCADE;
DROP FUNCTION IF EXISTS promote_substitute(uuid) CASCADE;
DROP FUNCTION IF EXISTS promote_substitute(uuid, uuid) CASCADE;
DROP FUNCTION IF EXISTS reserve_slot(uuid, uuid) CASCADE;
DROP FUNCTION IF EXISTS release_slot(uuid, uuid, boolean) CASCADE;
DROP FUNCTION IF EXISTS mark_slot_paid(uuid, uuid) CASCADE;
DROP FUNCTION IF EXISTS join_substitute_queue(uuid, uuid, uuid) CASCADE;
DROP FUNCTION IF EXISTS leave_substitute_queue(uuid, uuid, uuid) CASCADE;
DROP FUNCTION IF EXISTS get_upcoming_activities(uuid) CASCADE;
DROP FUNCTION IF EXISTS handle_new_community() CASCADE;
-- NOTE: handle_new_user is NOT dropped to keep auth trigger intact

-- Drop all tables (order matters for FK constraints)
DROP TABLE IF EXISTS slot_templates CASCADE;
DROP TABLE IF EXISTS slot_positions CASCADE;
DROP TABLE IF EXISTS substitute_queue CASCADE;
DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS slots CASCADE;
DROP TABLE IF EXISTS positions CASCADE;
DROP TABLE IF EXISTS slot_groups CASCADE;
DROP TABLE IF EXISTS activities CASCADE;
DROP TABLE IF EXISTS community_members CASCADE;
DROP TABLE IF EXISTS communities CASCADE;
DROP TABLE IF EXISTS profiles CASCADE;

-- ============================================================================
-- MIGRATION 001: Initial Schema
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name TEXT NOT NULL,
    avatar_url TEXT,
    fcm_token TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO profiles (id, display_name)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'display_name', split_part(NEW.email, '@', 1))
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION handle_new_user();

CREATE TABLE communities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    description TEXT,
    image_url TEXT,
    invite_code TEXT NOT NULL UNIQUE,
    created_by UUID NOT NULL REFERENCES profiles(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE community_members (
    community_id UUID NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('admin', 'user')) DEFAULT 'user',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (community_id, user_id)
);

CREATE OR REPLACE FUNCTION handle_new_community()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO community_members (community_id, user_id, role)
    VALUES (NEW.id, NEW.created_by, 'admin');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_community_created
    AFTER INSERT ON communities
    FOR EACH ROW EXECUTE FUNCTION handle_new_community();

CREATE TABLE activities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    community_id UUID NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT,
    datetime TIMESTAMPTZ NOT NULL,
    duration_minutes INT NOT NULL,
    location_name TEXT,
    location_lat DOUBLE PRECISION,
    location_lng DOUBLE PRECISION,
    cost_description TEXT,
    slot_mode TEXT NOT NULL CHECK (slot_mode IN ('unlimited', 'limited', 'limited_with_positions')),
    max_slots INT,
    created_by UUID NOT NULL REFERENCES profiles(id),
    status TEXT NOT NULL CHECK (status IN ('active', 'cancelled', 'completed')) DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE slot_groups (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    activity_id UUID NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE positions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    activity_id UUID NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    name TEXT NOT NULL
);

CREATE TABLE slots (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    activity_id UUID NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    group_id UUID REFERENCES slot_groups(id) ON DELETE CASCADE,
    sort_order INT NOT NULL DEFAULT 0,
    status TEXT NOT NULL CHECK (status IN ('available', 'reserved', 'paid')) DEFAULT 'available',
    reserved_by UUID REFERENCES profiles(id),
    reserved_at TIMESTAMPTZ
);

CREATE TABLE slot_positions (
    slot_id UUID NOT NULL REFERENCES slots(id) ON DELETE CASCADE,
    position_id UUID NOT NULL REFERENCES positions(id) ON DELETE CASCADE,
    PRIMARY KEY (slot_id, position_id)
);

CREATE TABLE substitute_queue (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    activity_id UUID NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    position_id UUID REFERENCES positions(id) ON DELETE CASCADE,
    queued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (activity_id, user_id, position_id)
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    type TEXT NOT NULL CHECK (type IN ('new_activity', 'slot_released', 'substitute_promoted')),
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    data JSONB,
    read BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_community_members_user ON community_members(user_id);
CREATE INDEX idx_activities_community ON activities(community_id);
CREATE INDEX idx_activities_datetime ON activities(datetime);
CREATE INDEX idx_slots_activity ON slots(activity_id);
CREATE INDEX idx_slots_reserved_by ON slots(reserved_by);
CREATE INDEX idx_slot_positions_slot ON slot_positions(slot_id);
CREATE INDEX idx_substitute_queue_activity ON substitute_queue(activity_id);
CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id) WHERE NOT read;

-- ============================================================================
-- MIGRATION 002: RPC Functions
-- ============================================================================
CREATE OR REPLACE FUNCTION reserve_slot(p_slot_id UUID, p_user_id UUID)
RETURNS BOOLEAN AS $$
DECLARE
    v_slot RECORD;
    v_activity RECORD;
BEGIN
    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF v_slot IS NULL THEN RAISE EXCEPTION 'Slot not found'; END IF;
    IF v_slot.status != 'available' THEN RETURN FALSE; END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;
    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id AND user_id = p_user_id
    ) THEN
        RAISE EXCEPTION 'User is not a member of this community';
    END IF;

    UPDATE slots
    SET status = 'reserved', reserved_by = p_user_id, reserved_at = now()
    WHERE id = p_slot_id;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION release_slot(p_slot_id UUID, p_user_id UUID, p_is_admin BOOLEAN)
RETURNS BOOLEAN AS $$
DECLARE
    v_slot RECORD;
BEGIN
    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF v_slot IS NULL THEN RAISE EXCEPTION 'Slot not found'; END IF;
    IF v_slot.status = 'available' THEN RETURN FALSE; END IF;

    IF v_slot.status = 'paid' THEN
        IF v_slot.reserved_by != p_user_id THEN
            RAISE EXCEPTION 'Only the user who reserved this slot can release a paid reservation';
        END IF;
    ELSIF v_slot.status = 'reserved' THEN
        IF v_slot.reserved_by != p_user_id AND NOT p_is_admin THEN
            RAISE EXCEPTION 'Only the reserved user or an admin can release this slot';
        END IF;
    END IF;

    UPDATE slots
    SET status = 'available', reserved_by = NULL, reserved_at = NULL
    WHERE id = p_slot_id;

    PERFORM promote_substitute(p_slot_id, v_slot.activity_id);

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION mark_slot_paid(p_slot_id UUID, p_admin_user_id UUID)
RETURNS BOOLEAN AS $$
DECLARE
    v_slot RECORD;
    v_activity RECORD;
BEGIN
    SELECT * INTO v_slot FROM slots WHERE id = p_slot_id FOR UPDATE;
    IF v_slot IS NULL THEN RAISE EXCEPTION 'Slot not found'; END IF;
    IF v_slot.status != 'reserved' THEN RETURN FALSE; END IF;

    SELECT * INTO v_activity FROM activities WHERE id = v_slot.activity_id;
    IF NOT EXISTS (
        SELECT 1 FROM community_members
        WHERE community_id = v_activity.community_id
          AND user_id = p_admin_user_id AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only community admins can mark slots as paid';
    END IF;

    UPDATE slots SET status = 'paid' WHERE id = p_slot_id;
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION join_substitute_queue(
    p_activity_id UUID, p_user_id UUID, p_position_id UUID DEFAULT NULL
)
RETURNS VOID AS $$
BEGIN
    INSERT INTO substitute_queue (activity_id, user_id, position_id)
    VALUES (p_activity_id, p_user_id, p_position_id)
    ON CONFLICT (activity_id, user_id, position_id) DO NOTHING;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION leave_substitute_queue(
    p_activity_id UUID, p_user_id UUID, p_position_id UUID DEFAULT NULL
)
RETURNS VOID AS $$
BEGIN
    IF p_position_id IS NULL THEN
        DELETE FROM substitute_queue
        WHERE activity_id = p_activity_id AND user_id = p_user_id AND position_id IS NULL;
    ELSE
        DELETE FROM substitute_queue
        WHERE activity_id = p_activity_id AND user_id = p_user_id AND position_id = p_position_id;
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION get_upcoming_activities(p_user_id UUID)
RETURNS SETOF activities AS $$
BEGIN
    RETURN QUERY
    SELECT a.*
    FROM activities a
    INNER JOIN community_members cm ON cm.community_id = a.community_id
    WHERE cm.user_id = p_user_id
      AND a.status = 'active'
      AND a.datetime >= now()
    ORDER BY a.datetime ASC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- MIGRATION 003: RLS Policies
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

-- Profiles
CREATE POLICY "Profiles are viewable by authenticated users"
    ON profiles FOR SELECT TO authenticated USING (true);
CREATE POLICY "Users can update their own profile"
    ON profiles FOR UPDATE TO authenticated
    USING (id = auth.uid()) WITH CHECK (id = auth.uid());

-- Communities
CREATE POLICY "Communities are viewable by members"
    ON communities FOR SELECT TO authenticated
    USING (id IN (SELECT community_id FROM community_members WHERE user_id = auth.uid()));
CREATE POLICY "Communities are viewable by invite code"
    ON communities FOR SELECT TO authenticated USING (true);
CREATE POLICY "Authenticated users can create communities"
    ON communities FOR INSERT TO authenticated WITH CHECK (created_by = auth.uid());

-- Community members
CREATE POLICY "Members can view their community's membership"
    ON community_members FOR SELECT TO authenticated
    USING (community_id IN (SELECT community_id FROM community_members WHERE user_id = auth.uid()));
CREATE POLICY "Users can join communities"
    ON community_members FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());
CREATE POLICY "Admins can update member roles"
    ON community_members FOR UPDATE TO authenticated
    USING (community_id IN (SELECT community_id FROM community_members WHERE user_id = auth.uid() AND role = 'admin'));
CREATE POLICY "Admins can remove members"
    ON community_members FOR DELETE TO authenticated
    USING (user_id = auth.uid() OR community_id IN (
        SELECT community_id FROM community_members WHERE user_id = auth.uid() AND role = 'admin'));

-- Activities
CREATE POLICY "Activities are viewable by community members"
    ON activities FOR SELECT TO authenticated
    USING (community_id IN (SELECT community_id FROM community_members WHERE user_id = auth.uid()));
CREATE POLICY "Community admins can create activities"
    ON activities FOR INSERT TO authenticated
    WITH CHECK (community_id IN (
        SELECT community_id FROM community_members WHERE user_id = auth.uid() AND role = 'admin')
        AND created_by = auth.uid());
CREATE POLICY "Community admins can update activities"
    ON activities FOR UPDATE TO authenticated
    USING (community_id IN (
        SELECT community_id FROM community_members WHERE user_id = auth.uid() AND role = 'admin'));

-- Slot groups, positions, slots, slot_positions (SELECT for members, INSERT for admins)
CREATE POLICY "Slot groups viewable by community members"
    ON slot_groups FOR SELECT TO authenticated
    USING (activity_id IN (SELECT a.id FROM activities a JOIN community_members cm ON cm.community_id = a.community_id WHERE cm.user_id = auth.uid()));
CREATE POLICY "Admins can create slot groups"
    ON slot_groups FOR INSERT TO authenticated
    WITH CHECK (activity_id IN (SELECT a.id FROM activities a JOIN community_members cm ON cm.community_id = a.community_id WHERE cm.user_id = auth.uid() AND cm.role = 'admin'));

CREATE POLICY "Positions viewable by community members"
    ON positions FOR SELECT TO authenticated
    USING (activity_id IN (SELECT a.id FROM activities a JOIN community_members cm ON cm.community_id = a.community_id WHERE cm.user_id = auth.uid()));
CREATE POLICY "Admins can create positions"
    ON positions FOR INSERT TO authenticated
    WITH CHECK (activity_id IN (SELECT a.id FROM activities a JOIN community_members cm ON cm.community_id = a.community_id WHERE cm.user_id = auth.uid() AND cm.role = 'admin'));

CREATE POLICY "Slots viewable by community members"
    ON slots FOR SELECT TO authenticated
    USING (activity_id IN (SELECT a.id FROM activities a JOIN community_members cm ON cm.community_id = a.community_id WHERE cm.user_id = auth.uid()));
CREATE POLICY "Admins can create slots"
    ON slots FOR INSERT TO authenticated
    WITH CHECK (activity_id IN (SELECT a.id FROM activities a JOIN community_members cm ON cm.community_id = a.community_id WHERE cm.user_id = auth.uid() AND cm.role = 'admin'));

CREATE POLICY "Slot positions viewable by community members"
    ON slot_positions FOR SELECT TO authenticated
    USING (slot_id IN (SELECT s.id FROM slots s JOIN activities a ON a.id = s.activity_id JOIN community_members cm ON cm.community_id = a.community_id WHERE cm.user_id = auth.uid()));
CREATE POLICY "Admins can create slot positions"
    ON slot_positions FOR INSERT TO authenticated
    WITH CHECK (slot_id IN (SELECT s.id FROM slots s JOIN activities a ON a.id = s.activity_id JOIN community_members cm ON cm.community_id = a.community_id WHERE cm.user_id = auth.uid() AND cm.role = 'admin'));

-- Substitute queue
CREATE POLICY "Substitute queue viewable by community members"
    ON substitute_queue FOR SELECT TO authenticated
    USING (activity_id IN (SELECT a.id FROM activities a JOIN community_members cm ON cm.community_id = a.community_id WHERE cm.user_id = auth.uid()));
CREATE POLICY "Users can manage their own substitute entries"
    ON substitute_queue FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());
CREATE POLICY "Users can remove their own substitute entries"
    ON substitute_queue FOR DELETE TO authenticated USING (user_id = auth.uid());

-- Notifications
CREATE POLICY "Users can view their own notifications"
    ON notifications FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY "Users can update their own notifications"
    ON notifications FOR UPDATE TO authenticated
    USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());

-- ============================================================================
-- MIGRATION 004: Notification Triggers
-- ============================================================================
CREATE OR REPLACE FUNCTION notify_new_activity()
RETURNS trigger AS $$
BEGIN
    INSERT INTO notifications (user_id, type, title, body, data)
    SELECT cm.user_id, 'new_activity', 'Nueva actividad', NEW.name,
        jsonb_build_object('activity_id', NEW.id, 'community_id', NEW.community_id)
    FROM community_members cm
    WHERE cm.community_id = NEW.community_id AND cm.user_id != NEW.created_by;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER trg_notify_new_activity
    AFTER INSERT ON activities
    FOR EACH ROW EXECUTE FUNCTION notify_new_activity();

CREATE OR REPLACE FUNCTION notify_slot_released()
RETURNS trigger AS $$
BEGIN
    IF OLD.status IN ('reserved', 'paid') AND NEW.status = 'available' THEN
        INSERT INTO notifications (user_id, type, title, body, data)
        SELECT sq.user_id, 'slot_released', 'Plaza disponible',
            'Se ha liberado una plaza en una actividad',
            jsonb_build_object('activity_id', NEW.activity_id, 'slot_id', NEW.id)
        FROM substitute_queue sq WHERE sq.activity_id = NEW.activity_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER trg_notify_slot_released
    AFTER UPDATE ON slots
    FOR EACH ROW EXECUTE FUNCTION notify_slot_released();

CREATE OR REPLACE FUNCTION promote_substitute(p_slot_id uuid, p_activity_id uuid)
RETURNS void AS $$
DECLARE
    v_sub record;
    v_slot_positions uuid[];
BEGIN
    SELECT array_agg(sp.position_id) INTO v_slot_positions
    FROM slot_positions sp WHERE sp.slot_id = p_slot_id;

    IF v_slot_positions IS NOT NULL AND array_length(v_slot_positions, 1) > 0 THEN
        SELECT * INTO v_sub FROM substitute_queue
        WHERE activity_id = p_activity_id
          AND (position_id = ANY(v_slot_positions) OR position_id IS NULL)
        ORDER BY queued_at ASC LIMIT 1;
    ELSE
        SELECT * INTO v_sub FROM substitute_queue
        WHERE activity_id = p_activity_id
        ORDER BY queued_at ASC LIMIT 1;
    END IF;

    IF v_sub IS NOT NULL THEN
        UPDATE slots SET status = 'reserved', reserved_by = v_sub.user_id, reserved_at = now()
        WHERE id = p_slot_id;
        DELETE FROM substitute_queue WHERE id = v_sub.id;

        INSERT INTO notifications (user_id, type, title, body, data)
        VALUES (v_sub.user_id, 'substitute_promoted', 'Has sido promocionado',
            'Se te ha asignado una plaza desde la cola de suplentes',
            jsonb_build_object('activity_id', p_activity_id, 'slot_id', p_slot_id));
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- MIGRATION 005: Slot Templates
-- ============================================================================
CREATE TABLE slot_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE slot_templates ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own templates"
    ON slot_templates FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own templates"
    ON slot_templates FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own templates"
    ON slot_templates FOR DELETE USING (auth.uid() = user_id);

-- ============================================================================
-- DONE: Re-insert profiles for existing auth users (if any)
-- ============================================================================
INSERT INTO profiles (id, display_name)
SELECT id, COALESCE(raw_user_meta_data->>'display_name', split_part(email, '@', 1))
FROM auth.users
ON CONFLICT (id) DO NOTHING;
