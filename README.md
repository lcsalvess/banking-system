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
| Spring Web      | -       | REST API development                |
| Spring Data JPA | -       | Data access and persistence         |
| Hibernate       | -       | JPA implementation and ORM          |
| Spring Security | -       | Authentication and authorization    |
| JWT             | -       | Stateless authentication            |
| BCrypt          | -       | Password hashing                    |
| PostgreSQL      | 18      | Relational database                 |
| Maven           | -       | Dependency management and build     |
| JUnit           | 6       | Unit and integration testing        |
| Mockito         | 5       | Mocking for tests                   |

---

## Architecture

The application follows a layered architecture, separating responsibilities into distinct layers.

### Package Structure

```text
src/main/java/com/lucas/sistemabancario
├── config/       # Application and security configuration
├── controller/   # REST API endpoints
├── dto/          # Request and response DTOs
├── entity/       # JPA entities and domain models
├── enums/        # Domain enumerations
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



