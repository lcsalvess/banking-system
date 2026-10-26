# Security

## Authentication and Authorization

The API is stateless. Clients authenticate through `POST /api/v1/auth/login` and send the returned token in every request:

```http
Authorization: Bearer <token>
```

| Aspect                  | Behavior                                                                                                                                                                                                                                                                                                                                                   |
|-------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Token                   | JWT signed with HMAC (key of at least 256 bits), valid for 1 hour by default                                                                                                                                                                                                                                                                               |
| Token validation        | Signature, expiration, and issuer (`jwt.issuer`) are verified on every request                                                                                                                                                                                                                                                                             |
| Token revocation        | The `tv` claim is required and must be a non-negative integer within the supported integer range. It must match the user's `token_version` in the database. A password change or account deactivation increments `token_version`, invalidating previously issued tokens. Tokens with a missing or invalid `tv` claim are rejected with `401 Unauthorized`. |
| Invalid version claim   | A `tv` claim that is not a non-negative integer is rejected as an invalid token (`401 Unauthorized`)                                                                                                                                                                                                                                                       |
| User status             | The user is loaded from the database on every request; a deactivated user is rejected with `401 Unauthorized`                                                                                                                                                                                                                                              |
| Login of inactive users | Same `401 Unauthorized` and message as wrong credentials, with a password check against a dummy hash to keep the response time similar                                                                                                                                                                                                                     |
| Startup check           | The application does not start if `JWT_SECRET` is missing, is not valid Base64, or is too short                                                                                                                                                                                                                                                            |
| Public routes           | `POST /api/v1/auth/login`, `/actuator/health/liveness`, `/actuator/health/readiness`, and OpenAPI routes when enabled (`/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`)                                                                                                                                                                            |
| Authenticated routes    | `PATCH /api/v1/auth/password`, open to any authenticated user, whatever the role                                                                                                                                                                                                                                                                           |
| Protected routes        | Everything else requires a valid token and the role required by the endpoint                                                                                                                                                                                                                                                                               |
| Invalid token           | `401 Unauthorized` (invalid, expired, revoked, or malformed token)                                                                                                                                                                                                                                                                                         |
| Infrastructure failure  | `503 Service Unavailable` when the user cannot be loaded because the database is unavailable, so a valid token is not reported as invalid                                                                                                                                                                                                                  |
| Insufficient role       | `403 Forbidden`, returned as JSON by `RestAccessDeniedHandler`                                                                                                                                                                                                                                                                                             |

### Access by role

