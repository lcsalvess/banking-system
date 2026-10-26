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

- Services publish events after creating or changing data: client `CREATED`/`UPDATED`, account `CREATED`/`CANCELLED`, user `CREATED`/`DEACTIVATED`/`REACTIVATED`/`PASSWORD_CHANGED`, and transactions (deposit, withdrawal, transfer, and yield, with `SYSTEM` as the actor for yield).
- Listeners use `@TransactionalEventListener` in the `AFTER_COMMIT` phase, so an operation is logged as successful only after its database transaction commits.
- The daily yield job logs the start and end of each run, including a summary of the results (`attempted`, `applied`, `alreadyProcessed`, `notEligible`, `noYield`, `balanceLimitReached`, `skippedAccounts`, and `failed`) and a status of `completed`, `completed with failures`, or `aborted`.
- The rate limiter logs each rejection with its operation, scope, and subject (the client IP for login rejections by IP, or `user:<id>` for password changes), never the username (see [Login Rate Limiting](SECURITY.md#login-rate-limiting) and [Password Change Rate Limiting](SECURITY.md#password-change-rate-limiting)).
- The global exception handler logs business errors, authentication and authorization failures, data integrity violations, address provider failures, and unexpected errors.
- The application log level is controlled by `logging.level.com.lcsalvess.bankingsystem` (`INFO` by default). SQL logging is disabled.

### Correlation ID

- Every request has a correlation ID, read from the `X-Correlation-ID` header.
- The header value is reused only when it is a canonical UUID and is normalized to lowercase. A missing, blank, or invalid value is replaced by a new random UUID.
- The ID is returned in the `X-Correlation-ID` response header, stored in the logging MDC as `correlationId`, included in log lines, and removed when the request ends.

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

| Counter                        | Description                                                                                                                                                             |
|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `security.rate_limit.rejected` | Requests rejected by the security rate limiter, tagged by `operation` (`login` or `password_change`) and `scope` (`client_ip`, `username_ip`, `username`, or `user_id`) |