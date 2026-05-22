# Life Organizer — Slice 1 Implementation Plan (TDD-Executable)

**Project root**: `/Users/juliolouzano/Software Development/life-organizer/`
**Spec**: `/Users/juliolouzano/Desktop/life-organizer-slice-1-spec.txt`
**Architecture**: `/Users/juliolouzano/Desktop/life-organizer-slice-1-architecture.md`
**Approved by**: julio.cdl.vet@gmail.com — 2026-05-14
**Status**: Ready to execute

---

## Context

Slice 1 delivers Users + JWT auth + Transactions CRUD as a Spring Boot 3 / Java 21 REST API backed by PostgreSQL 16. This plan decomposes the architecture's 8-phase outline (Section H) into **77 atomic TDD-sized steps**, each scoped to a single RED → GREEN → REFACTOR cycle of under 30 minutes. Every step begins with a failing test; production code is only written to make a test pass.

Every Acceptance Criterion in the spec (AC-A1..A16, AC-T1..T23, AC-X1..X13 — 52 ACs total) is mapped to at least one step. The plan enforces ADR-001 / ADR-002 layering via an ArchUnit test in Phase 6.

---

## Before You Start

### Local tooling (install once)
- **JDK 21 LTS** (Temurin/Adoptium recommended) — `java -version` reports 21.x
- **Maven 3.9+** (`mvn -v` ≥ 3.9.0; Spring Boot 3.x requires it)
- **Docker Desktop** (for `docker compose` and Testcontainers; AC-X6, AC-X8)
- **Git** (configured with user.name + user.email matching commit author)
- **`gh` CLI** (per `common/git.md`; replaces opening the browser for PRs)

### IntelliJ IDEA configuration (per `common/token-optimization.md` + personal preferences)
- Project SDK: Temurin 21
- Plugins enabled: **Lombok** (records-only project; keep for future), **Maven**, **Spring Boot**, **Database Tools and SQL**, **Docker**, **EnvFile** (auto-loads `.env` into run configs), **ArchUnit IntelliJ Support** (optional), **CheckStyle-IDEA**, **SonarLint**
- Code style: import google-java-format settings; "Format on save" ON
- Run config: enable **`-Dspring.profiles.active=local`** + EnvFile pointing at `./.env`

### Environment prerequisites (one-time)
1. `cp .env.example .env` and fill `JWT_SECRET` with ≥32-char random string
2. `docker compose up -d postgres` and verify `pg_isready` healthy
3. `mvn -version` and `java -version` both report what is expected

### Personal preferences enforced throughout (per CLAUDE.md)
- No emojis in code, comments, or commit messages
- Conventional Commits (`feat:`, `fix:`, `test:`, `refactor:`, `chore:`, `docs:`)
- Constructor injection only; all dependencies `final`
- DTOs are Java 21 records
- Immutable defaults: `final` fields, no setters except on JPA-mutable entity fields
- File size 200–400 lines (hard max 800)
- No `System.out.println`, no `e.printStackTrace()`, no hardcoded secrets
- Test naming pattern: `methodName_scenario_expectedBehavior()`
- 80%+ JaCoCo line coverage on `service.*` and `web.*` packages

---

## Phase Ordering Rationale

The 8 phases (0–7) are sequenced so that each unlocks the next without backtracking:

| Phase | Unlocks | Why this order |
|---|---|---|
| **0 — Bootstrap** | Compile, run, connect to Postgres | Without `pom.xml`, profiles, Compose, and the Spring Boot main class, nothing else compiles or boots. |
| **1 — Persistence Foundation** | Repositories and Flyway-managed schema | Establishes the DB contract before any service touches it. Validates schema with `@DataJpaTest` against Testcontainers. |
| **2 — Common Scaffolding** | `ApiResponse`, exceptions, `RequestIdFilter` | Every controller and service needs these. Building them once, with tests, prevents inconsistent error shapes in later phases. |
| **3 — Auth Subsystem** | Protected endpoints + JWT lifecycle | All other endpoints depend on `JwtAuthenticationFilter` and `SecurityConfig`. Auth ACs (A1..A16) close here. |
| **4 — Transactions Read Path** | POST/GET create/read/list | Read path is simpler than update/delete. Validates the JWT-scoped ownership pattern (ADR-005) end-to-end before mutating logic. |
| **5 — Transactions Mutation Path** | PUT + soft DELETE | Builds on Phase 4's ownership predicate; finishes T-series ACs. |
| **6 — Cross-cutting Verification** | ArchUnit, JaCoCo gate, `/actuator/health` integration | Validates non-functional spec requirements; closes X-series ACs. |
| **7 — Docs Polish** | README, ADR finalization, commit-history audit | Last because docs reference the now-stable code surface. |

**Cross-phase stop points**: at the end of every phase, the user reviews before proceeding to the next. Additional stop points are flagged inline below for risky steps.

---

## Total Step Count

**77 steps** across 8 phases:

| Phase | Step count | Step IDs |
|---|---|---|
| 0 — Bootstrap | 8 | 0.1 — 0.8 |
| 1 — Persistence Foundation | 9 | 1.1 — 1.9 |
| 2 — Common Scaffolding | 11 | 2.1 — 2.11 |
| 3 — Auth Subsystem | 18 | 3.1 — 3.18 |
| 4 — Transactions Read | 13 | 4.1 — 4.13 |
| 5 — Transactions Mutation | 8 | 5.1 — 5.8 |
| 6 — Cross-cutting Verification | 7 | 6.1 — 6.7 |
| 7 — Documentation | 3 | 7.1 — 7.3 |

---

## AC Coverage Checklist

Every AC from the spec maps to at least one step ID. **100% coverage achieved.**

### Auth & User ACs (A1–A16)
| AC | Step IDs |
|---|---|
| AC-A1 (register creates user, BCrypt, lowercased) | 3.7, 3.8, 3.9 |
| AC-A2 (duplicate email 409) | 3.10 |
| AC-A3 (invalid email 400) | 3.8 |
| AC-A4 (password policy 400) | 3.8 |
| AC-A5 (displayName 400) | 3.8 |
| AC-A6 (login returns access + refresh JWTs) | 3.11, 3.12 |
| AC-A7 (wrong password 401 INVALID_CREDENTIALS) | 3.11 |
| AC-A8 (unknown email identical 401 body) | 3.11 |
| AC-A9 (refresh returns new access) | 3.13 |
| AC-A10 (refresh typ mismatch 401 INVALID_TOKEN) | 3.13 |
| AC-A11 (expired refresh 401 TOKEN_EXPIRED) | 3.13 |
| AC-A12 (`/me` returns user) | 3.16 |
| AC-A13 (no Bearer 401 UNAUTHORIZED) | 3.15, 3.17 |
| AC-A14 (expired access 401 TOKEN_EXPIRED) | 3.15 |
| AC-A15 (missing JWT_SECRET fail-fast) | 3.1, 3.2 |
| AC-A16 (no password hash in body or log) | 3.18 |

### Transactions ACs (T1–T23)
| AC | Step IDs |
|---|---|
| AC-T1 (POST sets user_id from JWT) | 4.4, 4.5 |
| AC-T2 (server ignores body userId) | 4.6 |
| AC-T3 (amount <= 0 -> 400) | 4.3 |
| AC-T4 (amount > 2 decimals -> 400) | 4.3 |
| AC-T5 (invalid type -> 400) | 4.3 |
| AC-T6 (description blank or >255 -> 400) | 4.3 |
| AC-T7 (category blank or >50 -> 400) | 4.3 |
| AC-T8 (transactionDate malformed/missing -> 400) | 4.3 |
| AC-T9 (GET list scoped by user, active only) | 4.10 |
| AC-T10 (sort transaction_date DESC, id DESC) | 4.10 |
| AC-T11 (limit 20 default, opaque base64 nextCursor) | 4.8, 4.10 |
| AC-T12 (cursor composite predicate) | 4.8, 4.9, 4.10 |
| AC-T13 (limit=101 -> 400 INVALID_QUERY) | 4.11 |
| AC-T14 (limit=0 -> 400 INVALID_QUERY) | 4.11 |
| AC-T15 (from/to inclusive bounds) | 4.10 |
| AC-T16 (from > to -> 400 INVALID_QUERY) | 4.11 |
| AC-T17 (GET by id -> 200 owned active) | 4.12 |
| AC-T18 (GET by id 404 identical body, 3 cases) | 4.12, 4.13 |
| AC-T19 (PUT replaces all fields, bumps updatedAt) | 5.2, 5.3 |
| AC-T20 (PUT 404 under 3 cases) | 5.4 |
| AC-T21 (DELETE sets deleted_at, 204) | 5.5, 5.6 |
| AC-T22 (soft-deleted invisible + second DELETE 404) | 5.7 |
| AC-T23 (all transaction endpoints 401 without token) | 4.7, 5.8 |

### Cross-cutting ACs (X1–X13)
| AC | Step IDs |
|---|---|
| AC-X1 (every response uses ApiResponse envelope) | 2.1, 2.2, 4.5, 5.3 |
| AC-X2 (validation -> 400 with flat field-error map) | 2.5, 2.6, 3.8, 4.3 |
| AC-X3 (unhandled -> 500 no leak) | 2.7 |
| AC-X4 (log ERROR with URI + JWT subject) | 2.8, 2.9 |
| AC-X5 (>= 80% JaCoCo on service + controller) | 6.2 |
| AC-X6 (Testcontainers Postgres 16, same Flyway) | 1.2, 1.3 |
| AC-X7 (V1 + V2 migrations run clean) | 1.4, 1.5 |
| AC-X8 (`docker compose up` + .env template) | 0.4, 0.5 |
| AC-X9 (/actuator/health returns 200 UP) | 6.3, 6.4 |
| AC-X10 (no println / printStackTrace / hardcoded secrets) | 6.5 |
| AC-X11 (all deps injected via constructor, final fields) | 6.1 (ArchUnit) |
| AC-X12 (all DTOs are records) | 6.1 (ArchUnit) |
| AC-X13 (conventional commits used) | 7.3 |

---

## Risk Register Pointers

