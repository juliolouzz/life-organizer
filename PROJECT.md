# Life Organizer - Project Reference

A reference document for the codebase as it stands today. Use this when you
want to understand *what is there* and *why*, not *how to build it*. For the
build-from-scratch guide see [`TUTORIAL.md`](TUTORIAL.md). For deployment
see [`docs/deployment.md`](docs/deployment.md).

---

## Table of contents

1. [What the project is](#1-what-the-project-is)
2. [Feature inventory](#2-feature-inventory)
3. [Architecture overview](#3-architecture-overview)
4. [Backend module map](#4-backend-module-map)
5. [Data model](#5-data-model)
6. [HTTP API surface](#6-http-api-surface)
7. [Security model](#7-security-model)
8. [Mail delivery](#8-mail-delivery)
9. [Frontend architecture](#9-frontend-architecture)
10. [Operational profile](#10-operational-profile)
11. [Quality gates](#11-quality-gates)
12. [Key design decisions, indexed](#12-key-design-decisions-indexed)
13. [How to extend the system](#13-how-to-extend-the-system)
14. [Risk register](#14-risk-register)

---

## 1. What the project is

A personal-finance + life-management app. The user (typically the operator
themselves, but the design assumes shareable multi-user) records income,
expenses, and savings; categorises them; sets monthly budgets; defines
recurring transactions; views a dashboard; and produces monthly reports
(CSV + PDF).

It is a full-stack application:

- **Backend**: Spring Boot 3.3 REST API, JWT-authenticated, PostgreSQL 16
  via Flyway migrations.
- **Frontend**: Angular 17 single-page app, Angular Material 17 UI,
  standalone components + signals.
- **Containers**: multi-stage non-root Docker images for backend and
  frontend; one compose file boots both plus Postgres.
- **Production-grade defaults**: SMTP delivery, JSON logs, prod Spring
  profile, secrets via env vars, rate limiting on the public auth surface.

The codebase is organised by **slices** (vertical features) rather than
layers; see the [`docs/specs/`](docs/specs/) directory for the
spec/architecture/plan triplet for each slice.

---

## 2. Feature inventory

| Area | Capability |
|---|---|
| Authentication | Register, login, refresh, JWT (HS256), bcrypt password hashing |
| Account recovery | Password reset (single-use, 1h TTL), email verification (24h TTL), email change (typo-safe two-step), account restore link |
| Account self-service | Change display name, change password, change email, sign out everywhere |
| Account deletion | Soft delete with 30-day grace period, scheduled hard delete, two restore paths |
| Rate limiting | Sliding-window in-memory limiter on all 10 public-write auth paths (5 req / 15 min / IP / endpoint) |
| Transactions | Full CRUD, soft delete, keyset pagination, three types (INCOME / EXPENSE / SAVINGS) |
| Categories | First-class entity per user, auto-created on CSV import |
| Budgets | Monthly per-category budgets with status (spent / remaining / over) |
| Recurring transactions | Templates that auto-materialise on every transaction list call |
| CSV import | Bulk import in ISO or BR date format, dot or comma decimals, per-row error reporting. **Auto-detects bank-statement format** (`Date, Details, Debit, Credit, Balance`) and maps debit→expense, credit→income, balance-only rows skipped (post-Slice-14 hardening) |
| Dashboard | Stat cards (Net = Income − Expenses where Expenses includes SAVINGS), income/expense/savings chart, category donut, period selector with built-in presets + custom range, hover tooltips on each stat card |
| Reports | Monthly summary, year-over-year comparison, 6/12-month category trends |
| Per-user currency (Slice 13) | Display-only BRL / USD / EUR preference; symbol + locale propagate live via signals to every chart, donut, stat card, form prefix; existing amounts keep their stored value |
| Custom accounting month (Slice 14) | User-chosen day-of-month (1-31) drives the dashboard "This month" / "Last month" and budgets cycle. Weekend snap to previous Friday; day > last-of-month is clamped |
| Exports | Summary CSV + summary PDF (Thymeleaf + OpenHTMLtoPDF), transactions CSV (round-trip with import) |
| Mail delivery | Pluggable provider (file or SMTP), HTML + plain-text alternative, anti-enumeration semantics |
| OpenAPI / Swagger | Auto-generated docs at `/swagger-ui/index.html`, raw spec at `/v3/api-docs` |
| Observability hooks | Spring Actuator `/actuator/health` exposed unauthenticated |

---

## 3. Architecture overview

```
                       Browser
                          |
                          v
   +-------- nginx (frontend image, port 4200) --------+
   |   serves Angular bundle; proxies /api -> backend |
   +---------------------|----------------------------+
                          v
   +-------------------- Spring Boot backend (port 8080) -----------------+
   |                                                                      |
   |  CorsFilter -> RateLimitFilter -> JwtAuthenticationFilter -> ...     |
   |                                                                      |
   |  +---- web (controllers) ----+                                       |
   |  +---- service (business rules + @Transactional) ----+               |
   |  +---- persistence (Spring Data JPA repositories) ----+              |
   |                                                                      |
   +----------------------+-----------+-----------------+-----------------+
                          |           |                 |
                          v           v                 v
                     PostgreSQL    SMTP (prod)    File sink (dev)
```

Every authenticated HTTP request walks:

1. **CorsFilter** - configurable cross-origin (default off; the `local`
   profile lets in `http://localhost:4200`).
2. **RateLimitFilter** - 429 on the 6th call to `/login`,
   `/register`, `/forgot-password`, `/reset-password`, `/verify-email`,
   `/resend-verification`, `/confirm-email-change`,
   `/confirm-account-restore`, `/me/email`, `/me/delete`,
   `/me/sessions/logout-all` from the same IP in 15 minutes.
3. **JwtAuthenticationFilter** - parses `Bearer <token>`, loads the user
   from Postgres, runs the **deletion gate** (Slice 9) and the
   **token-version gate** (Slice 12), populates the SecurityContext.
4. **Controller** - HTTP only; calls a service.
5. **Service** - business logic; throws domain exceptions on failure.
6. **Repository** - Spring Data JPA; one transaction per write.
7. **GlobalExceptionHandler** maps every domain exception to the
   `ApiResponse` JSON envelope with the right HTTP status.

---

## 4. Backend module map

```
com.julio.lifeorganizer
├── LifeOrganizerApplication        # @SpringBootApplication + scheduling
├── auth/
│   ├── domain/                     # Role enum
│   ├── persistence/                # UserEntity + UserRepository
│   ├── security/                   # JwtAuthenticationFilter, AuthenticatedUser, JwtAuthenticationEntryPoint
│   ├── service/                    # AuthService, AccountService, AccountLifecycleJob, AccountHardDeleter, JwtService, UserService
│   └── web/                        # AuthController, AccountController, UserController + DTOs
├── transactions/
│   ├── domain/                     # TransactionType enum + cursor codec
│   ├── persistence/                # TransactionEntity + TransactionRepository
│   ├── service/                    # TransactionService, CsvImportService
│   └── web/                        # TransactionController, TransactionImportController + DTOs
├── categories/
│   ├── persistence/                # CategoryEntity + CategoryRepository
│   ├── service/                    # CategoryService
│   └── web/                        # CategoryController + DTOs
├── budgets/
│   ├── persistence/                # BudgetEntity + BudgetRepository
│   ├── service/                    # BudgetService
│   └── web/                        # BudgetController + DTOs
├── recurring/
│   ├── persistence/                # RecurringTransactionEntity + repository
│   ├── service/                    # RecurringService + RecurringMaterializer
│   └── web/                        # RecurringController + DTOs
├── insights/                       # Dashboard aggregations (Slice 3)
│   ├── persistence/                # row projections (TypeSumRow, CategoryTotalRow, DailyBucketRow)
│   ├── service/                    # InsightsService
│   └── web/                        # InsightsController + DTOs
├── reports/                        # Slice 10 reports + exports
│   ├── service/                    # ReportsService, ReportsExportService
│   └── web/                        # ReportsController, ReportsExportController + DTOs
├── mail/                           # Slice 11 pluggable mail
│   ├── MailService (interface)
│   ├── FileMailService             # dev sink, default
│   ├── SmtpMailService             # JavaMailSender + Thymeleaf templates
│   └── MailProperties
├── common/
│   ├── api/                        # ApiResponse, PageMeta, paged response helpers
│   ├── exception/                  # DomainException hierarchy + GlobalExceptionHandler
│   ├── logging/                    # RequestIdFilter (correlation id in MDC)
│   └── security/                   # RateLimiter, RateLimitFilter
└── config/                         # SecurityConfig, JwtProperties, AuthDevDeliveryProperties, OpenApiConfig, JacksonConfig
```

The same shape is used by every domain: `persistence` (entity + repo),
`service` (business logic + transactions), `web` (controller + DTOs).
Cross-cutting concerns live under `common/` and `config/`.

---

## 5. Data model

PostgreSQL tables (one Flyway migration per change; current latest is `V9`):

| Table | Key columns | Notes |
|---|---|---|
| `users` | `id`, `email` (unique), `password_hash` (bcrypt), `display_name`, `role`, `email_verified`, `deletion_scheduled_at` (nullable), `token_version`, timestamps | All slices add columns here; never split into multiple tables. Token revocation epoch lives on this row. |
| `transactions` | `id`, `user_id`, `amount` (NUMERIC(12,2)), `type` (CHECK constraint), `category` (denormalized string), `description` (nullable), `transaction_date`, `deleted_at` (nullable for soft delete), timestamps | Partial index `idx_transactions_user_active` on `(user_id, transaction_date DESC, id DESC)` WHERE `deleted_at IS NULL` powers the keyset list. |
| `categories` | `id`, `user_id`, `name` (unique per user), `kind`, timestamps | Slice 6. Used by the UI picker + auto-create on CSV import. Transactions do not FK to this table - the `category` column is a copy. |
| `budgets` | `id`, `user_id`, `category_id`, `amount`, `month` (1..12), `year`, timestamps | Slice 6. Status (spent / remaining / over) is computed on demand. |
| `recurring_transactions` | `id`, `user_id`, `category_id`, `amount`, `type`, `description`, `interval` (DAILY/WEEKLY/MONTHLY/YEARLY), `next_due_date`, `last_materialised_at` (nullable), timestamps | Slice 6. The `RecurringMaterializer` advances `next_due_date` and inserts a transaction on each due cycle. |
| `flyway_schema_history` | Managed by Flyway. | Inspect with `SELECT version, success FROM flyway_schema_history`. |

### Migrations in order

1. `V1__create_users_table.sql`
2. `V2__create_transactions_table.sql`
3. `V3__add_savings_type.sql`
4. `V4__create_categories_table.sql`
5. `V5__create_budgets_table.sql`
6. `V6__create_recurring_transactions_table.sql`
7. `V7__users_add_email_verified.sql`
8. `V8__users_add_deletion_scheduled_at.sql`
9. `V9__users_add_token_version.sql`

---

## 6. HTTP API surface

Full machine-readable spec at `/v3/api-docs` once the app is running. The
Swagger UI at `/swagger-ui/index.html` lets you paste a JWT and try every
endpoint from the browser.

### Auth (anonymous)

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/auth/register` | Create user + auto-login |
| POST | `/api/v1/auth/login` | Issue access + refresh tokens |
| POST | `/api/v1/auth/refresh` | New access token using refresh |
| POST | `/api/v1/auth/forgot-password` | Request reset link (anti-enumeration) |
| POST | `/api/v1/auth/reset-password` | Apply new password via token |
| POST | `/api/v1/auth/verify-email` | Mark email verified via token |
| POST | `/api/v1/auth/resend-verification` | Re-request verification link |
| POST | `/api/v1/auth/confirm-email-change` | Apply pending email change |
| POST | `/api/v1/auth/confirm-account-restore` | Cancel pending deletion via link |

### Account (auth required)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/me` | Current user profile |
| PATCH | `/api/v1/me` | Change display name |
| POST | `/api/v1/me/password` | Change password (current + new) |
| POST | `/api/v1/me/email` | Request email change to a new address |
| POST | `/api/v1/me/delete` | Soft-delete account (30-day grace) |
| POST | `/api/v1/me/restore` | Cancel own pending deletion |
| POST | `/api/v1/me/sessions/logout-all` | Bump token_version (revoke all sessions) |

### Transactions (auth required)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/transactions` | Keyset-paginated list with filters |
| POST | `/api/v1/transactions` | Create |
| GET | `/api/v1/transactions/{id}` | Read one |
| PUT | `/api/v1/transactions/{id}` | Full replace |
| DELETE | `/api/v1/transactions/{id}` | Soft delete |
| POST | `/api/v1/transactions/import` | CSV bulk import |

### Categories / Budgets / Recurring

Standard CRUD under `/api/v1/categories`, `/api/v1/budgets`,
`/api/v1/recurring`. Budgets adds a `/status` query for spend-vs-budget
on a month.

### Insights (Slice 3)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/insights/summary` | Totals + counts in a date window |
| GET | `/api/v1/insights/by-period` | Daily/weekly/monthly buckets |
| GET | `/api/v1/insights/top-categories` | Top N categories in a window |

### Reports (Slice 10)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/reports/summary` | Monthly summary JSON |
| GET | `/api/v1/reports/yoy` | Year-over-year comparison |
| GET | `/api/v1/reports/trends` | 6/12-month per-category series |
| GET | `/api/v1/reports/summary.csv` | Summary download as CSV |
| GET | `/api/v1/reports/summary.pdf` | Summary download as PDF |
| GET | `/api/v1/reports/transactions.csv` | Transactions in import-compatible CSV |

### Observability

- `GET /actuator/health` - liveness probe. Returns `{status: "UP"}`.

### Response envelope

Every JSON endpoint returns:

```json
{
  "success": true,
  "data": {...},
  "message": "optional human-readable string",
  "meta": {
    "code": "ERROR_CODE_IF_APPLICABLE",
    "nextCursor": "...if paginated",
    "fieldName": "validation message"
  }
}
```

Error codes used today: `INVALID_CREDENTIALS`, `INVALID_TOKEN`,
`TOKEN_EXPIRED`, `UNAUTHORIZED`, `USER_NOT_FOUND`, `USER_EMAIL_EXISTS`,
`USER_EMAIL_UNCHANGED`, `TRANSACTION_NOT_FOUND`, `CATEGORY_EXISTS`,
`INVALID_QUERY`, `MALFORMED_REQUEST`, `INVALID_ROW` (CSV per-row error),
`RATE_LIMITED`, `ACCOUNT_DELETION_PENDING`, `ACCOUNT_NOT_DELETING`,
`INTERNAL_ERROR`.

---

## 7. Security model

| Layer | Mechanism |
|---|---|
| Transport | TLS terminated by the deployment platform (Fly.io / nginx / Caddy). No HTTP between user and frontend in prod. |
| AuthN | JWT (HS256) in the `Authorization: Bearer <token>` header. Access token TTL 15m, refresh 7d. Token signing key is `JWT_SECRET` env var, validated at boot to be >= 32 chars. |
| AuthZ | Scope by JWT subject (`sub` claim). Every service method takes `userId` as the first parameter and filters by it. There is no shared / global data. |
| Password hashing | Bcrypt cost factor 12. Spring Security `BCryptPasswordEncoder`. |
| Anti-enumeration | `/forgot-password` and `/resend-verification` always return 200 with the same body. Login uses a generic `INVALID_CREDENTIALS` regardless of whether the email or the password was wrong. Both run constant-time work via decoy JWT signing. |
| Single-use tokens | Password reset and change-email tokens carry a SHA-256 fingerprint of the user's current password hash (`pwdv` claim). The next credential change invalidates them. |
| Revocation epoch | `users.token_version` + `tv` claim on every JWT. Bumping invalidates every prior token for that user. Bumped on password change, password reset, and explicit logout-all. |
| Deletion gate | `JwtAuthenticationFilter` rejects (with 403) any request for a user whose `deletion_scheduled_at` is set and in the future. `/me/restore` is exempt so the user can cancel from the same session. |
| Rate limiting | In-memory sliding window, 5 req / 15 min / IP / endpoint, applied to 10 public-write paths. Disabled in tests via `app.rate-limit.enabled=false`. |
| Referrer-Policy | `no-referrer` set globally so tokens in `?token=...` URLs don't leak via the `Referer` header. |
| Frontend URL hygiene | The SPA strips the token from the URL after read (Angular `Router.navigate` with `queryParams: { token: null }` and `replaceUrl: true`). |
| CSRF | Intentionally disabled - we use stateless JWT in the Authorization header; no cookie-bound ambient credentials. CodeQL alert is suppressed in `.github/codeql/codeql-config.yml` with rationale. |
| Error responses | Generic messages for unauthenticated callers (no "user X not found"). Validation errors expose field names but not internal exception details. The fallback handler returns `INTERNAL_ERROR` with no stack trace. |
| Headers | Default Spring Security headers (frame options, content-type options, XSS protection) plus `Referrer-Policy: no-referrer`. |

For a deep dive on individual decisions, see the architecture document for
the slice that introduced it (`docs/specs/slice-<N>-architecture.md`).

---

## 8. Mail delivery

```
AuthService / AccountService
        |
        v
   MailService (interface)
   /              \
FileMailService   SmtpMailService
   |                  |
   v                  v
.tmp/auth-dev-     JavaMailSender -> SMTP server
   links.txt           Thymeleaf templates render HTML
                       (with plain-text alternative)
```

- Selected at boot by `app.mail.provider` (`file` default, `smtp` for prod).
- `MailService` exposes one method per link type
  (`sendPasswordReset`, `sendEmailVerification`,
  `sendEmailChangeConfirmation`, `sendAccountRestore`).
- `SmtpMailService` constructs a `MimeMessage` with both HTML and plain-text
  parts; renders the HTML from `templates/mail/<kind>.html`.
- Failures are swallowed at WARN log level - the calling endpoint still
  returns its documented anti-enumeration success response. (Persistent
  outbox / retry is documented as a future slice.)
- Configure SMTP via the standard `spring.mail.host/port/username/password`
  properties and `app.mail.from-address`, `app.mail.from-name`,
  `app.mail.base-url`. Examples in `.env.prod.example` cover Gmail, SendGrid,
  Mailgun, and Mailtrap.

---

## 9. Frontend architecture

```
frontend/src/app/
├── app.config.ts                 # router + interceptor wiring
├── app.routes.ts                 # route table (lazy-loaded components)
├── core/
│   ├── auth/                     # AuthService, interceptors, guards, token store
│   ├── account/                  # AccountService (self-service writes)
│   ├── reports/                  # ReportsService (JSON + Blob downloads)
│   └── api/                      # ApiResponse type + error code constants
├── layout/
│   └── app-shell.component.ts    # sidenav + user card + main router-outlet
├── features/
│   ├── auth/                     # login, register, forgot/reset/verify pages
│   ├── dashboard/                # dashboard page + widgets (StatCard, IncomeExpenseChart, etc.)
│   ├── transactions/             # list / form / import pages + service
│   ├── categories/, budgets/, recurring/
│   ├── reports/                  # /reports page with 3 tabs
│   └── profile/                  # account management page + dialogs
└── shared/
    ├── components/               # PageHeader, EmptyState, banners
    └── pipes/                    # MoneyBrlPipe
```

Conventions:

- **Standalone components** everywhere - no NgModules.
- **Signals** for state, RxJS for HTTP and stream pipelines.
- **Lazy routes** via `loadComponent: () => import(...)` - keep the initial
  bundle small.
- **No business logic in components** - everything goes through services.
- **Auth interceptor** attaches the access token (unless `SKIP_AUTH` is set
  in the request context); **refresh interceptor** catches a single 401,
  refreshes, and retries once.

Build / test / lint commands:

```bash
cd frontend
npx ng build         # production bundle (Angular's prod config by default)
npx ng serve         # dev server on :4200 with HMR
npx jest             # 34 unit tests (Jest 29 + jest-preset-angular)
npx eslint "src/**/*.{ts,html}"
```

---

## 10. Operational profile

### Profiles

| Profile | Used for | What it changes |
|---|---|---|
| (none) | Default for tests + local Docker | Human-readable logs, file-based mail (disabled by default), permissive Hikari defaults |
| `local` | Developer laptops outside Docker | Enables `app.auth.dev-delivery` (file-based mail), looser CORS, formats SQL in logs |
| `test` | Integration tests | Disables rate limiter (singleton state would poison the test run); uses Testcontainers-provided DB URL |
| `prod` | Real deployment | SMTP mail, JSON logs via `logstash-logback-encoder`, Hikari pool 20/5, banner-mode off, error internals hidden |

### Environment variables

The minimum required to boot the prod profile:

| Variable | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | Activates the prod overlay |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Postgres connection |
| `JWT_SECRET` | >= 32 random chars; `openssl rand -base64 48` |
| `APP_CORS_ALLOWED_ORIGINS` | SPA origin (e.g. `https://your-domain.example`) |
| `APP_MAIL_PROVIDER=smtp` | Switch to real SMTP delivery |
| `APP_MAIL_FROM_ADDRESS`, `APP_MAIL_FROM_NAME` | From header on outbound mail |
| `APP_MAIL_BASE_URL` | Public origin for link generation |
| `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD` | SMTP credentials |

Full template + commented examples for four providers in
[`.env.prod.example`](.env.prod.example).

### Docker image properties

| Property | Value |
|---|---|
| Base | `eclipse-temurin:21-jre-alpine` |
| User | `lifeorg` (uid 10001, gid 10001) |
| PID 1 | `tini` |
| JVM tuning | `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom` |
| Healthcheck | `wget -q --spider http://localhost:8080/actuator/health` |
| Build cache | Two-stage with `mvn dependency:go-offline` for layer reuse |

### Logging

- `local`, `test`, default: Spring Boot's `%clr`-coloured pattern with
  `requestId` in MDC.
- `prod`: structured JSON via `logstash-logback-encoder`. Each log line is
  one JSON object with `@timestamp`, `message`, `logger`, `thread`, `level`,
  `requestId`, and a constant `service: "life-organizer"` field. Ready for
  Loki / ELK / Datadog ingestion without further wrangling.

---

## 11. Quality gates

Every PR runs four GitHub Actions checks before merge is allowed:

| Check | What it does |
|---|---|
| `backend - mvn verify (JDK 21, Postgres via Testcontainers)` | Unit + integration + ArchUnit + JaCoCo |
| `frontend - build, lint, jest (Node 20, Angular 17)` | `ng build`, ESLint, Jest |
| `Analyze (java-kotlin)` | CodeQL static analysis |
| `CodeQL` (post-check) | Aggregates new alerts on the merge ref |

Local mirror:

```bash
mvn verify                                    # backend
( cd frontend && npx ng build && npx eslint "src/**" && npx jest )
```

### Code-quality rules enforced by ArchUnit

- Services only in `..service..`; controllers only in `..web..`;
  repositories only in `..persistence..`.
- Controllers never depend on classes under `..persistence..`.
- Service fields are `private final`.
- DTOs in `..web.dto..` are Java records.
- No `System.out.println` / `e.printStackTrace()`.
- Constructor injection only - no `@Autowired` on fields.

### JaCoCo

>= 80% line coverage on `service.*` and `web.*` packages. The Maven build
fails the slice if a new service or controller is undertested.

### CodeQL

Standard `security-and-quality` queries plus a project-specific filter at
`.github/codeql/codeql-config.yml`:

- `java/spring-disabled-csrf-protection` is suppressed with a documented
  rationale (we use stateless JWT in the Authorization header; cookies are
  not used; the CSRF precondition does not hold).

---

## 12. Key design decisions, indexed

Per-slice ADRs live in `docs/specs/slice-<N>-architecture.md`. The headline
decisions across the codebase:

| # | Decision | Why | Where to read more |
|---|---|---|---|
| 1 | Layered architecture (web -> service -> persistence), enforced by ArchUnit | Keeps business logic out of HTTP layer; makes unit testing trivial | `docs/specs/slice-1-architecture.md` ADR-001/002 |
| 2 | JWT (HS256) instead of cookie-session | Stateless, scales horizontally, plays well with mobile clients later | `docs/specs/slice-1-architecture.md` |
| 3 | `ApiResponse<T>` envelope | Single response shape simplifies clients | Slice 1 spec section 5 |
| 4 | Keyset cursor pagination, not OFFSET | OFFSET is O(N) on large tables; cursor stays O(log N) regardless of page | Slice 1 architecture |
| 5 | Soft delete (`deleted_at`) | Reversible; preserves history for audit | Slice 1 spec |
| 6 | Three transaction types (INCOME / EXPENSE / SAVINGS) | Real personal finance needs the third category | Slice 5 |
| 7 | Categories denormalized into `transactions.category` (no FK) | Better UX for ad-hoc entry; categories table maintained separately for picker | Slice 6 spec |
| 8 | Recurring transactions materialise on list call, not via a scheduled job | Always-fresh without a cron; idempotent because `last_materialised_at` advances atomically | Slice 6 |
| 9 | CSV import accepts both ISO and BR date formats, dot or comma decimals | Real exports come in many shapes; defensive parsing | Slice 7 |
| 10 | Stateless JWTs for reset and verify, bound to a password-hash fingerprint (`pwdv` claim) | Single-use without server-side token storage; the next credential change invalidates them | Slice 8 architecture ADR-S8-02 |
| 11 | Anti-enumeration: forgot-password / resend always return 200, constant-time work | Hides whether an email is registered | Slice 8 |
| 12 | Tokens are NEVER logged; opt-in file delivery sink | Removes log access as an account-hijack vector | Slice 8 amendment A-1 |
| 13 | Email-change is two-step + typo-safe | Typos can't lock the user out; old email keeps working until the new one is verified | Slice 9 ADR-S9-01 |
| 14 | Account deletion is soft with a 30-day grace period | Forgiving UX; daily scheduled hard delete cleans up | Slice 9 ADR-S9-02 |
| 15 | Deletion gate in BOTH login and `JwtAuthenticationFilter` (return 403) | Existing sessions cannot outlive a deletion request | Slice 9 ADR-S9-03 |
| 16 | Reports aggregate in SQL via JPQL constructor projections (or in-memory for trends) | Push compute to the DB where possible; degrade to Java when JPQL portability is awkward | Slice 10 architecture ADR-S10-05 |
| 17 | PDF via OpenHTMLtoPDF + Thymeleaf | Apache 2.0; design in HTML/CSS; output identical across clients | Slice 10 ADR-S10-02 |
| 18 | Transactions CSV export uses the import format | Round-trip-safe; download / edit / re-import is a supported workflow | Slice 10 ADR-S10-03 |
| 19 | `MailService` abstraction with two implementations | Provider-agnostic; works with any SMTP server via Spring Mail | Slice 11 ADR-S11-01 |
| 20 | Mail failures swallowed at WARN | Preserves anti-enumeration; outbox / retry deferred | Slice 11 ADR-S11-02 |
| 21 | JSON logs gated by `prod` profile | Local dev keeps human-readable logs; prod ready for log aggregators | Slice 11 ADR-S11-04 |
| 22 | Non-root Docker (`lifeorg`, uid 10001) + tini + `MaxRAMPercentage=75` | Standard production hardening; respects cgroup limits | Slice 11 ADR-S11-05 |
| 23 | Per-user revocation epoch (`token_version` + `tv` claim) | Single-integer denylist; logout-everywhere with zero per-token state | Slice 12 ADR-S12-01 |
| 24 | Password change AND password reset both bump `token_version` | Credential rotation invalidates outstanding refresh tokens automatically | Slice 12 ADR-S12-03 |

---

## 13. How to extend the system

### Add a new endpoint

1. Decide the URL and method. Add a `@PostMapping` / `@GetMapping` method to
   the relevant controller (or create a new controller under `web/`).
2. The controller must call a service - never a repository directly
   (enforced by ArchUnit).
3. Add a DTO record under `web/dto/` for the request and response bodies.
4. Service method does the work; throws a domain exception on failure.
5. Add `@Valid` and Bean Validation constraints to the DTO so malformed
   input is rejected at the boundary.
6. Write an integration test under `src/test/java` extending
   `AbstractIntegrationTest`.
7. The OpenAPI spec at `/v3/api-docs` updates automatically. Add a `@Tag`
   on a new controller for nicer grouping in Swagger UI.

### Add a new database column

1. Create `V<N+1>__short_description.sql` in
   `src/main/resources/db/migration/`. Never edit an existing migration.
2. Add the field to the JPA entity.
3. Update DTOs that need to expose it.
4. Update `SchemaMigrationTest` to include the new migration number and any
   new column in the expected-columns assertion.

### Add a new vertical (e.g. health, diary)

Follow the slice template:

1. Write a spec in `docs/specs/slice-<N>-spec.txt` (decisions, endpoints,
   ACs, out of scope).
2. Write an architecture document with ADRs.
3. Create a new package `com.julio.lifeorganizer.<vertical>` with the same
   three-folder shape (`persistence`, `service`, `web`).
4. Add migrations.
5. Wire frontend pages under `features/<vertical>/`.
6. Add a sidebar entry.
7. PR + CI + merge.

### Add a new mail template

1. Add a new Thymeleaf template under `src/main/resources/templates/mail/`.
2. Add a method to `MailService`.
3. Implement it in both `FileMailService` and `SmtpMailService`.
4. The service that needs the link calls `mailService.sendXxx(...)`.

---

## 14. Risk register

Known risks the design accepts today. Each is documented somewhere; this
table is the catalog.

| # | Risk | Mitigation | Slice |
|---|------|-----------|---|
| R-1 | A leaked refresh token survives access-token expiry | Slice 12: `tv` revocation epoch; bump on password change, reset, logout-all | Slice 12 |
| R-2 | A leaked password-reset link is valid until expiry | 1h TTL + `pwdv` binding = single-use | Slice 8 |
| R-3 | An attacker can rate-limit-bypass via multiple IPs | Acceptable; the limiter is per-IP per-endpoint, not global. Real protection needs a CAPTCHA, deferred. | Slice 8 |
| R-4 | SMTP failures silently drop mails | Acceptable for personal use; WARN logged. Persistent outbox is a future slice. | Slice 11 |
| R-5 | The rate limiter state is per-JVM (resets on restart) | Acceptable for single-node deployment; revisit with Redis if scaling out | Slice 8 |
| R-6 | `.tmp/auth-dev-links.txt` is on the filesystem when dev-delivery is enabled | Off by default in prod (defense in depth); gitignored | Slice 8 / 11 |
| R-7 | CSRF is disabled | False positive for stateless JWT in `Authorization` header; documented + CodeQL-suppressed | Slice 1 / 8 |
| R-8 | Hard-delete job is non-idempotent if interrupted mid-transaction | One `@Transactional` per user = atomic; the next run picks up anything missed | Slice 9 |
| R-9 | Trends aggregation in Java (not SQL) could OOM on huge datasets | Personal-data scale only (tens of rows per category-month); cap top-categories to 5; daily array to 31 | Slice 10 |
| R-10 | No per-device session revocation - only revoke-everything | Acceptable; per-device session list is a future slice | Slice 12 |

Future slices currently on deck (in priority order):

- Observability (Micrometer / Prometheus / OpenTelemetry tracing)
- Persistent SMTP outbox + retry
- Per-device session list with granular revocation
- Refresh-token rotation
- New verticals (health, diary, reminders, goals)
