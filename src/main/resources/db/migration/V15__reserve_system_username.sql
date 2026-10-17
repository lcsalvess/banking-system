-- The username "system" identifies the actor of system-generated operations, such
-- as the daily yield, so no user can have it. The API already rejects it through
-- Bean Validation; this constraint keeps the rule when a user is created by any
-- other path. The migration fails if a user with this username already exists:
-- rename or remove it before migrating.
ALTER TABLE users
    ADD CONSTRAINT ck_users_username_not_system
        CHECK (LOWER(BTRIM(username)) <> 'system');
