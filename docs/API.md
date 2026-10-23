# API Reference

## Endpoints

Full request and response schemas are available in Swagger UI
when running with the `local` profile.

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

The deposit, withdrawal, and transfer endpoints require an `Idempotency-Key` header with a UUID. Repeating a request with the same key and payload returns the original result; reusing the key with a different operation or payload returns `409 Conflict`.

`POST /api/v1/auth/login` is rate limited per client IP, per username and IP, and per username (see [Login Rate Limiting](SECURITY.md#login-rate-limiting)). When a limit is reached, it returns `429 Too Many Requests` with a `Retry-After` header holding the number of seconds to wait:

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 900
```

Yield is not exposed as an endpoint: it is credited automatically (see [Savings Yield](BUSINESS_RULES.md#savings-yield)) and shows up in the transaction history as `YIELD` transactions.

Request bodies are strict about JSON types: text fields reject numbers and booleans (`"accountNumber": 99999` is rejected), and monetary amounts reject strings (`"amount": "100.00"` is rejected; send `100.00`). Both return `400 Bad Request`.

## Pagination, Sorting, and Filtering

List endpoints accept `page`, `size`, and `sort` (for example `sort=balance,desc`). The maximum page size is 100, and larger values are capped. Sorting by a property outside the list of an endpoint returns `400 Bad Request`. The `id` is always added as a final tie-breaker, so pages keep a stable order.

| Endpoint                                            | Filters                        | Sortable fields                              | Default sort                                 | Default size |
|-----------------------------------------------------|--------------------------------|----------------------------------------------|----------------------------------------------|--------------|
| `GET /api/v1/users`                                 | `search`, `role`, `active`     | `id`, `username`, `email`, `role`, `active`  | `id` ascending                               | 20           |
| `GET /api/v1/clients`                               | `name`                         | `id`, `name`                                 | `id` ascending                               | 10           |
| `GET /api/v1/accounts`                              | -                              | `accountNumber`, `balance`, `status`, `type` | `accountNumber` ascending                    | 10           |
| `GET /api/v1/transactions/accounts/{accountNumber}` | `type`, `startDate`, `endDate` | `createdAt`, `id`, `type`, `amount`          | `createdAt` descending, then `id` descending | 10           |

On the transaction history, `startDate` and `endDate` use the `yyyy-MM-dd` format and are inclusive. Both must be between 2020-01-01 and the current date, and `startDate` cannot be after `endDate` (`400 Bad Request`).

## Address Lookup Integration

Address lookup is not exposed as a standalone endpoint. It is performed internally by `POST /api/v1/clients` and by `PATCH /api/v1/clients/{id}` when address data is included. The client operations can return `404 Not Found` when the postal code is not found or `503 Service Unavailable` when the address providers cannot resolve it due to failures.

## Actuator Endpoints

| Method | Endpoint                     | Description                          | Access               |
| ------ | ---------------------------- | ------------------------------------ | -------------------- |
| GET    | `/actuator/health/liveness`  | Liveness probe (no details)          | Public               |
| GET    | `/actuator/health/readiness` | Readiness probe (no details)         | Public               |
| GET    | `/actuator/health`           | Application health                   | `EMPLOYEE`, `ADMIN`  |
| GET    | `/actuator/info`             | Application information              | `EMPLOYEE`, `ADMIN`  |
| GET    | `/actuator/metrics`          | Micrometer metrics                   | `ADMIN`              |

---

## Examples

The following examples illustrate the main API workflows. Replace the placeholder tokens and identifiers with values returned by your own requests.

```bash
# 1. Log in as the initial admin (replace with the configured ADMIN_PASSWORD)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "SecurePass123!"}'

# 2. Create an employee (ADMIN only; use a password that meets the password policy)
curl -X POST http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"username": "employee", "email": "employee@example.com", "password": "SecurePass123!", "role": "EMPLOYEE"}'

# 3. Log in as the employee, who operates clients, accounts, and transactions
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "employee", "password": "SecurePass123!"}'

# 4. Deposit into an account (each financial request requires an idempotency key)
curl -X POST http://localhost:8080/api/v1/transactions/deposit \
  -H "Authorization: Bearer <employee-token>" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 8e26c4a1-2f5d-4b8a-9c31-7d6e5f4a3210" \
  -d '{"accountNumber": "00001", "digit": "<account-digit>", "amount": 150.00}'

# 5. Partially update a client (only the fields sent are changed)
curl -X PATCH http://localhost:8080/api/v1/clients/1 \
  -H "Authorization: Bearer <employee-token>" \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "11987654321", "address": {"postalCode": "01001000", "streetNumber": "100"}}'

# 6. Look up a transaction by its public code
curl 'http://localhost:8080/api/v1/transactions/code/<transaction-code>' \
  -H "Authorization: Bearer <employee-token>"

# 7. Change your own password (all previously issued tokens become invalid)
curl -X PATCH http://localhost:8080/api/v1/auth/password \
  -H "Authorization: Bearer <employee-token>" \
  -H "Content-Type: application/json" \
  -d '{"currentPassword": "SecurePass123!", "newPassword": "EvenMoreSecure456!"}'

# 8. Deactivate a user (ADMIN only)
curl -X PATCH 'http://localhost:8080/api/v1/users/<user-id>/deactivate' \
  -H "Authorization: Bearer <admin-token>"
```

Transactions are returned with their public identifier, and the internal database ID is not exposed:

```json
{
  "transactionCode": "3f6c1d52-8a0e-4b7d-9c41-2e5a7b9d0f13",
  "type": "DEPOSIT",
  "amount": 150.00,
  "createdAt": "2026-09-30T14:32:10"
}
```

The `type` field can be `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_SENT`, `TRANSFER_RECEIVED`, or `YIELD`.

Transfer transactions also include the `transferCode` shared by both legs of the transfer. The field is omitted for other transaction types:

```json
{
  "transactionCode": "7b0e4a91-5c2d-4f38-8d16-a3c9e1f20b64",
  "transferCode": "c4d8f2a6-19b3-4e57-a0d2-6f81b5e3c790",
  "type": "TRANSFER_SENT",
  "amount": 100.00,
  "createdAt": "2026-10-09T14:20:45"
}
```