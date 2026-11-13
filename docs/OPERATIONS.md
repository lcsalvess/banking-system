# Operations

## Error Handling

Errors are handled by a single global exception handler (`GlobalExceptionHandler`), and business rule violations extend a common `BusinessException`. All error messages are centralized in `ApiErrorMessages`.

Application errors return a consistent structure:

```json
{
  "status": 400,
  "message": "O valor informado é maior do que o saldo."
}
```

Validation errors also include the invalid fields. The same structure is used for request bodies, path parameters, and query parameters:

```json
{
  "status": 400,
  "message": "Erro de validação.",
  "errors": {
    "postalCode": "O CEP deve conter exatamente 8 dígitos."
  }
}
```

| Scenario                                                                                                                                | Status                      |
|-----------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| Business rule violation                                                                                                                 | Defined by each exception   |
| Bean Validation failure, invalid parameters, malformed body, or wrong JSON type                                                         | `400 Bad Request`           |
| Invalid credentials, invalid or revoked token, or deactivated user                                                                      | `401 Unauthorized`          |
| Login rate limit reached, with a `Retry-After` header                                                                                   | `429 Too Many Requests`     |
| Access denied because the role is not allowed for the endpoint                                                                          | `403 Forbidden`             |
| Duplicate CPF, email, or username, including concurrent requests, or state conflicts such as deactivating the last active administrator | `409 Conflict`              |
| Address provider or database unavailable, row-lock wait timeout, or authentication infrastructure unavailable                           | `503 Service Unavailable`   |
| Unexpected error                                                                                                                        | `500 Internal Server Error` |

Database integrity violations are translated by constraint name through the `DatabaseConstraint` enum. A duplicate client CPF or email, a duplicate username or user email, a second active account of the same type, a balance above the limit, a reserved username, or an attempt to activate the `system_migration` user returns `409 Conflict` with a message identifying the problem. Other integrity violations return `500 Internal Server Error` with a generic message.

Only the SQLState and constraint name are logged for database integrity violations, never the exception detail, because database details may contain row values. The constraint is identified by the name reported by the database, not by the exception message.

## Logging

Operations are logged through Spring application events:

