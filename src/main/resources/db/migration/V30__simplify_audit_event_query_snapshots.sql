-- Simplifies the audit event query snapshots created by V29.
--
-- 1. The page size is no longer fixed by the snapshot: item positions are
--    absolute, so a client can read any page size from the same snapshot.
--    The page_size column and its check constraint are removed.
-- 2. The unique constraint on (snapshot_id, audit_event_id) is removed. The
--    materialized IDs come from one query over audit_events.id, so they are
--    already unique, and the constraint only added index writes for up to
--    10,000 rows per snapshot.
-- 3. Adds the index that serves the per-user limit on active snapshots
--    (newest first) and the foreign key to users.
--
-- Existing snapshots are short-lived (15 minutes) and remain valid.
-- Audit event records are not modified.

ALTER TABLE audit_event_query_snapshots
    DROP CONSTRAINT IF EXISTS ck_audit_event_query_snapshots_page_size;

ALTER TABLE audit_event_query_snapshots
    DROP COLUMN page_size;

ALTER TABLE audit_event_query_snapshot_items
    DROP CONSTRAINT IF EXISTS uq_audit_event_query_snapshot_items_event;

CREATE INDEX idx_audit_event_query_snapshots_actor_created_at
    ON audit_event_query_snapshots (actor_user_id, created_at DESC);
