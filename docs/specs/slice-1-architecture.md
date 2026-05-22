# Life Organizer — Slice 1 Architecture

**Project**: Life Organizer (personal learning project)
**Slice**: 1 — Users + JWT auth + Transactions CRUD
**Stack**: Java 21 LTS · Spring Boot 3.x · PostgreSQL 16 · Flyway · JPA/Hibernate 6 · JUnit 5 · Mockito · Testcontainers · Maven
**Spec reference**: `/Users/juliolouzano/Desktop/life-organizer-slice-1-spec.txt`
**Status**: Proposed
**Date**: 2026-05-14

> This document is a set of Architecture Decision Records (ADRs). Each ADR follows the format **Context → Decision → Consequences (Positive / Negative / Neutral) → Alternatives Considered**. The goal is a production-shaped, learning-friendly design that satisfies every Acceptance Criterion in the approved spec without over-engineering.

---

## ADR-001: Module & Package Structure

### Context

The spec defines two clear bounded contexts in slice 1 — **Auth/Identity** and **Transactions** — plus cross-cutting concerns (exception handler, ApiResponse envelope, security filter, configuration properties). The CLAUDE.md style rules favor **feature-oriented packaging** (group by domain, not by type) and warn against generic `utils/` or top-level `controller/` folders.

For a single Spring Boot module of ~30 source files, feature-oriented packaging gives better discoverability and prepares the codebase for future slices (Health, Diary, Reminders verticals) where each vertical will be added as a sibling package, not interleaved with existing layers.

### Decision

Adopt **feature-oriented top-level packages** under `com.julio.lifeorganizer`. Inside each feature package, use sub-packages by layer (`web`, `service`, `domain`, `persistence`). Cross-cutting concerns go under `common` and `config`.

```
com.julio.lifeorganizer
├── LifeOrganizerApplication.java
├── config/
│   ├── SecurityConfig.java
│   ├── JwtProperties.java
│   ├── PaginationProperties.java
│   └── OpenApiConfig.java                 // (optional, deferred if not needed)
├── common/
│   ├── api/
│   │   ├── ApiResponse.java               // record, generic envelope
│   │   ├── PageMeta.java                  // record { nextCursor, limit }
│   │   └── FieldError.java                // record { field, message }
│   ├── exception/
│   │   ├── DomainException.java           // sealed/abstract base
│   │   ├── NotFoundException.java
│   │   ├── ConflictException.java
│   │   ├── ValidationException.java
│   │   ├── AuthException.java
│   │   ├── InvalidTokenException.java
│   │   ├── TokenExpiredException.java
│   │   └── GlobalExceptionHandler.java    // @RestControllerAdvice
│   └── logging/
│       └── RequestIdFilter.java           // sets MDC requestId + userId
├── auth/
│   ├── web/
│   │   ├── AuthController.java
│   │   ├── MeController.java
│   │   └── dto/
│   │       ├── RegisterRequest.java
│   │       ├── LoginRequest.java
│   │       ├── RefreshRequest.java
│   │       ├── AuthTokensResponse.java
│   │       ├── AccessTokenResponse.java
│   │       └── UserResponse.java
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── UserService.java
│   │   └── JwtService.java
│   ├── security/
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── JwtAuthenticationEntryPoint.java
│   │   ├── JwtAccessDeniedHandler.java
│   │   └── AuthenticatedUser.java         // record principal
│   ├── domain/
│   │   ├── Role.java                      // enum
│   │   └── TokenType.java                 // enum { ACCESS, REFRESH }
│   └── persistence/
│       ├── UserEntity.java
│       └── UserRepository.java
└── transactions/
    ├── web/
    │   ├── TransactionController.java
    │   └── dto/
    │       ├── CreateTransactionRequest.java
    │       ├── UpdateTransactionRequest.java
    │       ├── TransactionResponse.java
    │       └── TransactionListQuery.java   // record bound from @ModelAttribute
    ├── service/
    │   └── TransactionService.java
    ├── domain/
    │   └── TransactionType.java            // enum { INCOME, EXPENSE }
    └── persistence/
        ├── TransactionEntity.java
        └── TransactionRepository.java
```

### Consequences

**Positive**
- New verticals (Health, Diary) drop in as sibling packages with zero churn to existing code.
- Each feature is independently understandable — a new contributor can read all of `auth/` without jumping to `controller/`, `service/`, `repository/` folders.
- Encourages high cohesion within a feature and low coupling between features.
- Clear path to extracting a module per vertical if the project ever outgrows a single deployable.

**Negative**
- Slightly more typing in import statements vs flat layer packages.
- Newcomers used to Spring tutorials (which uniformly use layer packages) need 5 minutes of orientation.

**Neutral**
- Layer-by-layer code reviews require touching multiple feature packages; this is the natural cost of feature packaging and is acceptable.

### Alternatives Considered

| Option | Pros | Cons |
|--------|------|------|
| Layer-oriented (`controller/`, `service/`, `repository/`, `domain/`) | Familiar Spring tutorial pattern; one place to find every controller | Folders balloon as features grow; cross-feature changes touch every folder; violates CLAUDE.md style rule |
| Hexagonal (ports/adapters per feature) | Maximum testability and infra isolation | Over-engineered for slice 1; adds 2x file count for two domains |
| **Feature-oriented with internal layers (chosen)** | Scales with new verticals; matches CLAUDE.md style; minimal ceremony | Slight onboarding cost |

---

## ADR-002: Layering Rules

### Context

Without explicit layering rules, code drifts: controllers start calling repositories directly, services start returning entities, entities pick up HTTP concerns. The spec's `ApiResponse<T>` envelope and DTO-as-record convention only work if layer boundaries are enforced.

### Decision

Adopt a strict **layered architecture per feature**, with the following responsibilities and allowed-import rules:

**Web layer** (`*/web/`)
- May: handle HTTP, parse/validate request DTOs (`@Valid`), call services, wrap return value in `ApiResponse`, set HTTP status.
- May NOT: contain business rules, call repositories, return entities, throw raw `RuntimeException`.
- May import: own DTOs, service interfaces, `ApiResponse`, Spring Web/Security annotations, domain enums.

**Service layer** (`*/service/`)
- May: orchestrate business rules, call repositories, call other services, throw domain exceptions, manage transactions (`@Transactional`), map entity → DTO via static factory.
- May NOT: depend on Spring Web, return `ResponseEntity` or DTOs that contain HTTP details, leak `EntityManager`/`Session` to callers.
- May import: own repositories, own entities, other services (carefully), domain exceptions, request/response DTOs from the same feature's `web/dto`.

**Persistence layer** (`*/persistence/`)
- May: define entities, repository interfaces, custom JPQL queries.
- May NOT: import any web or service class, throw business exceptions, perform validation beyond JPA constraints.
- May import: JPA, domain enums, Hibernate annotations.

**Domain layer** (`*/domain/`)
- May: define enums, value objects, sealed types representing business concepts.
- Must NOT: import Spring, JPA, or Jakarta. Pure Java.

**Cross-feature dependency direction**

```
                              ┌──────────────┐
                              │   config/    │  (depends on ~everything; bootstrap only)
                              └──────┬───────┘
                                     │
            ┌────────────────────────┼────────────────────────┐
            │                        │                        │
            v                        v                        v
   ┌──────────────┐         ┌──────────────┐         ┌──────────────┐
   │   auth/      │         │ transactions/│         │    ...       │  (future features)
   │              │         │              │         │              │
   │  web ──┐     │         │  web ──┐     │         │              │
   │        v     │         │        v     │         │              │
   │  service ┐   │         │  service ┐   │         │              │
   │          v   │         │          v   │         │              │
   │  persistence │         │  persistence │         │              │
   │     │        │         │     │        │         │              │
   │     v        │         │     v        │         │              │
   │  domain      │         │  domain      │         │              │
   └──────┬───────┘         └──────┬───────┘         └──────────────┘
          │                        │
          │                        │ (transactions/service may DEPEND ON auth/security
          │                        │  to read JWT principal — read-only contract via
          │                        │  SecurityContextHolder or an @AuthenticationPrincipal
          │                        │  parameter, NOT direct import of UserService)
          v                        v
                  ┌────────────────────────┐
                  │       common/          │  (api envelope, exceptions, logging)
                  └────────────────────────┘
```

**Concrete rules**
1. `transactions/*` must **not** import `auth/persistence/*` or `auth/service/*`. The JWT subject (`userId`) flows in via Spring's `@AuthenticationPrincipal` or `SecurityContextHolder`. The two features are deliberately decoupled.
2. `common/*` may **not** import any feature package — it is a leaf.
3. `web/dto` records have **no behavior except** static factory methods like `from(Entity e)` and Bean Validation annotations.
4. Entities expose only the getters required; no public setters on identity/audit fields. Mutation goes through domain methods (e.g. `transaction.replaceWith(...)`).

