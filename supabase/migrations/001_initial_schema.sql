-- ============================================================================
-- Migration 001: Initial Schema
-- Community management app with flexible slot reservation system
-- ============================================================================

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================================
-- PROFILES (extends auth.users)
-- ============================================================================
CREATE TABLE profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name TEXT NOT NULL,
    avatar_url TEXT,
    fcm_token TEXT,
    dark_mode BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Auto-create profile on user signup
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

CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION handle_new_user();

-- ============================================================================
-- COMMUNITIES
-- ============================================================================
CREATE TABLE communities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    description TEXT,
    image_url TEXT,
    invite_code TEXT NOT NULL UNIQUE,
    created_by UUID NOT NULL REFERENCES profiles(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================================
-- COMMUNITY MEMBERS (join table with role)
-- ============================================================================
CREATE TABLE community_members (
    community_id UUID NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('admin', 'user')) DEFAULT 'user',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (community_id, user_id)
);

-- Auto-add creator as admin when community is created
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

-- ============================================================================
-- ACTIVITIES
-- ============================================================================
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
    status TEXT NOT NULL CHECK (status IN ('active', 'archived')) DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================================
-- SLOT GROUPS (for organized team structure)
-- ============================================================================
CREATE TABLE slot_groups (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    activity_id UUID NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
);

-- ============================================================================
-- POSITIONS (available roles for slots)
-- ============================================================================
CREATE TABLE positions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    activity_id UUID NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    name TEXT NOT NULL
);

-- ============================================================================
-- SLOTS (individual bookable spots)
-- ============================================================================
CREATE TABLE slots (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    activity_id UUID NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    group_id UUID REFERENCES slot_groups(id) ON DELETE CASCADE,
    sort_order INT NOT NULL DEFAULT 0,
    status TEXT NOT NULL CHECK (status IN ('available', 'reserved', 'paid')) DEFAULT 'available',
    reserved_by UUID REFERENCES profiles(id),
    reserved_at TIMESTAMPTZ
);

-- ============================================================================
-- SLOT POSITIONS (N:M - a slot can accept multiple positions)
-- ============================================================================
CREATE TABLE slot_positions (
    slot_id UUID NOT NULL REFERENCES slots(id) ON DELETE CASCADE,
    position_id UUID NOT NULL REFERENCES positions(id) ON DELETE CASCADE,
    PRIMARY KEY (slot_id, position_id)
);

-- ============================================================================
-- SUBSTITUTE QUEUE (FIFO)
-- ============================================================================
CREATE TABLE substitute_queue (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    activity_id UUID NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    position_id UUID REFERENCES positions(id) ON DELETE CASCADE,
    queued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (activity_id, user_id, position_id)
);

-- ============================================================================
-- NOTIFICATIONS (in-app history)
-- ============================================================================
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

-- ============================================================================
-- INDEXES
-- ============================================================================
CREATE INDEX idx_community_members_user ON community_members(user_id);
CREATE INDEX idx_activities_community ON activities(community_id);
CREATE INDEX idx_activities_datetime ON activities(datetime);
CREATE INDEX idx_slots_activity ON slots(activity_id);
CREATE INDEX idx_slots_reserved_by ON slots(reserved_by);
CREATE INDEX idx_slot_positions_slot ON slot_positions(slot_id);
CREATE INDEX idx_substitute_queue_activity ON substitute_queue(activity_id);
CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id) WHERE NOT read;
