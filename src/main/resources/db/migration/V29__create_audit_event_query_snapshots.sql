-- Audit event query snapshots.
--
-- Creates auxiliary tables to preserve result-set membership and ordering
-- across paginated audit event queries.
--
-- Snapshot metadata stores the authenticated actor, normalized filter and
-- sort hashes, pagination settings, result count, and expiration timestamp.
-- Snapshot items reference the immutable audit_events records and preserve
-- their original positions within the materialized result set.
--
-- This migration does not modify or remove audit event records.
-- Runtime privileges are managed by the
-- afterMigrate__apply_runtime_privileges.sql callback.

CREATE TABLE audit_event_query_snapshots
(
    snapshot_id    UUID        NOT NULL,
    actor_user_id  BIGINT      NOT NULL,
    filter_hash    BYTEA       NOT NULL,
    sort_hash      BYTEA       NOT NULL,
    page_size      SMALLINT    NOT NULL,
    total_elements INTEGER     NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_audit_event_query_snapshots
        PRIMARY KEY (snapshot_id),

    CONSTRAINT fk_audit_event_query_snapshots_actor
        FOREIGN KEY (actor_user_id)
            REFERENCES users (id)
            ON DELETE RESTRICT,

    CONSTRAINT ck_audit_event_query_snapshots_filter_hash
        CHECK (octet_length(filter_hash) = 32),

    CONSTRAINT ck_audit_event_query_snapshots_sort_hash
        CHECK (octet_length(sort_hash) = 32),

    CONSTRAINT ck_audit_event_query_snapshots_page_size
        CHECK (page_size BETWEEN 1 AND 100),

    CONSTRAINT ck_audit_event_query_snapshots_total_elements
        CHECK (total_elements BETWEEN 0 AND 10000),

    CONSTRAINT ck_audit_event_query_snapshots_expiration
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_audit_event_query_snapshots_expires_at
    ON audit_event_query_snapshots (expires_at);


CREATE TABLE audit_event_query_snapshot_items
(
    snapshot_id    UUID    NOT NULL,
    position       INTEGER NOT NULL,
    audit_event_id BIGINT  NOT NULL,

    CONSTRAINT pk_audit_event_query_snapshot_items
        PRIMARY KEY (snapshot_id, position),

    CONSTRAINT fk_audit_event_query_snapshot_items_snapshot
        FOREIGN KEY (snapshot_id)
            REFERENCES audit_event_query_snapshots (snapshot_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_audit_event_query_snapshot_items_event
        FOREIGN KEY (audit_event_id)
            REFERENCES audit_events (id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_audit_event_query_snapshot_items_event
        UNIQUE (snapshot_id, audit_event_id),

    CONSTRAINT ck_audit_event_query_snapshot_items_position
        CHECK (position >= 0)
);
