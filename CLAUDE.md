# Life Organizer — Project Instructions

Personal learning project for `julio.cdl.vet@gmail.com`. This file is the canonical contract for anyone (human or AI) modifying this repository.

## Identity

- **Project name**: Life Organizer
- **Status**: Slices 1-14 shipped (REST API, Angular UI, dashboard, categories / budgets / recurring, CSV import, password reset / email verification / rate limit, account management, reports, ops hardening, refresh-token revocation, per-user currency, custom month boundary day). Most recently: a multi-round QA pass closed out 9 live-verified bugs (PRs 33-37).
- **Current focus**: post-Slice-14 maintenance — fix bugs surfaced by QA, keep the docs and CHANGELOG in step with `main`, deepen test coverage where regressions land.
- **Stack**: Java 21 (source level) · Spring Boot 3.3.x · PostgreSQL 16 · Flyway · JPA/Hibernate · JUnit 5 · Mockito · Testcontainers · Maven · Docker Compose · Angular 17 · TypeScript strict · Material 17 (M3) · Jest · Playwright
- **Domain**: Personal finance vertical first (transactions). Other verticals (health, diary, reminders, goals) are still future work — see the "What's next" section at the bottom of [`README.md`](README.md).

## Source of Truth

Per-slice behavioural contracts live in [`docs/specs/`](docs/specs/). Each slice ships its own `slice-N-spec.txt` (decisions ledger, data model, endpoints, acceptance criteria) and most have `slice-N-architecture.md` (ADRs, package tree, dependency diagram, risk register) + `slice-N-plan.md` (TDD-sized implementation steps). For the latest slice listing and tags see [`CHANGELOG.md`](CHANGELOG.md) and [`README.md`](README.md).

**Never write code that contradicts a spec. If the spec needs to change, amend it (with reason + date) before changing code.** When fixing a QA-found bug, write a failing test first that pins the corrected contract.

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
cp .env.docker.example .env      # fill JWT_SECRET with >=32 chars (openssl rand -base64 48)
docker compose -f docker-compose.full.yml up --build   # postgres + backend + nginx-frontend
open http://localhost:4200       # SPA; backend lives on the internal Docker network
```

For development mode (hot reload, native JDK + node) see the "Manual / development mode" section of [`README.md`](README.md).

## When You Hit a Blocker

1. Re-read the relevant `slice-N-spec.txt`.
2. Check the matching `slice-N-architecture.md` for ADRs and risk notes.
3. If still unclear, **stop and ask** — do not guess.
4. If the spec or ADR is wrong, amend it (with date + reason) before touching code.

## Still out of scope

Already shipped (do NOT treat these as "out of scope" anymore):

- Email verification, password reset, account deletion (Slices 8-9)
- In-memory sliding-window rate limit on public auth endpoints (Slice 8)
- Refresh-token server-side revocation via per-user epoch (Slice 12)
- Per-user currency preference (Slice 13)
- PATCH endpoint for /me (Slice 9)
- Soft delete with 30-day grace period for accounts (Slice 9)
- Reports, dashboard, recurring transactions, budgets (Slices 3, 6, 10)
- CSV bulk import — native AND bank-statement formats (Slice 7, post-14)
- Prod profile + CI/CD pipeline + non-root Docker stack (Slice 11)
- Angular 17 frontend (Slice 2+)
- Custom month boundary day (Slice 14)

Still genuinely future / not implemented:

- Account / wallet entities and FX conversion (currency is display-only)
- Optimistic locking / `@Version` (still last-write-wins on concurrent PUT)
- Refresh-token rotation per use, idempotency keys, audit columns
- Per-device session list with granular revocation
- Persistent outbox / SMTP retry-on-transient-failure
- Observability — Micrometer / Prometheus / OpenTelemetry
- Health, fitness, diary, reminders, goals verticals

## Personal Preferences

These reflect the project owner's hard preferences and override conflicting defaults:

- Prefer **records** over Lombok `@Data` for DTOs (records are immutable by language).
- Prefer **JPQL `@Query`** over Specifications for non-trivial repository queries.
- Prefer **explicit `deleted_at IS NULL`** predicates over Hibernate `@SQLRestriction` (visible in code, debuggable).
- Prefer **constructor-bound `@ConfigurationProperties` records** over field-bound `@Value`.
- Prefer **`Instant` for timestamps**, **`LocalDate` for calendar dates**, all UTC.