Each step that touches a risk from ADR-014 cites it explicitly:

| Risk | Touching Steps |
|---|---|
| R1 (JWT secret leak) | 0.4, 0.5, 3.1, 3.2 |
| R2 (no refresh-token revocation) | 3.13 (documented test; risk accepted) |
| R3 (soft-delete unique constraints) | 1.4, 1.5 (no uniques on transactions; documented) |
| R4 (partial index dependency) | 1.5, 4.10 (predicate explicit in JPQL) |
| R5 (clock skew) | 3.4 (30s tolerance test) |
| R6 (HS256 key rotation) | accepted, not coded |
| R7 (open-in-view false) | 0.6 |
| R8 (identical 404 body) | 4.12, 4.13 (assert identical bodies) |
| R9 (cursor composite) | RESOLVED via spec amendment 1; covered in 4.8, 4.9, 4.10 |
| R10 (fail-on-unknown-properties false) | 0.6, 4.6 |
| R11 (deleted user, valid token) | 3.14 (test for `/me` USER_NOT_FOUND only) |
| R12 (no rate limiting) | accepted, not coded |

---

## STOP Points

The user reviews at every phase boundary plus these inline risk points:

1. **End of Phase 0** — verify `mvn spring-boot:run` boots cleanly against Compose Postgres.
2. **End of Phase 1** — verify `mvn verify -Dgroups=integration` runs Flyway clean against Testcontainers.
3. **End of Phase 2** — verify hand-crafted exception throws produce correct JSON envelopes via a tiny ad-hoc test.
4. **End of Phase 3** — verify register → login → `/me` flow with `curl`.
5. **End of Phase 4** — verify GET /transactions with pagination + filters via `curl`.
6. **End of Phase 5** — verify DELETE then GET returns 404; verify identical 404 body across 3 cases.
7. **End of Phase 6** — verify `mvn verify` passes JaCoCo gate and ArchUnit; verify `/actuator/health`.
8. **End of Phase 7** — final review before merge to main.

---

# Implementation Steps

---

## Phase 0 — Project Bootstrap

**Goal**: a Spring Boot 3 / Java 21 project that boots, connects to a Compose-managed Postgres, and exposes `/actuator/health`.

### Step 0.1
- **Goal**: scaffold `pom.xml` with Java 21 + Spring Boot 3.x dependencies; verify build resolves.
- **RED test**: `com.julio.lifeorganizer.SmokeTest.contextLoads_whenAppBootsWithMinimalConfig_succeeds()` — asserts the Spring context loads without throwing (uses `@SpringBootTest` with a stub `JWT_SECRET` system property + H2 disabled, real Postgres deferred to next steps).
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/pom.xml` with `spring-boot-starter-parent` 3.3.x, properties `java.version=21`.
  - Dependencies: `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `postgresql`, `flyway-core`, `flyway-database-postgresql`, `io.jsonwebtoken:jjwt-api:0.12.6`, `jjwt-impl:0.12.6` (runtime), `jjwt-jackson:0.12.6` (runtime).
  - Test scope: `spring-boot-starter-test`, `spring-security-test`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`, `com.tngtech.archunit:archunit-junit5`.
  - Plugin: `jacoco-maven-plugin` (configured in Step 6.2), `maven-surefire-plugin` with `<groups>` support.
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/LifeOrganizerApplication.java` — minimal `@SpringBootApplication` class with `main(String[] args)` calling `SpringApplication.run`.
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/test/java/com/julio/lifeorganizer/SmokeTest.java`.
- **ACs covered**: precondition for AC-X8
- **Dependencies**: none
- **Parallelizable?**: no (foundational)
- **Commit**: `chore(bootstrap): scaffold maven pom with spring boot 3 and java 21`

### Step 0.2
- **Goal**: create `application.yml` defaults with property placeholders (no real secrets).
- **RED test**: `com.julio.lifeorganizer.config.ApplicationYamlTest.propertyResolution_whenJwtSecretEnvMissing_failsFast()` — boots `SpringApplication` programmatically without `JWT_SECRET` set; asserts startup fails with `BindException` (relates to AC-A15, deferred full implementation to Step 3.2).
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/resources/application.yml` per ADR-011 §F.
  - Set `fail-on-unknown-properties: false` (per ADR-011 correction note).
  - Add `app.jwt.*` and `app.pagination.*` stanzas referencing `${JWT_SECRET}`, etc.
- **ACs covered**: AC-A15 (partial), AC-X8 (partial)
- **Dependencies**: 0.1
- **Parallelizable?**: yes — with 0.3
- **Commit**: `chore(config): add application.yml with property placeholders for db and jwt`

### Step 0.3
- **Goal**: add `application-local.yml` and `application-test.yml` profiles.
- **RED test**: `com.julio.lifeorganizer.config.ProfileLoadingTest.activeProfile_whenTest_loadsTestYamlOverrides()` — uses `@SpringBootTest(properties = "spring.profiles.active=test")` and asserts a `test`-profile-only property (e.g. `logging.level.org.hibernate.SQL=WARN`) is bound.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/resources/application-local.yml` per ADR-011.
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/resources/application-test.yml` per ADR-011.
- **ACs covered**: AC-X8 (partial)
- **Dependencies**: 0.2
- **Parallelizable?**: yes — with 0.2
- **Commit**: `chore(config): add local and test profile overrides`

### Step 0.4
- **Goal**: create `.env.example` + `.gitignore` to keep secrets out of git (R1).
- **RED test**: `com.julio.lifeorganizer.GitignoreTest.gitignore_whenInspected_excludesDotEnv()` — reads `.gitignore` file and asserts `.env` is listed (file-content test, not a Spring test).
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/.env.example` per ADR-012.
  - Create `/Users/juliolouzano/Software Development/life-organizer/.gitignore` with `.env`, `.env.local`, `target/`, `.idea/`, `*.iml`.
- **ACs covered**: AC-X8 (partial); risks R1
- **Dependencies**: 0.1
- **Parallelizable?**: yes — with 0.2, 0.3, 0.5
- **Commit**: `chore(security): add env template and gitignore protecting secrets`

### Step 0.5
- **Goal**: create `docker-compose.yml` with Postgres 16-alpine and healthcheck.
- **RED test**: `com.julio.lifeorganizer.DockerComposeTest.compose_whenParsed_definesPostgresServiceWithHealthcheck()` — parses the YAML via SnakeYAML in a unit test; asserts `services.postgres.image == "postgres:16-alpine"` and `healthcheck` key present.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/docker-compose.yml` per ADR-012 §G.
- **ACs covered**: AC-X8
- **Dependencies**: 0.4
- **Parallelizable?**: yes — with 0.2, 0.3, 0.4
- **Commit**: `chore(infra): add docker compose for postgres 16 with healthcheck`

### Step 0.6
- **Goal**: harden Jackson + JPA defaults (open-in-view false, fail-on-unknown-properties false, jdbc UTC).
- **RED test**: `com.julio.lifeorganizer.config.JacksonAndJpaDefaultsTest.objectMapper_whenInjected_rejectsUnknownPropertiesPerSpec()` — actually the opposite per ADR-011 correction: asserts unknown fields are silently dropped (`acceptUnknownProperties == true` semantics) and dates serialize as ISO strings, not timestamps.
- **GREEN implementation**:
  - Adjust `application.yml` written in 0.2 to confirm `spring.jackson.deserialization.fail-on-unknown-properties=false`, `spring.jackson.serialization.write-dates-as-timestamps=false`, `spring.jpa.open-in-view=false`.
- **ACs covered**: AC-T2 (partial — body field ignored), R7, R10
- **Dependencies**: 0.2
- **Parallelizable?**: yes — with 0.7, 0.8
- **Commit**: `chore(config): harden jackson and jpa defaults for stateless rest`

### Step 0.7
- **Goal**: smoke-boot the application against running Postgres; verify `/actuator/health` returns 200 UP.
- **RED test**: `com.julio.lifeorganizer.HealthEndpointSmokeTest.health_whenAppBoots_returns200WithStatusUp()` — `@SpringBootTest(webEnvironment = RANDOM_PORT)`; uses Testcontainers Postgres (introduced fully in Step 1.2 but inline here for smoke). Asserts `GET /actuator/health` returns status 200 with JSON `{"status":"UP"}`.
- **GREEN implementation**:
  - Enable `management.endpoints.web.exposure.include=health` in `application.yml` (already done in 0.2).
  - Add minimal Testcontainers `@DynamicPropertySource` to the test class. Production code change: none.
- **ACs covered**: AC-X9 (partial; full at 6.3)
- **Dependencies**: 0.6
- **Parallelizable?**: no (smoke test gates phase exit)
- **Commit**: `test(actuator): smoke test for health endpoint against testcontainers`

### Step 0.8
- **Goal**: write README boot instructions; verify against fresh clone.
- **RED test**: `com.julio.lifeorganizer.ReadmeTest.readme_whenInspected_documentsBootSteps()` — file-content test asserting README contains the strings `"cp .env.example .env"` and `"docker compose up -d"` and `"mvn spring-boot:run"`.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/README.md` with quick-start, prerequisites, run/test commands.
- **ACs covered**: AC-X8 (final), AC-X13 (partial)
- **Dependencies**: 0.5, 0.7
- **Parallelizable?**: yes — with 0.6
- **Commit**: `docs(readme): add quick-start and boot instructions`

**STOP — Phase 0 review**: confirm `mvn clean verify -DskipTests` succeeds; `docker compose up -d` healthy; `mvn spring-boot:run` boots with `.env` loaded.

---

## Phase 1 — Persistence Foundation

**Goal**: Flyway migrations V1 + V2, entities, repositories (no business methods), Testcontainers-backed `@DataJpaTest`.

### Step 1.1
- **Goal**: create `AbstractIntegrationTest` parent with shared Testcontainers Postgres 16 + dynamic property source.
- **RED test**: `com.julio.lifeorganizer.AbstractIntegrationTestSelfTest.container_whenStarted_exposesJdbcUrlAndIsReachable()` — extends `AbstractIntegrationTest`; asserts `@Container POSTGRES.isRunning()` and `getJdbcUrl()` non-null.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/test/java/com/julio/lifeorganizer/AbstractIntegrationTest.java` per ADR-010.
  - Include `@Testcontainers`, static `PostgreSQLContainer<>` with `withReuse(true)`, `@DynamicPropertySource` wiring + `app.jwt.secret` test value.
