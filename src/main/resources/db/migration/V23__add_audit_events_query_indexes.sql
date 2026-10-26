-- Indexes that support the read-only audit query API (GET /api/v1/audit-events).
--
-- The default listing orders by (created_at DESC, id DESC) and every filter is
-- combined with an optional date range, so each index below leads with the
-- filtered column and ends with the same (created_at DESC, id DESC) sort keys.
-- That lets PostgreSQL read the first page straight from the index instead of
-- scanning and sorting the whole table.
--
-- Plain CREATE INDEX (not CONCURRENTLY) keeps this migration transactional.
-- It blocks writes to audit_events while each index is built, which is
-- negligible on a small table; on a large existing table, create the indexes
-- CONCURRENTLY out of band before deploying.

-- Default listing, date range filters and the default sort.
CREATE INDEX idx_audit_events_created_at
    ON audit_events (created_at DESC, id DESC);

-- Filter by eventType.
CREATE INDEX idx_audit_events_event_type_created_at
    ON audit_events (event_type, created_at DESC, id DESC);

-- Filter by outcome (FAILURE / DENIED are rare next to SUCCESS).
CREATE INDEX idx_audit_events_outcome_created_at
    ON audit_events (outcome, created_at DESC, id DESC);

-- Filter by actorUsername. The API matches case-insensitively by prefix
-- (lower(actor_username) LIKE 'prefix%'); text_pattern_ops is required for a
-- B-tree to serve LIKE prefix searches under non-C collations. Substring
-- search is intentionally not supported: it would need pg_trgm, and creating
-- an extension needs privileges the least-privilege migrator role does not have.
CREATE INDEX idx_audit_events_actor_username_prefix
    ON audit_events (lower(actor_username) text_pattern_ops);
