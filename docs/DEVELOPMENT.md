# Development Guide

## Prerequisites

- Java 21
- PostgreSQL 18
- Maven (or the included Maven Wrapper)
- Git

## Configuration

### 1. Create the databases

Create the empty databases. Flyway creates and manages the schema on the first startup.

```sql
CREATE DATABASE banking_system;
CREATE DATABASE banking_system_test;
```

### 2. Choose the Spring profile

Set the `SPRING_PROFILES_ACTIVE` environment variable to select the active profile.

| Profile | Database                                                                  | Swagger UI / OpenAPI | Admin bootstrap required |
|---------|---------------------------------------------------------------------------|----------------------|--------------------------|
| `local` | `DB_URL`, defaulting to `jdbc:postgresql://localhost:5432/banking_system` | Enabled              | No                       |
| `prod`  | `DB_URL` is required                                                      | Disabled             | Yes                      |
| `test`  | `jdbc:postgresql://localhost:5432/banking_system_test`                    | Disabled             | No                       |

Without an active profile, `DB_URL` is required, API documentation is disabled, and the initial administrator is required at startup, as in the `prod` profile. The `app.admin.bootstrap.required` property defaults to `true`; only `local` and `test` disable this requirement.

Use the `local` profile for development.

### 3. Configure environment variables

| Variable                 | Required                          | Description                                                                     |
|--------------------------|-----------------------------------|---------------------------------------------------------------------------------|
| `SPRING_PROFILES_ACTIVE` | No                                | Active profile (`local`, `prod`, or `test`)                                     |
| `DB_URL`                 | Yes, except in `local` and `test` | JDBC URL of the PostgreSQL database                                             |
| `DB_USERNAME`            | Yes                               | PostgreSQL username                                                             |
| `DB_PASSWORD`            | Yes                               | PostgreSQL password                                                             |
| `JWT_SECRET`             | Yes                               | Base64-encoded secret used to sign JWTs, with at least 256 bits of key material |
| `ADMIN_USERNAME`         | No                                | Username of the initial administrator                                           |
| `ADMIN_EMAIL`            | No                                | Email address of the initial administrator                                      |
| `ADMIN_PASSWORD`         | No                                | Password of the initial administrator                                           |

The initial administrator is created only when no `ADMIN` user exists and all three `ADMIN_*` variables are configured. `ADMIN_PASSWORD` must satisfy the same password policy used for user creation, including complexity and size limits.

If no administrator exists and the required variables are missing, the application logs a warning and starts in `local` and `test`. In other profiles, including `prod` and when no profile is active, startup fails. This prevents the application from running without an administrator when the bootstrap requirement is enabled.

