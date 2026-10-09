# Banking System

## About
A banking system backend developed with Java and Spring Boot, designed as a RESTful API with PostgreSQL persistence, authentication and authorization, external service integration, observability, and automated testing.

The project evolved from a pure Java application into a modular Spring Boot backend, progressively introducing persistence, security, validation, transactional consistency, external integrations, observability, and automated testing. It focuses on practical backend engineering practices such as object-oriented design, business rule enforcement, database constraints, secure API design, resilient external integrations, application events for decoupled operational logging, and metrics.

The main goal is to build a realistic backend system while exploring software engineering concepts such as separation of responsibilities, transactional data consistency, validation, exception handling, concurrency, and maintainable architecture.

---

## Technologies

| Technology            | Version | Description                              |
| --------------------- | ------- | ---------------------------------------- |
| Java                  | 21      | Programming language                     |
| Spring Boot           | 4.1.1   | Backend framework                        |
| Spring Web MVC        | -       | REST API development                     |
| Spring RestClient     | -       | HTTP communication with external APIs    |
| Bean Validation       | -       | Request and parameter validation         |
| Spring Data JPA       | -       | Data access and persistence              |
| Hibernate             | -       | JPA implementation and ORM               |
| Spring Security       | -       | Authentication and authorization         |
| Spring Events         | -       | Decoupled operation logging              |
| Spring Boot Actuator  | -       | Health, info, and metrics endpoints      |
| Micrometer            | -       | Metrics and observability                |
| JWT (JJWT)            | 0.13.0  | Stateless authentication                 |
| BCrypt                | -       | Password hashing                         |
| PostgreSQL            | 18      | Relational database                      |
| Flyway                | -       | Versioned database migrations            |
| springdoc-openapi     | 3.1.1   | OpenAPI / Swagger UI documentation       |
| Maven                 | -       | Dependency management and build          |
| JUnit                 | 6       | Unit and integration testing             |
| Mockito               | 5       | Mocking for tests                        |

---

## Architecture

The application follows a modular layered architecture, separating responsibilities into distinct components and layers.

### Package Structure

```text
src/main/java/com/lcsalvess/bankingsystem
├── bootstrap/       # Application startup and initialization
├── config/          # Spring application configuration (Jackson, JWT properties, OpenAPI, security)
├── controller/      # REST API endpoints
│   ├── address/     # Address lookup endpoint
│   └── security/    # Authentication endpoint
├── dto/             # Request and response DTOs
├── entity/          # Domain entities and enums
├── event/           # Application events and event listeners
├── exception/       # Business exceptions, database constraints, and global exception handling
├── filter/          # HTTP filters (correlation ID)
├── integration/
│   └── address/     # External address providers and lookup orchestration
│       ├── brasilapi/
│       ├── config/
│       ├── dto/
│       ├── exception/
│       ├── validation/
│       └── viacep/
├── repository/      # Database access through Spring Data JPA
├── security/        # JWT authentication filter
├── serialization/   # Custom JSON serialization
├── service/         # Business rules and application logic
│   ├── account/     # Account operations, row locking, and account number generation
│   ├── address/     # Address-related application logic
│   ├── client/      # Client operations and persistence responsibilities
│   ├── security/    # Authentication, JWT, and current user
│   ├── transaction/ # Transaction operations
│   └── user/        # User operations
└── validation/      # Custom constraints and validators
    ├── accountnumber/ # Account number and check digit validation
    └── cpf/           # CPF validation
```

### Request Flow

Shows the full request/response cycle, including authentication and authorization before business logic executes.

```text
┌─────────────────┐
│     Client      │
│   Postman/API   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Spring Security │
│ Authentication  │
│ Authorization   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│    Controller   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│     Service     │
│ Business Rules  │
└────────┬────────┘
         │
         ├──────────────► Persistence
         │                     │
         │                     ▼
         │                Repository
         │                     │
         │                     ▼
         │                PostgreSQL
         │                     │
         │◄────────────────────┘
         │
         ├──────────────► Integration
         │                     │
         │                     ▼
         │              External APIs
         │              ViaCEP / BrasilAPI
         │
         ├──────────────► Events
         │
         │
         ▼
┌─────────────────┐
│  Response DTO   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│     Client      │
└─────────────────┘
```

---

## Features

