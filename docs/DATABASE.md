# Database

- PostgreSQL, with the schema versioned and applied by Flyway. Migrations live in `src/main/resources/db/migration` and
  run automatically at startup. Hibernate does not change the schema; it only validates it against the entities
  (`spring.jpa.hibernate.ddl-auto=validate`).
- `V1__create_schema.sql` creates the initial schema: tables, constraints, indexes, and the `account_number_seq`
  sequence. `V2__increase_account_number_length.sql` widens `accounts.account_number` to 20 characters.
  `V3__add_transfer_code_to_transactions.sql` adds the nullable `transfer_code` column to `transactions`.
  `V4__add_transaction_history_index.sql` creates the index `idx_transactions_account_created_id` on
  `transactions (account_id, created_at DESC, id DESC)`, which supports the transaction history query.
- `V5__normalize_email_uniqueness.sql` lowercases the existing e-mails of `users` and `clients` and replaces the
  case-sensitive unique constraints with unique indexes on `LOWER(email)` (names kept: `uk_users_email` and
  `uk_clients_email`). `V6__add_transaction_performed_by_user.sql` creates the inactive technical user
  `system_migration`, adds the `performed_by_user_id` column to `transactions` as nullable, attributes the existing
  transactions to that user, and then makes the column `NOT NULL` with the foreign key
  `fk_transactions_performed_by_user`. It also aborts if a `system_migration` user already exists with unexpected data.
  V6 initializes the technical user's `created_at` and `updated_at` using `CURRENT_TIMESTAMP`.
  `V7__normalize_username_uniqueness.sql` lowercases and trims the existing usernames and replaces the case-sensitive
  unique constraint with a unique index on `LOWER(username)` (name kept: `uk_users_username`).
  `V8__harden_system_migration_user.sql` replaces the placeholder password of `system_migration` with a BCrypt hash,
  changes its e-mail to the reserved address `system-migration@invalid.example`, keeps the user inactive, and updates
  `updated_at` using `CURRENT_TIMESTAMP`.
  `V9__add_account_balance_upper_bound.sql` adds the `ck_accounts_balance_max` check constraint
  (`balance <= 9999999999999.99`).
- `V10__add_user_token_version.sql` adds the `token_version` column to `users` (`INTEGER NOT NULL DEFAULT 0`), the
  credential version carried by every JWT. `V11__keep_system_migration_user_inactive.sql` adds the
  `ck_users_system_migration_inactive` check constraint, so the `system_migration` user can never be active.
  `V12__daily_yield_processing.sql` adds the `yield_date` column to `transactions` (filled from `created_at` for the
  existing yields), replaces the `uk_transactions_daily_yield` index so it is based on `(account_id, yield_date)`
  instead of the creation time, and adds the `ck_transactions_yield_date` check constraint (a `YIELD` must have a
  `yield_date`, and other types must not). `V13__system_transaction_attribution.sql` makes `performed_by_user_id`
  nullable again, adds the `performed_by_system` column (`BOOLEAN NOT NULL DEFAULT FALSE`), and adds the
  `ck_transactions_actor` check constraint (a system transaction must be a `YIELD` with no user, and any other
  transaction must reference a user). `V14__track_daily_yield_processing.sql` adds the `last_yield_processed_date`
  column to `savings_accounts`, the cursor of the daily yield processing. Existing accounts start from the later of
  their last yield date and the day before the migration (in `America/Sao_Paulo`), so no retroactive yield is granted
  for the historical period.
- `V15__reserve_system_username.sql` adds the `ck_users_username_not_system` check constraint, so no user can have the
  reserved username `system`. The migration fails if such a user already exists.
  `V16__add_transaction_balance_after.sql` adds `balance_after` to `transactions`, backfills the running balance for
  existing transactions in ID order, enforces a non-null value and the `ck_transactions_balance_after_range` constraint,
  and creates `idx_transactions_account_id_desc` to support latest-transaction lookups.
  `V17__create_transaction_idempotency_requests.sql` creates `transaction_idempotency_requests` to persist idempotency
  keys per user, the request fingerprint, operation type, and resulting transaction reference; it adds foreign keys,
  uniqueness constraints, and checks for supported operation types and SHA-256 request hashes.
  `V18__add_created_at_index_to_transaction_idempotency_requests.sql` adds `idx_transaction_idempotency_created_at` to
  support efficient cleanup of expired idempotency records. `V19__create_audit_events.sql` creates the persistent
  `audit_events` table for audit records, including actor/action/target metadata, a timestamp, and the correlation ID,
  with database-side protection against `UPDATE`, `DELETE`, and `TRUNCATE`.
