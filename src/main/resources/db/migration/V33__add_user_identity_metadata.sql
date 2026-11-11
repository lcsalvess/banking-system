-- ============================================================
-- User identity metadata
-- ============================================================

-- Note: the constraints below are validated while holding a strong lock on
-- users. This is fine for a small table; for a large one, add them as
-- NOT VALID and run VALIDATE CONSTRAINT afterwards.

ALTER TABLE public.users
    ADD COLUMN user_type   VARCHAR(20) NOT NULL DEFAULT 'HUMAN',
    ADD COLUMN employee_id VARCHAR(30),
    ADD COLUMN full_name   VARCHAR(150);

-- Classify the technical migration account.
-- It is not a human employee and must not receive an employee ID.
UPDATE public.users
SET user_type = 'SYSTEM'
WHERE username = 'system_migration';

-- ============================================================
-- Employee ID sequence
-- ============================================================

CREATE SEQUENCE public.employee_id_seq
    AS BIGINT
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

-- The sequence is dropped together with the column.
ALTER SEQUENCE public.employee_id_seq
    OWNED BY public.users.employee_id;

-- Assign deterministic employee IDs to existing human users.
-- The internal database ID determines the assignment order.
WITH numbered_human_users AS (SELECT id,
                                     ROW_NUMBER() OVER (ORDER BY id) AS employee_number
                              FROM public.users
                              WHERE user_type = 'HUMAN')
UPDATE public.users AS u
SET employee_id = 'EMP-' ||
                  LPAD(
                          numbered.employee_number::TEXT,
                          GREATEST(6, LENGTH(numbered.employee_number::TEXT)),
                          '0'
                  )
FROM numbered_human_users AS numbered
WHERE u.id = numbered.id;

-- Continue the sequence after the identifiers assigned above.
SELECT setval(
               'public.employee_id_seq',
               GREATEST(COUNT(*), 1),
               COUNT(*) > 0
       )
FROM public.users
WHERE user_type = 'HUMAN';

-- ============================================================
-- Employee ID generator
-- ============================================================

-- SECURITY DEFINER lets the application generate IDs without holding
-- any privilege on the sequence itself; only EXECUTE on this function
-- is required. The search_path is pinned to prevent object hijacking.
CREATE FUNCTION public.next_employee_id()
    RETURNS VARCHAR(30)
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog, pg_temp
AS
$$
DECLARE
    next_number BIGINT;
BEGIN
    next_number := nextval('public.employee_id_seq'::regclass);

    RETURN 'EMP-' ||
           LPAD(
                   next_number::TEXT,
                   GREATEST(6, LENGTH(next_number::TEXT)),
                   '0'
           );
END;
$$;

-- The application receives explicit EXECUTE permission
-- through the runtime privilege callback.
REVOKE ALL PRIVILEGES
    ON FUNCTION public.next_employee_id()
    FROM PUBLIC;

-- ============================================================
-- User identity constraints
-- ============================================================

ALTER TABLE public.users
    ADD CONSTRAINT ck_users_user_type
        CHECK (user_type IN ('HUMAN', 'SYSTEM')),

    ADD CONSTRAINT ck_users_employee_id_nonblank
        CHECK (
            employee_id IS NULL
                OR employee_id ~ '[^[:space:]]'
            ),

    ADD CONSTRAINT ck_users_employee_id_by_type
        CHECK (
            (user_type = 'HUMAN' AND employee_id IS NOT NULL)
                OR
            (user_type = 'SYSTEM' AND employee_id IS NULL)
            ),

    -- Legacy human records may have no full name until updated.
    -- A provided name must contain at least one non-whitespace character.
    ADD CONSTRAINT ck_users_full_name_by_type
        CHECK (
            (
                user_type = 'HUMAN'
                    AND (
                    full_name IS NULL
                        OR full_name ~ '[^[:space:]]'
                    )
                )
                OR
            (
                user_type = 'SYSTEM'
                    AND full_name IS NULL
                )
            ),

    ADD CONSTRAINT uk_users_employee_id
        UNIQUE (employee_id);

-- ============================================================
-- User identity validation
-- ============================================================

-- The application obtains employee IDs through next_employee_id().
-- This trigger validates the identity metadata on every new user.
CREATE FUNCTION public.validate_user_identity_before_insert()
    RETURNS TRIGGER
    LANGUAGE plpgsql
    SET search_path = pg_catalog, pg_temp
AS
$$
BEGIN
    IF NEW.user_type = 'HUMAN' THEN

        IF NEW.employee_id IS NULL
            OR NEW.employee_id !~ '[^[:space:]]' THEN
            RAISE EXCEPTION
                'A valid employee ID is required for human users.'
                USING ERRCODE = '23514';
        END IF;

        IF NEW.full_name IS NULL
            OR NEW.full_name !~ '[^[:space:]]' THEN
            RAISE EXCEPTION
                'A full name is required for human users.'
                USING ERRCODE = '23514';
        END IF;

    ELSIF NEW.user_type = 'SYSTEM' THEN

        IF NEW.employee_id IS NOT NULL OR NEW.full_name IS NOT NULL THEN
            RAISE EXCEPTION
                'System users cannot have an employee ID or full name.'
                USING ERRCODE = '23514';
        END IF;

    ELSE

        RAISE EXCEPTION
            'Invalid user type.'
            USING ERRCODE = '23514';

    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_users_validate_identity_before_insert
    BEFORE INSERT
    ON public.users
    FOR EACH ROW
EXECUTE FUNCTION public.validate_user_identity_before_insert();

-- ============================================================
-- Identity immutability
-- ============================================================

CREATE FUNCTION public.prevent_user_identity_update()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF NEW.employee_id IS DISTINCT FROM OLD.employee_id THEN
        RAISE EXCEPTION
            'A user employee ID cannot be changed.'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.user_type IS DISTINCT FROM OLD.user_type THEN
        RAISE EXCEPTION
            'A user type cannot be changed.'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_users_prevent_identity_update
    BEFORE UPDATE
    ON public.users
    FOR EACH ROW
EXECUTE FUNCTION public.prevent_user_identity_update();

-- ============================================================
-- Documentation
-- ============================================================

COMMENT ON COLUMN public.users.user_type IS
    'HUMAN for people, SYSTEM for technical accounts. Immutable after insert.';

COMMENT ON COLUMN public.users.employee_id IS
    'Employee identifier generated through the database-backed generator. Immutable after assignment. NULL only for SYSTEM users.';

COMMENT ON COLUMN public.users.full_name IS
    'Full name of the human user. May be NULL for legacy records until updated. Always NULL for SYSTEM users.';