The initial administrator can manage users but cannot operate on clients, accounts, or transactions. Create an `EMPLOYEE` user to perform those operations. See the [API examples](API.md#examples).

Generate a suitable JWT secret with:

```bash
openssl rand -base64 32
```

The JWT lifetime (`jwt.expiration`, in seconds) and issuer (`jwt.issuer`) are defined in `application.properties`.

### 4. Additional configuration

The daily yield job runs at 00:05 by default, using the cron expression `0 5 0 * * *`. To change the schedule, configure `banking.yield.cron` with a Spring cron expression.

The job uses the `America/Sao_Paulo` time zone.

The base URLs for the address lookup providers are defined in `application.properties`:

- `integration.address.viacep.base-url`
- `integration.address.brasilapi.base-url`

## Running

Clone the repository:

```bash
git clone https://github.com/lcsalvess/banking-system.git
cd banking-system
```

### Linux / macOS

Set the active profile and start the application:

```bash
export SPRING_PROFILES_ACTIVE=local
./mvnw spring-boot:run
```

### Windows (PowerShell)

Set the active profile and start the application:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd spring-boot:run
```

The API will be available at:

`http://localhost:8080`

## API Documentation

With the application running under the `local` profile, open Swagger UI at:

`http://localhost:8080/swagger-ui/index.html`

Use the **Authorize** button and enter the JWT returned by `/api/v1/auth/login` to call protected endpoints.

In the `prod` profile, and when no profile is active, the OpenAPI documentation and Swagger UI are disabled.

For request examples, see the [API reference](API.md).

## Testing

The test suite runs against PostgreSQL using the `test` profile and the `banking_system_test` database. Flyway applies the same migrations used by the application, and Hibernate validates the resulting schema.

### Running the tests

#### Linux / macOS

```bash
./mvnw verify
```

#### Windows (PowerShell)

```powershell
.\mvnw.cmd verify
```

Tests require a local PostgreSQL instance and the `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` environment variables.

### Test coverage

The test suite contains more than 450 test methods, including parameterized tests, and covers the following areas:

- **Service unit tests** using JUnit and Mockito for accounts, clients, persistence operations, transactions, daily yield processing, users, password changes, JWTs, authentication, user details loading, address resolution, and address provider orchestration.
- **Daily yield processing tests** for scheduler batching, retries, per-account failure isolation, chronological catch-up, interrupted runs, asynchronous startup recovery, executor hand-off, and the default dedicated thread.
- **Concurrency integration tests** (`TransactionConcurrencyTests`) against PostgreSQL. These cover concurrent withdrawals that must not overdraw an account, deposits that must not be lost, mixed deposits and withdrawals, opposite transfers without deadlocks, multiple transfers into the same account, transfers constrained by available balance, concurrent daily yield processing, and account cancellation racing with deposits. The class also checks start-of-day balance calculations and recovery of pending yield dates with compounded yields.
- **User integration tests** against PostgreSQL. `PasswordChangeIntegrationTests` covers password changes, login using the new password, token revocation after password changes, validation failures, deactivated users, tokens remaining revoked after reactivation, and concurrent password changes. `UserDeactivationConcurrencyTests` verifies that concurrent administrator deactivations leave one active administrator and that simultaneous duplicate deactivations are idempotent.
- **Transaction idempotency integration tests** against PostgreSQL. `TransactionIdempotencyIntegrationTests` covers repeated requests, replay of the original result, conflicts when a key is reused with different data, rollback behavior, and concurrent requests. `TransactionIdempotencyCleanupIntegrationTests` verifies retention cleanup.
- **Database tests** (`DatabaseLockTimeoutTests`) for lock timeouts applied to pooled connections and the maximum balance constraint. `ReservedUsernameConstraintTests` verifies that PostgreSQL rejects reserved usernames regardless of case and accepts names that merely contain the reserved term.
- **Security tests** for JWT generation and validation, secret and issuer validation, token expiration, credential version, malformed version claims, public routes, token validation, method security, Actuator access, public liveness and readiness probes, correlation IDs on error responses, and the authentication entry point.
- **Login rate-limiting unit tests** for IP-based token consumption and refill, failed authentication attempt tracking, and temporary lockouts.
- **Controller tests** using `MockMvc` for clients, accounts, transactions, and authentication. They include account number and digit validation, transaction lookup by public transaction code, invalid UUID handling, and password changes. Address lookup is covered by service, orchestration, and provider-client tests rather than a standalone lookup endpoint.
- **Provider client tests** for ViaCEP and Brasil API using a local HTTP server. These cover response mapping, postal codes not found, invalid responses, error statuses, timeouts, and unreachable providers.
- **JSON coercion tests** that import `JacksonConfig` into a controller test slice and verify that wrongly typed fields are rejected.
- **Validation tests** for CPF, account numbers, account digits, password size in bytes, password complexity (including passwords containing line breaks), and reserved usernames.
- **Correlation ID filter tests** for ID generation, reuse, lowercase normalization, rejection of non-canonical values, and MDC cleanup.
- **Other unit tests** for the global exception handler, database constraint mapping, pagination tie-breakers, the administrator bootstrap runner, and application startup.

The tests use the `test` Spring profile and the `banking_system_test` database. They do not use an in-memory substitute for PostgreSQL.