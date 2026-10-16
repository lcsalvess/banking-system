-- Credential version carried by every JWT. Incremented when the user changes the
-- password, which invalidates all tokens issued before the change.
-- Existing users start at 0, so tokens issued before this migration (without the
-- claim) keep working until the user changes the password.
ALTER TABLE users
    ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