- Services publish events after creating or changing data: client `CREATED`/`UPDATED`, account `CREATED`/`CANCELLED`, user `CREATED`/`DEACTIVATED`/`REACTIVATED`/`PASSWORD_CHANGED`/`ROLE_CHANGED`, and transactions (deposit, withdrawal, transfer, and yield, with `SYSTEM` as the actor for yield).
- Listeners use `@TransactionalEventListener` in the `AFTER_COMMIT` phase, so an operation is logged as successful only after its database transaction commits.
- The daily yield job logs the start and end of each run, including a summary of the results (`attempted`, `applied`, `alreadyProcessed`, `notEligible`, `noYield`, `balanceLimitReached`, `skippedAccounts`, and `failed`) and a status of `completed`, `completed with failures`, or `aborted`.
- The rate limiter logs each rejection with its operation, scope, and subject (the client IP for login rejections by IP, or `user:<id>` for password changes), never the username (see [Login Rate Limiting](SECURITY.md#login-rate-limiting) and [Password Change Rate Limiting](SECURITY.md#password-change-rate-limiting)).
- Audit trail writes for security and HTTP events are best-effort. A failed write is logged at `ERROR` with the event type and outcome, and counted in `audit.detached.write.failures`. While the 5-second pause after a database availability failure is active, skipped events are counted and logged at `DEBUG` only (see [Audit of Security Events](SECURITY.md#audit-of-security-events)).
- The audit query snapshot cleanup logs the number of deleted snapshots at `INFO` when it removes any, and at `DEBUG` otherwise.
- The global exception handler logs business errors, authentication and authorization failures, data integrity violations, address provider failures, and unexpected errors.
- The application log level is controlled by `logging.level.com.lcsalvess.bankingsystem` (`INFO` by default). SQL logging is disabled.

### Correlation ID

- Every request has a correlation ID, read from the `X-Correlation-ID` header.
- The header value is reused only when it is a canonical UUID and is normalized to lowercase. A missing, blank, or invalid value is replaced by a new random UUID.
- The ID is returned in the `X-Correlation-ID` response header, stored in the logging MDC as `correlationId`, included in log lines, and removed when the request ends.

## Administrator Bootstrap and Recovery

At least one active `ADMIN` account is required for administrator-managed operations. Startup may continue without an active administrator only when no `ADMIN` account exists and bootstrap is optional. The startup behavior is determined by the administrator bootstrap configuration described in the [Development Guide](DEVELOPMENT.md#initial-administrator-bootstrap).

During startup, `AdminBootstrapRunner` checks for an active administrator:

- If an active administrator exists, bootstrap is skipped.
- If administrator accounts exist but none are active, startup fails. The application does not automatically create another administrator.
- If no administrator account exists, bootstrap follows the configuration described in the [Development Guide](DEVELOPMENT.md#initial-administrator-bootstrap).

### Recovering Access When All Administrators Are Inactive

The regular user reactivation endpoint (`PATCH /api/v1/users/{employeeId}/reactivate`) requires an authenticated `ADMIN` account. It cannot be used when all administrators are inactive.

In this situation, administrator access must be restored through a controlled database recovery procedure:

1. Stop all application instances to prevent concurrent application operations.
2. Create a database backup before making changes.
3. Identify an existing, trusted human administrator using the `employee_id`, full name, and username. Do not create another administrator or reactivate an account without verifying its ownership.
4. Connect to PostgreSQL using an authorized administrative database account, not the application's runtime credentials (`banking_app`).
5. Reactivate only the selected account after confirming that it belongs to a human user with the `ADMIN` role and is currently inactive. Update its `active` status and `updated_at`, and increment `token_version` to invalidate previously issued tokens.
6. Insert a corresponding `USER_REACTIVATED` event into the append-only `audit_events` table in the same database transaction. Use `OPERATIONAL_RECOVERY` as the reason and identify the affected user through the internal database ID. Set the actor type to `ANONYMOUS`, with `actor_user_id` and `actor_username` set to `NULL`, because the operation did not originate from an authenticated application user.
7. Verify that exactly one user was updated and that the audit event was inserted successfully. Commit the transaction only after these checks succeed; otherwise, roll it back.
8. Record the database operator, recovery reason, affected employee ID, and change reference in the operational change log.
9. Restart the application and verify that startup succeeds, readiness is healthy, and the restored administrator can authenticate.

This procedure restores an existing administrator account; it does not reset its password or create a new administrator. Password recovery, when necessary, must follow a separate authorized procedure.

After recovery, normal user management should be performed through the application API.

## Observability

- Spring Boot Actuator exposes `health`, `info`, and `metrics`. The `health` and `info` endpoints require an `EMPLOYEE` or `ADMIN` token, while `metrics` requires `ADMIN`. Health details are shown only to authorized users.
- The liveness (`/actuator/health/liveness`) and readiness (`/actuator/health/readiness`) probes are explicitly enabled and publicly accessible, allowing infrastructure health checks without authentication. They never expose health details. Other `/actuator/health/**` paths remain denied.
- Readiness becomes `ACCEPTING_TRAFFIC` only after the startup listeners return. Daily yield recovery and transaction idempotency cleanup submit their work to the dedicated `startupRecoveryExecutor`, so neither task blocks application readiness. The executor runs these tasks on a single background thread.
- **Transaction idempotency cleanup:** runs daily at 03:30 in the `America/Sao_Paulo` time zone and once after application startup. The startup execution recovers cleanup missed while the application was offline. The job removes records older than the configured retention period (`banking.idempotency.retention-days`, 7 days by default) and logs the number of deleted records and the retention period.
- Custom Micrometer timers:

| Timer                       | Description                                                  |
|-----------------------------|--------------------------------------------------------------|
| `address.lookup`            | Total time of an address lookup                              |
| `address.provider.lookup`   | Time of each provider query, tagged by `provider`            |
| `client.persistence.create` | Client creation inside the transaction, excluding the commit |
| `client.persistence.update` | Client update inside the transaction, excluding the commit   |

- Custom Micrometer counter:

| Counter                         | Description                                                                                                                                                                                   |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `security.rate_limit.rejected`  | Requests rejected by the security rate limiter, tagged by `operation` (`login` or `password_change`) and `scope` (`client_ip`, `username_ip`, `username`, or `user_id`)                       |
| `audit.detached.write.failures` | Security and HTTP audit events that could not be written, including events skipped during the 5-second pause after a database failure. A sustained rise means the audit store is unavailable. |
| `audit.detached.deduplicated`   | Security and HTTP audit events skipped as repeats of the same event within the one-minute window. A sudden rise indicates repeated blocked or failing requests.                               |
