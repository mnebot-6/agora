-- Slot templates: user-saved configurations for slot/position setup
CREATE TABLE IF NOT EXISTS slot_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- RLS: users can only see/manage their own templates
ALTER TABLE slot_templates ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own templates"
    ON slot_templates FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own templates"
    ON slot_templates FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete own templates"
    ON slot_templates FOR DELETE
    USING (auth.uid() = user_id);
