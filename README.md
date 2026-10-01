# Banking System

## About
A banking system backend developed with Java and Spring Boot.

The project started as a pure Java application and has evolved into a RESTful API using Spring Boot, PostgreSQL, Spring Data JPA, and Spring Security.

The main goal is to apply backend development concepts such as layered architecture, object-oriented programming, database persistence, authentication, authorization, business rules, external API integration, event-driven logging, and automated testing.

---

## Technologies

| Technology         | Version | Description                              |
| ------------------ | ------- | ---------------------------------------- |
| Java               | 21      | Programming language                     |
| Spring Boot        | 4.1.1   | Backend framework                        |
| Spring Web MVC     | -       | REST API development                     |
| Spring RestClient  | -       | HTTP communication with external APIs    |
| Bean Validation    | -       | Request validation                       |
| Spring Data JPA    | -       | Data access and persistence              |
| Hibernate          | -       | JPA implementation and ORM               |
| Spring Security    | -       | Authentication and authorization         |
| Spring Events      | -       | Decoupled operation logging              |
| JWT (JJWT)         | 0.13.0  | Stateless authentication                 |
| BCrypt             | -       | Password hashing                         |
| PostgreSQL         | 18      | Relational database                      |
| springdoc-openapi  | 3.1.1   | OpenAPI / Swagger UI documentation       |
| Maven              | -       | Dependency management and build          |
| JUnit              | 6       | Unit and integration testing             |
| Mockito            | 5       | Mocking for tests                        |

---

## Architecture

The application follows a layered architecture, separating responsibilities into distinct layers.

### Package Structure

```text
src/main/java/com/lucas/bankingsystem
├── config/       # Application and security configuration
├── controller/   # REST API endpoints
├── dto/          # Request and response DTOs
├── entity/       # JPA entities and domain models (enums in entity/enums)
├── event/        # Operation events and listeners used for logging
├── exception/    # Custom exceptions and exception handlers (global and address-specific)
├── integration/  # External API integrations
│   └── address/  # Address lookup providers (ViaCEP, BrasilAPI) and related components
├── repository/   # Database access through Spring Data JPA
├── service/      # Business rules and application logic
└── validation/   # Custom validators (CPF)
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
│  Controller     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│     Service     │
│ Business Rules  │
└────────┬────────┘
         │
         ├──────────────► Repository
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
         ├──────────────► Events (published during the operation,
         │                logged by listeners after commit)
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
- **Account management**: checking and savings accounts, with generated account numbers and check digits
- **Transactions**: deposit, withdrawal, transfer between accounts, transaction history per account, and lookup by public identifier
- **Public transaction identifier**: every transaction has a UUID (`transactionCode`) exposed in responses, while the internal database ID is never returned
- **Savings yield**: monthly yield applied to savings accounts
- **Account cancellation** with balance and status validation
- **Input validation** using Bean Validation and custom CPF validation
- **Global exception handling** with a consistent error response
- **Event-driven logging** of user, client, account, and transaction operations
- **Interactive API documentation** with Swagger UI, which can be disabled through configuration

---

## Business Rules

### Clients
- CPF must be unique and contain exactly 11 digits.
- CPF validation includes format checks and verification digit calculation.
- Phone number must contain 10 to 11 digits, including the area code.
- Every client requires an address with a postal code (CEP), a street number, and an optional complement.
- Address details such as street name, neighborhood, city, and state are retrieved through external postal code providers.
- Client updates are partial: only the fields sent in the request are changed, and omitted fields are left as they are.
- When an update includes an address, the street number and complement are updated as sent. If a new postal code is sent, the address details are looked up again through the providers.

### Accounts
- A client can have at most one active checking account and one active savings account. A new account of the same type can be opened after the previous one is canceled.
- Account numbers are generated from a database sequence (5 digits) followed by a check digit (modulo 11).
- Every operation that receives an account number also requires the check digit, which is validated before the account lookup.
- An account can only be canceled if it is active and has a zero balance.

### Transactions
- Deposits, withdrawals, and transfers require an active account and a positive amount.
- Amounts accept at most 17 integer digits and 2 decimal places.
- Withdrawals and transfers require sufficient balance.
- A transfer requires distinct and active source and destination accounts, and records one `TRANSFER_SENT` and one `TRANSFER_RECEIVED` transaction.
- All operations run inside a database transaction, so balance changes and transaction records are saved together or not at all.
- Every transaction receives a random UUID (`transactionCode`) when it is created. It is unique, immutable, and is the only identifier exposed by the API; the internal numeric ID is not part of the response.
- A transaction can be retrieved by its `transactionCode`: `404 Not Found` if it does not exist and `400 Bad Request` if the value is not a valid UUID.

### Savings Yield
- Yield is calculated as 0.5% of the current balance, rounded to 2 decimal places.
- An account becomes eligible one month after its creation or last yield date.
- Yield can be applied only once per day per account, enforced by the service and by a partial unique index in the database.

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
- Provider responses are validated (street, neighborhood, city, state, and an 8-digit postal code are required). An invalid response is treated as a provider failure.
- Provider failures are handled separately from postal codes that are not found:
  - `404 Not Found` when every provider answered and none found the postal code.
  - `503 Service Unavailable` when at least one provider failed and no other provider found the postal code.

---

## Authentication and Authorization

The API is stateless. Clients authenticate through `POST /auth/login` and send the returned token in every request:

```http
Authorization: Bearer <token>
```

| Aspect             | Behavior                                                                 |
|--------------------|--------------------------------------------------------------------------|
| Token              | JWT signed with HMAC, valid for 1 hour                                   |
| Public routes      | `/auth/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`      |
| Protected routes   | Everything else requires a valid token                                   |
| Admin-only routes  | `POST /users` (`@PreAuthorize("hasRole('ADMIN')")`)                      |
| Invalid token      | `401 Unauthorized`                                                       |
| Insufficient role  | `403 Forbidden`                                                          |

---

## API Endpoints

Full request and response schemas are available in Swagger UI.

| Method | Endpoint                                               | Description                          | Access        |
|--------|--------------------------------------------------------|--------------------------------------|---------------|
| POST   | `/auth/login`                                          | Authenticate and receive a JWT       | Public        |
| POST   | `/users`                                               | Create a user                        | `ADMIN`       |
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

### Address Lookup Endpoint

| Method | Endpoint                                | Description                       | Access        |
| ------ | --------------------------------------- | --------------------------------- | ------------- |
| GET    | `/api/v1/addresses/lookup/{postalCode}` | Look up an address by postal code | Authenticated |

Possible responses: `200` (address found), `400` (postal code is not 8 digits), `404` (postal code not found), and `503` (address providers unavailable).

---

### Example

```bash
# 1. Log in
curl -X POST http://localhost:8080/auth/login \
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