- `V20__configure_application_privileges.sql` establishes the initial table and sequence grants for the `banking_app`
  runtime role and default privileges for objects created by `banking_migrator`. The effective runtime privilege policy
  is maintained by the `afterMigrate__apply_runtime_privileges.sql` callback.
- `V21__allow_runtime_updates_to_checking_accounts.sql` historically grants `UPDATE` on `checking_accounts` to support
  Hibernate's pessimistic locking with joined inheritance. The current callback supersedes this grant and revokes it
  because account locking now targets only the base `accounts` table.
- `V22__allow_auditor_user_role.sql` replaces `ck_users_role` so that `users.role` also accepts `AUDITOR`.
- `V23__add_audit_events_query_indexes.sql` adds the indexes that support the audit query API (see below). It uses a
  plain `CREATE INDEX`, which blocks writes to `audit_events` while each index is built; on a large existing table,
  create the indexes `CONCURRENTLY` before deploying.
- `V24__use_timezone_aware_timestamps.sql` converts `users.created_at`, `users.updated_at`, `transactions.created_at`,
  `audit_events.created_at`, and `transaction_idempotency_requests.created_at` to `TIMESTAMP(6) WITH TIME ZONE`.
  Legacy application timestamps are interpreted in `America/Sao_Paulo`, except for `users.created_at` of the
  `system_migration` technical user, which V24 initially interpreted as UTC. The original value was generated by V6
  using `CURRENT_TIMESTAMP` while the Flyway session used `America/Sao_Paulo`. V24's UTC interpretation introduced
  a three-hour offset in this particular timestamp. `V26__correct_system_migration_created_at_timezone.sql`
  corrects this historical interpretation, restoring the original local-time meaning without modifying
  `updated_at`. V24 also restores `CURRENT_TIMESTAMP` defaults for new `audit_events` and
  `transaction_idempotency_requests` records.
- `V25__enforce_transaction_row_integrity.sql` strengthens transaction
  integrity at the database level. It adds `ck_transactions_amount_max`
  to enforce the maximum transaction amount and
  `ck_transactions_transfer_code` to require a transfer code for
  `TRANSFER_SENT` and `TRANSFER_RECEIVED` transactions while rejecting
  transfer codes on other transaction types. Both check constraints
  are added as `NOT VALID`, preserving existing rows while enforcing
  the rules for new or updated records. It also creates the partial
  unique index `uk_transactions_transfer_code_type` to prevent duplicate
  transfer directions for the same transfer code. Finally, it reattributes
  legacy yields performed by `system_migration` as system-generated
  transactions and strengthens `ck_transactions_actor`: yields must be
  attributed to the system, while all other transaction types must
  reference a user.
- `V26__correct_system_migration_created_at_timezone.sql` corrects the historical timezone interpretation of
  `users.created_at` for the `system_migration` technical user. V24 interpreted the timestamp as UTC, although
  the original value was generated by V6 using `CURRENT_TIMESTAMP` in a Flyway session configured for
  `America/Sao_Paulo`. The migration reinterprets the existing instant using the original local-time convention.
  It updates only `system_migration.created_at`; `updated_at` and timestamps belonging to other users remain
  unchanged. The update is conditional: if `system_migration` is absent, as can happen in a test database after
  integration-test fixture cleanup, the migration affects zero rows and completes successfully.

- Flyway connections explicitly initialize their session timezone to
  `America/Sao_Paulo` through the SQL callback
  `afterConnect.sql`, located in `db/migration`. The callback is
  automatically discovered by Flyway configurations that include this
  location, including Spring Boot-managed connections and programmatically
  configured instances. This makes SQL migration timestamp handling
  independent of the PostgreSQL server and JVM default timezone settings.
  Migrations that use `CURRENT_TIMESTAMP` with timezone-naive columns must
  follow this explicit session-timezone convention.