- **ACs covered**: AC-X6 (partial)
- **Dependencies**: 0.7
- **Parallelizable?**: yes — with 1.2
- **Commit**: `test(infra): add abstract integration test with testcontainers postgres`

### Step 1.2
- **Goal**: create `AbstractJpaTest` parent for `@DataJpaTest` against the same Testcontainers Postgres.
- **RED test**: `com.julio.lifeorganizer.AbstractJpaTestSelfTest.dataSource_whenWired_pointsAtTestcontainer()` — extends `AbstractJpaTest`; injects `DataSource` and asserts its URL starts with `jdbc:postgresql://`.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/test/java/com/julio/lifeorganizer/AbstractJpaTest.java` with `@DataJpaTest`, `@AutoConfigureTestDatabase(replace = NONE)`, `@Testcontainers`, same container + dynamic props.
- **ACs covered**: AC-X6 (partial)
- **Dependencies**: 0.7
- **Parallelizable?**: yes — with 1.1
- **Commit**: `test(infra): add abstract jpa test for repository slice tests`

### Step 1.3
- **Goal**: write V1 migration creating `users` table per spec §4.1.
- **RED test**: `com.julio.lifeorganizer.persistence.UsersTableMigrationTest.usersTable_afterFlywayMigrate_hasExpectedColumnsAndConstraints()` — extends `AbstractJpaTest`; uses `JdbcTemplate` to query `information_schema.columns` and asserts: `id BIGINT NOT NULL PK`, `email VARCHAR(255) NOT NULL UNIQUE`, `password_hash VARCHAR(72) NOT NULL`, `display_name VARCHAR(100) NOT NULL`, `role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER'`, CHECK constraint on role.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/resources/db/migration/V1__create_users_table.sql` per spec §4.1.
- **ACs covered**: AC-X7 (partial)
- **Dependencies**: 1.2
- **Parallelizable?**: yes — with 1.4
- **Commit**: `feat(persistence): add V1 flyway migration creating users table`

### Step 1.4
- **Goal**: write V2 migration creating `transactions` table per spec §4.2 with partial index.
- **RED test**: `com.julio.lifeorganizer.persistence.TransactionsTableMigrationTest.transactionsTable_afterFlywayMigrate_hasPartialIndexOnActiveRows()` — extends `AbstractJpaTest`; queries `pg_indexes` and asserts the partial index `idx_transactions_user_active` exists with definition matching `WHERE deleted_at IS NULL` and `(user_id, transaction_date DESC, id DESC)`.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/resources/db/migration/V2__create_transactions_table.sql` per spec §4.2 (including partial index and CHECK constraints for `amount > 0` and `type IN (...)`).
- **ACs covered**: AC-X7, R4
- **Dependencies**: 1.3
- **Parallelizable?**: yes — with 1.3 (different file, different table)
- **Commit**: `feat(persistence): add V2 flyway migration creating transactions table`

### Step 1.5
- **Goal**: create `Role` enum + `UserEntity` JPA class.
- **RED test**: `com.julio.lifeorganizer.auth.persistence.UserEntityMappingTest.userEntity_whenPersisted_roundTripsAllFields()` — extends `AbstractJpaTest`; `TestEntityManager` persists a UserEntity, flushes, clears, reloads; asserts all fields equal.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/auth/domain/Role.java` — enum `ROLE_USER, ROLE_ADMIN`.
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/auth/persistence/UserEntity.java` — `@Entity @Table(name="users")`, `@Id @GeneratedValue(IDENTITY)`, fields `email`, `passwordHash`, `displayName`, `role` (enum STRING), `createdAt`, `updatedAt`; protected no-arg constructor; explicit constructor for creation.
- **ACs covered**: AC-X11, AC-X12 (precondition)
- **Dependencies**: 1.3
- **Parallelizable?**: yes — with 1.6
- **Commit**: `feat(auth): add UserEntity and Role enum`

### Step 1.6
- **Goal**: create `TransactionType` enum + `TransactionEntity` JPA class.
- **RED test**: `com.julio.lifeorganizer.transactions.persistence.TransactionEntityMappingTest.transactionEntity_whenPersistedAndReloaded_preservesPrecisionAndType()` — extends `AbstractJpaTest`; persists a transaction with `amount = new BigDecimal("1234.56")` and `type=EXPENSE`, reloads, asserts scale=2 and type equality.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/transactions/domain/TransactionType.java` — enum `INCOME, EXPENSE`.
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/transactions/persistence/TransactionEntity.java` — `@Entity @Table(name="transactions")`, fields per spec; `BigDecimal amount`, `LocalDate transactionDate`, `Instant createdAt/updatedAt`, `Instant deletedAt` (nullable); domain method `replaceWith(BigDecimal amount, TransactionType type, String category, String description, LocalDate date)` and `markDeleted(Instant now)`.
- **ACs covered**: AC-X11, AC-X12 (precondition)
- **Dependencies**: 1.4
- **Parallelizable?**: yes — with 1.5
- **Commit**: `feat(transactions): add TransactionEntity and TransactionType enum`

### Step 1.7
- **Goal**: create `UserRepository` (extends `JpaRepository<UserEntity, Long>`) with `findByEmail` and `existsByEmail`.
- **RED test**: `com.julio.lifeorganizer.auth.persistence.UserRepositoryTest.findByEmail_whenUserExists_returnsUser()` and `findByEmail_whenLookupIsLowercase_returnsUserStoredAsLowercase()` — extends `AbstractJpaTest`; saves UserEntity, asserts lookup works.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/auth/persistence/UserRepository.java` — interface with `Optional<UserEntity> findByEmail(String email)` and `boolean existsByEmail(String email)`.
- **ACs covered**: precondition for AC-A1, AC-A2
- **Dependencies**: 1.5
- **Parallelizable?**: yes — with 1.8
- **Commit**: `feat(auth): add UserRepository with email lookups`

### Step 1.8
- **Goal**: create `TransactionRepository` with basic find/save inherited; specific methods deferred.
- **RED test**: `com.julio.lifeorganizer.transactions.persistence.TransactionRepositoryTest.save_whenCalled_assignsIdAndTimestamps()` — extends `AbstractJpaTest`; saves a transaction without id, asserts id non-null and `createdAt`/`updatedAt` populated.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/transactions/persistence/TransactionRepository.java` — interface `extends JpaRepository<TransactionEntity, Long>`. No custom methods yet.
- **ACs covered**: precondition for AC-T1
- **Dependencies**: 1.6
- **Parallelizable?**: yes — with 1.7
- **Commit**: `feat(transactions): add TransactionRepository skeleton`

### Step 1.9
- **Goal**: end-to-end Flyway clean run from empty schema (regression guard).
- **RED test**: `com.julio.lifeorganizer.persistence.FlywayCleanMigrateTest.migrate_whenSchemaEmpty_appliesV1AndV2InOrder()` — extends `AbstractJpaTest` with a fresh container instance (`@DirtiesContext(BEFORE_CLASS)`); queries `flyway_schema_history` and asserts two rows with versions `1` and `2` both `success=true`.
- **GREEN implementation**: none if 1.3/1.4 are correct; this is a regression-only test.
- **ACs covered**: AC-X6, AC-X7
- **Dependencies**: 1.3, 1.4
- **Parallelizable?**: no (gates phase exit)
- **Commit**: `test(persistence): regression test for flyway clean migration`

**STOP — Phase 1 review**: `mvn test -Dgroups=integration` green; manually run `docker compose up` + `mvn spring-boot:run` and inspect `flyway_schema_history` via `psql`.

---

## Phase 2 — Common Scaffolding

**Goal**: `ApiResponse<T>`, exception hierarchy, `GlobalExceptionHandler`, `RequestIdFilter`.

### Step 2.1
- **Goal**: implement `ApiResponse<T>` record with `ok`, `ok(data, meta)`, `error(message, code)`, `validationError(...)`, `paged(...)` factories.
- **RED test**: `com.julio.lifeorganizer.common.api.ApiResponseTest.ok_whenDataProvided_returnsSuccessTrueAndNullMessage()` plus parameterized tests for each factory.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/common/api/ApiResponse.java` per ADR-008.
- **ACs covered**: AC-X1, AC-X12
- **Dependencies**: 0.7
- **Parallelizable?**: yes — with 2.2, 2.3
- **Commit**: `feat(common): add ApiResponse envelope record with factory methods`

