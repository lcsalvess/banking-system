# Banking System

## About
A banking system backend developed with Java and Spring Boot, designed as a RESTful API with PostgreSQL persistence, authentication and authorization, external service integration, observability, and automated testing.

The project evolved from a pure Java application into a modular Spring Boot backend, progressively introducing persistence, security, validation, transactional consistency, external integrations, observability, and automated testing. It focuses on practical backend engineering practices such as object-oriented design, business rule enforcement, database constraints, secure API design, resilient external integrations, application events for decoupled operational logging, and metrics.

The main goal is to build a realistic backend system while exploring software engineering concepts such as separation of responsibilities, transactional data consistency, validation, exception handling, concurrency, and maintainable architecture.

---

## Technologies

| Technology           | Version | Description                                                                 |
|----------------------|---------|-----------------------------------------------------------------------------|
| Java                 | 21      | Programming language                                                        |
| Spring Boot          | 4.1.2   | Backend framework                                                           |
| Spring Web MVC       | -       | REST API development                                                        |
| Spring RestClient    | -       | HTTP communication with external APIs                                       |
| Bean Validation      | -       | Request and parameter validation                                            |
| Spring Data JPA      | -       | Data access and persistence                                                 |
| Hibernate            | -       | JPA implementation and ORM                                                  |
| Spring Security      | -       | Authentication and authorization                                            |
| Spring Events        | -       | Decoupled operation logging                                                 |
| Spring Scheduling    | -       | Scheduled yield processing, balance reconciliation, and idempotency cleanup |
| Spring Boot Actuator | -       | Health, info, metrics, and probes                                           |
| Micrometer           | -       | Metrics and observability                                                   |
| JWT (JJWT)           | 0.13.0  | Stateless authentication                                                    |
| BCrypt               | -       | Password hashing                                                            |
| PostgreSQL           | 18      | Relational database                                                         |
| Flyway               | -       | Versioned database migrations                                               |
| springdoc-openapi    | 3.2.0   | OpenAPI / Swagger UI documentation                                          |
| Maven                | -       | Dependency management and build                                             |
| JUnit                | 6       | Unit and integration testing                                                |
| Mockito              | 5       | Mocking for tests                                                           |

---

## Architecture

The application follows a modular layered architecture,
with clear separation between HTTP endpoints, business logic,
persistence, security, and external integrations.

For the package structure, request flow, and architectural details,
see [Architecture](docs/ARCHITECTURE.md).

---

## Features

- **Authentication and authorization** with JWT and role-based access: `ADMIN` manages users and metrics, `EMPLOYEE` operates clients, accounts, and transactions
- **User management**: user creation with role assignment, paginated listing with filters, deactivation and reactivation, and secure password storage (BCrypt)
- **Password change**: any authenticated user can change their own password, which revokes every token issued before the change
- **Password policy**: new passwords must include uppercase and lowercase letters, a digit, and a special character, in addition to the character and UTF-8 byte limits
- **Token revocation**: every JWT carries a credential version (`tv` claim) checked against the database, so tokens stop working after a password change or a deactivation
- **Login rate limiting**: limits login requests per IP and temporarily blocks a username per client IP, and globally at a higher threshold, after repeated failed attempts, returning `429 Too Many Requests` with a `Retry-After` header
- **Client management**: registration, listing, lookup, and partial update (`PATCH`), including address data
- **Sensitive data protection**: the CPF is masked in every client response
- **Internal address lookup**: client creation and address updates resolve postal codes using ViaCEP, with BrasilAPI as a fallback provider
- **Provider response validation**: addresses returned by external providers are validated before being used
- **Versioned database schema** managed by Flyway migrations and validated by Hibernate at startup
- **Database privilege separation**: separate PostgreSQL roles for application runtime (`banking_app`) and Flyway migrations (`banking_migrator`), with least-privilege table and sequence grants and restricted access to migration metadata
- **Account management**: checking and savings accounts, with generated account numbers and check digits
- **Transactions**: deposit, withdrawal, transfer between accounts, transaction history per account, and lookup by public identifier
- **Transaction idempotency**: a required `Idempotency-Key` UUID prevents duplicate financial operations on retries and allows the original result to be recovered
- **Idempotency cleanup**: removes expired idempotency records daily at 03:30 (`America/Sao_Paulo`) and after application startup, recovering cleanup missed while the application was offline (7-day retention by default)
- **Public transaction identifier**: every transaction has a UUID (`transactionCode`) exposed in responses, while the internal database ID is never returned
- **Transfer traceability**: the two transactions of a transfer share a `transferCode`, so both legs can be correlated
- **Transaction actor attribution**: every transaction records who performed it: the authenticated user for API operations (`performed_by_user_id`, including both legs of a transfer), or the system for automatic yields (`performed_by_system`)
- **Identity normalization**: usernames and e-mails are trimmed and lowercased, so `Admin` and `admin` are the same user and e-mail uniqueness is case-insensitive
- **Concurrency control**: pessimistic row locking (`SELECT ... FOR UPDATE`) on every operation that changes a balance or an account status, with a fixed lock order in transfers to avoid deadlocks
- **Automatic savings yield**: a scheduled job credits a compounded daily yield to savings accounts, catching up on missed days after downtime and recovering pending days at startup
- **Balance reconciliation**: a scheduled read-only check detects and logs differences between account balances and transaction balance snapshots
- **Account cancellation** with balance and status validation, safe against concurrent deposits
- **Input validation** using Bean Validation, with custom constraints for CPF, account number, check digit, password complexity, password byte length, and reserved usernames
- **Strict JSON input types**: text fields reject numbers and booleans, and monetary amounts reject strings
- **Pagination, sorting, and filtering** on list endpoints, with a whitelist of sortable fields per endpoint, a maximum page size of 100, and the `id` as tie-breaker for a stable order
- **Global exception handling** with a consistent error response
- **Decoupled operation logging** using Spring application events
- **Request tracing** with a correlation ID present in the response header and in every log line
- **Observability** with Spring Boot Actuator (health, info, metrics, and public liveness/readiness probes) and Micrometer timers
- **Environment profiles** (`local`, `prod`, and `test`) that separate database configuration and API documentation exposure
- **Interactive API documentation** with Swagger UI, enabled only in the `local` profile