- `V27__make_transactions_append_only.sql` makes `transactions` append-only for every database role, including the
  table owner, with `BEFORE UPDATE OR DELETE` and `BEFORE TRUNCATE` triggers, the same approach V19 uses for
  `audit_events`. See "Append-only ledger" below.
- Schema changes must be added as new versioned migrations (`V28__description.sql`, and so on). Migrations that were
  already applied must not be edited, because Flyway validates their checksums. V20 and V21 also change database
  privileges; do not edit them. Changes to the runtime role's privilege policy go in
  `db/callback/afterMigrate__apply_runtime_privileges.sql`, not in new migrations.

## Database roles and privileges

The local and production profiles use separate PostgreSQL login roles:

- `banking_app` is the runtime role used by the datasource. It must not own schema objects and must not be able to
  create objects in `public`.
- `banking_migrator` is the Flyway role. It applies migrations and owns the objects it creates, including
  `flyway_schema_history`.
- The test profile uses a separate test database and a dedicated test role (for example, `banking_test`) for both the
  datasource and Flyway. Do not use the restricted `banking_app` role to run integration tests that need to clean up
  fixtures.

`scripts/db/bootstrap-roles.sql` provisions the login roles and schema-level access; passwords are set separately and
are never stored in the script. The table and sequence grants below are applied by the Flyway callback
`src/main/resources/db/callback/afterMigrate__apply_runtime_privileges.sql`, which runs after every successful migrate.
It is the single source of truth for the runtime role's privileges: because it is re-applied on each startup, a grant
cannot be silently lost, and any manual change to the role's privileges is reverted on the next startup.

