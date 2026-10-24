-- Allow the runtime role to update checking account attributes.
-- Required by Hibernate pessimistic locking with JOINED inheritance.
-- Run the grant only when Flyway is connected as banking_migrator.

DO
$$
    BEGIN
        IF current_user = 'banking_migrator' THEN
            EXECUTE '
            GRANT UPDATE
            ON TABLE public.checking_accounts
            TO banking_app
        ';
        ELSE
            RAISE NOTICE
                'Skipping checking_accounts UPDATE grant because Flyway is running as %',
                current_user;
        END IF;
    END
$$;