Enforce with an **ArchUnit** test (`ArchitectureTest.java`) in `src/test/java`. Two rules to start:
- `noClasses().that().resideInAPackage("..persistence..").should().dependOnClassesThat().resideInAnyPackage("..web..", "..service..")`
- `classes().that().resideInAPackage("..web..").should().onlyDependOnClassesThat().resideInAnyPackage("..web..", "..service..", "..common..", "..domain..", "org.springframework..", "jakarta..", "java..")`

### Consequences

**Positive**
- Layer violations fail the build, not code review.
- Easy to mock services in `@WebMvcTest`; easy to mock repositories in unit tests.
- Future refactor (e.g. extracting a feature to its own module) is mechanical.

**Negative**
- A bit of upfront ceremony (ArchUnit test).
- Cross-feature service calls require deliberation rather than ad-hoc imports.

**Neutral**
- ArchUnit adds ~1s to test runtime; negligible.

### Alternatives Considered

| Option | Pros | Cons |
|--------|------|------|
| No enforcement, convention-only | Zero setup | Drifts within weeks |
| Module-info / JPMS | True compile-time enforcement | Heavy for slice 1; awkward with Spring Boot fat jars |
| **ArchUnit test (chosen)** | Catches violations in CI; readable rules; well-supported | One extra test dependency |

---

## ADR-003: Spring Security Filter Chain

### Context

The spec requires JWT-based stateless auth with three public endpoints (`/auth/register`, `/auth/login`, `/auth/refresh`, plus `/actuator/health`) and everything else protected. Auth failures must produce the `ApiResponse` envelope with specific `meta.code` values (AC-A13, AC-A14, AC-X1).

### Decision

Configure a single `SecurityFilterChain` bean with the following pipeline:

