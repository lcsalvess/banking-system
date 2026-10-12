-- Normalize existing usernames before enforcing case-insensitive uniqueness.
UPDATE users
SET username = LOWER(BTRIM(username))
WHERE username <> LOWER(BTRIM(username));

-- Replace case-sensitive username constraint with a unique index.
ALTER TABLE users
    DROP CONSTRAINT uk_users_username;

CREATE UNIQUE INDEX uk_users_username
    ON users (LOWER(username));