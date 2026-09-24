# Banking System

## About
A banking system backend developed with Java and Spring Boot.
<br>
The project started as a pure Java application and has evolved into a RESTful API using Spring Boot, PostgreSQL, Spring Data JPA, and Spring Security.
<br>
The main goal is to apply backend development concepts such as layered architecture, object-oriented programming, database persistence, authentication, authorization, business rules, and automated testing.

---

## Technologies

| Technology      | Version | Description                         |
|-----------------|---------|-------------------------------------|
| Java            | 21      | Programming language                |
| Spring Boot     | 4.1.1   | Backend framework                   |
| Spring Web MVC  | -       | REST API development                |
| Bean Validation | -       | Request validation                  |
| Spring Data JPA | -       | Data access and persistence         |
| Hibernate       | -       | JPA implementation and ORM          |
| Spring Security | -       | Authentication and authorization    |
| JWT (JJWT)      | 0.13.0  | Stateless authentication            |
| BCrypt          | -       | Password hashing                    |
| PostgreSQL      | 18      | Relational database                 |
| springdoc-openapi | 3.1.1 | OpenAPI / Swagger UI documentation  |
| Maven           | -       | Dependency management and build     |
| JUnit           | 6       | Unit and integration testing        |
| Mockito         | 5       | Mocking for tests                   |

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
├── exception/    # Custom exceptions and global exception handler
├── repository/   # Database access through Spring Data JPA
└── service/      # Business rules and application logic
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
│   Controller    │
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
         ▼
┌─────────────────┐
│  Response DTO   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   Controller    │
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
- **Client management**: registration, listing, lookup, and update, including address data
- **Account management**: checking and savings accounts, with generated account numbers and check digits
- **Transactions**: deposit, withdrawal, transfer between accounts, and transaction history per account
- **Savings yield**: monthly yield applied to savings accounts
- **Account cancellation** with balance and status validation
- **Global exception handling** with a consistent error response
- **Interactive API documentation** with Swagger UI

---

## Business Rules

### Clients
- CPF must be unique and contain exactly 11 digits.
- Phone number must contain 10 to 11 digits, including the area code.
- Every client requires a complete address (street type, street name, number, neighborhood, city, state, and 8-digit postal code).

### Accounts
- A client can have at most one checking account and one savings account.
- Account numbers are generated from a database sequence (5 digits) followed by a check digit (modulo 11).
- Every operation that receives an account number also requires the check digit, which is validated before the account lookup.
- An account can only be cancelled if it is active and has a zero balance.

### Transactions
- Deposits, withdrawals, and transfers require an active account and a positive amount.
- Withdrawals and transfers require sufficient balance.
- A transfer requires distinct and active source and destination accounts, and records one `TRANSFER_SENT` and one `TRANSFER_RECEIVED` transaction.
- All operations run inside a database transaction, so balance changes and transaction records are saved together or not at all.

### Savings Yield
- Yield is calculated as 0.5% of the current balance, rounded to 2 decimal places.
- An account becomes eligible one month after its creation or last yield date.
- Yield can be applied only once per day per account, enforced by the service and by a partial unique index in the database.

### Users
- Only `ADMIN` users can create new users.
- Usernames and e-mails must be unique, and passwords must have at least 8 characters.
- Passwords are stored as BCrypt hashes.
- An initial `ADMIN` is created at startup from environment variables when no admin exists (see [Configuration](#configuration)).

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

| Method | Endpoint                                            | Description                        | Access        |
|--------|-----------------------------------------------------|------------------------------------|---------------|
| POST   | `/auth/login`                                       | Authenticate and receive a JWT     | Public        |
| POST   | `/users`                                            | Create a user                      | `ADMIN`       |
| POST   | `/api/v1/clients`                                   | Register a client                  | Authenticated |
| GET    | `/api/v1/clients`                                   | List clients                       | Authenticated |
| GET    | `/api/v1/clients/{id}`                              | Get a client by ID                 | Authenticated |
| PUT    | `/api/v1/clients/{id}`                              | Update a client                    | Authenticated |
| POST   | `/api/v1/accounts`                                  | Create a checking or savings account | Authenticated |
| GET    | `/api/v1/accounts`                                  | List accounts                      | Authenticated |
| GET    | `/api/v1/accounts/{accountNumber}?digit=`           | Get an account                     | Authenticated |
| PATCH  | `/api/v1/accounts/{accountNumber}?digit=`           | Cancel an account                  | Authenticated |
| POST   | `/api/v1/transactions/deposit`                      | Deposit                            | Authenticated |
| POST   | `/api/v1/transactions/withdraw`                     | Withdraw                           | Authenticated |
| POST   | `/api/v1/transactions/transfer`                     | Transfer between accounts          | Authenticated |
| GET    | `/api/v1/transactions/accounts/{accountNumber}?digit=` | Transaction history of an account | Authenticated |
| POST   | `/api/v1/transactions/yield/{accountNumber}?digit=` | Apply yield to a savings account   | Authenticated |

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
```

---

## Error Handling

Errors are handled by a global exception handler, and business rule violations extend a common `BusinessException`. Every error returns the same structure:

```json
{
  "status": 400,
  "message": "O valor informado é maior do que o saldo."
}
```

| Scenario                                  | Status |
|-------------------------------------------|--------|
| Business rule violation                   | Defined by each exception |
| Bean Validation failure / malformed body  | `400`  |
| Invalid credentials                       | `401`  |
| Access denied                             | `403`  |
| Unexpected error                          | `500`  |

---

## Database

- PostgreSQL, with schema generated by Hibernate (`ddl-auto=update`).
- `Account` uses joined inheritance (`CheckingAccount` and `SavingsAccount` extend it).
- `schema.sql` creates the account number sequence and the partial unique index that prevents more than one yield per account per day.
- Monetary values use `BigDecimal` (`precision = 19`, `scale = 2`).

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

| Variable         | Required | Description                                                     |
|------------------|----------|-----------------------------------------------------------------|
| `DB_USERNAME`    | Yes      | PostgreSQL username                                             |
| `DB_PASSWORD`    | Yes      | PostgreSQL password                                             |
| `JWT_SECRET`     | Yes      | Base64-encoded secret used to sign tokens (at least 256 bits)   |
| `ADMIN_USERNAME` | No       | Username of the initial admin                                   |
| `ADMIN_EMAIL`    | No       | E-mail of the initial admin                                     |
| `ADMIN_PASSWORD` | No       | Password of the initial admin                                   |

The initial admin is created only when no `ADMIN` exists and all three `ADMIN_*` variables are set. To generate a suitable secret:

```bash
openssl rand -base64 32
```

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

---

## Testing

```bash
./mvnw test
```

The test suite has more than 50 tests and covers:

- **Unit tests** for services, using JUnit and Mockito: accounts, clients, transactions (deposit, withdrawal, transfer, yield), users, JWT, authentication, and user details loading
- **Integration tests** with `MockMvc` for the authentication endpoint
- **Context test** to verify the application starts

Tests run with the `test` profile against the `banking_system_test` database, which is recreated on each run (`create-drop`).

---

## Roadmap

- [ ] Pagination and filtering on list endpoints
- [ ] Concurrency control on balance updates (locking)
- [ ] Integration tests for the remaining controllers
- [ ] Differentiated permissions for `EMPLOYEE` and `ADMIN` on business endpoints
- [ ] Docker Compose for the application and database
- [ ] Refresh tokens
