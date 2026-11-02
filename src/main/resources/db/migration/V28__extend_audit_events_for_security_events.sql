-- Audit trail v2: security events and failures.
--
-- New columns are nullable, so existing rows (all SUCCESS) remain unchanged.
-- banking_app privileges are granted per table (afterMigrate), so the new
-- columns are automatically covered by the existing SELECT/INSERT permissions.
ALTER TABLE audit_events
    ADD COLUMN ip_address  INET,
    ADD COLUMN reason      VARCHAR(60),
    ADD COLUMN http_status INTEGER;

ALTER TABLE audit_events
    ADD CONSTRAINT ck_audit_events_ip_address_host
        CHECK (
            ip_address IS NULL
                OR masklen(ip_address) = CASE family(ip_address)
                                             WHEN 4 THEN 32
                                             WHEN 6 THEN 128
                END
            ),
    ADD CONSTRAINT ck_audit_events_reason
        CHECK (reason IS NULL OR reason ~ '^[A-Z][A-Z0-9_]{0,59}$'),
    ADD CONSTRAINT ck_audit_events_http_status
        CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599),
    -- Every non-success event must specify a reason.
    ADD CONSTRAINT ck_audit_events_reason_required
        CHECK (outcome = 'SUCCESS' OR reason IS NOT NULL);

-- Typical investigation query: "What did this IP do?"
CREATE INDEX idx_audit_events_ip_address_created_at
    ON audit_events (ip_address, created_at DESC)
    WHERE ip_address IS NOT NULL;