| Role       | Allowed operations                                                                                                                                                                                                     |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ADMIN`    | Create, list, deactivate, and reactivate other users; `/actuator/health`, `/actuator/info`, and `/actuator/metrics`. An administrator cannot deactivate their own account                                              |
| `EMPLOYEE` | Client and account management, deposits, withdrawals, transfers, and transaction history; `/actuator/health` and `/actuator/info`.                                                                                     |
| `AUDITOR`  | Read-only access to the audit trail through `GET /api/v1/audit-events`, including filtering, pagination, and sorting. No user administration, financial operations, or transaction history access.                     |

Roles are not hierarchical: `ADMIN`, `EMPLOYEE`, and `AUDITOR`
have separate permissions.

`ADMIN` is responsible for user administration and cannot perform
business operations or access transaction history. An administrator
cannot deactivate their own account, but may change their own password.

`EMPLOYEE` performs business operations and may access transaction
history, but cannot administer users or read the audit trail.

`AUDITOR` can read the audit trail through the dedicated audit endpoint,
but cannot perform business operations, access transaction history,
or administer users.

Changing one's own password is available to every authenticated user.
The audit API is read-only, and the database enforces the append-only
nature of audit records. All requests without an explicit authorization
rule are denied by default.

## Login Rate Limiting

Every login attempt goes through three independent controls, checked in this order. Any of them answers `429 Too Many Requests` with a `Retry-After` header.

| Control            | Default                                                         | Key                     | Purpose                                                          |
|--------------------|-----------------------------------------------------------------|-------------------------|------------------------------------------------------------------|
| IP bucket          | 20 tokens, refilled at 20 per minute                            | Client IP               | Limits the request rate of one client                            |
| Username + IP      | 5 attempts per 15-minute window, then a 15-minute lock          | Username and client IP  | Stops password guessing from one address                         |
| Username (any IP)  | 50 attempts per 15-minute window, then a 15-minute lock         | Username                | Contains guessing distributed across many addresses              |

How it behaves:

- **Atomic counting:** an attempt is counted *before* the password is checked, so parallel requests cannot exceed the limit while BCrypt is still running. The attempt that reaches the limit is still evaluated; the next one is rejected.
- **What counts:** wrong passwords, unknown usernames, and inactive accounts, all with the same response. Unknown usernames are tracked like existing ones, so the limiter does not reveal which usernames exist.
- **What does not count:** an attempt that cannot be evaluated because of an infrastructure failure (for example, the database is down) is released and not counted against the user.
- **Successful login:** clears the counters of that username, for that IP and across all IPs.
- **Requests that fail validation** (`400 Bad Request`, such as a blank username) never reach the limiter. The IP bucket, on the other hand, is consumed by every login request that reaches it, successful or not.
- **While locked,** even the correct password is rejected until the lock ends. A lock is never extended by further attempts.

### Design trade-offs

- **Why the lock includes the IP.** A lock keyed only by username lets anyone who knows a username (for example `admin`) lock that user out indefinitely. Keyed by username and IP, a wrong-password attacker only locks their own address, and the legitimate user can still sign in from another one. The higher per-username limit still stops distributed guessing. An attacker with many addresses can reach it and lock a username for 15 minutes, which is the accepted cost; the `username` rejection metric makes this visible.
- **Client IP.** The limiter uses `request.getRemoteAddr()`. Behind a reverse proxy or load balancer, every client appears with the proxy's address and they all share one IP bucket (20 requests per minute in total). To use the original address, set `PROXY_IP_HEADER` (for example `X-Forwarded-For`) and `TRUSTED_PROXIES` (a regular expression with the proxy IPs). The header is only honored for requests coming from a trusted proxy; without `TRUSTED_PROXIES` it is ignored. Never trust the header without restricting the proxies, or any client can forge its address.
- **IPv6.** Each address is limited individually, so a client holding a whole IPv6 prefix can rotate addresses. The per-username limit still applies. Values that are not a well-formed IP text are grouped in a single `invalid` bucket.
- **Memory and eviction.** State is kept in Caffeine caches limited to `banking.security.login.maximum-tracked-keys` entries (100000 by default). When a cache is full, Caffeine may evict entries, which ends a lock earlier than expected. This needs a flood of distinct usernames or addresses; size the limit accordingly and rate-limit at the edge when the API is exposed to the internet.
- **Local state.** The state is not shared between application instances and is cleared when the application restarts.

### Configuration

| Property                                                    | Default  | Description                                                                              |
|-------------------------------------------------------------|----------|------------------------------------------------------------------------------------------|
| `banking.security.login.ip-capacity`                        | `20`     | Capacity of the IP bucket                                                                |
| `banking.security.login.ip-refill-period`                   | `1m`     | Period in which the IP bucket is fully refilled                                          |
| `banking.security.login.max-failed-attempts`                | `5`      | Attempts per username and IP before the lock                                             |
| `banking.security.login.max-failed-attempts-per-username`   | `50`     | Attempts per username, all IPs, before the lock (must be at least `max-failed-attempts`) |
| `banking.security.login.failure-window`                     | `15m`    | Window in which attempts are added up                                                    |
| `banking.security.login.lock-duration`                      | `15m`    | Duration of a lock                                                                       |
| `banking.security.login.maximum-tracked-keys`               | `100000` | Maximum entries kept per cache                                                           |

Invalid values stop the application at startup.

### Monitoring

Each rejection increments the `security.rate_limit.rejected` counter, tagged by `operation` (`login` or `password_change`) and `scope` (`client_ip`, `username_ip`, `username`, or `user_id`), and logs a warning with the operation, the scope, and the subject (the client IP, or `user:<id>` for password changes). Usernames are not logged. A rise in `username` rejections indicates a distributed attack or a locked-out account; a rise in `password_change` rejections (scope `user_id`) indicates someone guessing the current password with a valid token.

This counter replaces the former `login.rate_limit.rejected`, which was tagged by `reason`. Update dashboards and alerts accordingly.

## Password Change Rate Limiting

`PATCH /api/v1/auth/password` confirms the current password, so a stolen or left-open token could otherwise be used to guess it without limit. The endpoint therefore reuses the login limiter with a separate counter per authenticated user.

- **Same limits as the login.** It uses `max-failed-attempts`, `failure-window`, and `lock-duration` from the table above: 5 wrong current passwords per 15-minute window, then a 15-minute lock answered with `429 Too Many Requests` and a `Retry-After` header.
- **Independent from the login.** The counter has its own key (`password-change:<user id>`). Wrong current passwords do not lock the user out of the login, and login failures do not block a password change.
- **Atomic counting.** As in the login, the attempt is counted *before* the password is checked, so parallel requests cannot exceed the limit while BCrypt is still running. The attempt that reaches the limit is still evaluated; the next one is rejected.
- **Reset and cancellation.** A failed current-password verification remains counted. If the user lookup or current-password verification cannot be completed because of an exception, the reserved attempt is cancelled. Once the current password is confirmed, the counter is reset; a later transaction rollback does not restore the previous in-memory counter state.
- **Accepted cost.** Someone holding the token can lock the owner's password change for 15 minutes. The login is not affected.
- **Local state.** Like the login limiter, the state is in memory, is not shared between instances, and is cleared on restart.