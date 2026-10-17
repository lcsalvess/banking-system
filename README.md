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
| Spring Scheduling     | -       | Automatic daily yield processing         |
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
├── config/          # Spring application configuration (Jackson, JWT properties, OpenAPI, pagination, clock, security)
├── controller/      # REST API endpoints
│   ├── address/     # Address lookup endpoint
│   └── security/    # Authentication endpoint
├── dto/             # Request and response DTOs
├── entity/          # Domain entities and enums
├── event/           # Application events and event listeners
├── exception/       # Business exceptions, database constraints, centralized error messages, and global exception handling
├── filter/          # HTTP filters (correlation ID)
├── integration/
│   └── address/     # External address providers and lookup orchestration
│       ├── brasilapi/
│       ├── config/
│       ├── dto/
│       ├── exception/
│       ├── validation/
│       └── viacep/
├── repository/      # Database access through Spring Data JPA, with Specifications for dynamic filters
├── security/        # JWT authentication filter and JSON handlers for 401/403 responses
├── serialization/   # Custom JSON serialization
├── util/            # Utility classes (CPF masking, text normalization, sort whitelist, pagination tie-breaker, application time zone)
├── service/         # Business rules and application logic
│   ├── account/     # Account operations, row locking, and account number generation
│   ├── address/     # Address-related application logic
│   ├── client/      # Client operations and persistence responsibilities
│   ├── security/    # Authentication, JWT, password change, and current user
│   ├── transaction/ # Transaction operations, daily yield scheduler, and startup recovery
│   └── user/        # User operations
└── validation/      # Custom constraints and validators
    ├── accountnumber/      # Account number and check digit validation
    ├── cpf/                # CPF validation
    └── passwordbytelength/ # Password size limit in bytes (BCrypt)
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

- **Authentication and authorization** with JWT and role-based access: `ADMIN` manages users and metrics, `EMPLOYEE` operates clients, accounts, and transactions
- **User management**: user creation with role assignment, paginated listing with filters, deactivation and reactivation, and secure password storage (BCrypt)
- **Password change**: any authenticated user can change their own password, which revokes every token issued before the change
- **Token revocation**: every JWT carries a credential version (`tv` claim) checked against the database, so tokens stop working after a password change or a deactivation
- **Client management**: registration, listing, lookup, and partial update (`PATCH`), including address data
- **Sensitive data protection**: the CPF is masked in every client response
- **Address lookup**: automatic address lookup by postal code using ViaCEP, with BrasilAPI as a fallback provider
- **Provider response validation**: addresses returned by external providers are validated before being used
- **Versioned database schema** managed by Flyway migrations and validated by Hibernate at startup
- **Account management**: checking and savings accounts, with generated account numbers and check digits
- **Transactions**: deposit, withdrawal, transfer between accounts, transaction history per account, and lookup by public identifier
- **Public transaction identifier**: every transaction has a UUID (`transactionCode`) exposed in responses, while the internal database ID is never returned
- **Transfer traceability**: the two transactions of a transfer share a `transferCode`, so both legs can be correlated
- **Transaction audit trail**: every transaction records who performed it: the authenticated user for operations requested through the API (`performed_by_user_id`, including both legs of a transfer), or the system for automatic yields (`performed_by_system`)
- **Identity normalization**: usernames and e-mails are trimmed and lowercased, so `Admin` and `admin` are the same user and e-mail uniqueness is case-insensitive
- **Concurrency control**: pessimistic row locking (`SELECT ... FOR UPDATE`) on every operation that changes a balance or an account status, with a fixed lock order in transfers to avoid deadlocks
- **Automatic savings yield**: a scheduled job credits a compounded daily yield to savings accounts, catching up on missed days after downtime and recovering pending days at startup
- **Account cancellation** with balance and status validation, safe against concurrent deposits
- **Input validation** using Bean Validation, with custom constraints for CPF, account number, and check digit
- **Strict JSON input types**: text fields reject numbers and booleans, and monetary amounts reject strings
- **Pagination, sorting, and filtering** on list endpoints, with a whitelist of sortable fields per endpoint, a maximum page size of 100, and the `id` as tie-breaker for a stable order
- **Global exception handling** with a consistent error response
- **Decoupled operation logging** using Spring application events
- **Request tracing** with a correlation ID present in the response header and in every log line
- **Observability** with Spring Boot Actuator (health, info, metrics) and Micrometer timers
- **Environment profiles** (`local`, `prod`, and `test`) that separate database configuration and API documentation exposure
- **Interactive API documentation** with Swagger UI, enabled only in the `local` profile

---

## Business Rules

