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

#### Provision the local application and migration roles

From the repository root, run `scripts/db/bootstrap-roles.sql` as a PostgreSQL administrator against the `banking_system` database. The script creates or hardens the login roles `banking_app` and `banking_migrator`, grants database connection and schema privileges, and prevents the runtime role from creating schema objects. It intentionally does not set or store passwords.

If `psql` is installed and available in `PATH`, run:

```powershell
psql -h localhost -U postgres -d banking_system -v ON_ERROR_STOP=1 -f scripts/db/bootstrap-roles.sql
```

Alternatively, in pgAdmin, connect to `banking_system` as a PostgreSQL administrator, open **Query Tool**, paste the full contents of `scripts/db/bootstrap-roles.sql`, and execute it. Run this bootstrap script before the first application startup. It is safe to rerun for the same roles; existing passwords are not changed by the script.

Set passwords separately and securely. For example, use pgAdmin's login-role properties, or connect with `psql` as an administrator and run `\password banking_app` and `\password banking_migrator` to set them through password prompts. Do not put real passwords in this script or commit them to source control.

For an **existing application database** whose objects were created by a previous database user, and which has successfully applied V19 (before or after V20/V21), run `scripts/db/transfer-existing-ownership.sql` as a PostgreSQL administrator against that application database, after the bootstrap script and before starting the new application version. It transfers the existing tables, sequences, and audit trigger function to `banking_migrator`, which the privilege callback requires in order to apply the least-privilege grants. If the application already fails at startup because `banking_migrator` cannot revoke privileges on these tables, run the script and start the application again. The script is idempotent and refuses to run against `banking_system_test`. Do not run it on a new, empty database.

If `psql` is available, run:

```powershell
psql -h localhost -U postgres -d banking_system -v ON_ERROR_STOP=1 -f scripts/db/transfer-existing-ownership.sql
```

Alternatively, connect to the existing application database as an administrator in pgAdmin, open **Query Tool**, paste the full contents of the script, and execute it. The script runs its ownership changes in a transaction.

### 2. Choose the Spring profile

Set the `SPRING_PROFILES_ACTIVE` environment variable to select the active profile.

| Profile | Database                                                                  | Swagger UI / OpenAPI | Admin bootstrap required |
|---------|---------------------------------------------------------------------------|----------------------|--------------------------|
| `local` | `DB_URL` is required                                                      | Enabled              | No                       |
| `prod`  | `DB_URL` is required                                                      | Disabled             | Yes                      |
| `test`  | `jdbc:postgresql://localhost:5432/banking_system_test`                    | Disabled             | No                       |

Without an active profile, `DB_URL` is required, API documentation is disabled, and the initial administrator is required at startup, as in the `prod` profile. The `app.admin.bootstrap.required` property defaults to `true`; only `local` and `test` disable this requirement.

Use the `local` profile for development.

### 3. Configure environment variables

| Variable                 | Required                          | Description                                                                               |
|--------------------------|-----------------------------------|-------------------------------------------------------------------------------------------|
| `SPRING_PROFILES_ACTIVE` | No                                | Active profile (`local`, `prod`, or `test`)                                               |
| `DB_URL`                 | Yes, except in `test`             | JDBC URL of the PostgreSQL database                                                       |
| `DB_USERNAME`            | Yes                               | PostgreSQL username for the application datasource                                        |
| `DB_PASSWORD`            | Yes                               | Password for the application datasource user                                              |
| `DB_MIGRATOR_USERNAME`   | Yes, except in `test`             | PostgreSQL username used by Flyway to apply database migrations                           |
| `DB_MIGRATOR_PASSWORD`   | Yes, except in `test`             | Password for the Flyway migration user                                                    |
| `JWT_SECRET`             | Yes                               | Base64-encoded secret used to sign JWTs, with at least 256 bits of key material           |
| `ADMIN_USERNAME`         | No                                | Username of the initial administrator                                                     |
| `ADMIN_EMAIL`            | No                                | Email address of the initial administrator                                                |
| `ADMIN_PASSWORD`         | No                                | Password of the initial administrator                                                     |
| `PROXY_IP_HEADER`        | No                                | Header with the original client IP behind a reverse proxy (for example `X-Forwarded-For`) |
| `TRUSTED_PROXIES`        | No                                | Regular expression with the IPs of the trusted proxies; without it the header is ignored  |

The application and Flyway use separate database credentials in the `local` and `prod` profiles.

- `DB_USERNAME` and `DB_PASSWORD` identify the runtime database user (`banking_app`). This user performs application operations and should receive only the table and sequence privileges required by the application. It should not own database objects or have privileges to change the database schema.
- `DB_MIGRATOR_USERNAME` and `DB_MIGRATOR_PASSWORD` identify the Flyway migration user (`banking_migrator`). This user applies schema migrations and owns the database objects managed by Flyway.

Before starting the application in `local` or `prod`, provision the roles and schema access with `scripts/db/bootstrap-roles.sql`, set both role passwords securely, and configure all four database environment variables. After each migration run, Flyway executes the callback `db/callback/afterMigrate__apply_runtime_privileges.sql` as `banking_migrator` to configure the runtime role's table, sequence, and default privileges. If Flyway connects as any other user, the callback fails the startup with an explicit error: verify `DB_MIGRATOR_USERNAME` and `DB_MIGRATOR_PASSWORD`. The `test` profile does not load this callback.

Database passwords must be provided through environment variables or the run configuration and must not be committed to source control.

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

The transaction idempotency cleanup runs daily at 03:30 by default, using the Spring cron expression configured by `banking.idempotency.cleanup-cron`. Its time zone is `America/Sao_Paulo`.

