-- ============================================================
-- Require full names for human users
-- ============================================================

-- The constraint is added as NOT VALID to avoid scanning existing rows
-- while holding the ACCESS EXCLUSIVE lock required by ADD CHECK.
--
-- PostgreSQL immediately enforces the constraint for subsequent INSERT
-- and UPDATE operations.
--
-- VALIDATE CONSTRAINT scans the existing rows using a less restrictive
-- SHARE UPDATE EXCLUSIVE lock, allowing regular reads and writes to continue.
--
-- This migration assumes that all legacy HUMAN users have already been
-- populated with valid full names.
--
-- SYSTEM users must continue to have NULL full_name, as enforced by
-- the existing ck_users_full_name_by_type constraint from V33.

ALTER TABLE public.users
    ADD CONSTRAINT ck_users_human_full_name_present
        CHECK (
            user_type <> 'HUMAN'
                OR full_name IS NOT NULL
            ) NOT VALID;

ALTER TABLE public.users
    VALIDATE CONSTRAINT ck_users_human_full_name_present;

-- ============================================================
-- Documentation
-- ============================================================

COMMENT ON CONSTRAINT ck_users_human_full_name_present
    ON public.users IS
    'Requires a non-null full name for HUMAN users. SYSTEM users must retain NULL.';
