-- ============================================================================
-- Migration 018: Add language_preference to profiles
-- ============================================================================
-- Permite que cada usuario elija el idioma de la app:
--   - 'auto': usa el idioma del sistema (default)
--   - 'es': español forzado
--   - 'en': inglés forzado
-- ============================================================================

BEGIN;

ALTER TABLE profiles
    ADD COLUMN IF NOT EXISTS language_preference text NOT NULL DEFAULT 'auto'
        CHECK (language_preference IN ('auto', 'es', 'en'));

COMMIT;
