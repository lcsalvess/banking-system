ALTER TABLE users
    ADD CONSTRAINT ck_users_system_migration_inactive
        CHECK (
            LOWER(BTRIM(username)) <> 'system_migration'
                OR active = FALSE
            );