### Step 2.2
- **Goal**: implement `PageMeta` record for paginated responses (used by `ApiResponse.paged`).
- **RED test**: `com.julio.lifeorganizer.common.api.PageMetaTest.of_whenNextCursorAndLimit_returnsImmutableRecord()`.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/common/api/PageMeta.java` — record `(String nextCursor, int limit)`.
- **ACs covered**: AC-X1
- **Dependencies**: 2.1
- **Parallelizable?**: yes — with 2.1, 2.3
- **Commit**: `feat(common): add PageMeta record`

### Step 2.3
- **Goal**: implement `DomainException` sealed base + `NotFoundException`, `ConflictException`, `ValidationException`, `InvalidQueryException`.
- **RED test**: `com.julio.lifeorganizer.common.exception.DomainExceptionHierarchyTest.notFoundException_whenConstructed_carriesErrorCodeAndMessage()`; verifies each subclass exposes `errorCode()` correctly.
- **GREEN implementation**:
  - Create files under `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/common/exception/`:
    - `DomainException.java` (abstract, RuntimeException, takes message + errorCode)
    - `NotFoundException.java`
    - `ConflictException.java`
    - `ValidationException.java`
    - `InvalidQueryException.java` (extends ValidationException)
- **ACs covered**: AC-X2, AC-X3 (precondition)
- **Dependencies**: 2.1
- **Parallelizable?**: yes — with 2.1, 2.2, 2.4
- **Commit**: `feat(common): add domain exception hierarchy`

### Step 2.4
- **Goal**: implement auth exception subclasses (`AuthException`, `InvalidCredentialsException`, `InvalidTokenException`, `TokenExpiredException`, `UnauthorizedException`, `UserNotFoundForTokenException`).
- **RED test**: `com.julio.lifeorganizer.common.exception.AuthExceptionTest.invalidTokenException_whenThrown_carriesCode_INVALID_TOKEN()`, parameterized per subtype.
- **GREEN implementation**:
  - Create the 6 classes under `common/exception/` per ADR-007.
- **ACs covered**: AC-A7, A8, A10, A11, A13, A14 (preconditions)
- **Dependencies**: 2.3
- **Parallelizable?**: yes — with 2.3 (different files, no overlap)
- **Commit**: `feat(common): add auth exception subclasses`

### Step 2.5
- **Goal**: `GlobalExceptionHandler` — handle `MethodArgumentNotValidException` returning 400 with field-error meta map.
- **RED test**: `com.julio.lifeorganizer.common.exception.GlobalExceptionHandlerValidationTest.handleValidation_whenBodyHasMultipleFieldErrors_returns400WithFlatMap()` — uses `MockMvc` with a stub `@RestController` whose request body has `@Valid`; sends invalid body; asserts JSON shape `{ success:false, data:null, message:"Validation failed", meta:{ field1:"msg", field2:"msg" } }`.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/common/exception/GlobalExceptionHandler.java` with `@RestControllerAdvice` and `@ExceptionHandler(MethodArgumentNotValidException.class)`.
- **ACs covered**: AC-X2
- **Dependencies**: 2.1, 2.3
- **Parallelizable?**: no
- **Commit**: `feat(common): handle validation errors with flat field map in advice`

### Step 2.6
- **Goal**: extend `GlobalExceptionHandler` for `HttpMessageNotReadableException` (400 MALFORMED_REQUEST), `MethodArgumentTypeMismatchException` (400 INVALID_QUERY), `ConstraintViolationException` (400, field map).
- **RED test**: three test methods — `handleMalformedJson_whenBodyBroken_returns400_MALFORMED_REQUEST`, `handleTypeMismatch_whenQueryParamWrongType_returns400_INVALID_QUERY`, `handleConstraintViolation_whenQueryParamFailsValidation_returns400WithFieldMap`.
- **GREEN implementation**: add three more `@ExceptionHandler` methods to `GlobalExceptionHandler`.
- **ACs covered**: AC-X2, AC-T13, AC-T14, AC-T16 (precondition for query handling)
- **Dependencies**: 2.5
- **Parallelizable?**: no
- **Commit**: `feat(common): handle malformed json and query param errors`

### Step 2.7
- **Goal**: extend `GlobalExceptionHandler` for domain exceptions (`NotFoundException`, `ConflictException`, `InvalidQueryException`, `ValidationException`) and the fallback `Exception` -> 500.
- **RED test**: `handleNotFound_returns404_WithCode`, `handleConflict_returns409_WithCode`, `handleInvalidQuery_returns400_INVALID_QUERY`, `handleFallback_whenRuntimeException_returns500_INTERNAL_ERROR_andNoStackInBody`.
- **GREEN implementation**: add `@ExceptionHandler` for each + `@ExceptionHandler(Exception.class)` fallback. Asserts no `trace` field in response.
- **ACs covered**: AC-X3, AC-A2 (precondition), AC-T18 (precondition)
- **Dependencies**: 2.6
- **Parallelizable?**: no
- **Commit**: `feat(common): handle domain exceptions and 500 fallback in advice`

### Step 2.8
- **Goal**: extend `GlobalExceptionHandler` for auth exceptions (401 family).
- **RED test**: parameterized over the 5 auth exceptions; each asserts `401` + correct `meta.code`.
- **GREEN implementation**: add `@ExceptionHandler` methods.
- **ACs covered**: AC-A7, A8, A10, A11, A13, A14 (handler portion)
- **Dependencies**: 2.7
- **Parallelizable?**: no
- **Commit**: `feat(common): handle auth exceptions returning 401 envelope`

### Step 2.9
- **Goal**: `RequestIdFilter` — generates `MDC.requestId` UUID per request; clears in finally.
- **RED test**: `com.julio.lifeorganizer.common.logging.RequestIdFilterTest.doFilter_whenRequestEnters_putsRequestIdInMdcAndClearsAfter()` — uses `MockFilterChain`, asserts `MDC.get("requestId")` non-null inside chain, null after.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/common/logging/RequestIdFilter.java` extending `OncePerRequestFilter`.
- **ACs covered**: AC-X4 (precondition)
- **Dependencies**: 2.1
- **Parallelizable?**: yes — with 2.5–2.8 (different file)
- **Commit**: `feat(logging): add RequestIdFilter populating MDC requestId`

### Step 2.10
- **Goal**: `GlobalExceptionHandler` logs ERROR for 5xx with URI + MDC userId, WARN for 4xx (per ADR-007).
- **RED test**: `com.julio.lifeorganizer.common.exception.GlobalExceptionHandlerLoggingTest.handleFallback_when500_logsErrorWithUriAndUserId()` — uses Logback `ListAppender` to capture; asserts log level ERROR and message contains URI placeholder.
- **GREEN implementation**: add `Logger log = LoggerFactory.getLogger(...)`; inject `HttpServletRequest`; log per ADR-007 §Logging.
- **ACs covered**: AC-X4
- **Dependencies**: 2.7, 2.9
- **Parallelizable?**: no
- **Commit**: `feat(common): add structured error logging to global advice`

### Step 2.11
- **Goal**: register `RequestIdFilter` early in the filter chain (no Spring Security yet — pure Servlet registration).
- **RED test**: `com.julio.lifeorganizer.common.logging.RequestIdFilterIntegrationTest.request_whenHittingHealthEndpoint_logsContainRequestIdInMdc()` — full `@SpringBootTest` against `/actuator/health`; asserts log line via ListAppender contains a `requestId` placeholder.
- **GREEN implementation**:
  - Add `@Component` to `RequestIdFilter` OR register via `FilterRegistrationBean` in a `@Configuration` class. Choose `FilterRegistrationBean` to set Order explicitly.
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/common/logging/LoggingConfig.java`.
- **ACs covered**: AC-X4
- **Dependencies**: 2.10
- **Parallelizable?**: no
- **Commit**: `feat(logging): register RequestIdFilter with explicit order`

**STOP — Phase 2 review**: ad-hoc throw of each exception from a sandbox controller should yield correct envelope JSON; log lines carry `requestId`.

---

## Phase 3 — Auth Subsystem

**Goal**: JWT issue/verify, security filter chain, register/login/refresh/me endpoints, all auth ACs closed.

### Step 3.1
- **Goal**: `JwtProperties` record with `@ConfigurationProperties("app.jwt")` + `@Validated`.
- **RED test**: `com.julio.lifeorganizer.config.JwtPropertiesTest.binding_whenSecretIsBlank_failsWithBindException()` — `@SpringBootTest` with `app.jwt.secret=""`; asserts `BindValidationException`.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/config/JwtProperties.java` per ADR-011.
  - Annotate `LifeOrganizerApplication` with `@ConfigurationPropertiesScan`.
- **ACs covered**: AC-A15 (precondition), R1
- **Dependencies**: 2.1
- **Parallelizable?**: yes — with 3.2
- **Commit**: `feat(config): add JwtProperties with validation`

### Step 3.2
- **Goal**: enforce secret length `>=32` chars at boot.
- **RED test**: `com.julio.lifeorganizer.config.JwtPropertiesTest.binding_whenSecretShorterThan32Chars_failsAtBoot()` — `@SpringBootTest` with `app.jwt.secret=tooShort`; asserts boot failure.
- **GREEN implementation**: add a `@PostConstruct` validator bean (or implement `Validator` on `JwtProperties` via `@Size(min=32)` on `secret`).
- **ACs covered**: AC-A15
- **Dependencies**: 3.1
- **Parallelizable?**: no
- **Commit**: `feat(config): enforce min jwt secret length at boot`

### Step 3.3
- **Goal**: `JwtService.generateAccessToken(userId, email, role)` — signs HS256 with `typ=access`, TTL from config.
- **RED test**: `com.julio.lifeorganizer.auth.service.JwtServiceTest.generateAccessToken_whenCalled_returnsSignedJwtWithCorrectClaims()` — parses returned token with jjwt; asserts `sub`, `email`, `role`, `typ=access`, `exp - iat == 900`.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/auth/service/JwtService.java`. Constructor injection of `JwtProperties` and `Clock` (for testability).
  - Methods: `generateAccessToken`, `generateRefreshToken`, `parseAccessToken`, `parseRefreshToken`.
- **ACs covered**: AC-A6 (issuance)
- **Dependencies**: 3.2
- **Parallelizable?**: yes — with 3.4
- **Commit**: `feat(auth): add JwtService generateAccessToken with HS256`

### Step 3.4
- **Goal**: `JwtService.generateRefreshToken(userId)` + parse methods reject typ mismatch + reject expired + accept 30s skew (R5).
- **RED test**: 4 tests — `parseAccessToken_whenRefreshTokenProvided_throwsInvalidToken`, `parseAccessToken_whenExpired_throwsTokenExpired`, `parseAccessToken_within30sSkew_succeeds`, `parseRefreshToken_whenValid_returnsSubject`.
- **GREEN implementation**: complete `JwtService`. Use jjwt parser with `setAllowedClockSkewSeconds(30)`. Manual `typ` claim check after parse.
- **ACs covered**: AC-A6, AC-A10, AC-A11, AC-A14, R5
- **Dependencies**: 3.3
- **Parallelizable?**: no
- **Commit**: `feat(auth): add refresh token issuance and parse with typ check`

