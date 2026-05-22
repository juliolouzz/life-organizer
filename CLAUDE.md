# Life Organizer — Project Instructions

Personal learning project for `julio.cdl.vet@gmail.com`. This file is the canonical contract for anyone (human or AI) modifying this repository.

## Identity

- **Project name**: Life Organizer
- **Current slice**: Slice 1 — Users + JWT auth + Transactions CRUD
- **Stack**: Java 21 (source level) · Spring Boot 3.3.x · PostgreSQL 16 · Flyway · JPA/Hibernate · JUnit 5 · Mockito · Testcontainers · Maven · Docker Compose
- **Domain**: Personal finance vertical first (transactions). Future verticals (health, diary, dashboard) are deferred per spec §9.

## Source of Truth

The behavioral contract for Slice 1 lives in `docs/specs/`:

| File | Purpose |
|---|---|
| `slice-1-spec.txt` | Behavioral specification — decisions ledger, data model, endpoints, acceptance criteria. Amendments tracked at the bottom. |
| `slice-1-architecture.md` | 14 Architecture Decision Records (ADRs), package tree, dependency diagram, filter chain order, risk register. |
| `slice-1-plan.md` | 77-step TDD-sized implementation plan across 8 phases. Every Acceptance Criterion maps to at least one step. |

**Never write code that contradicts the spec. If the spec needs to change, amend it (with reason + date) before changing code.**

## Module Layout

Documentation per module lives in `docs/modules/`:

| Module | Path | Doc |
|---|---|---|
| `auth` | `src/main/java/com/julio/lifeorganizer/auth` | `docs/modules/auth.md` |
| `transactions` | `src/main/java/com/julio/lifeorganizer/transactions` | `docs/modules/transactions.md` |
| `common` (shared scaffolding) | `src/main/java/com/julio/lifeorganizer/common` | `docs/modules/common.md` |
| `config` | `src/main/java/com/julio/lifeorganizer/config` | `docs/modules/config.md` |

Each module is feature-oriented (controller + service + dto + persistence under one package), not layer-oriented.

## Mandatory Workflow

1. **Spec → architecture → plan → tests → code**. Never write code from a vague prompt.
2. **TDD discipline**: RED (write failing test) → GREEN (minimal implementation) → REFACTOR (clean up). Implementation only exists to satisfy a named test.
3. **Branch per phase / feature**: `feature/phase-N-<name>` off `main`, merged back when green.
4. **Conventional Commits**: `feat:`, `fix:`, `refactor:`, `test:`, `chore:`, `docs:`. Imperative mood. Subject ≤ 72 chars. No period. No emojis.
5. **Tests gate merges**: `mvn verify` must pass (unit + integration + JaCoCo gate + ArchUnit) before merging to `main`.

## Hard Rules

| # | Rule | Enforced by |
|---|---|---|
| 1 | No emojis anywhere — code, comments, commits, docs | Code review |
| 2 | No hardcoded secrets — `JWT_SECRET` and DB credentials via env vars | `.gitignore` + `JwtProperties` boot-time validation |
| 3 | Constructor injection only — every dependency `final`, no `@Autowired` on fields | ArchUnit test in Phase 6 |
| 4 | All DTOs are Java 21 records | ArchUnit test in Phase 6 |
| 5 | No `System.out.println`, no `e.printStackTrace()`, no commented-out code | Phase 6 hygiene scan |
| 6 | Files ≤ 400 lines (hard max 800); split beyond | Code review |
| 7 | Test naming: `methodName_scenario_expectedBehavior()` | Code review |
| 8 | 80%+ JaCoCo line coverage on `service.*` and `web.*` packages | Maven build gate |
| 9 | Every response uses `ApiResponse<T>` envelope | Controller integration tests |
| 10 | JWT `sub` claim defines ownership — request bodies cannot set `userId` | `JacksonConfig` + Phase 4 tests |

## Layered Architecture (ADR-001 / ADR-002)

```
HTTP request
   |
   v
[ web ]            <-- @RestController, @WebMvcTest covers it
   |
   v
[ service ]        <-- business rules, @Transactional, throws domain exceptions
   |
   v
[ persistence ]    <-- JpaRepository, @DataJpaTest covers it
   |
   v
PostgreSQL (Flyway-managed schema)
```

- `web` may call `service`. `service` may call `persistence`. **No backward calls.**
- Domain exceptions thrown in `service` → caught by `GlobalExceptionHandler` → mapped to `ApiResponse.error(...)` JSON.

## Test Tiers (ADR-010)

| Tier | Annotation | Tag | What it tests | DB |
|---|---|---|---|---|
| Unit | none / `@ExtendWith(MockitoExtension.class)` | `@Tag("unit")` | Pure logic, no Spring context | In-memory mocks |
| JPA slice | `@DataJpaTest` | `@Tag("integration")` | Repositories + Flyway migrations | Testcontainers Postgres 16 |
| Web slice | `@WebMvcTest` | `@Tag("unit")` | Controllers with `@MockBean` of services | None |
| Full integration | `@SpringBootTest` + `AbstractIntegrationTest` | `@Tag("integration")` | End-to-end HTTP via `TestRestTemplate` | Testcontainers Postgres 16 |

Run:
- `mvn test` → unit tier only
- `mvn verify` → unit + integration + JaCoCo + ArchUnit

## Quick Start

```bash
cp .env.example .env             # then fill JWT_SECRET with >=32 chars
docker compose up -d postgres    # boots Postgres 16 with healthcheck
mvn spring-boot:run              # boots the API on :8080
curl http://localhost:8080/actuator/health
```

## When You Hit a Blocker

1. Re-read the spec — most ambiguity is resolved there.
2. Check the relevant ADR in `docs/specs/slice-1-architecture.md`.
3. If still unclear, **stop and ask** — do not guess.
4. If the spec or ADR is wrong, amend it (with date + reason) before touching code.

## Out of Scope for Slice 1

Per spec §9 — do **not** implement these in Slice 1, even if asked:

- Email verification, password reset, account deletion
- Lockout / rate limiting / captcha
- Refresh-token rotation or server-side revocation
- Multi-currency, FX, account/wallet entities
- List filters beyond `from`/`to`
- PATCH endpoints (PUT only, full replace)
- Hard delete or undelete
- Idempotency keys, optimistic locking, audit columns
- Reports, dashboards, recurring transactions, budgets, alerts
- File uploads, bulk import, webhooks
- Prod profile, CI/CD pipeline, K8s manifests
- Frontend (UI is a future slice — current slice is API-only)
- Health, diary, reminders verticals — future slices

## Personal Preferences

These reflect the project owner's hard preferences and override conflicting defaults:

- Prefer **records** over Lombok `@Data` for DTOs (records are immutable by language).
- Prefer **JPQL `@Query`** over Specifications for non-trivial repository queries.
- Prefer **explicit `deleted_at IS NULL`** predicates over Hibernate `@SQLRestriction` (visible in code, debuggable).
- Prefer **constructor-bound `@ConfigurationProperties` records** over field-bound `@Value`.
- Prefer **`Instant` for timestamps**, **`LocalDate` for calendar dates**, all UTC.