- **Authentication and authorization** with JWT and role-based access (`ADMIN` and `EMPLOYEE`)
- **User management**: user creation with role assignment and secure password storage
- **Client management**: registration, listing, lookup, and partial update (`PATCH`), including address data
- **Address lookup**: automatic address lookup by postal code using ViaCEP, with BrasilAPI as a fallback provider
- **Provider response validation**: addresses returned by external providers are validated before being used
- **Versioned database schema** managed by Flyway migrations and validated by Hibernate at startup
- **Account management**: checking and savings accounts, with generated account numbers and check digits
- **Transactions**: deposit, withdrawal, transfer between accounts, transaction history per account, and lookup by public identifier
- **Public transaction identifier**: every transaction has a UUID (`transactionCode`) exposed in responses, while the internal database ID is never returned
- **Transfer traceability**: the two transactions of a transfer share a `transferCode`, so both legs can be correlated
- **Concurrency control**: pessimistic row locking (`SELECT ... FOR UPDATE`) on every operation that changes a balance or an account status, with a fixed lock order in transfers to avoid deadlocks
- **Savings yield**: monthly yield applied to savings accounts
- **Account cancellation** with balance and status validation, safe against concurrent deposits
- **Input validation** using Bean Validation, with custom constraints for CPF, account number, and check digit
- **Strict JSON input types**: text fields reject numbers and booleans, and monetary amounts reject strings
- **Global exception handling** with a consistent error response
- **Decoupled operation logging** using Spring application events
- **Request tracing** with a correlation ID present in the response header and in every log line
- **Observability** with Spring Boot Actuator (health, info, metrics) and Micrometer timers
- **Interactive API documentation** with Swagger UI, which can be disabled through configuration

---

## Business Rules

### Clients
- CPF must be unique and contain exactly 11 digits.
- E-mail must be unique.
- CPF validation includes format checks and verification digit calculation.
- Phone number must contain 10 to 11 digits, including the area code.
- Every client requires an address with a postal code (CEP), a street number, and an optional complement.
- Address details such as street name, neighborhood, city, and state are retrieved through external postal code providers.
- Client updates are partial: only the fields sent in the request are changed, and omitted fields are left unchanged.
- When an update includes an address, the postal code is looked up again through the providers, and the street number and complement are updated along with it.
- Client creation checks whether the CPF already exists. Concurrent requests are also handled: if another request saves the same CPF or e-mail first, the unique constraint violation is translated into `409 Conflict` with a message that identifies the field. E-mail uniqueness is enforced by the database.
- The postal code lookup (external calls) happens before the database transaction starts, so no database connection is held while waiting for the providers.

### Accounts
- A client can have at most one active checking account and one active savings account. A new account of the same type can be opened after the previous one is canceled.
- Account numbers are generated from a database sequence, padded to at least 5 digits (for example `00001`), followed by a check digit (modulo 11). The `account_number` column holds up to 20 digits.
- Every operation that receives an account number also requires the check digit.
- The account number must contain between 5 and 20 digits and the check digit exactly 1 digit. Otherwise, the request is rejected with `400 Bad Request` before reaching the service.
- The check digit is then validated against the account number before the account lookup, and a mismatch also returns `400 Bad Request`.
- An account can only be canceled if it is active and has a zero balance.
- Cancellation takes the same row lock as deposits, withdrawals, transfers, and yield. A deposit that runs at the same time cannot be lost: the operations are serialized, and the cancellation is rejected if the deposit commits first.

### Transactions
- Deposits, withdrawals, and transfers require an active account and a positive amount.
- Amounts accept at most 17 integer digits and 2 decimal places.
- Withdrawals and transfers require sufficient balance.
- A transfer requires distinct and active source and destination accounts, and records one `TRANSFER_SENT` and one `TRANSFER_RECEIVED` transaction. Both share the same `transferCode` (UUID), which is returned only for transfer transactions.
- Every operation that changes a balance locks the account row with a pessimistic write lock (`PESSIMISTIC_WRITE`) before validating it, so the balance check and the update cannot interleave with another request on the same account. Concurrent withdrawals therefore never overdraw an account, and concurrent deposits are never lost.
- A transfer locks both accounts in ascending account number order, regardless of the transfer direction. Two opposite transfers (A to B and B to A) cannot deadlock.
- All operations that change balances run inside a database transaction, so balance changes and transaction records are saved together or not at all.
- Every transaction receives a random UUID (`transactionCode`) when it is created. It is unique, immutable, and is the only identifier exposed by the API; the internal numeric ID is not part of the response.
- A transaction can be retrieved by its `transactionCode`: `404 Not Found` if it does not exist and `400 Bad Request` if the value is not a valid UUID.
- The transaction history of an account is returned from the newest to the oldest transaction.

