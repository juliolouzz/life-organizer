# Life Organizer

[![CI](https://github.com/juliolouzz/life-organizer/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/juliolouzz/life-organizer/actions/workflows/ci.yml)
[![CodeQL](https://github.com/juliolouzz/life-organizer/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/juliolouzz/life-organizer/actions/workflows/codeql.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Personal finance + life management.

- **Slice 1**: Spring Boot 3 REST API (Users + JWT auth + Transactions CRUD)
- **Slice 2**: Angular 17 + Material 3 frontend consuming the API
- **Slice 3**: Dashboard — balance, income vs expense charts, category donut, period comparisons

> Behavioural contracts: [`docs/specs/slice-1-spec.txt`](docs/specs/slice-1-spec.txt) · [`docs/specs/slice-2-spec.txt`](docs/specs/slice-2-spec.txt)
> Architectures: [`slice-1-architecture.md`](docs/specs/slice-1-architecture.md) · [`slice-2-architecture.md`](docs/specs/slice-2-architecture.md)
> Project rules: [`CLAUDE.md`](CLAUDE.md) · Contributing: [`CONTRIBUTING.md`](CONTRIBUTING.md)

## Stack

**Backend**: Java 21 (source) · Spring Boot 3.3.x · PostgreSQL 16 · Flyway · JPA/Hibernate · JUnit 5 · Testcontainers · Maven · Docker Compose

**Frontend**: Angular 17 · TypeScript strict · standalone components · signals · Angular Material 17 (Material 3 theming) · Reactive Forms · Jest · Playwright · ESLint

## Quick Start

### Prerequisites

- JDK 21+ (Java 25 also works; source level pinned to 21)
- Maven 3.9+
- Node.js 20+
- Docker Desktop
- (Optional) `gh` CLI for PR workflows

### Boot the backend

```bash
# 1. create a .env file in the project root (gitignored)
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

`JWT_SECRET` **must** be at least 32 characters — boot fails fast otherwise.

### Boot the frontend (Slice 2)

```bash
cd frontend
npm ci --legacy-peer-deps

# 1. backend must be running (see above)
# 2. start the Angular dev server (proxy.conf.json forwards /api -> :8080)
npm run start         # opens http://localhost:4200
```

The frontend uses Material 3 theming with a custom violet + cyan palette, light / dark mode (toggle in the top bar, persisted to localStorage), Inter for UI text, and JetBrains Mono for numerals.

### Run all tests

```bash
# Backend
mvn test       # unit only
mvn verify     # unit + integration + JaCoCo coverage gate + ArchUnit

# Frontend
cd frontend
npm run lint
npm test            # Jest unit tests
npm run e2e         # Playwright (needs backend running on :8080)
npm run build       # production build to frontend/dist/
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

### Backend (`src/main/java/com/julio/lifeorganizer/`)

```
LifeOrganizerApplication.java
config/         SecurityConfig, JwtProperties, PaginationProperties, beans
common/         ApiResponse + PageMeta records, exception hierarchy,
                GlobalExceptionHandler, RequestIdFilter
auth/           Role enum, UserEntity, UserRepository, JwtService, AuthService,
                JwtAuthenticationFilter, AuthController, UserController, DTOs
transactions/   TransactionType, CursorCodec, TransactionEntity, repository,
                TransactionService, TransactionController, DTOs
insights/       InsightsService, InsightsController (3 aggregation endpoints),
                CategoryTotalRow / TypeSumRow / DailyBucketRow projections, DTOs
```

### Frontend (`frontend/src/app/`)

```
app.component.ts        global progress bar + router outlet
app.config.ts           providers (router, http, interceptors, animations)
app.routes.ts           lazy-loaded routes with auth + anonymous guards
core/
  api/                  ApiResponse types, error codes
  auth/                 AuthService, TokenStore, guards, auth + refresh interceptors
  http/                 error + loading interceptors
  theme/                ThemeService (light/dark, persisted)
  ui/                   LoadingService
shared/
  components/           PageHeader, EmptyState, ConfirmDialog
  pipes/                MoneyBrlPipe
layout/                 AppShellComponent (sidenav + topbar)
features/
  auth/login            LoginPage
  auth/register         RegisterPage (with password strength meter)
  profile               ProfilePage
  transactions/list     TransactionsListPage (table, filters, pagination)
  transactions/form     TransactionFormPage (create + edit unified)
  dashboard             DashboardPage + 3 widgets (stat-card, income-expense-chart,
                        category-donut), PeriodSelector, InsightsService
  not-found             NotFoundPage
```

## Status

**Slice 1**: complete — 52 backend tests pass via `mvn verify`, JaCoCo ≥80% on service + web, ArchUnit clean, full flow validated against live Postgres ([run evidence](docs/run-evidence.md)).

**Slice 2**: complete — Angular 17 frontend builds, lints, and unit-tests clean; Material 3 theme with dark mode; full register → login → transactions CRUD UI; Playwright E2E covers the happy path.

**Slice 3**: complete — Dashboard at `/dashboard` (new default landing) with 4 stat cards (net, income, expenses, savings rate), Income-vs-Expense bar chart with auto granularity, category donut (top 8 + Other), recent transactions card, period selector (this month / last month / last 3 months / this year / all time / custom). Three new server-side aggregation endpoints under `/api/v1/insights`. 6 new backend integration tests + 6 new frontend unit tests.

### Backend ACs

| Category | Count | Status |
|---|---|---|
| AC-A1..A16 — Auth & User | 16 | implemented + tested |
| AC-T1..T23 — Transactions | 23 | implemented + tested |
| AC-X1..X13 — Cross-cutting | 13 | implemented + tested |
| **Total** | **52** | all covered |

### Frontend ACs

| Category | Count | Status |
|---|---|---|
| AC-F-A1..A12 — Auth flow | 12 | implemented |
| AC-F-T1..T14 — Transactions UI | 14 | implemented |
| AC-F-X1..X12 — Cross-cutting | 12 | implemented |

## What's next

- Production profile (`application-prod.yml`), app `Dockerfile`, real secret manager
- Observability: Micrometer / Prometheus, OpenTelemetry tracing
- Branch protection rules on `main` requiring CI green
- Health / fitness vertical, diary vertical
- Reports, budgets, recurring transactions
- See [`docs/specs/slice-1-spec.txt`](docs/specs/slice-1-spec.txt) §9 and [`docs/specs/slice-2-spec.txt`](docs/specs/slice-2-spec.txt) §9 for explicitly deferred scope.

## License

[MIT](LICENSE).
