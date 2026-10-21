# Architecture

The application follows a modular layered architecture, separating responsibilities into distinct components and layers.

## Package Structure

```text
src/main/java/com/lcsalvess/bankingsystem
├── bootstrap/       # Application startup and initial administrator creation
├── config/          # Jackson, JWT, OpenAPI, pagination, clock, and security configuration
├── controller/      # REST endpoints for accounts, authentication, clients, transactions, and users
├── dto/             # Request and response DTOs, grouped by domain
├── entity/          # Domain entities and enums, grouped by domain
├── event/           # Application events and post-commit operation listeners
├── exception/          # Business exceptions, database constraints, error messages, and global handling
├── filter/             # HTTP filters, including correlation ID propagation
├── integration/
│   └── address/        # External address-provider clients and lookup orchestration
│       ├── brasilapi/
│       ├── config/
│       ├── dto/
│       ├── exception/
│       ├── validation/
│       └── viacep/
├── repository/         # Persistence repositories, query specifications, and projections
│   ├── idempotency/    # Transaction idempotency persistence
│   ├── projection/     # Read-only query projections
│   └── specification/ # Dynamic filters
├── security/           # JWT authentication filter and JSON handlers for 401/403 responses
├── serialization/      # Custom JSON serialization
├── util/               # Normalization, CPF masking, time-zone, and pagination utilities
├── service/            # Business rules and application logic
│   ├── account/        # Account operations, row locking, and account number generation
│   ├── address/        # Address-resolution application logic
│   ├── client/         # Client operations and persistence responsibilities
│   ├── security/       # Authentication, JWT, password changes, and current user
│   ├── transaction/    # Financial operations and balance reconciliation
│   │   ├── idempotency/ # Idempotency handling and scheduled cleanup
│   │   └── yield/        # Daily yield processing and startup recovery
│   └── user/           # User operations
└── validation/      # Custom Bean Validation constraints and validators
    ├── accountnumber/      # Account number and check digit validation
    ├── cpf/                # CPF validation
    ├── passwordbytelength/ # BCrypt UTF-8 byte-size limit
    ├── passwordstrength/   # Password complexity requirements
    └── reservedusername/   # Reserved username validation
```

## Request Flow

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