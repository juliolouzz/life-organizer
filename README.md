# Life Organizer

[![CI](https://github.com/juliolouzz/life-organizer/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/juliolouzz/life-organizer/actions/workflows/ci.yml)
[![CodeQL](https://github.com/juliolouzz/life-organizer/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/juliolouzz/life-organizer/actions/workflows/codeql.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Personal finance + life management.

- **Slice 1**: Spring Boot 3 REST API (Users + JWT auth + Transactions CRUD)
- **Slice 2**: Angular 17 + Material 3 frontend consuming the API
- **Slice 3**: Dashboard — balance, income vs expense charts, category donut, period comparisons
- **Slice 4**: Quick-add transaction dialog on the dashboard + full Docker stack (run anywhere with `docker compose up`)
- **Slice 5**: UX fixes (cross-page nav, optional description, empty-state dialog) + **SAVINGS** as a third transaction type with a dedicated dashboard card and chart series
- **Slice 6**: **Categories** as a first-class entity, **monthly budgets per category** with progress-bar widget on the dashboard, **recurring transactions** with auto-materialisation on every transactions list call
- **Slice 7**: **CSV import** for backfilling transactions — accepts ISO or BR-format dates, dot or comma decimals, optional description column, auto-creates categories, per-row error reporting
- **Slice 8**: **Auth completeness** — password reset (single-use tokens bound to password fingerprint), non-blocking email verification, in-memory sliding-window rate limit (5 req / 15 min) on every public auth endpoint, anti-enumeration responses, Referrer-Policy: no-referrer, opt-in file delivery for tokens while SMTP is not wired up

> Behavioural contracts: [`docs/specs/`](docs/specs/) (one `slice-N-spec.txt` per slice)
> Architectures: `slice-N-architecture.md` (Slices 1-3, 8) under [`docs/specs/`](docs/specs/)
> Project rules: [`CLAUDE.md`](CLAUDE.md) · Contributing: [`CONTRIBUTING.md`](CONTRIBUTING.md)

## Stack

**Backend**: Java 21 (source) · Spring Boot 3.3.x · PostgreSQL 16 · Flyway · JPA/Hibernate · JUnit 5 · Testcontainers · Maven · Docker Compose

**Frontend**: Angular 17 · TypeScript strict · standalone components · signals · Angular Material 17 (Material 3 theming) · Reactive Forms · Jest · Playwright · ESLint

## Quick Start

### Prerequisites

**For the Docker route**: only Docker Desktop. Nothing else.

**For manual / development**: JDK 21+, Maven 3.9+, Node.js 20+, Docker Desktop, (optional) `gh` CLI.

### The fastest path: full Docker stack

If you just want to run the whole thing without installing Java or Node:

```bash
# 1. one-time setup: create .env with a real JWT secret
cp .env.docker.example .env
# then edit .env and replace the placeholder JWT_SECRET, or generate one:
# echo "JWT_SECRET=$(openssl rand -base64 48)" > .env

# 2. build and start everything (Postgres + backend + nginx-served frontend)
docker compose -f docker-compose.full.yml up --build

# 3. open the app
open http://localhost:4200
```

First build takes ~3–5 minutes (downloads JDK 21 + Maven deps + Node + Angular build). Subsequent builds reuse cached layers and finish in seconds.

Stop everything:
```bash
docker compose -f docker-compose.full.yml down          # keeps the database
docker compose -f docker-compose.full.yml down -v       # also wipes the data volume
```

### Manual / development mode

If you want to iterate on code with hot reload, run the pieces separately:

#### Boot the backend

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

### Auth & users
| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/auth/register` | none | create user, returns 201 |
| POST | `/auth/login` | none | returns access + refresh JWT pair |
| POST | `/auth/refresh` | refresh JWT | new access token |
| GET | `/me` | access JWT | the authenticated user |

### Transactions
| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/transactions` | access JWT | create (type ∈ {INCOME, EXPENSE, SAVINGS}; description optional) |
| GET | `/transactions` | access JWT | list with keyset pagination; auto-materialises due recurring rows |
| GET | `/transactions/{id}` | access JWT | get one |
| PUT | `/transactions/{id}` | access JWT | replace all fields |
| DELETE | `/transactions/{id}` | access JWT | soft delete |
| POST | `/transactions/import` | access JWT | multipart CSV import |

### Insights (dashboard)
| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/insights/summary?from&to` | access JWT | totals + counts + previous-period comparison |
| GET | `/insights/by-category?from&to` | access JWT | per-category aggregation |
| GET | `/insights/by-period?from&to&granularity?` | access JWT | bucketed time series |

### Categories, budgets, recurring (Slice 6)
| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET/POST/PUT/DELETE | `/categories[/{id}]` | access JWT | CRUD + archive (DELETE archives, not hard-delete) |
| GET/POST/PUT/DELETE | `/budgets[/{id}]?year&month` | access JWT | monthly budget per category |
| GET | `/budgets/status?year&month` | access JWT | budget-vs-actual per category for the period |
| GET/POST/PUT/DELETE | `/recurring[/{id}]` | access JWT | recurring rule CRUD |
| POST | `/recurring/{id}/pause` | access JWT | stop materialising |
| POST | `/recurring/{id}/resume` | access JWT | resume materialising |

Full request/response schemas, validation rules, and error codes are in `docs/specs/slice-1-spec.txt` §6, `slice-5-spec.txt`, `slice-6-spec.txt`, and `slice-7-spec.txt`.

## Module Layout

### Backend (`src/main/java/com/julio/lifeorganizer/`)

```
LifeOrganizerApplication.java
config/         SecurityConfig, JwtProperties, PaginationProperties, beans
common/         ApiResponse + PageMeta records, exception hierarchy,
                GlobalExceptionHandler, RequestIdFilter
auth/           Role enum, UserEntity, UserRepository, JwtService, AuthService,
                JwtAuthenticationFilter, AuthController, UserController, DTOs
transactions/   TransactionType (INCOME/EXPENSE/SAVINGS), CursorCodec,
                TransactionEntity, TransactionService (writable list with
                materialiser hook), CsvImportService, TransactionController,
                TransactionImportController, DTOs
insights/       InsightsService, InsightsController (3 aggregation endpoints),
                CategoryTotalRow / TypeSumRow / DailyBucketRow projections, DTOs
categories/     CategoryEntity (id, user_id, name, kind, archived),
                CategoryService, CategoryController + DTOs
budgets/        BudgetEntity, BudgetService (status = budget-vs-actual join),
                BudgetController + DTOs
recurring/      RecurringTransactionEntity, Frequency enum,
                RecurringMaterialiser (cap 365/call), RecurringService,
                RecurringController + DTOs
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
  transactions/import   TransactionsImportPage (CSV upload + per-row result)
  dashboard             DashboardPage + 4 widgets (stat-card, income-expense-chart,
                        category-donut, budgets-widget), PeriodSelector,
                        QuickAddTransactionDialog, InsightsService
  categories            CategoriesPage + CategoriesService
  budgets               BudgetsPage + BudgetsService
  recurring             RecurringPage + RecurringService
  not-found             NotFoundPage
```

## Status

All 8 slices complete and merged through PRs with green CI + branch protection enforced.

- **Slice 1**: REST API + JWT auth + transactions CRUD ([run evidence](docs/run-evidence.md))
- **Slice 2**: Angular 17 + Material 3 frontend
- **Slice 3**: Dashboard with charts + period comparisons
- **Slice 4**: Quick-add dialog + full Docker stack
- **Slice 5**: UX fixes + SAVINGS type
- **Slice 6**: Categories + budgets + recurring transactions
- **Slice 7**: CSV import
- **Slice 8**: Password reset + email verification + rate limiting

**Numbers**: 52 backend tests + 17 frontend unit tests, JaCoCo ≥80% on service + web packages, ArchUnit layering rules enforced, CodeQL clean (CSRF false positive documented + suppressed), branch protection on `main` requires CI green + no force-pushes.

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

## Slice tags

| Tag | Slice |
|---|---|
| `v0.1.0` | Backend (Users + JWT + Transactions CRUD) |
| `v0.2.0` | Angular 17 + Material 3 frontend |
| `v0.3.0` | Dashboard with charts |
| `v0.4.0` / `v0.4.1` | Docker stack + quick-add dialog (.1 = CORS fix for browsers) |
| `v0.5.0` | UX fixes + SAVINGS type |
| `v0.6.0` | Categories + budgets + recurring |
| `v0.7.0` | CSV import |
| `v0.8.0` | Auth completeness (password reset + email verification + rate limit) |

## What's next

- **Slice 9** — Account management: change password while logged in, change email (re-verifies), edit display name, account deletion (soft -> hard with grace period)
- **Hosting** — production profile, image registry, deployment guide for Fly.io / Railway, real SMTP delivery
- **Observability** — Micrometer / Prometheus, OpenTelemetry tracing, JSON logs
- **Other verticals** — health / fitness, diary, reminders, goals

See [`docs/specs/`](docs/specs/) for behavioural contracts and ADRs.

## Development workflow

- `main` is protected: CI must be green and force-pushes are blocked.
- Every change goes through a PR. Branches are deleted on merge.
- Conventional commits (`feat:`, `fix:`, `refactor:`, `test:`, `chore:`, `docs:`).
- Tag releases as `vMAJOR.MINOR.PATCH`.
- The Dependabot config in [.github/dependabot.yml](.github/dependabot.yml) opens grouped weekly bumps; only minor / patch dependencies are accepted blindly — major bumps (e.g., Spring Boot 4) get reviewed and merged as their own slice.

## License

[MIT](LICENSE).
