# Changelog

All notable changes to this project. Format inspired by Keep a Changelog;
versions follow semantic versioning.

## [0.1.0] — 2026-05-22 — Slice 1: Users + JWT + Transactions

### Added

- **Auth subsystem**
  - `POST /api/v1/auth/register` — email + password + displayName; BCrypt strength 12;
    duplicate email returns 409 `USER_EMAIL_EXISTS`.
  - `POST /api/v1/auth/login` — returns HS256 access (15m) + refresh (7d) token pair.
    Wrong password and unknown email return byte-identical 401 bodies (no enumeration leak).
  - `POST /api/v1/auth/refresh` — issues new access token; validates `typ=refresh` claim;
    returns 401 `USER_NOT_FOUND` if the underlying user was deleted.
  - `GET /api/v1/me` — returns the authenticated user from the JWT subject.
- **Transactions subsystem**
  - `POST /api/v1/transactions` — creates a transaction; `user_id` always taken from the
    JWT, never from the request body.
  - `GET /api/v1/transactions` — keyset pagination with opaque base64 cursor encoding
    `<transactionDate>_<id>`; date-range filter (`from`, `to`); composite ordering by
    `(transaction_date DESC, id DESC)`.
  - `GET /api/v1/transactions/{id}`, `PUT /api/v1/transactions/{id}`,
    `DELETE /api/v1/transactions/{id}` — full CRUD with byte-identical 404 across the three
    "not found" cases (missing id, non-owner, soft-deleted).
  - PUT is full replace; DELETE is soft (sets `deleted_at`); second DELETE returns 404.
- **Infrastructure**
  - Spring Boot 3.3.5 on Java 21 source level (compiles cleanly on JDK 25).
  - PostgreSQL 16 via Docker Compose; Testcontainers for integration tests.
  - Flyway migrations V1 (users) + V2 (transactions with partial index for fast listing).
  - HikariCP pool tuned to 10 max / 2 idle.
  - `/actuator/health` exposed; reports DB liveness.
- **Cross-cutting**
  - Single `ApiResponse<T>` envelope for every endpoint (success and error alike).
  - Domain exception hierarchy mapped by `@RestControllerAdvice` to documented error codes.
  - `RequestIdFilter` writes a per-request UUID into MDC + `X-Request-Id` response header.
  - ArchUnit tests enforce layering (no upward calls, controllers never touch repositories)
    and CLAUDE.md hard rules (constructor injection only, all DTOs are records).
  - JaCoCo coverage gate ≥80% on `*.service.*` and `*.web.*` packages.
- **Documentation**
  - `CLAUDE.md` — project rules and quick reference.
  - `docs/specs/` — slice 1 spec, architecture (14 ADRs), plan (77 TDD steps).
  - `docs/modules/{auth,transactions,common,config}.md` — per-module docs.
  - `docs/run-evidence.md` — captured curl run of every endpoint.

### Security choices documented as accepted risks

- No server-side token revocation (R2). TTL-only.
- No HS256 key rotation procedure (R6).
- No rate limiting / IP throttling (R12).
- No optimistic locking (`@Version`); last-write-wins on concurrent PUT.

### Build

- `mvn test`   → 21 unit tests
- `mvn verify` → 52 tests total (unit + integration), JaCoCo gate enforced

### Branch strategy

Each phase developed on `feature/phase-N-*` and merged to `main` via `--no-ff`
once green. The merge graph documents the phased build.