### Savings Yield
- Yield is calculated as 0.5% of the current balance, rounded to 2 decimal places.
- An account becomes eligible one month after its creation or last yield date.
- Each applied yield moves the last yield date forward by one month.
- Yield can be applied only once per day per account, enforced by the service and by a partial unique index in the database.
- Concurrent yield requests for the same account are serialized by the row lock: one is applied and the others are rejected with `409 Conflict`.

### Users
- Only `ADMIN` users can create new users.
- Usernames and e-mails must be unique, and passwords must have at least 8 characters.
- Passwords are stored as BCrypt hashes.
- An initial `ADMIN` is created at startup from environment variables when no admin exists (see [Configuration](#configuration)).

### Address Lookup

- The postal code must contain exactly 8 digits, without a hyphen.
- Providers are queried in order: ViaCEP first, then BrasilAPI as a fallback. New providers can be added by implementing `AddressProvider` and defining their order with `@Order`.
- If a provider fails or does not find the postal code, the next provider is tried.
- Requests to providers use a 3-second connection timeout and a 5-second read timeout.
- Provider responses are validated. Required address fields include street, neighborhood, city, state, and an 8-digit postal code. An invalid response is treated as a provider failure.
- Provider failures are handled separately from postal codes that are not found:
  - `404 Not Found` when every provider answered and none found the postal code.
  - `503 Service Unavailable` when at least one provider failed and no other provider found the postal code.

---

## Authentication and Authorization

The API is stateless. Clients authenticate through `POST /api/v1/auth/login` and send the returned token in every request:

```http
Authorization: Bearer <token>
```

| Aspect             | Behavior                                                                                                      |
|--------------------|---------------------------------------------------------------------------------------------------------------|
| Token              | JWT signed with HMAC (key of at least 256 bits), valid for 1 hour by default                                  |
| Token validation   | Signature, expiration, and issuer (`jwt.issuer`) are verified on every request                                |
| User status        | The user is loaded from the database on every request; a deactivated user is rejected with `401 Unauthorized` |
| Startup check      | The application does not start if `JWT_SECRET` is missing, is not valid Base64, or is too short               |
| Public routes      | `/api/v1/auth/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`                                    |
| Protected routes   | Everything else requires a valid token, including `/actuator/**`                                              |
| Admin-only routes  | `POST /api/v1/users` (`@PreAuthorize("hasRole('ADMIN')")`)                                                    |
| Invalid token      | `401 Unauthorized`                                                                                            |
| Insufficient role  | `403 Forbidden`                                                                                               |

---

## API Endpoints

Full request and response schemas are available in Swagger UI.

| Method | Endpoint                                               | Description                          | Access        |
|--------|--------------------------------------------------------|--------------------------------------|---------------|
| POST   | `/api/v1/auth/login`                                   | Authenticate and receive a JWT       | Public        |
| POST   | `/api/v1/users`                                        | Create a user                        | `ADMIN`       |
| POST   | `/api/v1/clients`                                      | Register a client                    | Authenticated |
| GET    | `/api/v1/clients`                                      | List clients                         | Authenticated |
| GET    | `/api/v1/clients/{id}`                                 | Get a client by ID                   | Authenticated |
| PATCH  | `/api/v1/clients/{id}`                                 | Partially update a client            | Authenticated |
| POST   | `/api/v1/accounts`                                     | Create a checking or savings account | Authenticated |
| GET    | `/api/v1/accounts`                                     | List accounts                        | Authenticated |
| GET    | `/api/v1/accounts/{accountNumber}?digit=`              | Get an account                       | Authenticated |
| PATCH  | `/api/v1/accounts/{accountNumber}?digit=`              | Cancel an account                    | Authenticated |
| POST   | `/api/v1/transactions/deposit`                         | Deposit                              | Authenticated |
| POST   | `/api/v1/transactions/withdraw`                        | Withdraw                             | Authenticated |
| POST   | `/api/v1/transactions/transfer`                        | Transfer between accounts            | Authenticated |
| GET    | `/api/v1/transactions/accounts/{accountNumber}?digit=` | Transaction history of an account    | Authenticated |
| GET    | `/api/v1/transactions/code/{transactionCode}`          | Get a transaction by its public UUID | Authenticated |
| POST   | `/api/v1/transactions/yield/{accountNumber}?digit=`    | Apply yield to a savings account     | Authenticated |

`accountNumber` must have between 5 and 20 digits and `digit` exactly 1 digit, both in the path/query parameters and in request bodies.

Request bodies are strict about JSON types: text fields reject numbers and booleans (`"accountNumber": 99999` is rejected), and monetary amounts reject strings (`"amount": "100.00"` is rejected; send `100.00`). Both return `400 Bad Request`.

### Address Lookup Endpoint

| Method | Endpoint                                | Description                       | Access        |
| ------ | --------------------------------------- | --------------------------------- | ------------- |
| GET    | `/api/v1/addresses/lookup/{postalCode}` | Look up an address by postal code | Authenticated |

Possible responses: `200` (address found), `400` (postal code is not 8 digits), `404` (postal code not found), and `503` (address providers unavailable).

### Actuator Endpoints

| Method | Endpoint             | Description                          | Access        |
| ------ | -------------------- | ------------------------------------ | ------------- |
| GET    | `/actuator/health`   | Application health                   | Authenticated |
| GET    | `/actuator/info`     | Application information              | Authenticated |
| GET    | `/actuator/metrics`  | Micrometer metrics                   | Authenticated |

---

### Example

```bash
# 1. Log in
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "your-password"}'

# 2. Deposit into an account
curl -X POST http://localhost:8080/api/v1/transactions/deposit \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"accountNumber": "00001", "digit": "<account-digit>", "amount": 150.00}'

# 3. Partially update a client (only the fields sent are changed)
curl -X PATCH http://localhost:8080/api/v1/clients/1 \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "11987654321", "address": {"postalCode": "01001000", "streetNumber": "100"}}'

# 4. Look up a transaction by its public code
curl http://localhost:8080/api/v1/transactions/code/<transaction-code> \
  -H "Authorization: Bearer <token>"
```

Transactions are returned with their public identifier, and the internal ID is not exposed:

```json
{
  "transactionCode": "3f6c1d52-8a0e-4b7d-9c41-2e5a7b9d0f13",
  "type": "DEPOSIT",
  "amount": 150.00,
  "createdAt": "2026-09-30T14:32:10"
}
```

`type` is one of `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_SENT`, `TRANSFER_RECEIVED`, or `YIELD`.

Transfer transactions also include the `transferCode` shared by both legs of the transfer. The field is omitted for other types:

```json
{
  "transactionCode": "7b0e4a91-5c2d-4f38-8d16-a3c9e1f20b64",
  "transferCode": "c4d8f2a6-19b3-4e57-a0d2-6f81b5e3c790",
  "type": "TRANSFER_SENT",
  "amount": 100.00,
  "createdAt": "2026-10-09T14:20:45"
}
```

---

## Error Handling

Errors are handled by a single global exception handler (`GlobalExceptionHandler`), and business rule violations extend a common `BusinessException`. Every error returns the same structure:

```json
{
  "status": 400,
  "message": "O valor informado é maior do que o saldo."
}
```

Validation errors also include the invalid fields. The same structure is used for request bodies and for path and query parameters:

```json
{
  "status": 400,
  "message": "Erro de validação.",
  "errors": {
    "postalCode": "O CEP deve conter exatamente 8 dígitos."
  }
}
```

| Scenario                                                                           | Status                      |
|------------------------------------------------------------------------------------|-----------------------------|
| Business rule violation                                                            | Defined by each exception   |
| Bean Validation failure, invalid parameters, malformed body, or wrong JSON type    | `400 Bad Request`           |
| Invalid credentials                                                                | `401 Unauthorized`          |
| Access denied                                                                      | `403 Forbidden`             |
| CPF or e-mail already registered, including concurrent requests                    | `409 Conflict`              |
| Address providers unavailable                                                      | `503 Service Unavailable`   |
| Unexpected error                                                                   | `500 Internal Server Error` |

Database integrity violations are translated by constraint name through the `DatabaseConstraint` enum: a client CPF or e-mail conflict returns `409 Conflict`, a second yield on the same day returns `409 Conflict`, and any other violation returns `400 Bad Request` with a generic message. The constraint is identified by the name reported by the database, never by the exception message.

---

## Logging

Operations are logged through Spring application events:

- Services publish an event after creating or changing data: client `CREATED`/`UPDATED`, account `CREATED`/`CANCELLED`, user `CREATED`, and transactions (deposit, withdrawal, transfer, and yield).
- Listeners use `@TransactionalEventListener` in the `AFTER_COMMIT` phase, so an operation is only logged as successful after its database transaction is committed.
- The global exception handler logs business errors, authentication and authorization failures, data integrity violations, address provider failures, and unexpected errors.
- The log level is controlled by `logging.level.com.lcsalvess.bankingsystem` (`INFO` by default), and SQL logging is disabled.

### Correlation ID

- Every request has a correlation ID, read from the `X-Correlation-ID` header.
- The header value is reused only when it is a canonical UUID, and it is normalized to lowercase. A missing, blank, or invalid value is replaced by a new random UUID.
- The ID is returned in the `X-Correlation-ID` response header, stored in the logging MDC (`correlationId`), shown in every log line, and removed when the request ends.

---

## Observability

- Spring Boot Actuator exposes `health`, `info`, and `metrics`. These endpoints require authentication, and health details are shown only to authorized users.
- Custom Micrometer timers:

| Timer                       | Description                                                              |
|-----------------------------|--------------------------------------------------------------------------|
| `address.lookup`            | Total time of an address lookup                                          |
| `address.provider.lookup`   | Time of each provider query, tagged by `provider`                        |
| `client.persistence.create` | Client creation inside the transaction (excludes the commit)             |
| `client.persistence.update` | Client update inside the transaction (excludes the commit)               |

---

## Database

- PostgreSQL, with the schema versioned and applied by Flyway. Migrations live in `src/main/resources/db/migration` and run automatically at startup. Hibernate does not change the schema; it only validates it against the entities (`spring.jpa.hibernate.ddl-auto=validate`).
- `V1__create_schema.sql` creates the initial schema: tables, constraints, indexes, and the `account_number_seq` sequence. `V2__increase_account_number_length.sql` widens `accounts.account_number` to 20 characters. `V3__add_transfer_code_to_transactions.sql` adds the nullable `transfer_code` column to `transactions`.
- Schema changes must be added as new versioned migrations (`V4__description.sql`, and so on). Migrations that were already applied must not be edited, because Flyway validates their checksums.
- Tables use plural snake_case names: `users`, `clients`, `addresses`, `accounts`, `checking_accounts`, `savings_accounts`, and `transactions`.
- Constraints follow a naming pattern: `uk_<table>_<column>` for unique constraints, `fk_<table>_<reference>` for foreign keys, and `ck_<table>_<rule>` for check constraints.
- `Account` uses joined inheritance (`CheckingAccount` and `SavingsAccount` extend it), mapped to the `accounts`, `checking_accounts`, and `savings_accounts` tables.
- Check constraints in the database enforce a non-negative account balance, a positive transaction amount, and the allowed values of state, role, account type, account status, and transaction type.
- Two partial unique indexes enforce business rules: `uk_accounts_client_type_active` (one active account of each type per client) and `uk_transactions_daily_yield` (one yield per account per day).
- The constraints and indexes that the application translates into business errors are listed in the `DatabaseConstraint` enum, which is the only place in the code that holds their names. `DatabaseConstraintTest` checks that every name is declared in a migration, so renaming a constraint without updating the enum fails the test suite.
- Monetary values use `BigDecimal` (`precision = 19`, `scale = 2`).
- `Transaction` also has a `transfer_code` column (UUID, nullable, not updatable). It is filled only for `TRANSFER_SENT` and `TRANSFER_RECEIVED` and holds the same value in both rows of a transfer.
- Concurrency is handled with pessimistic locking: `AccountRepository.findByAccountNumberForUpdate` uses `LockModeType.PESSIMISTIC_WRITE`. The locking methods of `AccountService` require an existing transaction (`Propagation.MANDATORY`), so a lock can never be taken and released outside the operation that needs it.
- `Transaction` has a `transaction_code` column (UUID, not null, not updatable) with the `uk_transactions_code` unique constraint, used as its public identifier.
- Open Session in View is disabled (`spring.jpa.open-in-view=false`). Account queries that need the client use `JOIN FETCH` to avoid lazy loading outside a transaction.
- Read operations in the services run with `@Transactional(readOnly = true)`, while operations that change data use regular transactions.
- For clients, the transaction boundary is in `ClientPersistenceService`: `ClientService` resolves the address first and only then calls it, so the transaction covers just the persistence work.
- Most entities have no public setters: `Client` and `Address` change through domain methods such as `Client.update(...)` and `Address.updateFrom(...)`. `User` still exposes setters.

---

## Getting Started

### Prerequisites

- Java 21
- PostgreSQL 18
- Maven (or the included Maven Wrapper)

### Configuration

1. Create the empty databases (Flyway creates the schema on the first startup):

```sql
CREATE DATABASE banking_system;
CREATE DATABASE banking_system_test;
```

2. Set the environment variables:

| Variable          | Required | Description                                                     |
|-------------------|----------|-----------------------------------------------------------------|
| `DB_USERNAME`     | Yes      | PostgreSQL username                                             |
| `DB_PASSWORD`     | Yes      | PostgreSQL password                                             |
| `JWT_SECRET`      | Yes      | Base64-encoded secret used to sign tokens (at least 256 bits)   |
| `ADMIN_USERNAME`  | No       | Username of the initial admin                                   |
| `ADMIN_EMAIL`     | No       | E-mail of the initial admin                                     |
| `ADMIN_PASSWORD`  | No       | Password of the initial admin                                   |
| `SWAGGER_ENABLED` | No       | Enables the OpenAPI docs and Swagger UI (default: `true`)       |

The initial admin is created only when no `ADMIN` exists and all three `ADMIN_*` variables are set. To generate a suitable secret:

```bash
openssl rand -base64 32
```

The token lifetime (`jwt.expiration`, in seconds) and the token issuer (`jwt.issuer`) are defined in `application.properties`.

The base URLs of the address providers are defined in `application.properties` (`integration.address.viacep.base-url` and `integration.address.brasilapi.base-url`).

### Running

```bash
git clone https://github.com/lcsalvess/banking-system.git
cd banking-system
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

### API Documentation

With the application running, open Swagger UI at:

```
http://localhost:8080/swagger-ui/index.html
```

Use the **Authorize** button and paste the JWT returned by `/api/v1/auth/login` to call protected endpoints.

To disable the documentation (for example, in production), set `SWAGGER_ENABLED=false`.

---

## Testing

```bash
./mvnw test
```

The test suite covers:

- **Unit tests** for services, using JUnit and Mockito: accounts, clients (including the persistence service), transactions (deposit, withdrawal, transfer, yield, and the lock acquisition of each), users, JWT, authentication, user details loading, address data resolution, and address lookup provider orchestration.
- **Concurrency integration tests** (`TransactionConcurrencyTests`) against PostgreSQL, running operations on parallel threads released at the same instant: concurrent withdrawals that must not overdraw an account, deposits that must not be lost, mixed deposits and withdrawals, opposite transfers without deadlock, several transfers into the same account, transfers limited by the available balance, a yield requested twice at once, and an account cancellation racing with deposits.
- **Security tests** for JWT generation and validation (secret, issuer, expiration), the security filter chain configuration, and the authentication entry point.
- **Controller tests** using `MockMvc` for clients, accounts (including account number and digit format validation), transactions (including lookup by public transaction code and invalid UUID handling), address lookup, and authentication.
- **Provider client tests** for ViaCEP and Brasil API against a local HTTP server: response mapping, postal code not found, invalid responses, error statuses, timeouts, and unreachable providers.
- **Exception handler tests** for every response of `GlobalExceptionHandler`, including the translation of each database constraint into its status and message.
- **Constraint tests** for `DatabaseConstraint`: detection in the exception cause chain and consistency of every constraint name with the Flyway migrations.
- **JSON coercion tests** that import `JacksonConfig` into a controller slice to check that wrongly typed fields are rejected.
- **Validation tests** for CPF, account number, and account digit.
- **Filter tests** for the correlation ID: generation, reuse, lowercase normalization, rejection of non-canonical values, and MDC cleanup.
- **Context test** to verify the application starts.

The suite has more than 300 test methods, many of them parameterized.

Tests run with the `test` profile against the `banking_system_test` database. Its schema is created by the same Flyway migrations used in the application and validated by Hibernate (`validate`).

---

## Roadmap

- [x] Migration management with Flyway
- [x] Integration tests for external address providers (`ViaCepClient` and `BrasilApiClient`)
- [x] Tests for `AddressService` and `AddressLookupController`
- [x] Integration tests with PostgreSQL for transaction and concurrency scenarios
- [ ] Differentiated permissions for `EMPLOYEE` and `ADMIN` on business endpoints
- [ ] Pagination and filtering on list endpoints
- [ ] Docker Compose for the application and database
- [ ] CI/CD pipeline with GitHub Actions
- [ ] Refresh tokens