### Clients
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
- Amounts accept at most 13 integer digits and 2 decimal places. An account balance cannot exceed `9999999999999.99` (also enforced by the `ck_accounts_balance_max` constraint); a deposit or transfer that would exceed it is rejected with `409 Conflict`. A yield that would exceed it is skipped for that day (see [Savings Yield](#savings-yield)).
- Withdrawals and transfers require sufficient balance.
- A transfer requires distinct and active source and destination accounts, and records one `TRANSFER_SENT` and one `TRANSFER_RECEIVED` transaction. Both share the same `transferCode` (UUID), which is returned only for transfer transactions.
- Every operation that changes a balance locks the account row with a pessimistic write lock (`PESSIMISTIC_WRITE`) before validating it, so the balance check and the update cannot interleave with another request on the same account. Concurrent withdrawals therefore never overdraw an account, and concurrent deposits are never lost.
- A transfer locks both accounts in ascending account number order, regardless of the transfer direction. Two opposite transfers (A to B and B to A) cannot deadlock.
- Every pooled connection runs `SET lock_timeout = 3000`, so a request that waits more than 3 seconds for a row lock fails with `503 Service Unavailable` instead of holding a thread and a connection indefinitely.
- All operations that change balances run inside a database transaction, so balance changes and transaction records are saved together or not at all.
- Every transaction records who performed it, for auditing; this is not part of the API responses. Transactions requested through the API store the authenticated user, which is mandatory. Yield transactions are generated by the system, store no user, and are flagged with `performed_by_system`. The database requires every transaction to have either a user or the system flag, and only a `YIELD` can be a system transaction (`ck_transactions_actor`).
- Every transaction receives a random UUID (`transactionCode`) when it is created. It is unique, immutable, and is the only identifier exposed by the API; the internal numeric ID is not part of the response.
- A transaction can be retrieved by its `transactionCode`: `404 Not Found` if it does not exist and `400 Bad Request` if the value is not a valid UUID.
- The transaction history of an account is returned from the newest to the oldest transaction.

### Savings Yield
- Yield is credited automatically by a scheduled job (`DailyYieldScheduler`). It cannot be applied manually through the API.
- The job runs every day at 00:05 (`America/Sao_Paulo`). The schedule can be changed through `banking.yield.cron` (see [Configuration](#configuration)).
- The daily rate is equivalent to 0.5% per month compounded over 30 days, `(1 + 0.005)^(1/30) - 1`, about 0.0166% per day. It is stored as the decimal constant `0.000166265204228477063643`, so the credited value does not depend on floating-point arithmetic. The yield is the rate applied to the balance at the start of the reference date, rounded to 2 decimal places (`HALF_UP`).
- The balance is rebuilt from the transaction history as it stood at 00:00 of the reference date, not read from the current balance. A deposit made on the same day does not earn yield that day, and each day of a catch-up is calculated on its own historical balance, so previous yields compound.
- Every yield transaction carries a `yield_date`, the date it refers to. An account becomes eligible on the day after its creation or after its last yield date.
- Each savings account keeps a `last_yield_processed_date`, the cursor of the processing. Dates are processed one at a time, in chronological order, and an out-of-order date is rejected. Each account and date pair runs in its own database transaction, under the account row lock.
- Missed days are credited by the next run, one day at a time. If the application was down, the job also runs once right after startup (in every profile except `test`) to recover them.
- Processing is idempotent. A date that was already processed is skipped, and the partial unique index `uk_transactions_daily_yield` (`account_id`, `yield_date`) guarantees a single yield per account per date, even with concurrent executions or several application instances.
- A date is marked as processed even when no yield is credited: the account is not eligible yet, the yield rounds to zero, or crediting it would exceed the balance limit. Skipped days are not credited retroactively.
- If processing an account fails, the date stays pending and the next run resumes from it. Transient database failures are retried up to 3 times with an increasing delay. A persistent failure stops only that account, and the others continue. An account that is no longer active is skipped.
- Active savings accounts are read in batches of 100, ordered by ID.
- Yield transactions are created by the system, with no associated user, and are logged with `SYSTEM` as the actor.

### Users
- Only `ADMIN` users can create new users.
- Usernames have up to 20 characters and may contain only letters, digits, dot, hyphen, and underscore. They are trimmed and converted to lowercase on creation and on login, so usernames are case-insensitive.
- E-mails can have up to 254 characters and are trimmed and stored in lowercase. Usernames and e-mails must be unique.
- Passwords must have between 8 and 64 characters and at most 72 bytes in UTF-8, the limit supported by BCrypt. The same limits are checked at login and when changing the password.
- Passwords are stored as BCrypt hashes (strength 12).
- An initial `ADMIN` is created at startup from environment variables when no admin exists (see [Configuration](#configuration)). In the `prod` profile the application refuses to start when no admin exists and the credentials are not configured.
- Username uniqueness is case-insensitive and enforced by the database through a unique index on `LOWER(username)`.
- The username `system` (case-insensitive, ignoring surrounding spaces) is reserved for the actor of system-generated operations, such as the daily yield, and is rejected when creating a user and for the initial admin. The `ck_users_username_not_system` check constraint enforces the same rule in the database.
- `system_migration` is a reserved technical user created by migration `V6` and hardened by `V8` (BCrypt password hash and a non-routable `.invalid` e-mail). It is inactive, cannot authenticate, and owns the transactions that already existed when the audit trail was introduced.
- Only `ADMIN` users can list, deactivate, and reactivate users. The listing is paginated and can be filtered by `search` (username or e-mail), `role`, and `active`.
- Deactivating a user is idempotent: an already inactive user is returned unchanged. A deactivated user cannot log in (the response is the same `401 Unauthorized` as for invalid credentials) and is rejected on every request, even with a token that was still valid.
- The last active `ADMIN` cannot be deactivated (`409 Conflict`). The active admins are locked (`SELECT ... FOR UPDATE`) while the check runs, so concurrent deactivations cannot leave the system without an administrator.
- Every user has a `token_version`, included in the JWT as the `tv` claim. Deactivation and password change increment it, so tokens issued before either event stay invalid, even if the account is reactivated later.
- Reactivation is idempotent. The reserved `system_migration` user can never be reactivated (`409 Conflict`), and the `ck_users_system_migration_inactive` check constraint guarantees it stays inactive.
- Any authenticated user can change their own password through `PATCH /api/v1/auth/password`, sending the current and the new password. A successful change returns `204 No Content`.
- The current password must be correct, and the new password must differ from it. Otherwise the request is rejected with `400 Bad Request`.
- A password change locks the user row, so concurrent changes are serialized. It increments the `token_version`, so every token issued before the change, including the one used in the request, is rejected with `401 Unauthorized`, and the user must log in again.

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

| Aspect                  | Behavior                                                                                                                                                                                                |
|-------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Token                   | JWT signed with HMAC (key of at least 256 bits), valid for 1 hour by default                                                                                                                            |
| Token validation        | Signature, expiration, and issuer (`jwt.issuer`) are verified on every request                                                                                                                          |
| Token revocation        | The `tv` claim must match the `token_version` of the user in the database. A password change or a deactivation increments it, so older tokens are rejected. Tokens without the claim count as version 0 |
| Invalid version claim   | A `tv` claim that is not a non-negative integer is rejected as an invalid token (`401 Unauthorized`)                                                                                                    |
| User status             | The user is loaded from the database on every request; a deactivated user is rejected with `401 Unauthorized`                                                                                           |
| Login of inactive users | Same `401 Unauthorized` and message as wrong credentials, with a password check against a dummy hash to keep the response time similar                                                                  |
| Startup check           | The application does not start if `JWT_SECRET` is missing, is not valid Base64, or is too short                                                                                                         |
| Public routes           | `POST /api/v1/auth/login`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`                                                                                                                      |
| Authenticated routes    | `PATCH /api/v1/auth/password`, open to any authenticated user, whatever the role                                                                                                                        |
| Protected routes        | Everything else requires a valid token and the role required by the endpoint                                                                                                                            |
| Invalid token           | `401 Unauthorized` (invalid, expired, revoked, or malformed token)                                                                                                                                      |
| Infrastructure failure  | `503 Service Unavailable` when the user cannot be loaded because the database is unavailable, so a valid token is not reported as invalid                                                               |
| Insufficient role       | `403 Forbidden`, returned as JSON by `RestAccessDeniedHandler`                                                                                                                                          |

### Access by role

| Role       | Allowed operations                                                                                                                                                                  |
|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ADMIN`    | Create, list, deactivate, and reactivate users; read the transaction history and look up transactions by code; `/actuator/health`, `/actuator/info`, and `/actuator/metrics`        |
| `EMPLOYEE` | Clients, accounts, address lookup, deposit, withdrawal, and transfer; read the transaction history and look up transactions by code; `/actuator/health` and `/actuator/info`        |

Roles are not hierarchical: `ADMIN` does not inherit `EMPLOYEE` permissions. Business operations require an `EMPLOYEE` user, which an `ADMIN` creates through `POST /api/v1/users`. Endpoint access is enforced with `@PreAuthorize` on the controllers and with request rules in `SecurityConfig` for the actuator. Changing one's own password is the only operation open to every authenticated user.

---

## API Endpoints

Full request and response schemas are available in Swagger UI.

| Method | Endpoint                                               | Description                                   | Access              |
|--------|--------------------------------------------------------|-----------------------------------------------|---------------------|
| POST   | `/api/v1/auth/login`                                   | Authenticate and receive a JWT                | Public              |
| PATCH  | `/api/v1/auth/password`                                | Change the password of the authenticated user | Authenticated       |
| POST   | `/api/v1/users`                                        | Create a user                                 | `ADMIN`             |
| GET    | `/api/v1/users`                                        | Retrieve a paginated list of users            | `ADMIN`             |
| PATCH  | `/api/v1/users/{userId}/deactivate`                    | Deactivate a user account                     | `ADMIN`             |
| PATCH  | `/api/v1/users/{userId}/reactivate`                    | Reactivate a user account                     | `ADMIN`             |
| POST   | `/api/v1/clients`                                      | Register a client                             | `EMPLOYEE`          |
| GET    | `/api/v1/clients`                                      | Retrieve a paginated list of clients          | `EMPLOYEE`          |
| GET    | `/api/v1/clients/{id}`                                 | Get a client by ID                            | `EMPLOYEE`          |
| PATCH  | `/api/v1/clients/{id}`                                 | Partially update a client                     | `EMPLOYEE`          |
| POST   | `/api/v1/accounts`                                     | Create a checking or savings account          | `EMPLOYEE`          |
| GET    | `/api/v1/accounts`                                     | Retrieve a paginated list of accounts         | `EMPLOYEE`          |
| GET    | `/api/v1/accounts/{accountNumber}?digit=`              | Get an account                                | `EMPLOYEE`          |
| PATCH  | `/api/v1/accounts/{accountNumber}?digit=`              | Cancel an account                             | `EMPLOYEE`          |
| POST   | `/api/v1/transactions/deposit`                         | Deposit                                       | `EMPLOYEE`          |
| POST   | `/api/v1/transactions/withdraw`                        | Withdraw                                      | `EMPLOYEE`          |
| POST   | `/api/v1/transactions/transfer`                        | Transfer between accounts                     | `EMPLOYEE`          |
| GET    | `/api/v1/transactions/accounts/{accountNumber}?digit=` | Transaction history of an account             | `EMPLOYEE`, `ADMIN` |
| GET    | `/api/v1/transactions/code/{transactionCode}`          | Get a transaction by its public UUID          | `EMPLOYEE`, `ADMIN` |

`accountNumber` must have between 5 and 20 digits and `digit` exactly 1 digit, both in the path/query parameters and in request bodies.

Yield is not exposed as an endpoint: it is credited automatically (see [Savings Yield](#savings-yield)) and shows up in the transaction history as `YIELD` transactions.

Request bodies are strict about JSON types: text fields reject numbers and booleans (`"accountNumber": 99999` is rejected), and monetary amounts reject strings (`"amount": "100.00"` is rejected; send `100.00`). Both return `400 Bad Request`.

### Pagination, Sorting, and Filtering

List endpoints accept `page`, `size`, and `sort` (for example `sort=balance,desc`). The maximum page size is 100, and larger values are capped. Sorting by a property outside the list of an endpoint returns `400 Bad Request`. The `id` is always added as a final tie-breaker, so pages keep a stable order.

| Endpoint                                            | Filters                        | Sortable fields                              | Default sort                                 | Default size |
|-----------------------------------------------------|--------------------------------|----------------------------------------------|----------------------------------------------|--------------|
| `GET /api/v1/users`                                 | `search`, `role`, `active`     | `id`, `username`, `email`, `role`, `active`  | `id` ascending                               | 20           |
| `GET /api/v1/clients`                               | `name`                         | `id`, `name`                                 | `id` ascending                               | 10           |
| `GET /api/v1/accounts`                              | -                              | `accountNumber`, `balance`, `status`, `type` | `accountNumber` ascending                    | 10           |
| `GET /api/v1/transactions/accounts/{accountNumber}` | `type`, `startDate`, `endDate` | `createdAt`, `id`, `type`, `amount`          | `createdAt` descending, then `id` descending | 10           |

On the transaction history, `startDate` and `endDate` use the `yyyy-MM-dd` format and are inclusive. Both must be between 2020-01-01 and the current date, and `startDate` cannot be after `endDate` (`400 Bad Request`).

### Address Lookup Endpoint

| Method | Endpoint                                | Description                       | Access               |
| ------ | --------------------------------------- | --------------------------------- | -------------------- |
| GET    | `/api/v1/addresses/lookup/{postalCode}` | Look up an address by postal code | `EMPLOYEE`           |

Possible responses: `200` (address found), `400` (postal code is not 8 digits), `404` (postal code not found), and `503` (address providers unavailable).

### Actuator Endpoints

| Method | Endpoint             | Description                          | Access               |
| ------ | -------------------- | ------------------------------------ | -------------------- |
| GET    | `/actuator/health`   | Application health                   | `EMPLOYEE`, `ADMIN`  |
| GET    | `/actuator/info`     | Application information              | `EMPLOYEE`, `ADMIN`  |
| GET    | `/actuator/metrics`  | Micrometer metrics                   | `ADMIN`              |

---

### Example

```bash
# 1. Log in as the initial admin
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "your-password"}'

# 2. Create an employee (ADMIN only)
curl -X POST http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"username": "employee", "email": "employee@example.com", "password": "your-password", "role": "EMPLOYEE"}'

# 3. Log in as the employee, who operates clients, accounts, and transactions
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "employee", "password": "your-password"}'

# 4. Deposit into an account
curl -X POST http://localhost:8080/api/v1/transactions/deposit \
  -H "Authorization: Bearer <employee-token>" \
  -H "Content-Type: application/json" \
  -d '{"accountNumber": "00001", "digit": "<account-digit>", "amount": 150.00}'

# 5. Partially update a client (only the fields sent are changed)
curl -X PATCH http://localhost:8080/api/v1/clients/1 \
  -H "Authorization: Bearer <employee-token>" \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "11987654321", "address": {"postalCode": "01001000", "streetNumber": "100"}}'

# 6. Look up a transaction by its public code
curl http://localhost:8080/api/v1/transactions/code/<transaction-code> \
  -H "Authorization: Bearer <employee-token>"

# 7. Change your own password (any authenticated user); tokens issued before the change stop working
curl -X PATCH http://localhost:8080/api/v1/auth/password \
  -H "Authorization: Bearer <employee-token>" \
  -H "Content-Type: application/json" \
  -d '{"currentPassword": "your-password", "newPassword": "your-new-password"}'

# 8. Deactivate a user (ADMIN only)
curl -X PATCH http://localhost:8080/api/v1/users/<user-id>/deactivate \
  -H "Authorization: Bearer <admin-token>"
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

Errors are handled by a single global exception handler (`GlobalExceptionHandler`), and business rule violations extend a common `BusinessException`. All error messages are centralized in `ApiErrorMessages`. Application errors return the same structure:

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

| Scenario                                                                                                                                           | Status                      |
|----------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| Business rule violation                                                                                                                            | Defined by each exception   |
| Bean Validation failure, invalid parameters, malformed body, or wrong JSON type                                                                    | `400 Bad Request`           |
| Invalid credentials, invalid or revoked token, or deactivated user                                                                                 | `401 Unauthorized`          |
| Access denied (role not allowed for the endpoint)                                                                                                  | `403 Forbidden`             |
| Value already registered (CPF, e-mail, or username), including concurrent requests, or a state conflict such as deactivating the last active admin | `409 Conflict`              |
| Address providers unavailable, row lock wait timeout, or authentication infrastructure unavailable                                                 | `503 Service Unavailable`   |
| Unexpected error                                                                                                                                   | `500 Internal Server Error` |

Database integrity violations are translated by constraint name through the `DatabaseConstraint` enum: a duplicate client CPF or e-mail, a duplicate username or user e-mail, a second active account of the same type, a balance above the limit, a reserved username, or an attempt to activate the `system_migration` user returns `409 Conflict` with a message that identifies the problem, and any other violation returns `500 Internal Server Error` with a generic message. Only the SQLState and the constraint name are logged, never the exception detail, because the database detail contains row values. The constraint is identified by the name reported by the database, never by the exception message.

---

## Logging

Operations are logged through Spring application events:

- Services publish an event after creating or changing data: client `CREATED`/`UPDATED`, account `CREATED`/`CANCELLED`, user `CREATED`/`DEACTIVATED`/`REACTIVATED`/`PASSWORD_CHANGED`, and transactions (deposit, withdrawal, transfer, and yield, the last one with `SYSTEM` as the actor).
- Listeners use `@TransactionalEventListener` in the `AFTER_COMMIT` phase, so an operation is only logged as successful after its database transaction is committed.
- The daily yield job logs the start and the end of every run, with a summary of the results (`attempted`, `applied`, `alreadyProcessed`, `notEligible`, `noYield`, `balanceLimitReached`, `skippedAccounts`, and `failed`) and a status of `completed`, `completed with failures`, or `aborted`.
- The global exception handler logs business errors, authentication and authorization failures, data integrity violations, address provider failures, and unexpected errors.
- The log level is controlled by `logging.level.com.lcsalvess.bankingsystem` (`INFO` by default), and SQL logging is disabled.

### Correlation ID

- Every request has a correlation ID, read from the `X-Correlation-ID` header.
- The header value is reused only when it is a canonical UUID, and it is normalized to lowercase. A missing, blank, or invalid value is replaced by a new random UUID.
- The ID is returned in the `X-Correlation-ID` response header, stored in the logging MDC (`correlationId`), shown in every log line, and removed when the request ends.

---

## Observability

- Spring Boot Actuator exposes `health`, `info`, and `metrics`. `health` and `info` require an `EMPLOYEE` or `ADMIN` token, `metrics` requires `ADMIN`, and health details are shown only to authorized users.
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
- `V1__create_schema.sql` creates the initial schema: tables, constraints, indexes, and the `account_number_seq` sequence. `V2__increase_account_number_length.sql` widens `accounts.account_number` to 20 characters. `V3__add_transfer_code_to_transactions.sql` adds the nullable `transfer_code` column to `transactions`. `V4__add_transaction_history_index.sql` creates the index `idx_transactions_account_created_id` on `transactions (account_id, created_at DESC, id DESC)`, which supports the transaction history query.
- `V5__normalize_email_uniqueness.sql` lowercases the existing e-mails of `users` and `clients` and replaces the case-sensitive unique constraints with unique indexes on `LOWER(email)` (names kept: `uk_users_email` and `uk_clients_email`). `V6__add_transaction_performed_by_user.sql` creates the inactive technical user `system_migration`, adds the `performed_by_user_id` column to `transactions` as nullable, attributes the existing transactions to that user, and then makes the column `NOT NULL` with the foreign key `fk_transactions_performed_by_user`. It also aborts if a `system_migration` user already exists with unexpected data. `V7__normalize_username_uniqueness.sql` lowercases and trims the existing usernames and replaces the case-sensitive unique constraint with a unique index on `LOWER(username)` (name kept: `uk_users_username`). `V8__harden_system_migration_user.sql` replaces the placeholder password of `system_migration` with a BCrypt hash and its e-mail with the reserved address `system-migration@invalid.example`, keeping the user inactive. `V9__add_account_balance_upper_bound.sql` adds the `ck_accounts_balance_max` check constraint (`balance <= 9999999999999.99`). `V10__add_user_token_version.sql` adds the `token_version` column to `users` (`INTEGER NOT NULL DEFAULT 0`), the credential version carried by every JWT. `V11__keep_system_migration_user_inactive.sql` adds the `ck_users_system_migration_inactive` check constraint, so the `system_migration` user can never be active. `V12__daily_yield_processing.sql` adds the `yield_date` column to `transactions` (filled from `created_at` for the existing yields), replaces the `uk_transactions_daily_yield` index so it is based on `(account_id, yield_date)` instead of the creation time, and adds the `ck_transactions_yield_date` check constraint (a `YIELD` must have a `yield_date`, and other types must not). `V13__system_transaction_attribution.sql` makes `performed_by_user_id` nullable again, adds the `performed_by_system` column (`BOOLEAN NOT NULL DEFAULT FALSE`), and adds the `ck_transactions_actor` check constraint (a system transaction must be a `YIELD` with no user, and any other transaction must reference a user). `V14__track_daily_yield_processing.sql` adds the `last_yield_processed_date` column to `savings_accounts`, the cursor of the daily yield processing. Existing accounts start from the later of their last yield date and the day before the migration (in `America/Sao_Paulo`), so no retroactive yield is granted for the historical period. `V15__reserve_system_username.sql` adds the `ck_users_username_not_system` check constraint, so no user can have the reserved username `system`. The migration fails if such a user already exists.
- Schema changes must be added as new versioned migrations (`V16__description.sql`, and so on). Migrations that were already applied must not be edited, because Flyway validates their checksums.
- Tables use plural snake_case names: `users`, `clients`, `addresses`, `accounts`, `checking_accounts`, `savings_accounts`, and `transactions`.
- Constraints follow a naming pattern: `uk_<table>_<column>` for unique constraints, `fk_<table>_<reference>` for foreign keys, and `ck_<table>_<rule>` for check constraints.
- `Account` uses joined inheritance (`CheckingAccount` and `SavingsAccount` extend it), mapped to the `accounts`, `checking_accounts`, and `savings_accounts` tables.
- Check constraints in the database enforce a non-negative account balance, a positive transaction amount, and the allowed values of state, role, account type, account status, and transaction type.
- Two partial unique indexes enforce business rules: `uk_accounts_client_type_active` (one active account of each type per client) and `uk_transactions_daily_yield` (one yield per account per reference date).
- The constraints and indexes that the application translates into business errors are listed in the `DatabaseConstraint` enum, which is the only place in the code that holds their names. Every name must match a constraint or index declared in the Flyway migrations.
- Monetary values use `BigDecimal` (`precision = 19`, `scale = 2`).
- `Transaction` also has a `transfer_code` column (UUID, nullable, not updatable). It is filled only for `TRANSFER_SENT` and `TRANSFER_RECEIVED` and holds the same value in both rows of a transfer.
- Concurrency is handled with pessimistic locking: `AccountRepository.findByAccountNumberForUpdate` uses `LockModeType.PESSIMISTIC_WRITE`. The locking methods of `AccountService` require an existing transaction (`Propagation.MANDATORY`), so a lock can never be taken and released outside the operation that needs it.
- User operations use the same mechanism: `UserRepository.findByIdForUpdate` locks the row on deactivation, reactivation, and password change. Deactivating an `ADMIN` also locks every active admin before the last-admin check, so concurrent deactivations always leave at least one active administrator.
- `Transaction` has a `transaction_code` column (UUID, not null, not updatable) with the `uk_transactions_code` unique constraint, used as its public identifier.
- Open Session in View is disabled (`spring.jpa.open-in-view=false`). Account queries that need the client use `JOIN FETCH` to avoid lazy loading outside a transaction.
- Read operations in the services run with `@Transactional(readOnly = true)`, while operations that change data use regular transactions.
- For clients, the transaction boundary is in `ClientPersistenceService`: `ClientService` resolves the address first and only then calls it, so the transaction covers just the persistence work.
- Most entities have no public setters: `Client` and `Address` change through domain methods such as `Client.update(...)` and `Address.updateFrom(...)`. `User` changes only through its constructor, which normalizes the username and e-mail, and through `deactivate()`, `reactivate()`, and `changePassword(...)`. `deactivate()` (when the user is active) and `changePassword(...)` increment `tokenVersion`.

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

2. Choose the Spring profile with `SPRING_PROFILES_ACTIVE`:

| Profile | Database                                                                    | Swagger UI / OpenAPI | Admin bootstrap required |
|---------|-----------------------------------------------------------------------------|----------------------|--------------------------|
| `local` | `DB_URL`, defaulting to `jdbc:postgresql://localhost:5432/banking_system`   | Enabled              | No                       |
| `prod`  | `DB_URL` is required                                                        | Disabled             | Yes                      |
| `test`  | `jdbc:postgresql://localhost:5432/banking_system_test` (used by the tests)  | Disabled             | No                       |

Without an active profile, `DB_URL` is required, the API documentation is disabled, and the admin bootstrap is not required. Use `local` for development.

3. Set the environment variables:

| Variable                 | Required                          | Description                                                     |
|--------------------------|-----------------------------------|-----------------------------------------------------------------|
| `SPRING_PROFILES_ACTIVE` | No                                | Active profile (`local`, `prod`)                                |
| `DB_URL`                 | Yes, except in `local` and `test` | JDBC URL of the PostgreSQL database                             |
| `DB_USERNAME`            | Yes                               | PostgreSQL username                                             |
| `DB_PASSWORD`            | Yes                               | PostgreSQL password                                             |
| `JWT_SECRET`             | Yes                               | Base64-encoded secret used to sign tokens (at least 256 bits)   |
| `ADMIN_USERNAME`         | No                                | Username of the initial admin                                   |
| `ADMIN_EMAIL`            | No                                | E-mail of the initial admin                                     |
| `ADMIN_PASSWORD`         | No                                | Password of the initial admin                                   |

The initial admin is created only when no `ADMIN` exists and all three `ADMIN_*` variables are set. When no admin exists and the variables are missing, the application logs a warning and starts in `local` and `test`, but fails at startup in `prod` (`app.admin.bootstrap.required=true`), so a production deployment never runs without an administrator. The initial admin can only manage users: create an `EMPLOYEE` user to operate clients, accounts, and transactions (see [Example](#example)). To generate a suitable secret:

```bash
openssl rand -base64 32
```

The token lifetime (`jwt.expiration`, in seconds) and the token issuer (`jwt.issuer`) are defined in `application.properties`.

The daily yield job runs at 00:05 by default (`0 5 0 * * *`). To change the schedule, set `banking.yield.cron` to a Spring cron expression. The time zone is fixed to `America/Sao_Paulo`.

The base URLs of the address providers are defined in `application.properties` (`integration.address.viacep.base-url` and `integration.address.brasilapi.base-url`).

### Running

```bash
git clone https://github.com/lcsalvess/banking-system.git
cd banking-system

# Linux / macOS
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run

# Windows (PowerShell)
$env:SPRING_PROFILES_ACTIVE = "local"
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

### API Documentation

With the application running in the `local` profile, open Swagger UI at:

```
http://localhost:8080/swagger-ui/index.html
```

Use the **Authorize** button and paste the JWT returned by `/api/v1/auth/login` to call protected endpoints. In the `prod` profile, and when no profile is active, the OpenAPI documentation and Swagger UI are disabled.

---

## Testing

```bash
./mvnw test
```

The test suite covers:

- **Unit tests** for services, using JUnit and Mockito: accounts, clients (including the persistence service), transactions (deposit, withdrawal, transfer, daily yield processing, and the lock acquisition of each), the daily yield scheduler (batching, retries, isolation of per-account failures, chronological catch-up, and interrupted runs) and its startup recovery, users (including deactivation and reactivation), password change, JWT (including the credential version claim), authentication, user details loading, address data resolution, and address lookup provider orchestration.
- **Concurrency integration tests** (`TransactionConcurrencyTests`) against PostgreSQL, running operations on parallel threads released at the same instant: concurrent withdrawals that must not overdraw an account, deposits that must not be lost, mixed deposits and withdrawals, opposite transfers without deadlock, several transfers into the same account, transfers limited by the available balance, a daily yield processed twice at once, and an account cancellation racing with deposits. The same class also checks the start-of-day balance calculation and the recovery of pending yield dates with compounded yields.
- **User integration tests** against PostgreSQL: `PasswordChangeIntegrationTests` covers the password change flow end to end (login only with the new password, revocation of tokens issued before the change, validation failures, deactivated users, tokens that stay revoked after reactivation, and concurrent changes), and `UserDeactivationConcurrencyTests` checks that concurrent deactivations of admins always leave one active administrator and that deactivating the same admin twice at once is idempotent.
- **Database configuration tests** (`DatabaseLockTimeoutTests`): the lock timeout applied to pooled connections and the balance upper bound constraint. `ReservedUsernameConstraintTests` inserts users directly into PostgreSQL to check that the database rejects the reserved username, in any case, and accepts names that only contain it.
- **Security tests** for JWT generation and validation (secret, issuer, expiration, credential version, and malformed version claim), the security filter chain (public routes, token validation, method security, actuator access, and correlation ID on error responses), and the authentication entry point.
- **Controller tests** using `MockMvc` for clients, accounts (including account number and digit format validation), transactions (including lookup by public transaction code and invalid UUID handling), address lookup, and authentication (including password change).
- **Provider client tests** for ViaCEP and Brasil API against a local HTTP server: response mapping, postal code not found, invalid responses, error statuses, timeouts, and unreachable providers.
- **JSON coercion tests** that import `JacksonConfig` into a controller slice to check that wrongly typed fields are rejected.
- **Validation tests** for CPF, account number, account digit, password size in bytes, and reserved username.
- **Filter tests** for the correlation ID: generation, reuse, lowercase normalization, rejection of non-canonical values, and MDC cleanup.
- **Other unit tests** for the global exception handler, the database constraint mapping, the pagination tie-breaker, and the admin bootstrap runner.
- **Context test** to verify the application starts.

The suite has more than 450 test methods, many of them parameterized.

Tests need a local PostgreSQL instance and the `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` environment variables. They run with the `test` profile against the `banking_system_test` database. Its schema is created by the same Flyway migrations used in the application and validated by Hibernate (`validate`).

---

## Roadmap

- [x] Migration management with Flyway
- [x] Integration tests for external address providers (`ViaCepClient` and `BrasilApiClient`)
- [x] Tests for `AddressService` and `AddressLookupController`
- [x] Integration tests with PostgreSQL for transaction and concurrency scenarios
- [x] Differentiated permissions for `EMPLOYEE` and `ADMIN` on business endpoints
- [x] Separate `local`, `prod`, and `test` profiles
- [x] Pagination and filtering on list endpoints
- [x] Password change with JWT invalidation
- [x] User deactivation and reactivation
- [x] Automatic daily savings yield with recovery after downtime
- [ ] Docker Compose for the application and database
- [ ] CI/CD pipeline with GitHub Actions
- [ ] Refresh tokens