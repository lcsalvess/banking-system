# Business Rules

## Clients

- CPF must be unique and contain exactly 11 digits.
- E-mail must be unique, ignoring case, and can have up to 254 characters. It is trimmed and stored in lowercase.
- CPF validation includes format checks and verification digit calculation.
- Phone number must contain 10 to 11 digits, including the area code.
- The CPF is masked in responses, showing only the last two digits (`***.***.***-12`). The full value is stored in the database and is never returned by the API.
- Every client requires an address with a postal code (CEP), a street number, and an optional complement.
- Address details such as street name, neighborhood, city, and state are retrieved through external postal code providers.
- Client updates are partial: only the fields sent in the request are changed, and omitted fields are left unchanged.
- When an update includes an address, the postal code is looked up again through the providers, and the street number and complement are updated along with it.
- Client creation checks whether the CPF already exists. Concurrent requests are also handled: if another request saves the same CPF or e-mail first, the unique constraint violation is translated into `409 Conflict` with a message that identifies the field. E-mail uniqueness is enforced by the database.
- The postal code lookup (external calls) happens before the database transaction starts, so no database connection is held while waiting for the providers.
- Client creation and update are recorded in the audit trail (see [Audit Trail](#audit-trail)).

## Accounts

- A client can have at most one active checking account and one active savings account. A new account of the same type can be opened after the previous one is canceled.
- Account numbers are generated from a database sequence, padded to at least 5 digits (for example `00001`), followed by a check digit (modulo 11). The `account_number` column holds up to 20 digits.
- Every operation that receives an account number also requires the check digit.
- The account number must contain between 5 and 20 digits and the check digit exactly 1 digit. Otherwise, the request is rejected with `400 Bad Request` before reaching the service.
- The check digit is then validated against the account number before the account lookup, and a mismatch also returns `400 Bad Request`.
- An account can only be canceled if it is active and has a zero balance.
- Cancellation takes the same row lock as deposits, withdrawals, transfers, and yield. A deposit that runs at the same time cannot be lost: the operations are serialized, and the cancellation is rejected if the deposit commits first.
- Account creation and cancellation are recorded in the audit trail (see [Audit Trail](#audit-trail)).

## Transactions

- Deposits, withdrawals, and transfers require an active account and a positive amount.
- Each deposit, withdrawal, and transfer request must include an `Idempotency-Key` header containing a UUID. The key is scoped to the authenticated user.
- Reusing the same key with the same operation and request data returns the original transaction result without applying the financial operation again. Reusing a key with a different operation or request payload returns `409 Conflict`.
- The idempotency reservation, financial operation, and stored transaction reference participate in the same database transaction. If the operation rolls back, the reservation rolls back too, allowing a retry.
- Idempotency records older than the configured retention period are deleted by a scheduled job. The default retention is 7 days (`banking.idempotency.retention-days`), and the cleanup runs daily at 03:30 in `America/Sao_Paulo`. After a record is deleted, reusing its key can execute a new operation.
- Amounts accept at most 13 integer digits and 2 decimal places. An account balance cannot exceed `9999999999999.99` (also enforced by the `ck_accounts_balance_max` constraint); a deposit or transfer that would exceed it is rejected with `409 Conflict`. A yield that would exceed it is skipped for that day (see [Savings Yield](#savings-yield)).
- Withdrawals and transfers require sufficient balance.
- A transfer requires distinct and active source and destination accounts, and records one `TRANSFER_SENT` and one `TRANSFER_RECEIVED` transaction. Both share the same `transferCode` (UUID), which is returned only for transfer transactions.
- Every operation that changes a balance acquires a pessimistic write lock (`PESSIMISTIC_WRITE`) on the account row before validating the account status and current balance and applying the change. Request-level validation, such as checking that the amount is positive, occurs before the lock is acquired.
- A transfer locks both accounts in ascending account number order, regardless of the transfer direction. Two opposite transfers (A to B and B to A) cannot deadlock.
- Every pooled connection runs `SET lock_timeout = 3000`, so a request that waits more than 3 seconds for a row lock fails with `503 Service Unavailable` instead of holding a thread and a connection indefinitely.
- All operations that change balances run inside a database transaction, so balance changes and transaction records are saved together or not at all.
- Every transaction records who performed it, for auditing; this is not part of the API responses. Transactions requested through the API store the authenticated user, which is mandatory. Yield transactions are generated by the system, store no user, and are flagged with `performed_by_system`. The database requires every transaction to have either a user or the system flag, and only a `YIELD` can be a system transaction (`ck_transactions_actor`).
- Every transaction receives a random UUID (`transactionCode`) when it is created. It is unique and immutable, and serves as the public identifier used to retrieve an individual transaction. The internal numeric ID is not exposed by the API. Transfer transactions additionally expose the shared `transferCode`.
- A transaction can be retrieved by its `transactionCode`: `404 Not Found` if it does not exist and `400 Bad Request` if the value is not a valid UUID.
- By default, an account's transaction history is sorted by creation time in descending order, with the transaction ID in descending order as a tie-breaker. Clients can request other supported sort orders.

## Savings Yield

- Yield is credited automatically by a scheduled job (`DailyYieldScheduler`). It cannot be applied manually through the API.
- The job runs every day at 00:05 (`America/Sao_Paulo`). The schedule can be changed through `banking.yield.cron` (see [Configuration](DEVELOPMENT.md#configuration)).
- The daily rate is equivalent to 0.5% per month compounded over 30 days, `(1 + 0.005)^(1/30) - 1`, about 0.0166% per day. It is stored as the decimal constant `0.000166265204228477063643`, so the credited value does not depend on floating-point arithmetic. The yield is the rate applied to the balance at the start of the reference date, rounded to 2 decimal places (`HALF_UP`).
- The balance is rebuilt from the transaction history as it stood at 00:00 of the reference date, not read from the current balance. A deposit made on the same day does not earn yield that day, and each day of a catch-up is calculated on its own historical balance, so previous yields compound.
- Every yield transaction carries a `yield_date`, the date it refers to. An account becomes eligible on the day after its creation or after its last yield date.
- Each savings account keeps a `last_yield_processed_date`, the cursor of the processing. Dates are processed one at a time, in chronological order, and an out-of-order date is rejected. Each account and date pair runs in its own database transaction, under the account row lock.
- Missed days are credited by the next run, one day at a time. If the application was down, the job also runs once right after startup (in every profile except `test`) to recover them. This recovery runs in a background thread, so a long catch-up does not delay the readiness probe, and a failure in it is logged without affecting the application. Running it alongside the scheduled job is safe because the processing is idempotent.
- Processing is idempotent. A date that was already processed is skipped, and the partial unique index `uk_transactions_daily_yield` (`account_id`, `yield_date`) guarantees a single yield per account per date, even with concurrent executions or several application instances.
- A date is marked as processed even when no yield is credited: the account is not eligible yet, the yield rounds to zero, or crediting it would exceed the balance limit. Skipped days are not credited retroactively.
- If processing an account fails, the date remains pending and the next run resumes from it. Transient database failures receive up to three total attempts (the initial attempt plus two retries), with delays of 200 ms and 400 ms before the retries. A persistent per-account failure stops further processing for that account, while other accounts continue. A failure while retrieving the next batch of accounts aborts the overall run.
- Active savings accounts are read in batches of 100, ordered by ID.
- Yield transactions are created by the system, with no associated user, and are logged with `SYSTEM` as the actor.

## Balance Reconciliation

- A scheduled job compares account balances with the `balance_after` snapshot of the latest transaction and logs any mismatches, including the first transaction where the balance chain diverges when it can be identified.
- Reconciliation is diagnostic only: it does not modify account balances or transaction history automatically.
- The job runs daily at 03:00 by default in `America/Sao_Paulo`; its schedule can be configured with `banking.reconciliation.cron`.
- The diagnostic check also runs asynchronously after application startup, except in the `test` profile. Startup execution makes up to three total attempts if an execution error occurs; persistent errors are logged, and the next scheduled run may try again.

## Users

- Only `ADMIN` users can create new users.
- Usernames have up to 20 characters and may contain only letters, digits, dot, hyphen, and underscore. They are trimmed and converted to lowercase on creation and on login, so usernames are case-insensitive.
- E-mails can have up to 254 characters and are trimmed and stored in lowercase. Usernames and e-mails must be unique.
- When creating a user or setting a new password, the password must contain 8–64 characters, at least one uppercase Unicode letter, one lowercase Unicode letter, one ASCII digit, and one Unicode punctuation or symbol character. It must also be no more than 72 bytes when encoded in UTF-8, the limit supported by BCrypt.
- The complexity rule is not reapplied to the login password or the current password during a password change, so existing credentials are not rejected solely for lacking the new composition requirements. The existing character-length and UTF-8 byte-length checks still apply to these fields.
- Passwords are stored as BCrypt hashes (strength 12).
- An initial `ADMIN` is created at startup from environment variables when no admin exists (see [Configuration](DEVELOPMENT.md#configuration)). The application refuses to start when no admin exists and the credentials are not configured, in every profile except `local` and `test`, which relax this rule.
- Username uniqueness is case-insensitive and enforced by the database through a unique index on `LOWER(username)`.
- The username `system` (case-insensitive, ignoring surrounding spaces) is reserved for the actor of system-generated operations, such as the daily yield, and is rejected when creating a user and for the initial admin. The `ck_users_username_not_system` check constraint enforces the same rule in the database.
- `system_migration` is a reserved technical user created by migration `V6` and hardened by `V8` with a BCrypt password hash and the reserved email address `system-migration@invalid.example`. It is inactive and cannot authenticate. It is the recorded actor for transactions that already existed when V6 was applied, because their original actors could not be reliably determined.
- Only `ADMIN` users can list, deactivate, and reactivate users. The listing is paginated and can be filtered by `search` (username or e-mail), `role`, and `active`.
- Deactivating a user is idempotent: an already inactive user is returned unchanged. A deactivated user cannot log in (the response is the same `401 Unauthorized` as for invalid credentials) and is rejected on every request, even with a token that was still valid.
- The last active `ADMIN` cannot be deactivated (`409 Conflict`). The active admins are locked (`SELECT ... FOR UPDATE`) while the check runs, so concurrent deactivations cannot leave the system without an administrator.
- Every user has a `token_version`, included in the JWT as the `tv` claim. Deactivation and password change increment it, so tokens issued before either event stay invalid, even if the account is reactivated later.
- Reactivation is idempotent. The reserved `system_migration` user can never be reactivated (`409 Conflict`), and the `ck_users_system_migration_inactive` check constraint guarantees it stays inactive.
- Any authenticated user can change their own password through `PATCH /api/v1/auth/password`, sending the current and the new password. A successful change returns `204 No Content`.
- The current password must be correct, and the new password must differ from it. Otherwise the request is rejected with `400 Bad Request`.
- A password change locks the user row, so concurrent changes are serialized. It increments the `token_version`, so every token issued before the change, including the one used in the request, is rejected with `401 Unauthorized`, and the user must log in again.

## Address Lookup Integration

- Address lookup is an internal integration, not a standalone public HTTP endpoint. It is used during client creation and when a client update includes address data.
- The postal code must contain exactly 8 digits, without a hyphen.
- Providers are queried in order: ViaCEP first, then BrasilAPI as a fallback. New providers can be added by implementing `AddressProvider` and defining their order with `@Order`.
- If a provider fails or does not find the postal code, the next provider is tried.
- Requests to providers use a 3-second connection timeout and a 5-second read timeout.
- Provider responses are validated. Required address fields include street, neighborhood, city, state, and an 8-digit postal code. An invalid response is treated as a provider failure.
- Provider failures are handled separately from postal codes that are not found:
    - `404 Not Found` when every provider answered and none found the postal code.
    - `503 Service Unavailable` when at least one provider failed and no other provider found the postal code. These responses are returned by the client endpoints that depend on the lookup.

## Audit Trail
- Successful operations covered by the audit trail are recorded in `audit_events` within the same database transaction as the business change. If the operation rolls back, its audit record rolls back as well. If recording the audit event fails, the business transaction cannot commit.
- Audited operations include user creation, deactivation, reactivation, and password change; client creation and update; account creation and cancellation; deposits, withdrawals, and transfers. Daily yield credits are not persisted in `audit_events`: their ledger transactions are attributed to the system through `performed_by_system`, and yield processing is logged by the scheduled job. Separate audit events for individual yield credits and daily yield executions are not currently generated.
- Each record stores the event type, outcome, actor type and identifiers (user ID and username snapshot for authenticated users), entity type and identifier (database IDs for users, clients, and accounts; transaction and transfer codes), the correlation ID when available, and the timestamp. Audit events do not copy client profile details such as CPF, e-mail, phone number, or address.
- Database triggers reject `UPDATE`, `DELETE`, and `TRUNCATE` on the table, so the application can only append records.
- The current production audit-writing flow records `SUCCESS` outcomes only. `FAILURE` and `DENIED` are defined in the data model and supported as query filter values, but are not currently generated by the audit-writing flow.