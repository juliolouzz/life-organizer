# Life Organizer

[![CI](https://github.com/juliolouzz/life-organizer/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/juliolouzz/life-organizer/actions/workflows/ci.yml)
[![CodeQL](https://github.com/juliolouzz/life-organizer/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/juliolouzz/life-organizer/actions/workflows/codeql.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

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
# 1. create a .env file in the project root with the values below.
#    .env is gitignored - never commit it.
cat > .env <<'ENV'
DB_URL=jdbc:postgresql://localhost:5432/life_organizer
DB_USERNAME=life_organizer
DB_PASSWORD=life_organizer
JWT_SECRET=$(openssl rand -base64 48)
SPRING_PROFILES_ACTIVE=local
ENV

# 2. start Postgres 16 (named volume; healthcheck included)
docker compose up -d postgres

# 3. load env and boot the API on :8080
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run

# 4. sanity check
curl http://localhost:8080/actuator/health
# -> {"status":"UP"}
```

`JWT_SECRET` **must** be at least 32 characters of high-entropy data — boot fails
fast otherwise. `openssl rand -base64 48` gives 48 bytes of base64.

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

**Slice 1 complete.** All 9 endpoints implemented, 52 tests pass via `mvn verify`,
JaCoCo gate met (≥80% on service + web packages), ArchUnit rules enforced, full
register → login → /me → transaction CRUD flow validated end-to-end against a
live Postgres ([run evidence](docs/run-evidence.md)).

### Acceptance Criteria coverage

| Category | Count | Status |
|---|---|---|
| AC-A1..A16 — Auth & User | 16 | implemented + tested |
| AC-T1..T23 — Transactions | 23 | implemented + tested |
| AC-X1..X13 — Cross-cutting | 13 | implemented + tested |
| **Total** | **52** | **all covered** |

### Test summary

```
Unit tests:        21  (ApiResponse, ExceptionHierarchy, JwtService,
                        CursorCodec, RequestIdFilter, Architecture)
Integration tests: 25  (Schema, JPA repositories, AuthFlow,
                        TransactionFlow, Health, Smoke)
Total:             52  passing
```

### What's next

Slice 1 is API-only by design. Future slices may add:

- Web UI (Angular or React) consuming this API
- Health / fitness vertical
- Diary / notes vertical
- Reports, budgets, recurring transactions
- Production profile + deployment pipeline

See [`docs/specs/slice-1-spec.txt`](docs/specs/slice-1-spec.txt) section 9 for the
explicitly deferred scope.

## License

Personal project — no public license.
