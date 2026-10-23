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
- The login rate limiter logs each rejection with its reason and the client IP, never the username (see [Login Rate Limiting](SECURITY.md#login-rate-limiting)).
- The global exception handler logs business errors, authentication and authorization failures, data integrity violations, address provider failures, and unexpected errors.
- The application log level is controlled by `logging.level.com.lcsalvess.bankingsystem` (`INFO` by default). SQL logging is disabled.

### Correlation ID

- Every request has a correlation ID, read from the `X-Correlation-ID` header.
- The header value is reused only when it is a canonical UUID and is normalized to lowercase. A missing, blank, or invalid value is replaced by a new random UUID.
- The ID is returned in the `X-Correlation-ID` response header, stored in the logging MDC as `correlationId`, included in log lines, and removed when the request ends.

## Observability

- Spring Boot Actuator exposes `health`, `info`, and `metrics`. The `health` and `info` endpoints require an `EMPLOYEE` or `ADMIN` token, while `metrics` requires `ADMIN`. Health details are shown only to authorized users.
- The liveness (`/actuator/health/liveness`) and readiness (`/actuator/health/readiness`) probes are explicitly enabled and publicly accessible, allowing infrastructure health checks without authentication. They never expose health details. Other `/actuator/health/**` paths remain denied.
- Readiness becomes `ACCEPTING_TRAFFIC` only after the startup listeners return. The daily yield recovery therefore runs in a background thread so it does not block application readiness.
- Custom Micrometer timers:

| Timer                       | Description                                                  |
|-----------------------------|--------------------------------------------------------------|
| `address.lookup`            | Total time of an address lookup                              |
| `address.provider.lookup`   | Time of each provider query, tagged by `provider`            |
| `client.persistence.create` | Client creation inside the transaction, excluding the commit |
| `client.persistence.update` | Client update inside the transaction, excluding the commit   |

- Custom Micrometer counter:

| Counter                       | Description                                                                                                       |
|-------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `login.rate_limit.rejected`   | Login requests rejected by the rate limiter, tagged by `reason`: `client_ip`, `username_ip`, or `username`        |