The retention period is configured by `banking.idempotency.retention-days` and defaults to 7 days. Expired records are also cleaned up asynchronously after application startup, so cleanup missed while the application was offline does not have to wait until the next scheduled run.

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

The `test` profile uses `DB_USERNAME` and `DB_PASSWORD` for both the application datasource and Flyway. For local test execution, these credentials must belong to a dedicated test database user with permission to apply migrations and clean up integration-test fixtures in `banking_system_test`. Do not use the runtime `banking_app` credentials for this purpose: its production-style grants intentionally prohibit physical deletion from business tables and transaction history. The GitHub Actions workflow uses a dedicated `banking_test` user and a separate `banking_system_test` database. The privilege callback is not loaded in the `test` profile, which keeps test fixture permissions separate from local/production privileges. That separation is covered by `LeastPrivilegeSmokeTests`, which runs only when the `PRIVILEGE_TEST_*` environment variables are set (the `least-privilege` job in GitHub Actions sets them). It needs an empty database with the roles from `scripts/db/bootstrap-roles.sql`:

```bash
PRIVILEGE_TEST_DB_URL=jdbc:postgresql://localhost:5432/banking_privilege_test \
PRIVILEGE_TEST_MIGRATOR_USER=banking_migrator PRIVILEGE_TEST_MIGRATOR_PASSWORD=... \
PRIVILEGE_TEST_APP_USER=banking_app PRIVILEGE_TEST_APP_PASSWORD=... \
./mvnw test -Dtest=LeastPrivilegeSmokeTests
```

### Running the tests

#### Linux / macOS

```bash
./mvnw verify
```

#### Windows (PowerShell)

```powershell
.\mvnw.cmd verify
```

Tests require a local PostgreSQL instance and the `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` environment variables. In the `test` profile, `DB_URL` is defined by `application-test.properties`, so it is not required.

### Test coverage

The test suite contains more than 450 test methods, including parameterized tests, and covers the following areas:

- **Service unit tests** using JUnit and Mockito for accounts, clients, persistence operations, transactions, daily yield processing, users, password changes, JWTs, authentication, user details loading, address resolution, and address provider orchestration.
- **Daily yield processing tests** for scheduler batching, retries, per-account failure isolation, chronological catch-up, interrupted runs, asynchronous startup recovery, executor hand-off, and the default dedicated thread.
- **Concurrency integration tests** (`TransactionConcurrencyTests`) against PostgreSQL. These cover concurrent withdrawals that must not overdraw an account, deposits that must not be lost, mixed deposits and withdrawals, opposite transfers without deadlocks, multiple transfers into the same account, transfers constrained by available balance, concurrent daily yield processing, and account cancellation racing with deposits. The class also checks start-of-day balance calculations and recovery of pending yield dates with compounded yields.
- **User integration tests** against PostgreSQL. `PasswordChangeIntegrationTests` covers password changes, login using the new password, token revocation after password changes, validation failures, deactivated users, tokens remaining revoked after reactivation, and concurrent password changes. `UserDeactivationConcurrencyTests` verifies that concurrent administrator deactivations leave one active administrator and that simultaneous duplicate deactivations are idempotent.
- **Transaction idempotency tests** against PostgreSQL and unit tests. `TransactionIdempotencyIntegrationTests` covers repeated requests, replay of the original result, conflicts when a key is reused with different data, rollback behavior, and concurrent requests. `TransactionIdempotencyCleanupIntegrationTests` verifies retention cleanup against PostgreSQL. `TransactionIdempotencyCleanupStartupRecoveryTests` verifies asynchronous startup scheduling, cleanup execution, and handling of unexpected failures.
- **Database tests** (`DatabaseLockTimeoutTests`) for lock timeouts applied to pooled connections and the maximum balance constraint. `ReservedUsernameConstraintTests` verifies that PostgreSQL rejects reserved usernames regardless of case and accepts names that merely contain the reserved term.
- **Security tests** for JWT generation and validation, secret and issuer validation, token expiration, credential version, malformed version claims, public routes, token validation, method security, Actuator access, public liveness and readiness probes, correlation IDs on error responses, and the authentication entry point.
- **Login rate-limiting tests** at three levels. Unit tests for the protection service cover IP token consumption and refill, malformed IPs, attempt counting per username and IP and per username, lock expiry without extension, window reset, successful-login reset, attempt cancellation, isolation between IPs and users, `Retry-After` rounding, metrics, and parallel requests that must not exceed the limit. Unit tests for `AuthService`, the global exception handler, and `LoginRateLimitProperties` cover the rejection path, the `429` response with `Retry-After`, and configuration validation. `AuthControllerTest` exercises the limits end to end against PostgreSQL, including that a legitimate user can still sign in from another IP while an attacker's IP is locked.
- **Controller tests** using `MockMvc` for clients, accounts, transactions, and authentication. They include account number and digit validation, transaction lookup by public transaction code, invalid UUID handling, and password changes. Address lookup is covered by service, orchestration, and provider-client tests rather than a standalone lookup endpoint.
- **Provider client tests** for ViaCEP and Brasil API using a local HTTP server. These cover response mapping, postal codes not found, invalid responses, error statuses, timeouts, and unreachable providers.
- **JSON coercion tests** that import `JacksonConfig` into a controller test slice and verify that wrongly typed fields are rejected.
- **Validation tests** for CPF, account numbers, account digits, password size in bytes, password complexity (including passwords containing line breaks), and reserved usernames.
- **Correlation ID filter tests** for ID generation, reuse, lowercase normalization, rejection of non-canonical values, and MDC cleanup.
- **Other unit tests** for the global exception handler, database constraint mapping, pagination tie-breakers, the administrator bootstrap runner, and application startup.

The tests use the `test` Spring profile and the `banking_system_test` database. They do not use an in-memory substitute for PostgreSQL.