For upgrades of an **existing database at V19**, if its objects were created by an earlier database user, run
`scripts/db/transfer-existing-ownership.sql` as a PostgreSQL administrator against the application database after the
bootstrap script and before V20 is applied. It transfers ownership of existing tables, sequences, and the audit trigger
function to `banking_migrator`. The script deliberately rejects the test database and an upgrade where V20 is already
recorded as successful. Fresh databases do not need this script because Flyway creates their objects as
`banking_migrator`. See the [Development Guide](DEVELOPMENT.md#configuration) for the exact procedure.

| Table                              | SELECT | INSERT | UPDATE                                                                                       | DELETE |
|------------------------------------|:------:|:------:|----------------------------------------------------------------------------------------------|:------:|
| `accounts`                         |  Yes   |  Yes   | `balance`, `status`                                                                          |   No   |
| `addresses`                        |  Yes   |  Yes   | `city`, `complement`, `neighborhood`, `postal_code`, `state`, `street_name`, `street_number` |   No   |
| `audit_events`                     |  Yes   |  Yes   | No                                                                                           |   No   |
| `checking_accounts`                |  Yes   |  Yes   | No                                                                                           |   No   |
| `clients`                          |  Yes   |  Yes   | `name`, `email`, `phone_number`                                                              |   No   |
| `flyway_schema_history`            |   No   |   No   | No                                                                                           |   No   |
| `savings_accounts`                 |  Yes   |  Yes   | `last_yield_date`, `last_yield_processed_date`                                               |   No   |
| `transaction_idempotency_requests` |  Yes   |  Yes   | Yes                                                                                          |  Yes   |
| `transactions`                     |  Yes   |  Yes   | No                                                                                           |   No   |
| `users`                            |  Yes   |  Yes   | `password`, `active`, `token_version`, `updated_at`                                          |   No   |

`checking_accounts` has only the `id` column, and `banking_app` does not
receive any `UPDATE` privilege on it. Previously, V21 granted `UPDATE`
on this table because Hibernate's pessimistic lock over joined inheritance
also locked the subclass table. Account locking now uses an explicit
native query in `AccountRepository.lockIdByAccountNumber`, which executes
`SELECT id FROM public.accounts WHERE account_number = :accountNumber
FOR UPDATE`. After acquiring the lock on the base row, `AccountService`
loads the polymorphic entity through `findById`. This preserves
transaction-level serialization without requiring `UPDATE` on
`checking_accounts`.

PostgreSQL requires `SELECT` and `UPDATE` privileges on at least one
column of the table being locked by `SELECT ... FOR UPDATE`. The
`balance` and `status` column grants on `accounts` satisfy this
requirement. Account operations retain their existing transaction
boundaries, and transfers continue to acquire account locks in
deterministic account-number order.

Objects created later by `banking_migrator` in `public` receive no default privileges for `banking_app`: the callback
revokes the default table and sequence privileges instead of granting them, which also clears the default grants left
by earlier versions of the callback. A new table is therefore closed to the runtime role until it is granted
explicitly in `afterMigrate__apply_runtime_privileges.sql`, together with its entry in the `REVOKE` list so the policy
stays deterministic. Sequences backing `GENERATED ... AS IDENTITY` columns need no separate grant, because `INSERT` on
the table is enough; `serial` sequences and standalone sequences (such as `account_number_seq`) do.

The callback fails the startup, instead of skipping, when Flyway is not connected as `banking_migrator`, so a
misconfigured `local` or `prod` environment cannot run with missing or excess privileges. The `test` profile does not
load the `db/callback` location (`spring.flyway.locations`) and runs Flyway as the fixture owner. `V20` and `V21` are
kept unchanged because Flyway validates the checksums of applied migrations. They remain conditional on
`banking_migrator`, and the callback supersedes them: it first revokes everything and then re-grants the policy above.
The two-role behavior is verified in CI by two classes. `LeastPrivilegeSmokeTests` migrates an empty database as
`banking_migrator` and checks what `banking_app` can and cannot do, including that new objects are closed by default
and that the ledger triggers are enabled. `RuntimePrivilegesEndToEndTests` starts the application with the datasource
connected as `banking_app` and runs the real flows (deposit, withdrawal, transfer, account cancellation, daily yield,
password change, user deactivation, client and address updates), so a flow that dirties a column outside the policy
fails with `permission denied` in CI instead of in production.

- Tables use snake_case names, generally plural: `users`, `clients`, `addresses`, `accounts`, `checking_accounts`,
  `savings_accounts`, `transactions`, `transaction_idempotency_requests`, and `audit_events`.
- Constraints and indexes follow descriptive naming conventions: `uk_<table>_<column>` for conventional unique
  constraints or indexes, `fk_<table>_<reference>` for foreign keys, and `ck_<table>_<rule>` for check constraints.
  Composite and partial unique indexes use names that describe the enforced business rule, such as
  `uk_accounts_client_type_active` and `uk_transactions_daily_yield`.
- `Account` uses joined inheritance (`CheckingAccount` and `SavingsAccount` extend it), mapped to the `accounts`,
  `checking_accounts`, and `savings_accounts` tables.
- Entities subject to column-level runtime UPDATE privileges use Hibernate's `@DynamicUpdate`. This makes generated
  UPDATE statements include only modified columns, aligning Hibernate's dirty-checking behavior with the PostgreSQL
  column-level grants. When adding a new mutable entity attribute or update operation, its required column privilege
  must also be reviewed in the runtime privilege callback.
- Application timestamps migrated by V24 use `TIMESTAMP(6) WITH TIME ZONE` in PostgreSQL and `Instant` in Java, with
  microsecond precision. Flyway sessions use `America/Sao_Paulo` explicitly when executing migrations. Business dates
  such as `yield_date` remain date-only values and must not be treated as timestamps.
- Check constraints in the database enforce a non-negative account balance, a positive transaction amount within the
  maximum allowed value (`9999999999999.99`), and the allowed values of state, role, account type, account status, and
  transaction type.
- Three partial unique indexes enforce business rules: `uk_accounts_client_type_active` (one active account of each type
  per client), `uk_transactions_daily_yield` (one yield per account per reference date), and
  `uk_transactions_transfer_code_type` (at most one transaction per transfer direction for each transfer code).
- `audit_events` has indexes for the audit query filters. Each one ends with the same sort keys as the default listing
  (`created_at DESC, id DESC`), so the first page is read straight from the index: `idx_audit_events_created_at`,
  `idx_audit_events_event_type_created_at`, and `idx_audit_events_outcome_created_at`.
  `idx_audit_events_actor_username_prefix` indexes `lower(actor_username)` with `text_pattern_ops` and serves the
  case-insensitive prefix search of `actorUsername`; substring search is not supported because it would need the
  `pg_trgm` extension, and creating an extension requires privileges that `banking_migrator` does not have.
  `idx_audit_events_entity` and `idx_audit_events_actor` (from V19) serve lookups by affected entity and by actor ID.
- The database constraint and index names mapped to business errors are centralized in the `DatabaseConstraint` enum
  within the Java application code. Each name must match a constraint or index defined by a Flyway migration.
- Monetary values use `BigDecimal` (`precision = 19`, `scale = 2`).
- `Transaction` also has a `transfer_code` column (UUID, nullable, not updatable). It is filled only for `TRANSFER_SENT`
  and `TRANSFER_RECEIVED`, and both rows of a transfer share the same value. The `ck_transactions_transfer_code` check
  constraint requires a non-null value for transfer transactions and rejects it for other transaction types.
  The `uk_transactions_transfer_code_type` partial unique index prevents duplicate transactions of the same direction
  for a given transfer code. These database guarantees do not require both transfer directions to exist.
- The `ck_transactions_actor` check constraint enforces transaction actor attribution. `YIELD` transactions must have
  `performed_by_system = TRUE` and `performed_by_user_id = NULL`. All other transaction types must have
  `performed_by_system = FALSE` and a non-null `performed_by_user_id`. During V25, legacy yield transactions attributed
  to `system_migration` were reclassified as system-generated transactions.
- Append-only ledger. `trg_transactions_no_update_delete` and `trg_transactions_no_truncate` reject `UPDATE`, `DELETE`,
  and `TRUNCATE` on `transactions` for every role, so an accidental statement by the table owner or an administrator
  cannot rewrite financial history. A superuser can still bypass triggers deliberately. A migration that must correct
  ledger rows has to say so explicitly and restore the protection before it ends:
  `ALTER TABLE transactions DISABLE TRIGGER trg_transactions_no_update_delete;`, the corrective statements, then
  `ALTER TABLE transactions ENABLE TRIGGER trg_transactions_no_update_delete;`. Flyway runs each migration in a
  transaction, so a failure rolls the `DISABLE` back too. Tests that need to remove ledger rows use
  `LedgerTestCleaner`, which does the same inside one transaction.
- Concurrency is handled with pessimistic locking. `AccountRepository.lockIdByAccountNumber` executes a native
  `SELECT ... FOR UPDATE` against the base `accounts` table, and `AccountService` loads the corresponding polymorphic
  entity after acquiring the lock. Account locking methods require an existing transaction (`Propagation.MANDATORY`), so
  a lock cannot be acquired and released outside the operation that needs it. Transfers acquire locks in deterministic
  account-number order to avoid deadlocks.
- User operations use the same mechanism: `UserRepository.findByIdForUpdate` locks the row on deactivation,
  reactivation, and password change. Deactivating an `ADMIN` also locks every active admin before the last-admin check,
  so concurrent deactivations always leave at least one active administrator.
- `Transaction` has a `transaction_code` column (UUID, not null, not updatable) with the `uk_transactions_code` unique
  constraint, used as its public identifier.
- Open Session in View is disabled (`spring.jpa.open-in-view=false`). Account queries that need the client use
  `JOIN FETCH` to avoid lazy loading outside a transaction.
- Read operations in the services run with `@Transactional(readOnly = true)`, while operations that change data use
  regular transactions.
- For clients, the transaction boundary is in `ClientPersistenceService`: `ClientService` resolves the address first and
  only then calls it, so the transaction covers just the persistence work.
- Most entities have no public setters: `Client` and `Address` change through domain methods such as
  `Client.update(...)` and `Address.updateFrom(...)`. `User` changes only through its constructor, which normalizes the
  username and e-mail, and through `deactivate()`, `reactivate()`, and `changePassword(...)`. `deactivate()` (when the
  user is active) and `changePassword(...)` increment `tokenVersion`.