1. `DisableEncodingFilter` — N/A (Spring defaults are fine)
2. **`RequestIdFilter`** (custom, from `common/logging/`) — generates a UUID `requestId`, puts it in MDC. Runs before security so all logs (including auth failures) carry it.
3. Spring Security filters (CORS, CSRF disabled, session = STATELESS)
4. **`JwtAuthenticationFilter`** (custom, `OncePerRequestFilter`) — extracts `Authorization: Bearer <token>`, validates via `JwtService`, builds an `AuthenticatedUser` principal, places `UsernamePasswordAuthenticationToken` in `SecurityContextHolder`. **No DB call on the hot path**: the principal carries `userId`, `email`, `role` claims and trusts them for the request's duration. A DB lookup is done only when an endpoint explicitly needs the live user (currently `/me`).
5. `AuthorizationFilter` (Spring's, evaluates `authorizeHttpRequests` rules)
6. `ExceptionTranslationFilter` — catches `AuthenticationException` / `AccessDeniedException` and delegates to the entry point / handler.

**Beans**
- `PasswordEncoder` → `BCryptPasswordEncoder(12)` (spec ties this to BCrypt strength 12 / AC-A1).
- `AuthenticationManager` → built via `AuthenticationManagerBuilder` with a `DaoAuthenticationProvider` wired to a `UserDetailsService` that loads from `UserRepository`. Used **only** by `AuthService.login()` — not on every request.
- `JwtAuthenticationEntryPoint` and `JwtAccessDeniedHandler` write `ApiResponse.error(...)` JSON directly via the configured `ObjectMapper`.

**Public route matchers**
```
POST   /api/v1/auth/register       permitAll
POST   /api/v1/auth/login          permitAll
POST   /api/v1/auth/refresh        permitAll
GET    /actuator/health            permitAll
**     /**                         authenticated
```

`/auth/refresh` is `permitAll` at the security layer because the refresh-token validation happens **inside `AuthService.refresh()`**, not in the JWT filter (the filter only accepts `typ=access`). This avoids double-validation logic and gives precise error codes (`INVALID_TOKEN` vs `TOKEN_EXPIRED` from spec 6.3).

**Auth failure → ApiResponse mapping**

| Failure | Translated by | HTTP | meta.code |
|---|---|---|---|
| No `Authorization` header on protected route | `JwtAuthenticationEntryPoint` | 401 | `UNAUTHORIZED` |
| Bearer present but malformed/invalid signature | `JwtAuthenticationFilter` throws `InvalidTokenException` → caught by `GlobalExceptionHandler` | 401 | `INVALID_TOKEN` |
| Bearer present but expired | `JwtAuthenticationFilter` throws `TokenExpiredException` | 401 | `TOKEN_EXPIRED` |
| Bearer present, valid signature, `typ != access` | `JwtAuthenticationFilter` throws `InvalidTokenException` | 401 | `INVALID_TOKEN` |
| Authenticated but lacks role for endpoint | `JwtAccessDeniedHandler` | 403 | `FORBIDDEN` (unused in slice 1 — no admin endpoints) |

> **Implementation note**: filter-thrown exceptions need to be carried to the `GlobalExceptionHandler`. The cleanest pattern is for the filter to delegate to `HandlerExceptionResolver` (bean named `handlerExceptionResolver`), which re-routes the exception through the standard `@RestControllerAdvice`. This keeps **all** error responses going through one place — see ADR-007.

### Consequences

**Positive**
- One source of truth for error responses (the `@RestControllerAdvice`).
- No DB hit per request for token validation — pure signature + claim check.
- Public/private boundary expressed in one readable security config.

**Negative**
- If a user is deleted, their access token remains valid until expiry (max 15 min). Acceptable per spec decision 7e (no revocation) and AC-A14 only tests the expiry case. For `/me`, the spec **does** require detecting deleted users → `USER_NOT_FOUND` (6.4), so that endpoint must do a fresh DB lookup. **Other endpoints (transactions) do not look up the user — the JWT subject is trusted for ownership scoping**, which is correct per AC-T2.

**Neutral**
- `HandlerExceptionResolver` delegation pattern is well-documented but slightly less obvious than throwing from a controller.

### Alternatives Considered

| Option | Pros | Cons |
|--------|------|------|
| DB lookup on every request inside filter | Strict revocation on user delete | Defeats stateless JWT; AC-A14 doesn't require it; spec decision 7e forbids server revocation |
| Catch auth errors only in `AuthenticationEntryPoint` (not via advice) | Slightly fewer moving parts | Duplicates JSON-writing logic; harder to keep envelope consistent |
| **Filter delegates exceptions to `HandlerExceptionResolver` (chosen)** | Single error-response code path; testable via `@WebMvcTest` | Requires injecting the resolver bean |

---

## ADR-004: JWT Issuance & Validation

### Context

Spec locks: HS256, access TTL 900s, refresh TTL 604800s, `typ` claim discriminates, `JWT_SECRET` env var, no server-side revocation. The library is unspecified.

### Decision

**Library: `io.jsonwebtoken:jjwt` (jjwt-api + jjwt-impl + jjwt-jackson) version 0.12.x.**

Rationale vs Nimbus JOSE+JWT:
- jjwt's fluent builder/parser API maps 1:1 to slice 1's needs (sign HS256, validate signature, read claims). Less ceremony.
- Nimbus is more powerful (JWK sets, JWS/JWE separation, OIDC) but slice 1 doesn't need any of that.
- jjwt has zero reflection issues with Java 21 records; Nimbus is fine too but heavier.
- Switching to Nimbus later (e.g. when adopting RS256 + rotating keys) is a 1-file change isolated to `JwtService`.

**Claim layout**

Access token:
```json
{
  "sub":   "42",           // user id, stringified per JWT spec
  "email": "julio@example.com",
  "role":  "ROLE_USER",
  "typ":   "access",
  "iat":   1715683200,
  "exp":   1715684100      // iat + 900
}
```

Refresh token:
```json
{
  "sub": "42",
  "typ": "refresh",
  "iat": 1715683200,
  "exp": 1716288000        // iat + 604800
}
```

Refresh tokens deliberately carry **only `sub` and `typ`** — no email or role. The `/auth/refresh` flow re-reads the user from DB to get current role/email (and to detect a deleted user → 401 `USER_NOT_FOUND` per spec 6.3).

**Signing key bootstrap**

`JwtProperties` (see ADR-011) is a `@ConfigurationProperties(prefix="app.jwt")` record with `@NotBlank String secret`. On Spring context start:
1. Spring binds `app.jwt.secret` from env var `JWT_SECRET` (via property placeholder in `application.yml`).
2. Validation fires; missing/blank secret → fail-fast (AC-A15).
3. `JwtService` constructor decodes the secret. For HS256, jjwt requires >= 256 bits (32 bytes). Two options:
   - Treat the env var as a raw UTF-8 string and require `>= 32` chars (simplest, documented in `.env.example`).
   - Treat it as Base64 and decode. More flexible but adds confusion for a learning project.
   - **Chosen**: raw UTF-8 string with a length check (`>= 32 chars`); throw at startup otherwise.

**Validation**
- Library parser configured with `setSigningKey(...)`, `setAllowedClockSkewSeconds(30)`.
- On parse, jjwt throws:
  - `ExpiredJwtException` → translate to `TokenExpiredException`
  - `MalformedJwtException`, `SignatureException`, `UnsupportedJwtException` → translate to `InvalidTokenException`
- After parsing, **manually** check `typ` claim against the expected type (access vs refresh). Mismatch → `InvalidTokenException`. This is explicit and easy to test.

**Clock skew: 30 seconds.** Standard industry default; tolerates minor NTP drift between client and server. Smaller values risk false expirations in tests run on slow CI; larger values weaken the time bound unnecessarily.

### Consequences

**Positive**
- Single library handles parsing, signing, expiry checks.
- Fail-fast secret validation catches misconfiguration at boot, not at first request.
- Stateless design has zero infra requirements (no Redis, no DB table).

**Negative**
- 30s clock skew means a token can be accepted up to 30s past `exp`. Acceptable for a personal project; tightenable later.
- Logout requires waiting up to 15 min — acknowledged risk (see ADR-014).

**Neutral**
- jjwt 0.12.x is the current stable line (released 2024); maintained.

### Alternatives Considered

| Option | Pros | Cons |
|--------|------|------|
| Nimbus JOSE+JWT | Industry standard, JWK support | Overkill for HS256; more API surface |
| Spring Authorization Server | Full OAuth 2.1 / OIDC | Massive over-engineering for slice 1 |
| Hand-rolled HMAC + Jackson | "Educational" | Reinventing the wheel; security risk |
| **jjwt 0.12.x (chosen)** | Minimal, focused, idiomatic | One more dependency (acceptable) |

---

## ADR-005: Transaction-Ownership Enforcement

### Context

AC-T18, AC-T20, AC-T22 all require **identical 404 bodies** when a transaction is missing, not owned, or soft-deleted. AC-T1, AC-T2, AC-T9 require that the JWT subject — never a body field — drives ownership. Three implementation options:

A. **Service-layer guard**: `findById(id)` then check `entity.userId == currentUserId` and throw `NotFoundException` if not. Two-step.
B. **Query-time predicate**: every repository method takes both `id` and `userId` and filters: `findByIdAndUserId(id, userId)`. One step.
C. **Hibernate filter** (`@FilterDef` + `@FilterName`): activate per-session filter that injects `user_id = :currentUserId` into every query automatically.

### Decision

Use **option B — query-time predicate baked into the repository signature**.

Every transaction repository method takes `userId` as a parameter:
```java
Optional<TransactionEntity> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
List<TransactionEntity> findActiveForUser(Long userId, /* cursor + filters */);
```

The service receives `userId` from the controller (which extracts it from `@AuthenticationPrincipal`), passes it down. Soft-delete predicate is composed with the ownership predicate in the same WHERE clause — see ADR-006.

**404 vs 404-not-owner indistinguishability**

The service throws `NotFoundException("Transaction not found")` whenever `findByIdAndUserIdAndDeletedAtIsNull` returns empty — regardless of whether the row doesn't exist, exists but belongs to another user, or exists but is soft-deleted. The exception type and message are **identical** in all three cases. AC-T18 / AC-T20 / AC-T22 satisfied automatically.

### Consequences

**Positive**
- Impossible to forget the ownership check — it's part of the repository signature.
- Single DB round-trip vs two for option A.
- Identical 404 semantics fall out naturally — no test-case-specific code.
- Trivially mockable in unit tests.

**Negative**
- Repository signatures grow longer (`findByIdAndUserId...` instead of `findById`). Acceptable; it's documentation.
- If the test set ever wants to confirm "row exists but is not owned", it must inspect the DB directly, not the API. Fine.

**Neutral**
- Spec mandates this behavior, so the cost of expressiveness is mandatory anyway.

### Alternatives Considered

| Option | Pros | Cons |
|--------|------|------|
| A — service-layer guard | Repositories are reusable across owners | Two DB calls; easy to forget the second check; allows "exists but not owned" timing-attack discovery |
| **B — query-time predicate (chosen)** | One round-trip; safety-by-default; identical 404 falls out | Slightly longer method names |
| C — Hibernate filter | Cross-cutting; service code looks clean | Filter activation requires session interceptor or AOP; hidden behavior; breaks native queries; not worth the magic for two endpoints |

---

## ADR-006: Soft-Delete Strategy

### Context

Spec: DELETE sets `deleted_at = now()`; soft-deleted rows are invisible to all GETs and to second DELETE (AC-T22). Schema already includes `deleted_at TIMESTAMPTZ NULL` plus a partial index `WHERE deleted_at IS NULL`.

Two implementation options:
A. **Hibernate 6 `@SoftDelete` (or `@SQLDelete` + `@SQLRestriction`)** annotates the entity so Hibernate auto-translates `DELETE` into `UPDATE deleted_at = now()` and auto-appends `deleted_at IS NULL` to every generated SELECT.
B. **Explicit `deleted_at IS NULL` in every repository method**, plus an explicit `softDelete(...)` repository operation.

### Decision

Use **option B — explicit `deleted_at IS NULL` predicate, no Hibernate magic**.

Reasoning:
- The codebase already enforces ownership via explicit predicates (ADR-005). Soft-delete should follow the same explicit pattern. Consistency > cleverness.
- `@SQLRestriction` applies to **all** queries generated from the entity, including JPQL ones. That sounds nice — until a future admin endpoint needs to see soft-deleted rows for an undelete feature. Then the restriction has to be turned off per query, which fights the abstraction.
- Native queries (e.g. the keyset pagination JPQL — see ADR-009) interact poorly with `@SQLRestriction`: Hibernate auto-appends the predicate to JPQL but **not** to native SQL, so the codebase ends up with both implicit and explicit predicates depending on query type. Confusing.
- The partial index `idx_transactions_user_active` is hit either way; query optimizer sees the predicate regardless of source.

**Concrete pattern**

Repository:
```java
@Query("""
    SELECT t FROM TransactionEntity t
    WHERE t.userId = :userId
      AND t.deletedAt IS NULL
      AND (:cursor IS NULL OR t.id < :cursor)
      AND (:from IS NULL OR t.transactionDate >= :from)
      AND (:to   IS NULL OR t.transactionDate <= :to)
    ORDER BY t.transactionDate DESC, t.id DESC
    """)
List<TransactionEntity> findPage(Long userId, Long cursor, LocalDate from, LocalDate to, Pageable pageable);

@Modifying
@Query("UPDATE TransactionEntity t SET t.deletedAt = :now WHERE t.id = :id AND t.userId = :userId AND t.deletedAt IS NULL")
int softDelete(Long id, Long userId, Instant now);
```

Soft-delete via `@Modifying` returns the affected row count. The service checks `if (rows == 0) throw new NotFoundException(...)`. This naturally handles all three failure cases of AC-T22 (missing / not owner / already soft-deleted) with one bullet-proof query.

**PUT behavior**: the update goes through `findByIdAndUserIdAndDeletedAtIsNull(...)` first; if absent, throw `NotFoundException`. If present, mutate fields and let Hibernate flush. The `deleted_at` filter ensures AC-T20 is satisfied.

### Consequences

**Positive**
- Same predicate-driven pattern as ownership → consistent mental model.
- No hidden Hibernate behavior; explicit JPQL is what executes.
- Future "list including soft-deleted" admin endpoint trivially adds a new query without flag-fighting.
- Soft-delete idempotency (second DELETE returns 404) falls out of the `int rows = ...` check, not a separate test.

**Negative**
- Developers must remember to write `AND t.deletedAt IS NULL` in new queries. Mitigation: code review + a single integration test that lists transactions including a soft-deleted one and asserts it's absent.
- Slightly more verbose JPQL.

**Neutral**
- Partial index utilization is identical regardless of approach.

### Alternatives Considered

| Option | Pros | Cons |
|--------|------|------|
| A — `@SQLDelete` + `@SQLRestriction` | Less typing in repositories; auto-applies | Hidden behavior; awkward with native queries; future admin queries fight the restriction; less consistent with ADR-005 |
| Hibernate 6 `@SoftDelete` (newer) | Standard annotation | Same issues as A; less battle-tested in mid-2026 |
| **B — explicit predicate (chosen)** | Consistent; explicit; future-proof | Slightly verbose |

---

## ADR-007: Exception Hierarchy + @RestControllerAdvice Mapping

### Context

Spec mandates `ApiResponse<T>` envelope on every response, including errors, with specific `meta.code` values and identical bodies for ambiguous-by-design cases (404 for transactions, 401 for credentials).

### Decision

**Exception hierarchy** (under `common/exception/`):

```
DomainException (abstract; carries `errorCode` String and overrides constructor for message)
├── NotFoundException             // 404 ; code derived from caller (e.g. "TRANSACTION_NOT_FOUND", "USER_NOT_FOUND")
├── ConflictException             // 409 ; code from caller (e.g. "USER_EMAIL_EXISTS")
├── ValidationException           // 400 ; for service-layer validations not covered by Bean Validation
├── InvalidQueryException         // 400 ; code "INVALID_QUERY" (subclass of ValidationException)
└── AuthException                 // 401 base
    ├── InvalidCredentialsException   // code "INVALID_CREDENTIALS"
    ├── InvalidTokenException         // code "INVALID_TOKEN"
    ├── TokenExpiredException         // code "TOKEN_EXPIRED"
    ├── UnauthorizedException         // code "UNAUTHORIZED" (no/blank Bearer)
    └── UserNotFoundForTokenException // code "USER_NOT_FOUND" (used at /me + /refresh)
```

All extend `RuntimeException` (per CLAUDE.md style: unchecked exceptions only). `errorCode` is part of the constructor, not parsed from the class name.

**GlobalExceptionHandler — full mapping table** (covers every error in spec sections 6.1–6.9):

| Exception | HTTP | meta.code | message source | Notes |
|---|---|---|---|---|
| `MethodArgumentNotValidException` | 400 | (none — `meta` is the field-error map directly) | "Validation failed" | Per AC-X2 / spec §5; flat `Map<String,String>` |
| `ConstraintViolationException` | 400 | (none — `meta` is field-error map) | "Validation failed" | For `@Validated` on query params |
| `HttpMessageNotReadableException` | 400 | `MALFORMED_REQUEST` | "Malformed JSON request body" | Per 6.1 |
| `MethodArgumentTypeMismatchException` | 400 | `INVALID_QUERY` | "Invalid query parameter" | e.g. cursor not a long |
| `InvalidQueryException` | 400 | `INVALID_QUERY` | exception message | from > to, limit out of range |
| `ConflictException` (USER_EMAIL_EXISTS) | 409 | `USER_EMAIL_EXISTS` | "Email already registered" | AC-A2 |
| `NotFoundException` (TRANSACTION_NOT_FOUND) | 404 | `TRANSACTION_NOT_FOUND` | "Transaction not found" | AC-T18/T20/T22; identical body |
| `NotFoundException` (USER_NOT_FOUND) | 404 | `USER_NOT_FOUND` | "User not found" | Only at admin paths; slice 1 doesn't trigger this |
| `UserNotFoundForTokenException` | 401 | `USER_NOT_FOUND` | "User not found" | AC for /me and /refresh when user was deleted |
| `UnauthorizedException` | 401 | `UNAUTHORIZED` | "Authentication required" | AC-A13 |
| `InvalidCredentialsException` | 401 | `INVALID_CREDENTIALS` | "Invalid email or password" | AC-A7, AC-A8 — identical body for both |
| `InvalidTokenException` | 401 | `INVALID_TOKEN` | "Invalid token" | AC-A10, 6.4 |
| `TokenExpiredException` | 401 | `TOKEN_EXPIRED` | "Token expired" | AC-A11, AC-A14 |
| `AccessDeniedException` (Spring) | 403 | `FORBIDDEN` | "Access denied" | Unused in slice 1 (no roles enforce) |
| `Exception` (fallback) | 500 | `INTERNAL_ERROR` | "An unexpected error occurred" | AC-X3: log full stack at ERROR, no leak |

**Response shape examples**

Validation failure (per spec §5 + AC-X2):
```json
{
  "success": false,
  "data": null,
  "message": "Validation failed",
  "meta": { "email": "must be a well-formed email address", "password": "size must be between 8 and 100" }
}
```

Business error (per spec §5):
```json
{
  "success": false,
  "data": null,
  "message": "Transaction not found",
  "meta": { "code": "TRANSACTION_NOT_FOUND" }
}
```

**Logging in the handler** (AC-X4)

```java
log.error("Request {} {} failed: code={} userId={} requestId={}",
    request.getMethod(), request.getRequestURI(),
    errorCode, MDC.get("userId"), MDC.get("requestId"), ex);
```

5xx logged at ERROR with full stack; 4xx logged at WARN with summary (no stack) to avoid log pollution from misbehaving clients. **Never** log request bodies (could contain passwords).

**Filter → advice bridging** (continued from ADR-003)

The `JwtAuthenticationFilter` calls `handlerExceptionResolver.resolveException(req, res, null, thrownException)` instead of writing the response directly. The resolver finds the `@ExceptionHandler` method on `GlobalExceptionHandler` and renders the standard envelope. This unifies error responses; without it, filter-thrown errors would need their own JSON-writing logic.

### Consequences

**Positive**
- Every spec error case is covered explicitly in a single table the developer can scan.
- Identical-body requirements (AC-T18, AC-A8) are satisfied trivially because the same exception type → same response generator.
- Adding a new error code in a future slice is a one-line addition to the table.

**Negative**
- ~15 small exception classes. Inevitable given the spec's distinct `meta.code` values.

**Neutral**
- Tying logging level to HTTP class (5xx=ERROR, 4xx=WARN) is convention but documented here for the team.

### Alternatives Considered

| Option | Pros | Cons |
|--------|------|------|
| Single `DomainException` with code parameter | Fewer classes | Loses type-safety; harder to test specific cases |
| Catch in filter, write directly | Slightly fewer hops | Duplicates JSON writing; harder to keep envelope consistent |
| **Typed hierarchy + advice + resolver bridge (chosen)** | Single error code path; easy to test; idiomatic | More classes (acceptable) |

---

## ADR-008: ApiResponse<T> Envelope Integration

### Context

Spec §5 mandates `{ success, data, message, meta }` on every response. Slice 1 controllers can either:
A. Return `ApiResponse<T>` directly from every method.
B. Return `T` (or `ResponseEntity<T>`) and let a `ResponseBodyAdvice` / `HandlerMethodReturnValueHandler` wrap it automatically.

### Decision

**Option A — controllers return `ApiResponse<T>` directly.**

Rationale:
- Explicit > implicit. Reading the controller signature tells you the exact shape of the response.
- Auto-wrapping introduces edge cases for endpoints that should return non-wrapped responses (e.g. health checks, future file downloads, future SSE streams). Slice 1 doesn't need this, but adding `ResponseBodyAdvice` later if a need arises is easy; removing it after team relies on it is painful.
- DELETE returning 204 with no body is trivial: the controller method returns `void` (or `ResponseEntity.noContent().build()` — see below) and never touches the envelope.
- Testing is simpler: `@WebMvcTest` assertions read the literal return value without un-wrapping concerns.

**`ApiResponse<T>` design (record, immutable)**

```java
public record ApiResponse<T>(
    boolean success,
    T data,
    String message,
    Map<String, Object> meta
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, Map<String, Object> meta) {
        return new ApiResponse<>(true, data, null, meta);
    }

    public static <T> ApiResponse<T> error(String message, String code) {
        return new ApiResponse<>(false, null, message, Map.of("code", code));
    }

    public static ApiResponse<Void> validationError(Map<String, String> fieldErrors) {
        // meta is the flat field-error map per spec §5 + AC-X2
        return new ApiResponse<>(false, null, "Validation failed",
            new LinkedHashMap<>(fieldErrors));   // preserve insertion order
    }

    public static <T> ApiResponse<List<T>> paged(List<T> items, Long nextCursor, int limit) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("nextCursor", nextCursor);   // explicit null allowed
        meta.put("limit", limit);
        return new ApiResponse<>(true, items, null, meta);
    }
}
```

**Configure Jackson** to **not** drop nulls in `data` (so `data: null` appears explicitly in error responses), but **do** drop nulls everywhere else if desired. In practice the simplest config is `spring.jackson.default-property-inclusion: always` — the spec explicitly shows `null` values in error bodies, so always emitting null is correct.

**HTTP status codes**

Controllers use `@ResponseStatus` for fixed statuses (201 on POST register, 204 on DELETE). For 200 on the success path, no annotation needed (Spring default). Dynamic statuses (none in slice 1) would use `ResponseEntity<ApiResponse<T>>`.

**DELETE 204**: the controller method has signature `void deleteTransaction(...)` annotated `@DeleteMapping` + `@ResponseStatus(NO_CONTENT)`. No envelope. This is one of two endpoints (the other being `/actuator/health`) that don't use `ApiResponse`. Documented as a deliberate exception per HTTP semantics — 204 means "no content", and wrapping `null` in an envelope contradicts the standard.

**`paged()` placement**

Used by `GET /transactions`. The controller calls `service.findPage(...)` which returns a small `PageResult(List<TransactionResponse> items, Long nextCursor, int limit)` record, then `ApiResponse.paged(result.items(), result.nextCursor(), result.limit())`.

### Consequences

**Positive**
- One look at any controller signature reveals exactly what gets serialized.
- `@WebMvcTest` assertions are direct: `jsonPath("$.success").value(true); jsonPath("$.data.id").value(42)`.
- No magic in `ResponseBodyAdvice` to debug.
- DELETE 204 cleanly handled by HTTP semantics, not envelope-bending.

**Negative**
- Controller methods are slightly more verbose (`return ApiResponse.ok(...)` vs `return ...`). Trivial.

**Neutral**
- `LinkedHashMap` preserves field-error order (helps debugging and gives stable test assertions).

### Alternatives Considered

| Option | Pros | Cons |
|--------|------|------|
| B — `ResponseBodyAdvice` auto-wrap | Less typing in controllers | Hidden serialization step; edge cases (404 from filter, redirects, file downloads) require carve-outs; harder to test |
| Returning `Mono<ApiResponse<T>>` (WebFlux) | Reactive readiness | Slice 1 is sync MVC; not justified |
| **A — direct return of `ApiResponse<T>` (chosen)** | Explicit, testable, predictable | Slightly more typing |

---

## ADR-009: Keyset Pagination Implementation

### Context

Spec 6.6: `WHERE user_id = ? AND deleted_at IS NULL [AND transaction_date BETWEEN from AND to] AND (cursor is null OR id < cursor) ORDER BY transaction_date DESC, id DESC LIMIT :limit`.

The composite ORDER BY (`transaction_date DESC, id DESC`) is critical. The cursor must be interpreted relative to the **last item of the previous page**, which means the predicate is **not** simply `id < cursor` but the proper composite keyset condition:

```
(transaction_date < cursorDate) OR (transaction_date = cursorDate AND id < cursorId)
```

> ✅ **Spec amendment 1 (2026-05-14) — RESOLVED.** Option (a) accepted by the spec owner.
> Cursor is an **opaque base64 token** encoding `"<transactionDate>_<id>"`.
> Keyset predicate is composite: `(transaction_date < :cursorDate) OR (transaction_date = :cursorDate AND id < :cursorId)`.
> AC-T11 and AC-T12 reworded in the spec accordingly.

### Decision

The cursor is a **base64-encoded `"<transactionDate>_<id>"` string** passed as a query parameter. The controller decodes it into a `(LocalDate cursorDate, Long cursorId)` pair. If the decoding fails, throw `InvalidQueryException("INVALID_QUERY")` (covers AC-T spec line "cursor not parseable").

**Query implementation: explicit `@Query` JPQL on the repository**, not Spring Data derived queries (too verbose for this pattern) and not Criteria/Specification API (over-engineered for one query).

**The JPQL**:

```java
@Query("""
    SELECT t FROM TransactionEntity t
    WHERE t.userId = :userId
      AND t.deletedAt IS NULL
      AND (:from IS NULL OR t.transactionDate >= :from)
      AND (:to   IS NULL OR t.transactionDate <= :to)
      AND (
            :cursorDate IS NULL
         OR  t.transactionDate <  :cursorDate
         OR (t.transactionDate = :cursorDate AND t.id < :cursorId)
      )
    ORDER BY t.transactionDate DESC, t.id DESC
    """)
List<TransactionEntity> findPage(
    @Param("userId")     Long userId,
    @Param("from")       LocalDate from,
    @Param("to")         LocalDate to,
    @Param("cursorDate") LocalDate cursorDate,
    @Param("cursorId")   Long cursorId,
    Pageable pageable                              // size = limit; no sort (already in JPQL)
);
```

**Why JPQL, not Specification**

- The cursor predicate is a fixed pattern, not a runtime-composed AND-tree.
- JPQL is readable; Specification builders for this query would be ~40 lines and harder to reason about.
- `Pageable` provides the LIMIT; `OFFSET` is not used (keyset, per CLAUDE.md performance rules).

**Cursor encoding**

```
cursor = base64url("2026-05-10_124")
```

Decoder validates: contains exactly one `_`, left side is `LocalDate.parse(...)`, right side is `Long.parseLong(...)`. Any failure → `InvalidQueryException`.

**Index usage**

The partial index `idx_transactions_user_active(user_id, transaction_date DESC, id DESC) WHERE deleted_at IS NULL` is used by Postgres for this exact predicate ordering. Verifiable with `EXPLAIN ANALYZE` in an integration test (optional; not required for slice 1).

### Consequences

**Positive**
- Correct under all data shapes, including same-date rows.
- Single round-trip; no N+1.
- Index-aligned: query planner picks the partial index in one scan.
- JPQL is self-documenting.

**Negative**
- Slightly more verbose than `id < cursor`.
- Requires base64 encode/decode on cursor — small but non-zero complexity.

**Neutral**
- Composite cursor confirmed by spec amendment 1 (2026-05-14); no conditional path remains.

### Alternatives Considered

| Option | Pros | Cons |
|--------|------|------|
| Spring Data derived: `findByUserIdAndDeletedAtIsNullAndTransactionDateBetweenAndIdLessThanOrderBy...` | No JPQL needed | Method name 200 chars; can't express composite cursor predicate at all |
| Criteria/Specification API | Composable | Overkill for one query; harder to read |
| **Explicit @Query JPQL (chosen)** | Readable, correct, performant | Manual but small |
| Pure id-DESC sort with `id < cursor` | Simplest | Doesn't match spec sort; loses date ordering correctness |

---

## ADR-010: Test Architecture

### Context

AC-X5 requires ≥80% JaCoCo line coverage on service and controller packages. AC-X6 mandates Testcontainers Postgres 16 with the same Flyway migrations as local. Spec implies three test tiers without naming them.

### Decision

**Three-tier strategy**:

**Tier 1 — Unit tests** (`@Tag("unit")`)
- Pure JUnit 5 + Mockito + AssertJ. No Spring context.
- Target: services, JWT logic, mappers, validators, exception handler logic.
- Fast (< 100ms per test).
- Naming: `<ClassName>Test`.

**Tier 2 — Slice tests** (`@Tag("slice")` — or `unit`, depending on team preference; treated as unit-class for speed)
- `@WebMvcTest(TransactionController.class)` — boots only the web layer; `@MockBean` for `TransactionService` and `JwtService`. Tests request parsing, validation, response shaping, status codes.
- `@DataJpaTest` — boots only JPA; tests repository JPQL against an **in-memory H2 isn't acceptable** (Postgres-specific behavior: `NUMERIC`, partial indexes, `TIMESTAMPTZ`). Use `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` pointing at a Testcontainers Postgres instance — see Tier 3 infrastructure.
- Spring Security tests use `@WithMockUser` or a custom annotation (below).

**Tier 3 — Integration tests** (`@Tag("integration")`)
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `MockMvc` or `TestRestTemplate`.
- Full stack: real Postgres via Testcontainers, real Flyway, real Spring Security, real JWT issuance.
- Target: end-to-end ACs — register → login → POST transaction → list → delete → 404 on second delete, etc.
- Slow (a few seconds per class); few of these, but they prove the contract.

**Abstract test parents**

```java
// src/test/java/com/julio/lifeorganizer/AbstractIntegrationTest.java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("lifeorganizer_test")
        .withReuse(true);                  // speeds up local runs

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("app.jwt.secret", () -> "test-secret-must-be-at-least-32-chars-long!");
    }
}
```

A second abstract `AbstractJpaTest` for `@DataJpaTest` mirrors the same Testcontainers wiring.

**Authentication in integration tests**

Two complementary options, both implemented:

1. **`AuthTestHelper`** — a small helper bean used by tests that need end-to-end realism (register a user, log them in, return the access token). Used in flow tests like "register → create transaction".

2. **`@WithMockJwt(userId = 42, email = "...", role = "ROLE_USER")`** — a custom test annotation backed by a `SecurityContext` factory that places a pre-built `AuthenticatedUser` principal in `SecurityContextHolder`. Used in slice tests (`@WebMvcTest`) where the JWT filter is **disabled** and only the controller logic matters. Implemented via `WithSecurityContextFactory<WithMockJwt>`.

**Coverage**

- JaCoCo plugin in `pom.xml`, attached to `verify` goal.
- Threshold: 80% line coverage on `com.julio.lifeorganizer.auth.service.*`, `auth.web.*`, `transactions.service.*`, `transactions.web.*`.
- Exclusions: `LifeOrganizerApplication`, `*Config`, `*Properties`, generated Lombok code (if any — slice 1 likely uses none beyond records), DTO records (auto-generated equals/hashCode noise).
- CI command: `mvn verify` runs unit + slice + integration tests + coverage gate.

**Filtering**

```
mvn test -Dgroups=unit           # fast feedback loop
mvn test -Dgroups=integration    # before push
mvn verify                       # everything + coverage gate
```

### Consequences

**Positive**
- Tight feedback loop on unit tests (< 5s for the unit tier).
- Real Postgres in integration → catches `NUMERIC`/`TIMESTAMPTZ`/partial-index issues that H2 hides.
- Coverage gate fails the build, not the PR review.
- `@WithMockJwt` makes controller tests concise.

**Negative**
- Testcontainers requires Docker on developer machines (already required for `docker compose`).
- First integration test run is slow (image pull); `.withReuse(true)` mitigates subsequent runs.

**Neutral**
- ~5–10 integration tests for slice 1 is sufficient — the rest is unit and slice.

### Alternatives Considered

| Option | Pros | Cons |
|--------|------|------|
| H2 in-memory for `@DataJpaTest` | Faster | Lies about `NUMERIC`, partial indexes, `TIMESTAMPTZ`; risk of false-green tests |
| Single tier — all `@SpringBootTest` | Simple | 10–100x slower; bad feedback loop |
| **Three-tier with Testcontainers (chosen)** | Right test for the right concern; matches CLAUDE.md `java/testing.md` | Slight setup cost (one-time) |

---

## ADR-011: Configuration Boundaries

### Context

CLAUDE.md: "No hardcoded secrets, ever. Configuration values come from env vars or config files." Slice 1 needs DB credentials, JWT secret, JWT TTLs, pagination defaults/limits.

### Decision

**Three-tier configuration**:

1. **`application.yml`** — defaults, structural config, references to env vars. **Committed to git.**
2. **`application-local.yml`** + **`application-test.yml`** — profile overrides for human-readable logging, SQL trace, test-specific tweaks. **Committed.**
3. **`.env`** (loaded by `docker-compose` and exported to the JVM by IDE run config) — secrets only. **Gitignored.** Template at `.env.example` **committed.**

**`application.yml` skeleton**

```yaml
spring:
  application:
    name: life-organizer
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
  datasource:
    url:      ${DB_URL:jdbc:postgresql://localhost:5432/lifeorganizer}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  jpa:
    open-in-view: false                # avoid hidden lazy loading in web layer
    hibernate:
      ddl-auto: validate               # Flyway owns the schema; validate ensures parity
    properties:
      hibernate:
        jdbc.time_zone: UTC
        format_sql: false
  flyway:
    enabled: true
    baseline-on-migrate: false
    locations: classpath:db/migration
  jackson:
    default-property-inclusion: always
    serialization:
      write-dates-as-timestamps: false
    deserialization:
      fail-on-unknown-properties: true

server:
  port: 8080
  error:
    include-stacktrace: never          # never leak stack to client
    include-message: never

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
      probes:
        enabled: true

app:
  jwt:
    secret: ${JWT_SECRET}              # required; fail-fast on missing
    access-ttl: PT15M                  # ISO-8601 duration
    refresh-ttl: P7D
    issuer: life-organizer
    clock-skew: PT30S
  pagination:
    default-limit: 20
    max-limit: 100

logging:
  level:
    root: INFO
    com.julio.lifeorganizer: INFO
```

**`application-local.yml`**

```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.orm.jdbc.bind: TRACE          # parameter values
    com.julio.lifeorganizer: DEBUG
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level [reqId=%X{requestId},user=%X{userId}] %logger{36} - %msg%n"
```

**`application-test.yml`**

```yaml
spring:
  jpa:
    properties:
      hibernate:
        format_sql: false
logging:
  level:
    org.hibernate.SQL: WARN          # too noisy for tests
    com.julio.lifeorganizer: INFO
```

**`@ConfigurationProperties` records**

```java
@ConfigurationProperties(prefix = "app.jwt")
@Validated
public record JwtProperties(
    @NotBlank String secret,
    @NotNull Duration accessTtl,
    @NotNull Duration refreshTtl,
    @NotBlank String issuer,
    @NotNull Duration clockSkew
) {
    // optional: secret length check could go in a @PostConstruct on a validator bean
}

@ConfigurationProperties(prefix = "app.pagination")
@Validated
public record PaginationProperties(
    @Min(1) @Max(1000) int defaultLimit,
    @Min(1) @Max(1000) int maxLimit
) {}
```

Activated in `@SpringBootApplication` via `@ConfigurationPropertiesScan`. Fail-fast: missing `JWT_SECRET` → `BindException` at boot (AC-A15).

**Why `open-in-view: false`**

Default `true` keeps a Hibernate session open through view rendering, hiding lazy-loading bugs and holding DB connections longer than needed. Explicit DTO mapping in the service layer means views never touch entities; `false` is correct.

**Why `fail-on-unknown-properties: true`**

If a client sends `userId` in a `POST /transactions` body (AC-T2 says it must be ignored), this setting throws `HttpMessageNotReadableException`. Stricter than the spec demands. Two alternative readings of AC-T2:
- Strict: reject the body. Returns 400 `MALFORMED_REQUEST`.
- Lenient: silently ignore the field. Returns 201 with the JWT subject as owner.

The spec says "any userId in body is ignored" — leaning lenient. To honor this, set `fail-on-unknown-properties: false`. Final decision: **lenient = `false`**, matches spec wording literally.

> Correction to the skeleton above: change `fail-on-unknown-properties: true` to `false`. Documented here for the implementation phase.

### Consequences

**Positive**
- Secrets never in git. `.env.example` documents what's needed.
- Profile-based overrides are explicit and discoverable.
- Boot-time validation catches missing config before first request.
- HikariCP tuned per CLAUDE.md guidance.

**Negative**
- Newcomers must remember to copy `.env.example` → `.env`. Mitigation: README.

**Neutral**
- `open-in-view: false` may surprise developers used to defaults; documented.

### Alternatives Considered

| Option | Pros | Cons |
|--------|------|------|
| All config in `application.yml` with literal values | Simplest | Secrets in git — non-starter |
| Spring Cloud Config | Centralized | Massive over-engineering for slice 1 |
| **Three-tier: yaml + profile yaml + .env (chosen)** | Clear separation; matches Spring Boot idioms | Slight setup |

---

## ADR-012: Docker Compose & Flyway

### Context

AC-X6: integration tests use Testcontainers with same Flyway as local. AC-X7: V1 + V2 migrations. AC-X8: `docker compose up` starts Postgres on `localhost:5432`, creds from `.env`.

### Decision

**`docker-compose.yml`** (project root):

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: life-organizer-postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER:     ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      POSTGRES_DB:       ${DB_NAME}
    volumes:
      - life_organizer_pgdata:/var/lib/postgresql/data
    healthcheck:
      test:     ["CMD-SHELL", "pg_isready -U ${DB_USER} -d ${DB_NAME}"]
      interval: 5s
      timeout:  5s
      retries:  5

volumes:
  life_organizer_pgdata:
```

**`.env.example`** (committed):

```
DB_USER=lifeorganizer
DB_PASSWORD=change_me_locally
DB_NAME=lifeorganizer
DB_URL=jdbc:postgresql://localhost:5432/lifeorganizer

JWT_SECRET=replace_this_with_a_strong_random_string_min_32_chars

SPRING_PROFILES_ACTIVE=local
```

**`.gitignore`** must include `.env` (and `.env.local`).

**Flyway**

- **Runs at Spring Boot startup**, not via `mvn flyway:migrate`. Confirmed approach because:
  - Spring Boot's `FlywayAutoConfiguration` reads the same `spring.datasource.*` properties as JPA — no duplicate configuration.
  - Testcontainers integration tests automatically run Flyway against the test container on context start (AC-X6).
  - `mvn flyway:migrate` requires a parallel `flyway-maven-plugin` config which would duplicate connection details. Skip it.
  - For schema-only operations outside the app (e.g. an undo migration), `flyway-maven-plugin` can be added later; not needed for slice 1.

- **Migration files** in `src/main/resources/db/migration/`:
  - `V1__create_users_table.sql`
  - `V2__create_transactions_table.sql`

- **Naming rules**:
  - `V<n>__<snake_case_description>.sql` for forward migrations.
  - Two underscores between version and description.
  - Never modify a migration once committed; create `V3__...` for changes.
  - Description in past tense or imperative ("create_users_table" — imperative).

- **Migration content** is dictated by the spec's §4 schema; both migrations include the partial index for transactions and the unique index for users.email. Use `CREATE INDEX` (not `CONCURRENTLY` in initial migrations — there's no existing traffic).

### Consequences

**Positive**
- One-command bootstrap: `cp .env.example .env && docker compose up -d && mvn spring-boot:run`.
- Migrations live with the code; same file applied to local Postgres, Testcontainers Postgres, and any future prod.
- Healthcheck ensures dependent services (none yet) wait for Postgres readiness.
- Named volume persists data across container restarts; trivially nuked via `docker compose down -v`.

**Negative**
- Developers must run `docker compose up -d` separately from `mvn spring-boot:run`. Acceptable; documenting in README.

**Neutral**
- Postgres 16-alpine is smaller and faster to pull than the full image; identical functionality for slice 1.

### Alternatives Considered

| Option | Pros | Cons |
|--------|------|------|
| Spring Boot Docker Compose support (`spring.docker.compose.enabled=true`) | Auto-starts container with `mvn spring-boot:run` | Magic; can surprise developers; not worth the convenience |
| Run Flyway via Maven plugin | Schema can be applied without booting app | Duplicates DB config; not needed for slice 1 |
| **Standalone compose + Flyway-on-boot (chosen)** | Explicit; testable; aligns with Testcontainers | One extra command (acceptable) |

---

## ADR-013: Logging & Observability

### Context

AC-X4: log unhandled exceptions at ERROR with URI and JWT subject. AC-X9: `/actuator/health` returns 200 with DB indicator. CLAUDE.md: human-readable in local, JSON in prod (slice 1 has no prod profile).

### Decision

**Logback configuration** (`src/main/resources/logback-spring.xml`):

```xml
<configuration>
    <springProfile name="local | default">
        <include resource="org/springframework/boot/logging/logback/base.xml"/>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

    <springProfile name="test">
        <include resource="org/springframework/boot/logging/logback/base.xml"/>
        <root level="WARN"/>
    </springProfile>

    <!-- Future prod profile would add logstash-logback-encoder for JSON -->
</configuration>
```

The console pattern (set in `application-local.yml` per ADR-011) includes MDC fields `requestId` and `userId`.

**MDC population**

- `RequestIdFilter` (`OncePerRequestFilter`, ordered before security filter chain):
  - On entry: `MDC.put("requestId", UUID.randomUUID().toString())`.
  - On exit: `MDC.clear()` (in `finally`).
- `JwtAuthenticationFilter` after successful auth:
  - `MDC.put("userId", authenticatedUser.id().toString())`.
  - Cleared by `RequestIdFilter` on exit (it owns the lifecycle).
- Unauthenticated requests: `userId` is absent from MDC; pattern shows `user=` (empty).

**SQL logging**

- `local` profile: `org.hibernate.SQL=DEBUG`, parameter binding at `TRACE`. Visible in console.
- `test` profile: `WARN` only (test output is noisy enough).
- Never enable parameter logging in any future prod profile (PII risk).

**Actuator**

Per AC-X9 and spec decision 30: expose **only** `health`. Disable `info`, `metrics`, etc. The `db` health indicator is auto-configured by Spring Boot when a `DataSource` bean is present — no explicit setup needed. `show-details: never` prevents leaking schema/version info.

**Prohibitions** (AC-X10)

- No `System.out.println` — caught by Java `style` settings / IDE rules.
- No `e.printStackTrace()` — use `log.error("...", ex)`.
- No hardcoded secrets — caught by code review + `git-secrets`-style hook.

### Consequences

**Positive**
- Every log line in a request carries enough correlation info to trace the failure to a user + request.
- SQL trace in local helps catch N+1 early; off in test keeps CI logs readable.
- `/actuator/health` is the only public surface — minimal attack surface.

**Negative**
- MDC values must be cleared meticulously; thread leak in async code (none in slice 1) could leak `userId` to unrelated requests. Acknowledged.

**Neutral**
- JSON logging deferred until a prod profile exists.

### Alternatives Considered

| Option | Pros | Cons |
|--------|------|------|
| Always-JSON logging | Production-grade from day one | Hostile in local dev; slice 1 has no prod consumer |
| No MDC, ad-hoc fields in log strings | Simpler | Inconsistent; hard to grep |
| **Human-readable local, JSON-deferred (chosen)** | Right tool per profile; matches CLAUDE.md | Two pattern definitions (acceptable) |

---

## ADR-014: Risk Register

### Context

Documenting risks at design time means the team accepts them consciously rather than discovering them in production.

### Risks

| # | Risk | Likelihood | Impact | Mitigation / Acceptance |
|---|---|---|---|---|
| R1 | **JWT secret leaks** (.env committed, log accidentally prints it, env var visible in `ps`) | Low | Critical (all tokens forgeable) | `.gitignore`-protect `.env`; never log `JwtProperties`; rotate immediately if leaked; for any future prod, move to a secret manager (Vault/AWS SM); document "treat the secret like a master key" in README. **Accepted** for personal slice 1. |
| R2 | **No refresh-token revocation** — stolen refresh token usable for 7 days | Medium | High | Accepted per spec decision 7e. **Spec amendment recommended** for any future prod slice: add a `refresh_tokens` table or a "minimum issued at" per user. Out of scope for slice 1. |
| R3 | **Soft-delete + unique constraints** — if a future migration adds a unique constraint on a transactions column, soft-deleted rows still hold the slot and block re-insertion | Low (no uniques on transactions in slice 1) | Medium | Document the rule: any future unique index on a soft-delete table must be a **partial unique index** `WHERE deleted_at IS NULL`. Add to `CLAUDE.md` (`database/patterns.md`) follow-up. |
| R4 | **Partial index dependency** — if a developer writes a future repository query that doesn't include `deleted_at IS NULL`, the partial index is bypassed and the query may seq-scan | Medium | Low (correctness preserved by explicit predicate; only performance hit) | Code review; an integration test that asserts the index is used (`EXPLAIN ANALYZE` assertion) — nice-to-have, not slice 1. |
| R5 | **Clock skew between client and server** — token rejection at the 15-min boundary | Medium | Low | 30s skew tolerance (ADR-004); document expected client behavior (refresh ~60s before expiry). |
| R6 | **HS256 secret rotation requires invalidating all live tokens** | Low (never rotated in slice 1) | Medium when needed | Future plan: implement key-id ("kid") in JWT header to allow dual-key validation during rotation. Out of scope for slice 1; **accepted**. |
| R7 | **`open-in-view: false` exposes lazy-loading bugs** that worked accidentally | Medium during development | Low (caught by tests, not in prod) | Slice 1 has no lazy associations (Transaction has no relationship to User in JPA — just a `userId` column). Risk is near-zero for slice 1. Documented for future slices. |
| R8 | **Identical 404 body for missing/not-owner/soft-deleted** prevents debugging of legitimate "where did my row go?" | Low | Low | Server-side logs distinguish all three cases (different log statements in the service); operations team can grep by `userId` + `requestId`. **Accepted**. |
| R9 | **Composite cursor encoding mismatch** if pagination spec lands on `id < cursor` only (see ADR-009 amendment recommendation) | RESOLVED 2026-05-14 | Medium (correctness) | **RESOLVED via spec amendment 1 (2026-05-14)**: cursor is opaque composite base64 `<date>_<id>` token; composite keyset predicate. No further action. |
| R10 | **`fail-on-unknown-properties: false` permits typos** in client payloads to silently succeed | Medium | Low | Accept per spec wording on AC-T2. Document in API docs (future). |
| R11 | **Stateless filter doesn't detect deleted users** for transaction endpoints — a deleted user's 15-min access token still works | Low | Medium | Spec only requires user-deleted detection on `/me` and `/refresh`. Other endpoints continue to honor the token until expiry. **Accepted**. |
| R12 | **No rate limiting on `/auth/register` or `/auth/login`** — vulnerable to credential stuffing and email enumeration via timing (despite identical-body protection per AC-A8) | Medium | Medium | Out of scope per spec decision 6 + 26. Add to slice 2 backlog. **Accepted**. |

### Spec amendments (status)

1. **ADR-009 / pagination cursor semantics** — **RESOLVED 2026-05-14** via spec amendment 1. Option (a) accepted: opaque base64 cursor `<date>_<id>`; composite keyset predicate. AC-T11 and AC-T12 reworded in the spec accordingly.

2. **No other amendments outstanding** — every other risk in the register is consciously accepted per the spec's deferral list.

---

## Concrete Artifacts

### A. Package tree (ASCII)

See ADR-001 for the full tree. Compact version:

```
com.julio.lifeorganizer
├── LifeOrganizerApplication
├── config/        (SecurityConfig, JwtProperties, PaginationProperties)
├── common/
│   ├── api/       (ApiResponse, PageMeta, FieldError)
│   ├── exception/ (DomainException + subtypes + GlobalExceptionHandler)
│   └── logging/   (RequestIdFilter)
├── auth/
│   ├── web/       (AuthController, MeController, dto/*)
│   ├── service/   (AuthService, UserService, JwtService)
│   ├── security/  (JwtAuthenticationFilter, EntryPoint, AccessDeniedHandler, AuthenticatedUser)
│   ├── domain/    (Role, TokenType)
│   └── persistence/ (UserEntity, UserRepository)
└── transactions/
    ├── web/       (TransactionController, dto/*)
    ├── service/   (TransactionService)
    ├── domain/    (TransactionType)
    └── persistence/ (TransactionEntity, TransactionRepository)
```

### B. Dependency direction diagram (ASCII)

```
                                        ┌─────────────┐
                                        │   config/   │
                                        └──────┬──────┘
                                               │ (bootstrap only)
            ┌──────────────────────────────────┴──────────────────────────────┐
            v                                                                 v
   ┌────────────────────┐                                          ┌──────────────────────┐
   │     auth/web       │                                          │  transactions/web    │
   └──────────┬─────────┘                                          └──────────┬───────────┘
              │  (calls only)                                                 │
              v                                                               v
   ┌────────────────────┐                                          ┌──────────────────────┐
   │   auth/service     │                                          │ transactions/service │
   └──────────┬─────────┘                                          └──────────┬───────────┘
              │                                                               │
              v                                                               v
   ┌────────────────────┐                                          ┌──────────────────────┐
   │  auth/persistence  │                                          │transactions/persistence│
   └──────────┬─────────┘                                          └──────────┬───────────┘
              │                                                               │
              v                                                               v
   ┌────────────────────┐                                          ┌──────────────────────┐
   │     auth/domain    │                                          │ transactions/domain  │
   └────────────────────┘                                          └──────────────────────┘

   transactions/* MUST NOT import auth/persistence or auth/service.
   transactions/web reads JWT subject via SecurityContext / @AuthenticationPrincipal.

   All packages may import from common/* (leaf).
   common/* MUST NOT import any feature package.
```

### C. Security filter chain order

1. `RequestIdFilter` (custom; MDC `requestId`)
2. `DisableEncodeUrlFilter` (Spring default)
3. `WebAsyncManagerIntegrationFilter`
4. `SecurityContextHolderFilter`
5. `HeaderWriterFilter`
6. `CsrfFilter` (disabled-mode; no-op)
7. `LogoutFilter` (no logout endpoint; no-op)
8. **`JwtAuthenticationFilter`** (custom; before `AuthorizationFilter`)
9. `RequestCacheAwareFilter`
10. `SecurityContextHolderAwareRequestFilter`
11. `AnonymousAuthenticationFilter`
12. `SessionManagementFilter` (STATELESS; no-op)
13. **`ExceptionTranslationFilter`** (translates `AuthenticationException` → `JwtAuthenticationEntryPoint`; `AccessDeniedException` → `JwtAccessDeniedHandler`)
14. **`AuthorizationFilter`** (evaluates `authorizeHttpRequests` rules)

### D. Exception → response mapping table

See ADR-007 for the full table — reproduced concisely:

| HTTP | Exception | meta.code | message | ACs |
|---|---|---|---|---|
| 400 | `MethodArgumentNotValidException` | — (meta = field map) | "Validation failed" | AC-A3/A4/A5, AC-T3..T8 |
| 400 | `ConstraintViolationException` | — (meta = field map) | "Validation failed" | AC-T13/T14 |
| 400 | `HttpMessageNotReadableException` | `MALFORMED_REQUEST` | "Malformed JSON request body" | 6.1 |
| 400 | `InvalidQueryException` (incl. `MethodArgumentTypeMismatchException`) | `INVALID_QUERY` | exception msg | AC-T13, T14, T16, 6.6 |
| 401 | `UnauthorizedException` | `UNAUTHORIZED` | "Authentication required" | AC-A13 |
| 401 | `InvalidCredentialsException` | `INVALID_CREDENTIALS` | "Invalid email or password" | AC-A7, A8 |
| 401 | `InvalidTokenException` | `INVALID_TOKEN` | "Invalid token" | AC-A10, 6.3, 6.4 |
| 401 | `TokenExpiredException` | `TOKEN_EXPIRED` | "Token expired" | AC-A11, AC-A14 |
| 401 | `UserNotFoundForTokenException` | `USER_NOT_FOUND` | "User not found" | 6.3, 6.4 |
| 404 | `NotFoundException` (transaction) | `TRANSACTION_NOT_FOUND` | "Transaction not found" | AC-T18, T20, T22 |
| 409 | `ConflictException` (email) | `USER_EMAIL_EXISTS` | "Email already registered" | AC-A2 |
| 500 | `Exception` (fallback) | `INTERNAL_ERROR` | "An unexpected error occurred" | AC-X3 |

### E. JPQL for `GET /transactions` (paginated)

```java
@Query("""
    SELECT t FROM TransactionEntity t
    WHERE t.userId = :userId
      AND t.deletedAt IS NULL
      AND (:from IS NULL OR t.transactionDate >= :from)
      AND (:to   IS NULL OR t.transactionDate <= :to)
      AND (
            :cursorDate IS NULL
         OR  t.transactionDate <  :cursorDate
         OR (t.transactionDate = :cursorDate AND t.id < :cursorId)
      )
    ORDER BY t.transactionDate DESC, t.id DESC
    """)
List<TransactionEntity> findPage(
    @Param("userId")     Long userId,
    @Param("from")       LocalDate from,
    @Param("to")         LocalDate to,
    @Param("cursorDate") LocalDate cursorDate,
    @Param("cursorId")   Long cursorId,
    Pageable pageable
);
```

(Cursor amendment confirmed 2026-05-14 — this JPQL is final for slice 1.)

### F. `application.yml` skeleton

See ADR-011 in full. The boundaries: defaults in `application.yml`, profile-specific tweaks in `application-{local,test}.yml`, secrets exclusively in `.env`.

### G. `docker-compose.yml`

See ADR-012 in full.

### H. Phased implementation order

Phases mapped to spec ACs. Each phase is a logical commit / PR.

| Phase | Deliverable | ACs covered |
|---|---|---|
| **0** | Project skeleton: `pom.xml` (Java 21, Spring Boot 3.x, deps), `LifeOrganizerApplication`, `docker-compose.yml`, `.env.example`, `application.yml`, `application-local.yml`, `application-test.yml`, README boot instructions, `.gitignore` | AC-X8 |
| **1** | Flyway migrations V1 + V2; `UserEntity`, `TransactionEntity`, repositories (no business methods yet); `AbstractIntegrationTest` + `AbstractJpaTest`; `@DataJpaTest` for repository CRUD and partial-index sanity check | AC-X6, AC-X7 |
| **2** | Common scaffolding: `ApiResponse<T>`, exception hierarchy, `GlobalExceptionHandler` (with placeholder for filter-thrown exceptions), `RequestIdFilter`. Unit tests for `ApiResponse` factories and exception mapping. | AC-X1, AC-X2, AC-X3, AC-X4 |
| **3** | Auth subsystem: `JwtService` + `JwtProperties`, `JwtAuthenticationFilter` (with `HandlerExceptionResolver` delegation), `JwtAuthenticationEntryPoint`, `JwtAccessDeniedHandler`, `SecurityConfig`, `AuthService` (register/login/refresh), `UserService` (load for `/me`), `AuthController`, `MeController`. Unit tests for each service; `@WebMvcTest` for controllers with `@WithMockJwt`; integration test for register → login → /me roundtrip. | AC-A1..A16, AC-A15 (fail-fast secret) |
| **4** | Transactions create/read/list: `TransactionEntity` validations, `TransactionService` (create, findById, findPage), `TransactionController` (POST, GET by id, GET list). Validation DTOs with Bean Validation. Unit + slice + integration tests including identical-404 assertion. | AC-T1..T18, AC-T23, AC-X1, AC-X2 |
| **5** | Transactions update + soft delete: `TransactionService.update`, `TransactionService.softDelete` (via `@Modifying`), PUT/DELETE endpoints. Tests for "second DELETE returns 404", PUT replaces all fields, PUT 404 on soft-deleted. | AC-T19..T22 |
| **6** | Cross-cutting verification: ArchUnit test, JaCoCo gate (`mvn verify`), `/actuator/health` integration test against DB-up and DB-down (latter via @Container restart), end-to-end "happy path" integration test. | AC-X5, AC-X9, AC-X10, AC-X11, AC-X12 |
| **7** | Documentation pass: README, `.env.example` finalization, ADR file finalized, conventional-commit-style git history check. | AC-X13 |

### I. Risks Summary

See ADR-014. Key items: R1 (secret leak), R2 (no revocation), R9 (cursor semantics — **needs spec confirmation**), R12 (no rate limiting).

---

**End of Architecture Document — Slice 1**