---

## Business Rules

The application enforces explicit business rules for clients, accounts,
financial transactions, user management, and automated savings yield.

For detailed rules, validations, financial constraints, and concurrency
behavior, see [Business Rules](docs/BUSINESS_RULES.md).

---

## Security

The API uses stateless JWT authentication, role-based authorization,
credential-version token revocation, password validation, and login
rate limiting.

For authentication flows, role permissions, token validation, and security
controls, see [Security](docs/SECURITY.md).

---

## API Reference

The REST API provides endpoints for authentication, user and client
management, bank accounts, financial transactions, and transaction history.

List endpoints support pagination, sorting, and filtering where applicable.
Financial operations require an `Idempotency-Key` header.

For the endpoint catalog, access rules, response behavior, and request
examples, see the [API Reference](docs/API.md).

Full request and response schemas are available through Swagger UI
when running with the `local` profile.

---

## Operations

Error handling, application logging, correlation IDs, Actuator endpoints, and custom metrics are covered in the [Operations Guide](docs/OPERATIONS.md).

---

## Database

The application uses PostgreSQL for persistence and Flyway for versioned schema migrations. In the `local` and `prod` profiles, the application runtime and Flyway use separate database roles: `banking_app` for application operations and `banking_migrator` for schema migrations. Least-privilege grants restrict access to tables and sequences, while migration metadata remains reserved for the migration role.

For database design, migrations, indexes, integrity constraints, and database role privileges, see the [Database](docs/DATABASE.md) and [Development Guide](docs/DEVELOPMENT.md) documentation.

---

## Getting Started

To run the application locally, you need Java 21 and PostgreSQL 18.

The [Development Guide](docs/DEVELOPMENT.md) explains how to configure the environment variables, create the databases, select the Spring profile, start the application, and run the tests.

### Quick Start

1. Clone the repository and enter the project directory:

   ```bash
   git clone https://github.com/lcsalvess/banking-system.git
   cd banking-system
   ```

2. Create the `banking_system` and `banking_system_test` databases, then provision the PostgreSQL roles and schema permissions using [`scripts/db/bootstrap-roles.sql`](scripts/db/bootstrap-roles.sql) as a PostgreSQL administrator. Configure the required environment variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_MIGRATOR_USERNAME`, `DB_MIGRATOR_PASSWORD`, and `JWT_SECRET`. The runtime datasource uses `banking_app`, while Flyway uses `banking_migrator` in the `local` and `prod` profiles. See the [Development Guide](docs/DEVELOPMENT.md#configuration) for database setup and profile-specific configuration.

To create the initial administrator, also set `ADMIN_USERNAME`, `ADMIN_EMAIL`, and `ADMIN_PASSWORD`.

3. Start the application using the `local` profile.

   **Windows (PowerShell):**

   ```powershell
   $env:SPRING_PROFILES_ACTIVE = "local"
   .\mvnw.cmd spring-boot:run
   ```

   **Linux / macOS:**

   ```bash
   export SPRING_PROFILES_ACTIVE=local
   ./mvnw spring-boot:run
   ```

The API will be available at `http://localhost:8080`.

### API Documentation

When running with the `local` profile, Swagger UI is available at:

`http://localhost:8080/swagger-ui/index.html`

For endpoint descriptions, request examples, authentication requirements, and pagination details, see the [API Reference](docs/API.md).

### Running Tests

**Windows (PowerShell):**

```powershell
.\mvnw.cmd verify
```

**Linux / macOS:**

```bash
./mvnw verify
```

Tests run against PostgreSQL using the `test` profile. See the [Development Guide](docs/DEVELOPMENT.md#testing) for prerequisites and test coverage.

---

## Roadmap

- [x] Migration management with Flyway
- [x] Integration tests for external address providers (`ViaCepClient` and `BrasilApiClient`)
- [x] Tests for `AddressService` and address-provider orchestration
- [x] Integration tests with PostgreSQL for transaction and concurrency scenarios
- [x] Differentiated permissions for `EMPLOYEE` and `ADMIN` on business endpoints
- [x] Separate `local`, `prod`, and `test` profiles
- [x] Pagination and filtering on list endpoints
- [x] Password change with JWT invalidation
- [x] Password complexity validation on user creation and password changes
- [x] User deactivation and reactivation
- [x] Automatic daily savings yield with recovery after downtime
- [x] Transaction idempotency with scheduled retention cleanup and startup recovery
- [x] Scheduled balance reconciliation
- [x] Continuous integration (CI) with GitHub Actions
- [ ] Docker Compose for the application and database
- [ ] Continuous deployment (CD)
- [ ] Refresh tokens