### Step 3.5
- **Goal**: `AuthenticatedUser` principal record + `UserDetailsService` adapter for `AuthenticationManager`.
- **RED test**: `com.julio.lifeorganizer.auth.security.AuthenticatedUserTest.fromEntity_whenUserHasRole_buildsPrincipalWithGrantedAuthority()`.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/auth/security/AuthenticatedUser.java` — record `(Long id, String email, String role)`.
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/auth/service/JpaUserDetailsService.java` implementing `UserDetailsService` for `AuthenticationManager` use only.
- **ACs covered**: AC-A6 (login path)
- **Dependencies**: 1.7
- **Parallelizable?**: yes — with 3.3, 3.4
- **Commit**: `feat(auth): add AuthenticatedUser principal and UserDetailsService`

### Step 3.6
- **Goal**: `PasswordEncoder` bean (BCrypt strength 12).
- **RED test**: `com.julio.lifeorganizer.config.SecurityBeansTest.passwordEncoder_whenInjected_isBCryptStrength12()` — `@SpringBootTest`; asserts encoded password matches and starts with `$2a$12$`.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/config/SecurityConfig.java` with `@Bean PasswordEncoder` returning `new BCryptPasswordEncoder(12)`.
- **ACs covered**: AC-A1 (precondition), AC-A16 (hashing)
- **Dependencies**: 3.5
- **Parallelizable?**: no
- **Commit**: `feat(auth): add bcrypt password encoder bean`

### Step 3.7
- **Goal**: `RegisterRequest` DTO record with `@NotBlank @Email @Size(max=255)` on email, `@Size(min=8, max=100) @Pattern(letter+digit)` on password, `@NotBlank @Size(min=2, max=100)` on displayName.
- **RED test**: `com.julio.lifeorganizer.auth.web.dto.RegisterRequestValidationTest.validate_whenAllFieldsValid_passes()` plus 5 negative cases per spec §6.1.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/auth/web/dto/RegisterRequest.java`.
- **ACs covered**: AC-A3, AC-A4, AC-A5
- **Dependencies**: 2.1
- **Parallelizable?**: yes — with 3.3..3.6
- **Commit**: `feat(auth): add RegisterRequest dto with validation`

### Step 3.8
- **Goal**: `AuthService.register(RegisterRequest)` — lowercase email, check duplicate (`ConflictException("USER_EMAIL_EXISTS")`), bcrypt hash, save, return `UserResponse`.
- **RED test**: `com.julio.lifeorganizer.auth.service.AuthServiceRegisterTest.register_whenValid_savesUserWithLowercasedEmailAndBcryptHash()`; `register_whenEmailExists_throwsConflictException()`.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/auth/web/dto/UserResponse.java` — record `(Long id, String email, String displayName, String role)` with `from(UserEntity)`.
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/auth/service/AuthService.java`. Constructor inject `UserRepository`, `PasswordEncoder`, `JwtService`, `Clock`. Mark class `@Service @Transactional(readOnly = true)`; method `@Transactional`.
- **ACs covered**: AC-A1, AC-A2, AC-A3, AC-A4, AC-A5, AC-A16
- **Dependencies**: 3.6, 3.7
- **Parallelizable?**: no
- **Commit**: `feat(auth): implement AuthService register flow`

### Step 3.9
- **Goal**: `AuthController.register` endpoint at `POST /api/v1/auth/register` returning `201` + `ApiResponse<UserResponse>`.
- **RED test**: `com.julio.lifeorganizer.auth.web.AuthControllerRegisterTest.register_whenValidBody_returns201WithUserData()` — `@WebMvcTest(AuthController.class)`, `@MockBean AuthService`, posts JSON, asserts `jsonPath("$.data.id")`, `$.success=true`.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/auth/web/AuthController.java` with `@RestController @RequestMapping("/api/v1/auth")` and `register` method `@PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)`.
- **ACs covered**: AC-A1, AC-X1
- **Dependencies**: 3.8
- **Parallelizable?**: yes — with 3.10
- **Commit**: `feat(auth): expose POST /api/v1/auth/register endpoint`

### Step 3.10
- **Goal**: register integration test — register two distinct users + duplicate email -> 409.
- **RED test**: `com.julio.lifeorganizer.auth.AuthRegisterIntegrationTest.register_then_duplicateRegister_returns409_USER_EMAIL_EXISTS()` — extends `AbstractIntegrationTest`; uses `TestRestTemplate`. Note: skip security for this endpoint (it's `permitAll`).
- **GREEN implementation**: none if 3.9 correct; this seals the AC.
- **ACs covered**: AC-A2 (final)
- **Dependencies**: 3.9
- **Parallelizable?**: no (gates next step)
- **Commit**: `test(auth): integration test for duplicate registration returning 409`

### Step 3.11
- **Goal**: `AuthService.login(LoginRequest)` returning `AuthTokensResponse(accessToken, refreshToken, tokenType="Bearer", expiresIn=900)`. Identical exception on wrong password vs unknown email.
- **RED test**:
  - `login_whenCredentialsValid_returnsTokensWithCorrectExpiresIn()`
  - `login_whenWrongPassword_throwsInvalidCredentialsException()`
  - `login_whenUnknownEmail_throwsInvalidCredentialsException_SAME_EXCEPTION_TYPE()` — asserts same exception class and same message text in both cases.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/auth/web/dto/LoginRequest.java`, `AuthTokensResponse.java`.
  - Add `login(LoginRequest)` to `AuthService`. Inject `AuthenticationManager` (or call `passwordEncoder.matches` directly — simpler given no extra Spring Security flows needed yet).
- **ACs covered**: AC-A6, AC-A7, AC-A8
- **Dependencies**: 3.8
- **Parallelizable?**: yes — with 3.13 (different methods)
- **Commit**: `feat(auth): implement AuthService login returning JWT pair`

### Step 3.12
- **Goal**: `AuthController.login` endpoint at `POST /api/v1/auth/login` returning 200 + `ApiResponse<AuthTokensResponse>`.
- **RED test**: `com.julio.lifeorganizer.auth.web.AuthControllerLoginTest.login_whenValid_returns200WithTokens()` and `login_whenInvalid_returns401_INVALID_CREDENTIALS_withIdenticalBody()`.
- **GREEN implementation**: add `@PostMapping("/login")` to `AuthController`.
- **ACs covered**: AC-A6, AC-A7, AC-A8
- **Dependencies**: 3.11
- **Parallelizable?**: no
- **Commit**: `feat(auth): expose POST /api/v1/auth/login endpoint`

### Step 3.13
- **Goal**: `AuthService.refresh(RefreshRequest)` + `AuthController.refresh`.
- **RED test**: 4 tests in `AuthServiceRefreshTest` and `AuthControllerRefreshTest`:
  - `refresh_whenValid_returnsNewAccessToken` (AC-A9)
  - `refresh_whenTypIsAccess_throwsInvalidToken` (AC-A10)
  - `refresh_whenExpired_throwsTokenExpired` (AC-A11)
  - `refresh_whenUserDeleted_throwsUserNotFoundForToken` (spec §6.3 USER_NOT_FOUND)
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/auth/web/dto/RefreshRequest.java`, `AccessTokenResponse.java`.
  - Add `refresh(RefreshRequest)` to `AuthService` — parse refresh token, look up user, generate new access; throw appropriate exception per case.
  - Add `@PostMapping("/refresh")` to `AuthController`.
- **ACs covered**: AC-A9, AC-A10, AC-A11, R2 (test documents accepted risk)
- **Dependencies**: 3.4, 3.11
- **Parallelizable?**: no
- **Commit**: `feat(auth): implement AuthService refresh and expose endpoint`

### Step 3.14
- **Goal**: `JwtAuthenticationFilter` — extract Bearer token, parse via `JwtService`, set `SecurityContextHolder` authentication; delegate exceptions to `HandlerExceptionResolver`.
- **RED test**: `com.julio.lifeorganizer.auth.security.JwtAuthenticationFilterTest`:
  - `doFilter_whenNoHeader_passesThroughWithoutAuth`
  - `doFilter_whenValidAccessToken_setsAuthentication`
  - `doFilter_whenInvalidSignature_delegatesToHandlerExceptionResolverWithInvalidTokenException`
  - `doFilter_whenExpired_delegatesWithTokenExpiredException`
  - `doFilter_whenRefreshTokenProvided_delegatesWithInvalidTokenException`
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/auth/security/JwtAuthenticationFilter.java` extending `OncePerRequestFilter`. Inject `JwtService` + `HandlerExceptionResolver` (Spring bean name `handlerExceptionResolver`).
- **ACs covered**: AC-A10, AC-A13, AC-A14, R11
- **Dependencies**: 3.4, 2.11
- **Parallelizable?**: no
- **Commit**: `feat(auth): add JwtAuthenticationFilter delegating exceptions to advice`

