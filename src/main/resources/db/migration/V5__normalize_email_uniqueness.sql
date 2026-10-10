-- Normalize existing emails before enforcing case-insensitive uniqueness.
UPDATE users
SET email = LOWER(BTRIM(email))
WHERE email <> LOWER(BTRIM(email));

UPDATE clients
SET email = LOWER(BTRIM(email))
WHERE email <> LOWER(BTRIM(email));

-- Replace case-sensitive email constraints with unique indexes.

ALTER TABLE users
    DROP CONSTRAINT uk_users_email;

CREATE UNIQUE INDEX uk_users_email
    ON users (LOWER(email));

ALTER TABLE clients
    DROP CONSTRAINT uk_clients_email;

CREATE UNIQUE INDEX uk_clients_email
    ON clients (LOWER(email));