---

## Error Handling

Errors are handled by a global exception handler, and business rule violations extend a common `BusinessException`. Address provider failures are handled separately by `AddressExceptionHandler`, which applies only to the address lookup and client controllers. Every error returns the same structure:

```json
{
  "status": 400,
  "message": "O valor informado é maior do que o saldo."
}
```

Validation errors also include the invalid fields:

```json
{
  "status": 400,
  "message": "Erro de validação.",
  "errors": {
    "postalCode": "O CEP deve conter exatamente 8 dígitos."
  }
}
```

| Scenario                                                       | Status                      |
|----------------------------------------------------------------|-----------------------------|
| Business rule violation                                        | Defined by each exception   |
| Bean Validation failure, invalid parameters, or malformed body | `400 Bad Request`           |
| Invalid credentials                                            | `401 Unauthorized`          |
| Access denied                                                  | `403 Forbidden`             |
| Address providers unavailable                                  | `503 Service Unavailable`   |
| Unexpected error                                               | `500 Internal Server Error` |

---

## Logging

Operations are logged through Spring application events:

- Services publish an event after creating or changing data: client `CREATED`/`UPDATED`, account `CREATED`/`CANCELLED`, user `CREATED`, and transactions (deposit, withdrawal, transfer, and yield).
- Listeners use `@TransactionalEventListener` in the `AFTER_COMMIT` phase, so an operation is only logged as successful after its database transaction is committed.
- The global exception handler logs business errors, authentication and authorization failures, data integrity violations, and unexpected errors. Address provider failures are logged by `AddressExceptionHandler`.
- The log level is controlled by `logging.level.com.lucas.bankingsystem` (`INFO` by default), and SQL logging is disabled.

---

## Database

- PostgreSQL, with schema generated by Hibernate (`ddl-auto=update`).
- `Account` uses joined inheritance (`CheckingAccount` and `SavingsAccount` extend it).
- `schema.sql` creates the account number sequence and the partial unique index that prevents more than one yield per account per day.
- Monetary values use `BigDecimal` (`precision = 19`, `scale = 2`).
- `Transaction` has a `transaction_code` column (UUID, not null, not updatable) with the `uk_transaction_code` unique constraint, used as its public identifier.
- Open Session in View is disabled (`spring.jpa.open-in-view=false`). Account queries that need the client use `JOIN FETCH` to avoid lazy loading outside a transaction.
- Read operations in the services run with `@Transactional(readOnly = true)`, while operations that change data use regular transactions.

---

## Getting Started

### Prerequisites

- Java 21
- PostgreSQL 18
- Maven (or the included Maven Wrapper)

### Configuration

1. Create the databases:

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

Use the **Authorize** button and paste the JWT returned by `/auth/login` to call protected endpoints.

To disable the documentation (for example, in production), set `SWAGGER_ENABLED=false`.

---

## Testing

```bash
./mvnw test
```

The test suite covers:

- **Unit tests** for services, using JUnit and Mockito: accounts, clients, transactions (deposit, withdrawal, transfer, yield), users, JWT, authentication, user details loading, and address lookup provider orchestration.
- **Controller tests** using `MockMvc` for clients, accounts, transactions (including lookup by public transaction code and invalid UUID handling), and authentication.
- **Validation tests** for CPF validation.
- **Context test** to verify the application starts.

Tests run with the `test` profile against the `banking_system_test` database, which is recreated on each run (`create-drop`).

---

## Roadmap

- [ ] Unit tests for external address providers (`ViaCepClient` and `BrasilApiClient`)
- [ ] Tests for `AddressService` and `AddressLookupController`
- [ ] Metrics and health checks (Spring Boot Actuator)
- [ ] Review and expand entity constraints and relationships
- [ ] Integration tests with PostgreSQL for transaction and concurrency scenarios
- [ ] Differentiated permissions for `EMPLOYEE` and `ADMIN` on business endpoints
- [ ] Pagination and filtering on list endpoints
- [ ] Docker Compose for the application and database
- [ ] CI/CD pipeline with GitHub Actions
- [ ] Evaluate migration management with Flyway
- [ ] Refresh tokens
- [ ] Consider a microservices architecture after consolidating the current application
