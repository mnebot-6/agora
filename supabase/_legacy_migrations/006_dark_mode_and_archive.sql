-- ============================================================================
-- Migration 006: Dark Mode column + Activity Archive status + Community policies
-- ============================================================================

-- 1. Add dark_mode column to profiles (for theme persistence)
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS dark_mode BOOLEAN DEFAULT false;

-- 2. Drop old CHECK constraint FIRST (it only allows active/cancelled/completed)
ALTER TABLE activities DROP CONSTRAINT IF EXISTS activities_status_check;

-- 3. Migrate activity statuses: cancelled/completed -> archived
UPDATE activities SET status = 'archived' WHERE status IN ('cancelled', 'completed');

-- 4. Add new CHECK constraint
ALTER TABLE activities ADD CONSTRAINT activities_status_check
    CHECK (status IN ('active', 'archived'));

-- 4. Community UPDATE/DELETE policies (admin can edit, creator can delete)
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE policyname = 'Community admins can update communities'
    ) THEN
        CREATE POLICY "Community admins can update communities"
            ON communities FOR UPDATE TO authenticated
            USING (id IN (SELECT get_my_admin_community_ids()))
            WITH CHECK (id IN (SELECT get_my_admin_community_ids()));
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE policyname = 'Community creator can delete communities'
    ) THEN
        CREATE POLICY "Community creator can delete communities"
            ON communities FOR DELETE TO authenticated
            USING (created_by = auth.uid());
    END IF;
END $$;
