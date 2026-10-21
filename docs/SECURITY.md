# Security

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
| Public routes           | `POST /api/v1/auth/login`, `/actuator/health/liveness`, `/actuator/health/readiness`, and OpenAPI routes when enabled (`/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`)                         |
| Authenticated routes    | `PATCH /api/v1/auth/password`, open to any authenticated user, whatever the role                                                                                                                        |
| Protected routes        | Everything else requires a valid token and the role required by the endpoint                                                                                                                            |
| Invalid token           | `401 Unauthorized` (invalid, expired, revoked, or malformed token)                                                                                                                                      |
| Infrastructure failure  | `503 Service Unavailable` when the user cannot be loaded because the database is unavailable, so a valid token is not reported as invalid                                                               |
| Insufficient role       | `403 Forbidden`, returned as JSON by `RestAccessDeniedHandler`                                                                                                                                          |

### Access by role

| Role       | Allowed operations                                                                                                                                                                                                     |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ADMIN`    | Create, list, deactivate, and reactivate users; read the transaction history and look up transactions by code; `/actuator/health`, `/actuator/info`, and `/actuator/metrics`                                           |
| `EMPLOYEE` | Clients (including internal address lookup during creation/update), accounts, deposits, withdrawals, and transfers; read transaction history and look up transactions by code; `/actuator/health` and `/actuator/info` |

Roles are not hierarchical: `ADMIN` does not inherit `EMPLOYEE` permissions. Business operations require an `EMPLOYEE` user, which an `ADMIN` creates through `POST /api/v1/users`. Endpoint access is enforced with `@PreAuthorize` on the controllers and with request rules in `SecurityConfig` for the actuator. Changing one's own password is the only operation open to every authenticated user. The only unauthenticated routes are the login, the liveness and readiness probes, and, when enabled, the OpenAPI documentation; every other route is denied by default.

## Login Rate Limiting

Login attempts are subject to in-memory rate limiting:

- **IP-based limit:** 20-token bucket capacity, replenished at a rate of 20 tokens per minute.
- **Successful login:** clears the recorded failed attempts for that username.
- **Failed authentication attempts:** 5 failures within a 15-minute window.
- **Temporary lockout:** 15 minutes after the failed-attempt threshold is reached.
- **Rate-limit response:** HTTP `429 Too Many Requests`, with a `Retry-After` header.

The rate-limit state is stored locally in Caffeine caches. It is not shared
between application instances and is cleared when the application restarts.