### Step 3.15
- **Goal**: `JwtAuthenticationEntryPoint` — write 401 envelope with `UNAUTHORIZED` code via the `HandlerExceptionResolver`.
- **RED test**: `com.julio.lifeorganizer.auth.security.JwtAuthenticationEntryPointTest.commence_whenNoAuthentication_writes401_UNAUTHORIZED_envelope()`.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/auth/security/JwtAuthenticationEntryPoint.java`. Throws `UnauthorizedException` via handler resolver pattern (or writes JSON directly; choose direct write for simplicity since EntryPoint is outside the standard advice chain).
  - Create `JwtAccessDeniedHandler` similarly (writes 403 FORBIDDEN — unused in slice 1, but configured for completeness).
- **ACs covered**: AC-A13
- **Dependencies**: 3.14
- **Parallelizable?**: yes — with 3.16
- **Commit**: `feat(auth): add JwtAuthenticationEntryPoint and AccessDeniedHandler`

### Step 3.16
- **Goal**: `SecurityConfig` — `SecurityFilterChain` bean with public matchers (`/auth/**`, `/actuator/health`) and JWT filter on protected routes.
- **RED test**: `com.julio.lifeorganizer.config.SecurityConfigTest.filterChain_whenHittingProtectedRoute_returns401_UNAUTHORIZED_envelope()` — `@SpringBootTest`; hits `/api/v1/me` without Authorization header.
- **GREEN implementation**:
  - Complete `SecurityConfig` with `@EnableWebSecurity`, `SessionCreationPolicy.STATELESS`, CSRF disabled, `authorizeHttpRequests` matchers, register `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`, set entry point + access denied handler.
- **ACs covered**: AC-A13, AC-T23 (precondition)
- **Dependencies**: 3.15
- **Parallelizable?**: no
- **Commit**: `feat(auth): configure security filter chain with public routes and jwt filter`

### Step 3.17
- **Goal**: `UserService.findById(Long)` returning `UserResponse`; throws `UserNotFoundForTokenException` if not found.
- **RED test**: `UserServiceTest.findById_whenExists_returnsResponse`; `findById_whenMissing_throwsUserNotFoundForToken`.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/auth/service/UserService.java`.
- **ACs covered**: AC-A12 (precondition)
- **Dependencies**: 1.7, 2.4
- **Parallelizable?**: yes — with 3.16
- **Commit**: `feat(auth): add UserService findById`

### Step 3.18
- **Goal**: `MeController.getMe()` at `GET /api/v1/me` returning 200 with `UserResponse` from authenticated principal; verifies password hash never leaks.
- **RED test**:
  - `MeControllerTest.getMe_whenAuthenticated_returnsUserPayloadWithoutPasswordHash()` (`@WebMvcTest` + `@WithMockJwt` from custom support class — to be added below).
  - Integration test `MeIntegrationTest.fullFlow_register_login_getMe_succeeds()` (extends `AbstractIntegrationTest`).
  - `MeIntegrationTest.getMe_whenUserDeleted_returns401_USER_NOT_FOUND()` (R11 + spec §6.4).
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/auth/web/MeController.java`. Uses `@AuthenticationPrincipal AuthenticatedUser` + `UserService` for live DB lookup.
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/test/java/com/julio/lifeorganizer/auth/security/WithMockJwt.java` (annotation) and `WithMockJwtSecurityContextFactory.java` (per ADR-010 §Authentication-in-IT).
- **ACs covered**: AC-A12, AC-A14, AC-A16, AC-A13 (final)
- **Dependencies**: 3.16, 3.17
- **Parallelizable?**: no
- **Commit**: `feat(auth): add GET /api/v1/me endpoint and WithMockJwt test support`

**STOP — Phase 3 review**: `curl` register -> login -> `/me` works end-to-end with real Postgres. Confirm no password hash in any response.

---

## Phase 4 — Transactions Read Path

**Goal**: POST/GET-by-id/GET-list with cursor + filters; identical 404 body.

### Step 4.1
- **Goal**: `PaginationProperties` record (`@ConfigurationProperties("app.pagination")`).
- **RED test**: `com.julio.lifeorganizer.config.PaginationPropertiesTest.binding_whenDefaultAndMaxSet_bindsValidatedValues()`.
- **GREEN implementation**: Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/config/PaginationProperties.java`.
- **ACs covered**: AC-T11 (precondition)
- **Dependencies**: 3.1
- **Parallelizable?**: yes — with 4.2
- **Commit**: `feat(config): add PaginationProperties`

### Step 4.2
- **Goal**: `CreateTransactionRequest` DTO record with full Bean Validation per spec §6.5.
- **RED test**: `CreateTransactionRequestValidationTest`:
  - `validate_whenAllFieldsValid_passes`
  - `validate_whenAmountZero_failsOnAmount`
  - `validate_whenAmountNegative_failsOnAmount`
  - `validate_whenAmountHas3DecimalPlaces_failsOnAmount`
  - `validate_whenTypeNull_failsOnType`
  - `validate_whenCategoryBlank_failsOnCategory`
  - `validate_whenCategoryOver50Chars_failsOnCategory`
  - `validate_whenDescriptionBlank_failsOnDescription`
  - `validate_whenDescriptionOver255_failsOnDescription`
  - `validate_whenTransactionDateNull_failsOnTransactionDate`
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/transactions/web/dto/CreateTransactionRequest.java`.
  - Use `@DecimalMin("0.01")` and `@Digits(integer=13, fraction=2)` for amount; `@NotNull TransactionType type`; `@NotBlank @Size(min=1,max=50) String category` (with `@JsonDeserialize` trim or custom validator); `@NotBlank @Size(min=1,max=255) String description`; `@NotNull LocalDate transactionDate`.
- **ACs covered**: AC-T3, T4, T5, T6, T7, T8
- **Dependencies**: 2.3
- **Parallelizable?**: yes — with 4.1
- **Commit**: `feat(transactions): add CreateTransactionRequest with full validation`

### Step 4.3
- **Goal**: Slice `@WebMvcTest` verifying validation envelope shape for each failing field.
- **RED test**: `TransactionControllerCreateValidationTest.create_whenAmountInvalid_returns400_withMetaAmountMessage()` (parameterized over all validation failure cases).
- **GREEN implementation**: Initially this fails because `TransactionController` doesn't exist yet. To stage RED -> GREEN cleanly, this step is run **after 4.5** in execution; documented here for AC mapping. (Reordered: actual creation begins at 4.4.)
- **ACs covered**: AC-T3..T8, AC-X2
- **Dependencies**: 4.5
- **Parallelizable?**: no
- **Commit**: `test(transactions): assert validation envelope on POST /transactions`

### Step 4.4
- **Goal**: `TransactionService.create(Long userId, CreateTransactionRequest)` returning `TransactionResponse`.
- **RED test**: `TransactionServiceCreateTest`:
  - `create_whenCalled_savesEntityWithProvidedUserId`
  - `create_whenCalled_returnsResponseWithGeneratedId`
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/transactions/web/dto/TransactionResponse.java` — record + `from(TransactionEntity)`.
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/transactions/service/TransactionService.java` (constructor inject `TransactionRepository`, `Clock`; `@Transactional` on writes).
- **ACs covered**: AC-T1 (service portion)
- **Dependencies**: 1.8, 4.2
- **Parallelizable?**: no
- **Commit**: `feat(transactions): add TransactionService create`

### Step 4.5
- **Goal**: `TransactionController.create()` at `POST /api/v1/transactions` returning 201 + `ApiResponse<TransactionResponse>`.
- **RED test**: `TransactionControllerCreateTest.create_whenAuthenticated_returns201WithData()` — `@WebMvcTest`, `@WithMockJwt(userId=42)`, asserts `service.create(42L, request)` invoked and JSON shape.
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/transactions/web/TransactionController.java`. Inject `TransactionService`. Use `@AuthenticationPrincipal AuthenticatedUser` for userId.
- **ACs covered**: AC-T1, AC-X1
- **Dependencies**: 4.4
- **Parallelizable?**: no
- **Commit**: `feat(transactions): expose POST /api/v1/transactions endpoint`

### Step 4.6
- **Goal**: assert that body fields beyond the DTO's declared fields are silently ignored (AC-T2 via `fail-on-unknown-properties: false`).
- **RED test**: `TransactionControllerCreateTest.create_whenBodyContainsExtraneousUserId_ignoresItAndUsesJwtSubject()` — sends `userId=999` in body but JWT subject is `42`; asserts entity persisted with `userId=42`.
- **GREEN implementation**: none; verifies 0.6 setting + 4.5 behavior.
- **ACs covered**: AC-T2, R10
- **Dependencies**: 4.5
- **Parallelizable?**: yes — with 4.7
- **Commit**: `test(transactions): assert body userId ignored in favor of jwt subject`

### Step 4.7
- **Goal**: assert 401 on all transaction endpoints without token.
- **RED test**: `TransactionControllerAuthTest.post_whenNoAuthorizationHeader_returns401_UNAUTHORIZED()` and one for GET/PUT/DELETE.
- **GREEN implementation**: none if Phase 3 done; this is a security regression test.
- **ACs covered**: AC-T23 (partial — POST/GET portion)
- **Dependencies**: 4.5, 3.16
- **Parallelizable?**: yes — with 4.6
- **Commit**: `test(transactions): assert 401 without bearer on POST and GET`

### Step 4.8
- **Goal**: `CursorCodec` utility — encode/decode opaque base64 `<date>_<id>` per spec amendment 1.
- **RED test**: `com.julio.lifeorganizer.transactions.service.CursorCodecTest`:
  - `encode_thenDecode_returnsSameDateAndId`
  - `decode_whenNotBase64_throwsInvalidQueryException`
  - `decode_whenWrongSeparatorCount_throwsInvalidQueryException`
  - `decode_whenDatePortionMalformed_throwsInvalidQueryException`
  - `decode_whenIdNotLong_throwsInvalidQueryException`
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/transactions/service/CursorCodec.java`.
- **ACs covered**: AC-T11, AC-T12, R9 (resolved)
- **Dependencies**: 2.3
- **Parallelizable?**: yes — with 4.4
- **Commit**: `feat(transactions): add CursorCodec for opaque base64 page cursor`

### Step 4.9
- **Goal**: `TransactionRepository.findPage(...)` JPQL per ADR-009 §E.
- **RED test**: `TransactionRepositoryFindPageTest`:
  - `findPage_whenCursorNull_returnsAllInSortOrder` — extends `AbstractJpaTest`; seeds 5 transactions same-user, asserts order `transaction_date DESC, id DESC`.
  - `findPage_whenCursorOnSameDate_returnsOnlyLowerIds` — verifies composite predicate.
  - `findPage_whenCursorOnEarlierDate_excludesRowsOnAndAfterCursorDate` — verifies the strict-less-than on date.
  - `findPage_whenDeletedAtSet_excludesRow` — soft-delete filtering.
  - `findPage_whenDifferentUserId_excludesRow` — ownership scoping.
  - `findPage_whenFromAndToSet_includesBothBoundsInclusive` (AC-T15).
- **GREEN implementation**:
  - Add `findPage(...)` to `TransactionRepository` with `@Query` JPQL per ADR-009 §E.
- **ACs covered**: AC-T9, AC-T10, AC-T12, AC-T15, R4
- **Dependencies**: 1.8
- **Parallelizable?**: yes — with 4.8
- **Commit**: `feat(transactions): add JPQL findPage with composite keyset predicate`

### Step 4.10
- **Goal**: `TransactionService.findPage(userId, cursor, limit, from, to)` returning `PageResult(items, nextCursor, limit)`.
- **RED test**: `TransactionServiceFindPageTest`:
  - `findPage_whenResultHas20OrFewerItems_nextCursorIsNull` — fewer-than-limit means no more.
  - `findPage_whenResultEqualsLimitPlusOne_dropsLastAndSetsNextCursor` — uses `limit+1` query trick.
  - `findPage_whenCursorProvided_decodesBeforeQuery`
  - `findPage_whenNextCursorComputed_encodesLastItemDateAndId`
- **GREEN implementation**:
  - Add `findPage(...)` method to `TransactionService`. Use `Pageable.ofSize(limit + 1)` to detect "more available"; if returned list size > limit, drop the last and encode the new last item.
  - Create `PageResult` record (file-private inside service package).
- **ACs covered**: AC-T9, AC-T10, AC-T11, AC-T12
- **Dependencies**: 4.8, 4.9
- **Parallelizable?**: no
- **Commit**: `feat(transactions): add TransactionService findPage with cursor encoding`

### Step 4.11
- **Goal**: `TransactionController.list()` at `GET /api/v1/transactions` with query param validation; reject `limit > 100`, `limit < 1`, `from > to`, malformed cursor.
- **RED test**:
  - `TransactionControllerListTest.list_whenLimitOver100_returns400_INVALID_QUERY` (AC-T13)
  - `list_whenLimitZero_returns400_INVALID_QUERY` (AC-T14)
  - `list_whenFromAfterTo_returns400_INVALID_QUERY` (AC-T16)
  - `list_whenCursorMalformed_returns400_INVALID_QUERY` (bound to CursorCodec failure)
  - `list_whenValid_returns200WithNextCursorMeta` — integration test extending `AbstractIntegrationTest`.
- **GREEN implementation**:
  - Add `@GetMapping` to `TransactionController` with `@RequestParam @Valid` and a `@Validated` method-level annotation. Use a small helper to throw `InvalidQueryException` when `from > to`.
  - Bind directly via individual `@RequestParam` annotations to avoid model-attribute complexity.
- **ACs covered**: AC-T11, AC-T13, AC-T14, AC-T15, AC-T16
- **Dependencies**: 4.10
- **Parallelizable?**: no
- **Commit**: `feat(transactions): expose GET /api/v1/transactions with cursor pagination`

### Step 4.12
- **Goal**: `TransactionRepository.findByIdAndUserIdAndDeletedAtIsNull(Long, Long)` + `TransactionService.findById(userId, id)`.
- **RED test**:
  - `TransactionRepositoryTest.findByIdAndUserIdAndDeletedAtIsNull_whenOwned_returnsEntity`
  - `_whenDifferentOwner_returnsEmpty`
  - `_whenSoftDeleted_returnsEmpty`
  - `TransactionServiceFindByIdTest.findById_whenNotPresent_throwsNotFoundException("TRANSACTION_NOT_FOUND")`
- **GREEN implementation**:
  - Add method to `TransactionRepository`.
  - Add `findById(userId, id)` to `TransactionService`.
- **ACs covered**: AC-T17, AC-T18 (service portion), AC-T22 (precondition)
- **Dependencies**: 1.8, 4.4
- **Parallelizable?**: yes — with 4.11
- **Commit**: `feat(transactions): add ownership-scoped findById in repo and service`

### Step 4.13
- **Goal**: `TransactionController.getById()` at `GET /api/v1/transactions/{id}` + assert identical 404 body across 3 cases (R8).
- **RED test**:
  - `TransactionControllerGetByIdTest.get_whenOwnedAndActive_returns200WithData` (AC-T17)
  - Integration: `TransactionGetByIdIdentical404Test.get_underAllThreeMissingConditions_returnsIdenticalBody()` — captures the JSON body in three scenarios (missing id, different owner, soft-deleted) and asserts byte-for-byte equality.
- **GREEN implementation**: add `@GetMapping("/{id}")` to `TransactionController`.
- **ACs covered**: AC-T17, AC-T18, R8
- **Dependencies**: 4.12
- **Parallelizable?**: no (gates phase exit)
- **Commit**: `feat(transactions): expose GET /api/v1/transactions/{id} with identical 404 body`

**STOP — Phase 4 review**: live curl POST + GET list with cursor + GET by id; verify cursor round-trips.

---

## Phase 5 — Transactions Mutation Path

**Goal**: PUT (full replace) and DELETE (soft) endpoints; all T-series ACs closed.

### Step 5.1
- **Goal**: `UpdateTransactionRequest` DTO record — same shape as `CreateTransactionRequest` (could share a parent interface; choose separate records for clarity per ADR-001 web/dto convention).
- **RED test**: `UpdateTransactionRequestValidationTest` — parameterized over the same failures as Create.
- **GREEN implementation**: Create `/Users/juliolouzano/Software Development/life-organizer/src/main/java/com/julio/lifeorganizer/transactions/web/dto/UpdateTransactionRequest.java`.
- **ACs covered**: AC-T19 (precondition)
- **Dependencies**: 4.2
- **Parallelizable?**: yes — with 5.5
- **Commit**: `feat(transactions): add UpdateTransactionRequest dto`

### Step 5.2
- **Goal**: `TransactionService.update(userId, id, UpdateTransactionRequest)` — load via ownership predicate, mutate via domain method, return `TransactionResponse`. Throws `NotFoundException("TRANSACTION_NOT_FOUND")` when missing/not-owner/soft-deleted.
- **RED test**:
  - `TransactionServiceUpdateTest.update_whenValid_replacesAllFiveFields_andBumpsUpdatedAt` (AC-T19)
  - `update_whenNotFound_throwsNotFound` (AC-T20)
  - `update_whenNotOwner_throwsNotFound`
  - `update_whenSoftDeleted_throwsNotFound`
  - `update_doesNotModifyIdOrUserIdOrCreatedAtOrDeletedAt` (AC-T19 explicit)
- **GREEN implementation**: add `update(...)` method to `TransactionService`; uses `replaceWith(...)` domain method on `TransactionEntity` from Step 1.6.
- **ACs covered**: AC-T19, AC-T20
- **Dependencies**: 4.12, 5.1
- **Parallelizable?**: no
- **Commit**: `feat(transactions): implement TransactionService update with full replace`

### Step 5.3
- **Goal**: `TransactionController.update()` at `PUT /api/v1/transactions/{id}` returning 200 + `ApiResponse<TransactionResponse>`.
- **RED test**: `TransactionControllerUpdateTest.put_whenValidOwned_returns200WithUpdatedData` and slice tests for validation failures (mirror 4.3).
- **GREEN implementation**: add `@PutMapping("/{id}")` to `TransactionController`.
- **ACs covered**: AC-T19, AC-X1, AC-X2
- **Dependencies**: 5.2
- **Parallelizable?**: no
- **Commit**: `feat(transactions): expose PUT /api/v1/transactions/{id}`

### Step 5.4
- **Goal**: Integration test asserting PUT 404 identical body across the 3 conditions.
- **RED test**: `TransactionPutIdentical404Test.put_underAllThreeMissingConditions_returnsIdenticalBody()`.
- **GREEN implementation**: none if 5.2 + 5.3 correct.
- **ACs covered**: AC-T20, R8
- **Dependencies**: 5.3
- **Parallelizable?**: no
- **Commit**: `test(transactions): identical 404 body across PUT failure cases`

### Step 5.5
- **Goal**: `TransactionRepository.softDelete(id, userId, now)` returning affected row count.
- **RED test**:
  - `TransactionRepositorySoftDeleteTest.softDelete_whenOwnedAndActive_returns1_andSetsDeletedAt`
  - `softDelete_whenAlreadyDeleted_returns0`
  - `softDelete_whenNotOwner_returns0`
  - `softDelete_whenMissing_returns0`
- **GREEN implementation**: add `@Modifying @Query("UPDATE TransactionEntity t SET t.deletedAt = :now WHERE t.id = :id AND t.userId = :userId AND t.deletedAt IS NULL")` per ADR-006.
- **ACs covered**: AC-T21, AC-T22 (precondition)
- **Dependencies**: 1.8
- **Parallelizable?**: yes — with 5.1
- **Commit**: `feat(transactions): add repository softDelete returning row count`

### Step 5.6
- **Goal**: `TransactionService.delete(userId, id)` — calls `softDelete`; throws `NotFoundException` when rows == 0.
- **RED test**:
  - `TransactionServiceDeleteTest.delete_whenSuccessful_returnsNormally`
  - `delete_whenRowsZero_throwsNotFound`
- **GREEN implementation**: add `delete(...)` to `TransactionService`.
- **ACs covered**: AC-T21, AC-T22
- **Dependencies**: 5.5
- **Parallelizable?**: no
- **Commit**: `feat(transactions): implement TransactionService softDelete via row count`

### Step 5.7
- **Goal**: `TransactionController.delete()` at `DELETE /api/v1/transactions/{id}` returning 204; integration test for second-DELETE -> 404 + soft-deleted invisible to GETs.
- **RED test**:
  - `TransactionControllerDeleteTest.delete_whenSuccessful_returns204_noBody`
  - Integration `TransactionDeleteFlowIT.softDelete_thenGetById_returns404_andSecondDelete_returns404_andList_excludesIt()` (AC-T22 final).
- **GREEN implementation**:
  - Add `@DeleteMapping("/{id}") @ResponseStatus(NO_CONTENT) public void delete(...)` to `TransactionController`.
- **ACs covered**: AC-T21, AC-T22, AC-T9 (regression)
- **Dependencies**: 5.6
- **Parallelizable?**: no
- **Commit**: `feat(transactions): expose DELETE /api/v1/transactions/{id} returning 204`

### Step 5.8
- **Goal**: Authentication regression test for PUT and DELETE without bearer -> 401.
- **RED test**: `TransactionControllerAuthTest.put_whenNoBearer_returns401`; `delete_whenNoBearer_returns401`.
- **GREEN implementation**: none.
- **ACs covered**: AC-T23 (final)
- **Dependencies**: 5.3, 5.7
- **Parallelizable?**: no
- **Commit**: `test(transactions): assert 401 on PUT and DELETE without bearer`

**STOP — Phase 5 review**: full T-series ACs green. Run all integration tests; identical-body assertions should be green across both GET-by-id and PUT failure pathways.

---

## Phase 6 — Cross-cutting Verification

**Goal**: ArchUnit, JaCoCo gate, `/actuator/health` integration, full happy-path E2E.

### Step 6.1
- **Goal**: ArchUnit test enforcing ADR-001 / ADR-002 package boundaries + DTO-is-record + constructor-injection-only.
- **RED test**: `com.julio.lifeorganizer.ArchitectureTest`:
  - `persistence_doesNotDependOnWebOrService`
  - `web_onlyDependsOnAllowedPackages`
  - `transactions_doesNotImportAuthPersistenceOrService`
  - `common_doesNotImportAnyFeaturePackage`
  - `allDtos_inWebDtoPackages_areRecords`
  - `allServices_haveOnlyConstructorInjection_noAutowiredFields`
- **GREEN implementation**:
  - Create `/Users/juliolouzano/Software Development/life-organizer/src/test/java/com/julio/lifeorganizer/ArchitectureTest.java` with ArchUnit rules per ADR-002.
  - Tag: `@Tag("unit")`.
- **ACs covered**: AC-X11, AC-X12
- **Dependencies**: 5.7 (full codebase exists)
- **Parallelizable?**: yes — with 6.2, 6.3
- **Commit**: `test(arch): enforce layering, dto-record, constructor-injection via archunit`

### Step 6.2
- **Goal**: configure JaCoCo plugin + 80% line coverage gate on service + web packages (AC-X5).
- **RED test**: run `mvn verify` — initial failure if coverage < 80%. Once green it stays as a CI gate.
- **GREEN implementation**:
  - Edit `pom.xml` to add `jacoco-maven-plugin` bound to `verify`, with `<rule>` constraining `com.julio.lifeorganizer.auth.service.*`, `auth.web.*`, `transactions.service.*`, `transactions.web.*`. Exclude `LifeOrganizerApplication`, `*Config`, `*Properties`, DTO records (generated equals/hashCode noise).
- **ACs covered**: AC-X5
- **Dependencies**: 5.7
- **Parallelizable?**: yes — with 6.1
- **Commit**: `chore(quality): enforce 80% jacoco line coverage on service and web`

### Step 6.3
- **Goal**: `/actuator/health` integration test against live Testcontainers DB.
- **RED test**: `HealthEndpointIntegrationTest.health_whenDbUp_returns200_UP()` — extends `AbstractIntegrationTest`.
- **GREEN implementation**: none if 0.7 done; this is the final AC-X9 closer.
- **ACs covered**: AC-X9
- **Dependencies**: 1.9
- **Parallelizable?**: yes — with 6.1, 6.2
- **Commit**: `test(actuator): integration test for health endpoint`

### Step 6.4
- **Goal**: `/actuator/health` DOWN scenario (DB unreachable) — optional but valuable.
- **RED test**: `HealthEndpointIntegrationTest.health_whenDbStopped_returns503_DOWN()` — stop the Testcontainers container mid-test (use `POSTGRES.stop()` in a method).
- **GREEN implementation**: none.
- **ACs covered**: AC-X9 (full coverage of both DB states)
- **Dependencies**: 6.3
- **Parallelizable?**: yes — with 6.5
- **Commit**: `test(actuator): integration test for health endpoint when db down`

### Step 6.5
- **Goal**: static-source scan for forbidden patterns (`System.out.println`, `printStackTrace`, `TODO`, hardcoded secret literals).
- **RED test**: `com.julio.lifeorganizer.SourceCleanlinessTest.sourceTree_whenScanned_containsNoForbiddenStatements()` — walks `src/main/java` and asserts no occurrence of the forbidden patterns.
- **GREEN implementation**: fix violations if any; expected zero if previous phases followed style.
- **ACs covered**: AC-X10
- **Dependencies**: 5.7
- **Parallelizable?**: yes — with 6.4
- **Commit**: `test(quality): scan source tree for forbidden statements`

### Step 6.6
- **Goal**: end-to-end "happy path" integration test — register, login, POST transaction, list, GET by id, PUT, DELETE, verify 404 + list excludes.
- **RED test**: `com.julio.lifeorganizer.HappyPathIntegrationTest.fullFlow_registerThroughDelete_succeedsAtEveryStep()`.
- **GREEN implementation**: none.
- **ACs covered**: smoke-tests AC-A1, A6, A12, T1, T9–T22 together
- **Dependencies**: 5.7
- **Parallelizable?**: no
- **Commit**: `test(e2e): full happy path integration test`

### Step 6.7
- **Goal**: enable `mvn verify` to be the single CI command; confirm it covers unit + slice + integration + JaCoCo + ArchUnit.
- **RED test**: shell test in `/Users/juliolouzano/Software Development/life-organizer/scripts/verify.sh` (optional) or simply run `mvn verify` and inspect output.
- **GREEN implementation**: ensure `<groups>` includes both `unit` and `integration` in `maven-surefire-plugin` config; or use `failsafe` for integration.
- **ACs covered**: AC-X5 (gate active), AC-X6 (Testcontainers run via Maven)
- **Dependencies**: 6.6
- **Parallelizable?**: no
- **Commit**: `chore(ci): wire mvn verify to run unit, integration, jacoco gate`

**STOP — Phase 6 review**: `mvn clean verify` is green end-to-end.

---

## Phase 7 — Documentation & Polish

**Goal**: README, .env.example finalized, ADR finalized, conventional-commit history audit.

### Step 7.1
- **Goal**: finalize README — boot, env vars, endpoint list, sample curl flows.
- **RED test**: `ReadmeFinalTest.readme_whenInspected_containsSampleCurlForEachEndpoint()` — file-content check for `curl` strings referencing each of the 9 endpoints.
- **GREEN implementation**: expand `/Users/juliolouzano/Software Development/life-organizer/README.md`.
- **ACs covered**: AC-X8 (polish)
- **Dependencies**: 6.7
- **Parallelizable?**: yes — with 7.2
- **Commit**: `docs(readme): add endpoint reference and curl examples`

### Step 7.2
- **Goal**: copy approved ADR to repo `docs/` for future reference.
- **RED test**: file-existence test for `/Users/juliolouzano/Software Development/life-organizer/docs/architecture-slice-1.md`.
- **GREEN implementation**: copy `life-organizer-slice-1-architecture.md` to `/Users/juliolouzano/Software Development/life-organizer/docs/architecture-slice-1.md`; also copy the spec.
- **ACs covered**: none directly; project hygiene
- **Dependencies**: none (file copy)
- **Parallelizable?**: yes — with 7.1
- **Commit**: `docs(architecture): commit slice-1 spec and architecture under docs`

### Step 7.3
- **Goal**: audit `git log` for conventional-commits compliance; rewrite any non-compliant commit messages on the branch (interactive rebase is forbidden, so flag any violators and amend before merging).
- **RED test**: `scripts/check-commit-format.sh` — greps `git log --pretty=%s main..HEAD` and asserts each line matches `^(feat|fix|test|refactor|chore|docs|perf|ci)(\(.+\))?: .+$`.
- **GREEN implementation**: amend commits that fail; never force-push to `main`.
- **ACs covered**: AC-X13
- **Dependencies**: 7.1, 7.2
- **Parallelizable?**: no
- **Commit**: `chore(repo): audit and align commit messages to conventional commits`

**STOP — Phase 7 review**: open PR via `gh pr create`; allow `gh pr checks --watch` to verify CI green.

---

## Parallelization Map (for future subagent dispatch)

Within each phase, the following sibling steps may run concurrently (no shared state, no dependency between them). Sequential gates inside each phase listed too.

```
Phase 0:
  Group A (parallel):   0.2, 0.3, 0.4, 0.5
  Sequential gates:     0.1 -> Group A -> 0.6 -> 0.7 -> 0.8

Phase 1:
  Group A (parallel):   1.1, 1.2
  Group B (parallel):   1.3, 1.4
  Group C (parallel):   1.5, 1.6
  Group D (parallel):   1.7, 1.8
  Sequential gates:     0.7 -> A -> B -> C -> D -> 1.9

Phase 2:
  Group A (parallel):   2.1, 2.2 (after 2.1), 2.3, 2.4
                        (2.2 needs 2.1; 2.4 needs 2.3; otherwise free)
  Sequential gates:     2.5 -> 2.6 -> 2.7 -> 2.8; 2.9 parallel with 2.5–2.8;
                        2.10 needs 2.7 and 2.9; 2.11 last.

Phase 3:
  Group A (parallel after 3.1+3.2): 3.3, 3.4 (3.4 needs 3.3), 3.5
  Group B (parallel):  3.6, 3.7
  Sequential gates:     3.8 -> 3.9 -> 3.10 -> 3.11 -> 3.12 -> 3.13
                        3.14 -> 3.15 -> 3.16; 3.17 parallel with 3.14–3.16; 3.18 last.

Phase 4:
  Group A (parallel):   4.1, 4.2, 4.8, 4.9
  Sequential gates:     4.4 -> 4.5 -> 4.3 + 4.6 + 4.7 (parallel)
                        4.10 (needs 4.8, 4.9) -> 4.11
                        4.12 -> 4.13 (last)

Phase 5:
  Group A (parallel):   5.1, 5.5
  Sequential gates:     5.2 -> 5.3 -> 5.4
                        5.6 -> 5.7 -> 5.8

Phase 6:
  Group A (parallel):   6.1, 6.2, 6.3 (after 5.7 and 6.3 needs 1.9)
                        6.4, 6.5 (parallel with 6.3 after 5.7)
  Sequential gates:     6.6 -> 6.7

Phase 7:
  Group A (parallel):   7.1, 7.2
  Sequential gates:     7.3 last
```

---

## Final Notes

- This plan is **planning only**. No production code is to be written yet.
- Each step's RED test is the entry point — write it first, run it, see it fail, then write the minimal GREEN code.
- Refactor after GREEN but before commit; never refactor on a red bar.
- Commit at the end of each step using the conventional message provided.
- At every STOP point, the user reviews the result before proceeding.
- All file paths above are absolute and rooted at `/Users/juliolouzano/Software Development/life-organizer/`.
