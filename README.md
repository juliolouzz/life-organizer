# Life Organizer

Personal finance + life management API. **Slice 1**: user accounts, JWT auth, transactions CRUD.

> Behavioral contract: [`docs/specs/slice-1-spec.txt`](docs/specs/slice-1-spec.txt)
> Architecture: [`docs/specs/slice-1-architecture.md`](docs/specs/slice-1-architecture.md)
> Implementation plan: [`docs/specs/slice-1-plan.md`](docs/specs/slice-1-plan.md)
> Project rules: [`CLAUDE.md`](CLAUDE.md)

## Stack

Java 21 (source) | Spring Boot 3.3.x | PostgreSQL 16 | Flyway | JPA/Hibernate | JUnit 5 | Testcontainers | Maven | Docker Compose

## Quick Start

### Prerequisites

- JDK 21+ (Java 25 also works; source level pinned to 21)
- Maven 3.9+
- Docker Desktop
- (Optional) `gh` CLI for PR workflows

### Boot

```bash
# 1. copy and fill the env template — JWT_SECRET needs >=32 chars
cp .env.example .env

# 2. start Postgres 16 (named volume; healthcheck included)
docker compose up -d postgres

# 3. boot the API on :8080
mvn spring-boot:run

# 4. sanity check
curl http://localhost:8080/actuator/health
# -> {"status":"UP"}
```

### Run tests

```bash
mvn test       # unit tests only
mvn verify     # unit + integration + JaCoCo coverage gate + ArchUnit
```

## API Summary

All endpoints under `/api/v1`. Response envelope:

```json
{ "success": true|false, "data": <T>|null, "message": "..."|null, "meta": {...}|null }
```

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/auth/register` | none | create user, returns 201 |
| POST | `/auth/login` | none | returns access + refresh JWT pair |
| POST | `/auth/refresh` | refresh JWT | new access token |
| GET | `/me` | access JWT | the authenticated user |
| POST | `/transactions` | access JWT | create transaction |
| GET | `/transactions` | access JWT | list with keyset pagination |
| GET | `/transactions/{id}` | access JWT | get one |
| PUT | `/transactions/{id}` | access JWT | replace all fields |
| DELETE | `/transactions/{id}` | access JWT | soft delete |

Full request/response schemas, validation rules, and error codes are in `docs/specs/slice-1-spec.txt` §6.

## Module Layout

```
src/main/java/com/julio/lifeorganizer/
├── LifeOrganizerApplication.java
├── config/                  # SecurityConfig, JwtProperties, beans
├── common/
│   ├── api/                 # ApiResponse, PageMeta records
│   ├── exception/           # DomainException hierarchy + GlobalExceptionHandler
│   └── logging/             # RequestIdFilter
├── auth/
│   ├── domain/              # Role enum
│   ├── persistence/         # UserEntity, UserRepository
│   ├── security/            # JwtAuthenticationFilter, EntryPoint, AuthenticatedUser
│   ├── service/             # JwtService, AuthService, UserService
│   └── web/                 # AuthController, UserController + DTOs
└── transactions/
    ├── domain/              # TransactionType enum, CursorCodec
    ├── persistence/         # TransactionEntity, TransactionRepository
    ├── service/             # TransactionService
    └── web/                 # TransactionController + DTOs
```

## Status

Active development. See [`docs/specs/slice-1-plan.md`](docs/specs/slice-1-plan.md) for current progress through the 8-phase plan.

## License

Personal project — no public license.
