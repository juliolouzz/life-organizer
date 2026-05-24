# Life Organizer - Build It Yourself Tutorial

This document is a step-by-step guide that takes someone who is **learning to
code** and walks them through building this entire project from scratch. It
explains *why* each decision was made, not only *what* to type, so you finish
the tutorial knowing the trade-offs, not just the keystrokes.

If you only need the deployment story, see [`docs/deployment.md`](docs/deployment.md).
For an as-built reference of the architecture, see [`PROJECT.md`](PROJECT.md).

---

## Table of contents

1. [Who this tutorial is for](#1-who-this-tutorial-is-for)
2. [What you will build](#2-what-you-will-build)
3. [Prerequisites](#3-prerequisites)
4. [Folder & project layout](#4-folder--project-layout)
5. [Slice 1 - REST API + JWT auth + Transactions CRUD](#5-slice-1---rest-api--jwt-auth--transactions-crud)
6. [Slice 2 - Angular 17 frontend](#6-slice-2---angular-17-frontend)
7. [Slice 3 - Dashboard insights](#7-slice-3---dashboard-insights)
8. [Slice 4 - Quick-add + full Docker stack](#8-slice-4---quick-add--full-docker-stack)
9. [Slice 5 - UX fixes + SAVINGS type](#9-slice-5---ux-fixes--savings-type)
10. [Slice 6 - Categories + budgets + recurring](#10-slice-6---categories--budgets--recurring)
11. [Slice 7 - CSV import](#11-slice-7---csv-import)
12. [Slice 8 - Auth completeness](#12-slice-8---auth-completeness)
13. [Slice 9 - Account management](#13-slice-9---account-management)
14. [Slice 10 - Reports & insights](#14-slice-10---reports--insights)
15. [Slice 11 - Operational hardening](#15-slice-11---operational-hardening)
16. [Slice 12 - Refresh-token revocation](#16-slice-12---refresh-token-revocation)
17. [Slice 13 - Per-user currency](#17-slice-13---per-user-currency)
18. [Slice 14 - Custom month boundary day](#18-slice-14---custom-month-boundary-day)
19. [Cross-cutting habits to build](#19-cross-cutting-habits-to-build)
20. [Troubleshooting reference](#20-troubleshooting-reference)
21. [Where to go next](#21-where-to-go-next)

---

## 1. Who this tutorial is for

You should already know:

- The basics of a programming language (variables, functions, types, classes).
- What HTTP is (requests, responses, status codes).
- How to open a terminal and run a command.

You do **not** need to know Java, Spring Boot, Angular, Docker, or PostgreSQL
yet. Each slice explains the concepts as they appear.

### The mental model

Throughout this tutorial, every slice follows the same rhythm:

1. **Decide** what you're building and *why*.
2. **Write the contract** before code (spec + acceptance criteria).
3. **Build the smallest thing** that satisfies one criterion.
4. **Add a test** that proves it.
5. **Refactor** without breaking the test.
6. **Repeat** until every criterion passes.

If you can internalise that rhythm, you're a software engineer. The rest is
just typing.

---

## 2. What you will build

A personal-finance app with:

- A **Java + Spring Boot** REST API.
- A **PostgreSQL** database with Flyway migrations.
- An **Angular 17** single-page frontend.
- **JWT** authentication, password reset, email verification, rate limiting,
  account deletion with a grace period, revocation epochs.
- **Reports** with CSV and PDF export.
- **Real email delivery** via SMTP and a fall-back dev sink.
- **Docker** images and a documented production deployment path.

By the end you'll have ~14k lines of production-quality code, 93 backend
tests, 34 frontend Jest tests, and a complete OpenAPI specification. Two
additional slices (per-user currency, custom month boundary day) and a
multi-round QA pass shipped on top — see [`CHANGELOG.md`](CHANGELOG.md).

---

## 3. Prerequisites

Install these once. Each is justified - we don't add tools we don't use.

| Tool | Why | Install command (macOS via Homebrew) |
|---|---|---|
| JDK 21+ | Java runtime for the backend | `brew install --cask temurin` |
| Maven 3.9+ | Build tool for Java | `brew install maven` |
| Node.js 20+ | Runtime for the frontend | `brew install node` |
| Docker Desktop | Containers for Postgres + production-like runs | install from docker.com |
| Git | Version control | `brew install git` |
| GitHub CLI (`gh`) | Talk to GitHub from the terminal | `brew install gh` |
| IntelliJ IDEA (Community) | Java IDE | `brew install --cask intellij-idea-ce` |
| VS Code | Frontend / docs IDE | `brew install --cask visual-studio-code` |

> Linux / Windows users: replace the Homebrew commands with the equivalents
> for your package manager (`apt`, `dnf`, `winget`, `choco`).

### Verify

```bash
java --version    # openjdk 21.x.x
mvn -v            # Apache Maven 3.9.x
node --version    # v20.x
docker --version  # Docker version 24+
```

If any of those fails, fix it before moving on. Don't keep going on a half-
configured environment - the time you save is illusory.

---

## 4. Folder & project layout

Clone the repo or create a folder named `Life_Organizer`. The layout you'll
end up with:

```
Life_Organizer/
├── pom.xml                       # Java/Maven config
├── Dockerfile                    # backend container
├── docker-compose.yml            # Postgres-only for local dev
├── docker-compose.full.yml       # full stack (backend + frontend + db)
├── .env.example, .env.docker.example, .env.prod.example
├── src/
│   ├── main/
│   │   ├── java/com/julio/lifeorganizer/
│   │   │   ├── LifeOrganizerApplication.java
│   │   │   ├── auth/ ...
│   │   │   ├── transactions/ ...
│   │   │   ├── categories/ ...
│   │   │   ├── budgets/ ...
│   │   │   ├── recurring/ ...
│   │   │   ├── insights/ ...
│   │   │   ├── reports/ ...
│   │   │   ├── mail/ ...
│   │   │   ├── common/ ...
│   │   │   └── config/ ...
│   │   └── resources/
│   │       ├── application.yml, application-prod.yml, ...
│   │       ├── db/migration/   # Flyway SQL files V1..V9
│   │       ├── templates/      # Thymeleaf (mail + PDF report)
│   │       └── logback-spring.xml
│   └── test/java/...
└── frontend/
    ├── package.json
    ├── angular.json
    └── src/app/...
```

You build this **incrementally** - one slice at a time. Don't try to scaffold
the whole thing up front; you'll get lost in choices that don't matter yet.

---

## 5. Slice 1 - REST API + JWT auth + Transactions CRUD

> Spec: [`docs/specs/slice-1-spec.txt`](docs/specs/slice-1-spec.txt) | Architecture: [`docs/specs/slice-1-architecture.md`](docs/specs/slice-1-architecture.md)

### Decisions, with reasoning

| Decision | Why |
|---|---|
| Java 21 + Spring Boot 3.3 | Most popular JVM web stack; Spring Boot handles HTTP, JSON, validation, DI, and DB wiring out of the box. Java 21 has records (concise DTOs), sealed types, pattern matching - all reduce boilerplate. |
| PostgreSQL | Open source, battle-tested, supports JSON + advanced indexes if we ever need them. Avoid MySQL idiosyncrasies. |
| Flyway for migrations | Versions every schema change as `V<N>__description.sql`. Runs on boot. Trivial to roll forward. |
| JWT auth (HS256) | Stateless: the API server doesn't need a sessions table. Symmetric key keeps key management simple - good fit for a single backend. |
| Bcrypt for password hashing | Slow-by-design, salted. Spring Security ships a ready encoder. **Never** SHA-256 + your own salt. |
| ApiResponse envelope | Every response is `{success, data, message, meta}`. Clients always know where to look. |
| Layered architecture (web -> service -> persistence) | Forces business logic out of controllers; lets us unit-test services without spinning up Spring. |
| Constructor injection only | Final fields, immutable. ArchUnit enforces it in a test. |

### Step-by-step build

1. **Scaffold the project** at [start.spring.io](https://start.spring.io):
   - Group: `com.julio`
   - Artifact: `life-organizer`
   - Java: 21
   - Dependencies: Spring Web, Spring Security, Spring Data JPA, Flyway, PostgreSQL Driver, Validation, Actuator.
   - Download, unzip, open in IntelliJ.

2. **Verify it boots**:
   ```bash
   ./mvnw spring-boot:run
   ```
   You'll see `Started LifeOrganizerApplication in X seconds`. Even with no
   endpoints, the framework is alive.

3. **Add Postgres via Docker Compose** (`docker-compose.yml`):
   ```yaml
   services:
     postgres:
       image: postgres:16-alpine
       environment:
         POSTGRES_DB: life_organizer
         POSTGRES_USER: life_organizer
         POSTGRES_PASSWORD: life_organizer
       ports: ["5432:5432"]
       volumes: [lifeorg_pgdata:/var/lib/postgresql/data]
   volumes:
     lifeorg_pgdata:
   ```
   `docker compose up -d postgres` starts a database in the background.

4. **Configure `application.yml`** to point at Postgres and enable Flyway.

5. **First migration** `V1__create_users_table.sql`:
   ```sql
   CREATE TABLE users (
     id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
     email VARCHAR(255) NOT NULL UNIQUE,
     password_hash VARCHAR(72) NOT NULL,
     display_name VARCHAR(100) NOT NULL,
     role VARCHAR(20) NOT NULL,
     created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
   );
   ```
   Boot the app; Flyway runs the migration; you have a `users` table.

6. **Build the layers** for users:
   - `UserEntity` (JPA `@Entity`, fields, no setters except via dedicated
     methods like `changeEmail`).
   - `UserRepository` (Spring Data interface extending `JpaRepository`).
   - `AuthService` (constructor-injected dependencies, business logic).
   - `AuthController` (Spring `@RestController`, calls only the service).
   - DTOs as Java records: `RegisterRequest`, `LoginRequest`, `UserResponse`,
     `AuthTokensResponse`.

7. **JWT signing** lives in `JwtService`. It reads `JWT_SECRET` from
   environment via a `@ConfigurationProperties` record. Refuse to boot if
   the secret is missing or shorter than 32 characters - fail fast.

8. **Spring Security setup** in `SecurityConfig`:
   - `csrf().disable()` because we use Bearer tokens, not cookies.
   - `sessionCreationPolicy(STATELESS)` for the same reason.
   - Custom `JwtAuthenticationFilter` reads the `Authorization: Bearer ...`
     header, parses the JWT, populates a `SecurityContextHolder` with an
     `AuthenticatedUser` principal.

9. **Add the transactions vertical** mirroring the auth layout
   (entity + repository + service + controller + DTOs). Same shape, different
   domain.

10. **Tests for every behaviour**:
    - Unit tests with `@ExtendWith(MockitoExtension.class)` for services.
    - `@WebMvcTest` for controllers (loads only the web layer).
    - `@DataJpaTest` with Testcontainers for repositories (real Postgres).
    - One full `@SpringBootTest` integration test that exercises register ->
      login -> create transaction.

11. **Commit in conventional format** (`feat: add user registration`),
    open a PR, merge to main. Repeat for each milestone.

### Why this slice is the longest

It builds the spine of everything else: HTTP envelope, auth, error
handling, test scaffolding, migrations. Every later slice builds on these
patterns - if you don't internalise them now you'll fight them later.

---

## 6. Slice 2 - Angular 17 frontend

> Spec: [`docs/specs/slice-2-spec.txt`](docs/specs/slice-2-spec.txt) | Architecture: [`docs/specs/slice-2-architecture.md`](docs/specs/slice-2-architecture.md)

### Decisions

| Decision | Why |
|---|---|
| Angular 17 | Strong opinions => fewer choices. RxJS, signals, dependency injection, lazy routes - all batteries included. Recommended pattern is now standalone components + signals, which we adopt. |
| Angular Material 17 | Polished Material 3 design system, accessible, fits a finance app's "trustworthy" tone. |
| TypeScript strict | Catches bugs at compile time. Required for any non-trivial frontend. |
| Tokens in localStorage (refresh) + memory (access) | Refresh token survives reload; access token is short-lived enough to keep in memory only. Trade-off between UX and XSS exposure. |
| HTTP interceptor for auth + refresh | Attach the Bearer token to every outgoing call; on 401, swap a fresh access token using the refresh token, retry once. |

### Step-by-step

1. **Generate the project** in the `frontend/` folder:
   ```bash
   npx @angular/cli@17 new frontend --routing --style=scss --standalone
   cd frontend
   ng add @angular/material
   ```

2. **Scaffold a `core/` folder** for cross-cutting code:
   - `core/auth/auth.service.ts` (login, register, refresh, currentUser
     signal).
   - `core/auth/auth.interceptor.ts` (adds Bearer header).
   - `core/auth/refresh.interceptor.ts` (catches 401, refreshes once, retries).
   - `core/auth/auth.guard.ts` (redirects unauthed users to /login).
   - `core/auth/anonymous.guard.ts` (redirects authed users away from /login).
   - `core/api/api-response.ts` matches the backend envelope.

3. **Scaffold a `features/` folder** by domain (`auth`, `transactions`).
   Each feature gets its own service (HTTP wrapper) and pages
   (`login.page.ts`, `transactions-list.page.ts`, etc.).

4. **Set up the route table** in `app.routes.ts`. Auth-only pages live under
   a parent route guarded by `authGuard`; the parent loads an `AppShell` with
   the sidenav.

5. **Build the UI screens** with Material components:
   - `<mat-form-field>` for inputs
   - `<mat-card>` for sections
   - `<mat-table>` for the transactions list
   - `<mat-snack-bar>` for non-blocking feedback

6. **Tests** with Jest + Angular's `TestBed`. Each service gets a small
   `HttpTestingController` test. Each page gets a render test.

7. **Build & commit**:
   ```bash
   npx ng build       # production bundle
   npm test           # Jest
   npx eslint src/    # linter
   ```

---

## 7. Slice 3 - Dashboard insights

> Spec: [`docs/specs/slice-3-spec.txt`](docs/specs/slice-3-spec.txt)

The dashboard pulls *aggregations* (sum by type, sum by category, daily
buckets) and renders them as cards + charts.

### Decisions

- Aggregate **in the database** via JPQL constructor projections, not in
  Java loops. SQL is built for this.
- Use **Chart.js** (via `ng2-charts`) for the bar chart and donut.
- A `PeriodSelectorComponent` is shared by every dashboard widget so
  changing the date range refetches everything.

### Build

1. **Backend**: add an `insights/` package mirroring the structure of
   transactions. Three JPQL queries on the existing transactions table:
   `SUM by type`, `SUM by category`, `SUM by day`.
2. **Frontend**: build leaf chart components (`StatCardComponent`,
   `IncomeExpenseChartComponent`, `CategoryDonutComponent`); compose them
   into `DashboardPage`.
3. Default the landing route to `/dashboard`.

The exercise here is **composition**: each leaf component takes its data as
an `@Input()`, the page wires them together.

---

## 8. Slice 4 - Quick-add + full Docker stack

### Decisions

- Add a quick-add **dialog** on the dashboard so users don't navigate away
  to log a transaction. Dialogs in Material are simple to wire up.
- Add a `docker-compose.full.yml` that builds *both* the backend image and
  a nginx-served frontend image, plus Postgres. One `docker compose up`
  boots the entire app.
- Nginx reverse-proxies `/api` to the backend container so the SPA and API
  share an origin (avoids CORS in production).

### Build

1. **`QuickAddDialogComponent`** opens via `MatDialog.open(...)`. On success
   it returns the new transaction; the dashboard inserts it locally without
   a refetch.
2. **`frontend/Dockerfile`** is a two-stage build: `node:20-alpine` for the
   Angular build, `nginx:alpine` to serve it.
3. **`nginx.conf`** in the frontend image proxies `/api/` to
   `http://backend:8080/api/`.
4. **`docker-compose.full.yml`** wires it all together.

---

## 9. Slice 5 - UX fixes + SAVINGS type

A small slice. Two themes:

### UX fixes
- Make the description field optional (some transactions have no useful
  description).
- Add cross-page nav (back to dashboard from transactions list, etc.).
- Replace the "no transactions" empty page with a friendly dialog that
  prompts the user to add one.

### SAVINGS as a third transaction type

The original schema had only INCOME and EXPENSE. Real finance has a third:
money set aside but not spent. Three implementation choices:

1. **Modify the CHECK constraint** to allow SAVINGS:
   ```sql
   ALTER TABLE transactions DROP CONSTRAINT transactions_type_check;
   ALTER TABLE transactions ADD CONSTRAINT transactions_type_check
     CHECK (type IN ('INCOME', 'EXPENSE', 'SAVINGS'));
   ```
2. **Add a `TransactionType.SAVINGS` enum value** in Java.
3. **Update the dashboard** to show savings as a third chart series + a
   dedicated stat card.

A new migration `V3__add_savings_type.sql` captures step 1; the JPA layer
adapts to the new enum value automatically.

---

## 10. Slice 6 - Categories + budgets + recurring

> Spec: [`docs/specs/slice-6-spec.txt`](docs/specs/slice-6-spec.txt)

Three features that share one theme: making transactions more useful
without sacrificing simplicity.

### Decisions

- **Categories** are denormalized: `transactions.category` is a string,
  not a foreign key. Reason: the user types categories on the fly; a
  separate categories table is *also* maintained (for the picker dropdown
  + auto-creation on CSV import) but transactions don't enforce the FK.
  Trade-off: data integrity is weaker, but UX is much smoother.
- **Budgets** are per (user, category, month, year). One row per cell of
  the matrix. Status is computed on demand.
- **Recurring transactions** are templates with a `next_due_date`. On
  every transactions-list call we **materialise** any rules whose
  `next_due_date <= today` into real transactions, then bump the date.

### Build

1. New migrations: `V4__create_categories_table.sql`,
   `V5__create_budgets_table.sql`, `V6__create_recurring_transactions_table.sql`.
2. Three new verticals (`categories/`, `budgets/`, `recurring/`) following
   the established layered architecture.
3. Frontend pages: `/categories`, `/budgets`, `/recurring`. The budgets
   page also surfaces a widget on the dashboard.

Pay attention to **transactional boundaries** in `RecurringMaterializer`:
materialise rules and advance dates in **one** transaction so a crash
doesn't double-create transactions on retry.

---

## 11. Slice 7 - CSV import

> Spec: [`docs/specs/slice-7-spec.txt`](docs/specs/slice-7-spec.txt)

The first feature that has to forgive sloppy input.

### Decisions

- Use **opencsv** for parsing (handles quoted fields, embedded commas,
  CR/LF inside cells correctly - don't roll your own).
- Accept **ISO dates** (`2025-06-01`) and **BR dates** (`01/06/2025`).
- Accept **dot** or **comma** as the decimal separator.
- **Per-row errors**: parse every row, collect failures, return an array
  of `{rowIndex, error}` objects alongside the count of successful imports.
- Auto-create missing categories so the user doesn't have to set them up first.

### Build

1. `CsvImportController` accepts `multipart/form-data` with a single `file` field.
2. `CsvImportService.parse()` reads line by line, validates each, accumulates
   results, and only commits the successful rows.
3. Frontend `/transactions/import` page with a file picker and a result
   table showing successes and failures.

This is a great slice to practice **defensive parsing**: every transformation
is a tiny function that returns an `Either<error, value>`-shape result. No
exceptions across module boundaries.

---

## 12. Slice 8 - Auth completeness

> Spec: [`docs/specs/slice-8-spec.txt`](docs/specs/slice-8-spec.txt) | Architecture: [`docs/specs/slice-8-architecture.md`](docs/specs/slice-8-architecture.md)

The first slice where security gets serious.

### Decisions

- **Stateless tokens** for password reset (1h TTL) and verify email (24h
  TTL). Each carries a `typ` claim distinguishing it from auth tokens.
- **Anti-enumeration**: `/forgot-password` and `/resend-verification` always
  return 200 with the same body shape regardless of whether the email exists.
- **Rate limiting** via an in-memory sliding-window limiter, 5 requests per
  15 minutes per IP per endpoint.
- **Email "delivery"** writes to a local file initially (Slice 11 wires
  real SMTP).
- **Password-fingerprint binding** (`pwdv` claim): a SHA-256 of the user's
  current password hash is embedded in reset tokens. Once the password
  changes, the fingerprint differs and the token is rejected. Makes reset
  tokens *de facto* single-use.

### Build

The security review during this slice introduced fixes that became
patterns later:

- `Referrer-Policy: no-referrer` so tokens in URLs don't leak via the
  `Referer` header.
- Frontend strips the token from the URL on read (`replaceUrl: true`).
- Backend logs only event metadata, never the token.

Read the spec amendments at the bottom of `slice-8-spec.txt` - they record
*why* the implementation deviates from the original spec. That's a useful
artifact when you wonder "why is it like this?".

---

## 13. Slice 9 - Account management

> Spec: [`docs/specs/slice-9-spec.txt`](docs/specs/slice-9-spec.txt) | Architecture: [`docs/specs/slice-9-architecture.md`](docs/specs/slice-9-architecture.md)

### Decisions worth highlighting

- **Email change is typo-safe**: a verification link goes to the *new*
  address; `users.email` only updates when the user clicks it. The old
  email keeps working until then.
- **Account deletion** is soft with a 30-day grace period. A scheduled
  job hard-deletes (recurring -> budgets -> transactions -> categories ->
  user, all in one transaction) when the deadline passes.
- **Two restore paths**: authenticated `/me/restore` and anonymous
  `/auth/confirm-account-restore` (the email link). The auth-path is
  exempt from the JwtAuthenticationFilter deletion gate so the user can
  cancel within the same session.

The implementation is repetitive: each operation follows the same
"verify password / generate token / log event / route through MailService"
shape. The interesting part is the **deletion gate** wiring across login,
the filter, and both restore paths.

---

## 14. Slice 10 - Reports & insights

> Spec: [`docs/specs/slice-10-spec.txt`](docs/specs/slice-10-spec.txt) | Architecture: [`docs/specs/slice-10-architecture.md`](docs/specs/slice-10-architecture.md)

A pure read-side feature - no schema changes.

### Decisions

- **OpenHTMLtoPDF + Thymeleaf** for PDFs. Apache 2.0 (iText 7 is AGPL or
  paid), and "design the report in HTML/CSS, then render" beats hand-rolling
  PDF primitives.
- **CSV format matches Slice 7's import format** for the transactions CSV.
  Round-trip safe: download, edit in Excel, re-import.
- **Trends aggregation is in-memory** (fetch rows in the window, group by
  year+month in Java) rather than SQL. PostgreSQL doesn't have a portable
  `year(date)` function in JPQL; in-memory is fine at personal-data volumes.

### Build

- New endpoints under `/api/v1/reports/`: three JSON (summary, yoy, trends)
  + three downloads (`.csv`, `.pdf`, `transactions.csv`).
- Frontend `/reports` page with Material tabs, lazy-loaded per tab.
- Add a "Download CSV" button to the transactions list.

---

## 15. Slice 11 - Operational hardening

> Spec: [`docs/specs/slice-11-spec.txt`](docs/specs/slice-11-spec.txt) | Architecture: [`docs/specs/slice-11-architecture.md`](docs/specs/slice-11-architecture.md)

Everything that makes the app shareable.

### Decisions

- **Pluggable MailService**: `app.mail.provider=file` (default) or `smtp`.
  SMTP uses Spring Mail's `JavaMailSender` so any SMTP-compliant provider
  works (Gmail, SendGrid, Mailgun, Mailtrap, SES, self-hosted Postfix). No
  vendor lock-in.
- **Mail failures are swallowed** and logged at WARN. The calling endpoint
  still returns its anti-enumeration success response.
- **Prod profile** (`application-prod.yml`) flips on SMTP, JSON logs, tighter
  Hikari pool, hidden error internals.
- **Structured JSON logs** via `logstash-logback-encoder`, gated by the
  prod profile.
- **Non-root Docker** image: alpine multi-stage, dedicated `lifeorg` user
  (uid 10001), tini as PID 1, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`
  so the JVM respects cgroup memory limits.
- **`.env.prod.example` + `docs/deployment.md`** cover Fly.io and generic
  VPS paths.

This slice doesn't add user-facing features; it makes the app
*operable* by someone other than you.

---

## 16. Slice 12 - Refresh-token revocation

> Spec: [`docs/specs/slice-12-spec.txt`](docs/specs/slice-12-spec.txt) | Architecture: [`docs/specs/slice-12-architecture.md`](docs/specs/slice-12-architecture.md)

### Decisions

- **Per-user revocation epoch** (`users.token_version`): single integer,
  every JWT carries a `tv` claim, mismatch = revoked. Constant DB footprint,
  no list-growth headaches.
- **Free piggyback**: `JwtAuthenticationFilter` already loaded the user
  (for the Slice 9 deletion gate); the `tv` check is one extra integer compare.
- **`POST /me/sessions/logout-all`** requires password confirmation,
  bumps the version, signs the calling session out too.
- **Auto-bump on credential rotation**: password change AND password reset
  both increment the version. A leaked refresh token cannot outlive a
  credential rotation - patterns we've seen in real breaches.

---

## 17. Slice 13 - Per-user currency

### Decisions

- **Display-only**, no FX conversion. Switching from BRL to EUR changes the
  symbol + number formatting; existing transaction amounts keep their stored
  numeric value. Real multi-currency would need a per-transaction currency
  column + FX-table — explicitly future work.
- **One source of truth on the frontend**: `AuthService` exposes
  `currencySymbol()` + `currencyLocale()` as computed signals derived from
  `currentUser().currency`. The `MoneyBrlPipe`, stat cards, chart tooltip /
  axis callbacks, donut tooltip, and every form prefix read those signals
  so changing currency in /profile propagates live without a reload.
- **Reactive in Chart.js callbacks** needs care: signal reads inside a
  callback Angular invokes later (outside its reactive context) are NOT
  tracked. The chart components hoist the reads to the top of
  `chartOptions()` so the dependency is registered before the callback is
  defined. Without this, the chart silently kept the old currency forever.
- **MoneyBrlPipe is `pure: false`** so a signal change re-triggers
  `transform()` even when the input amount hasn't changed. Pure pipes cache
  by input value, which would mask currency switches.
- **Backend** is minimal: V10 migration adds `users.currency VARCHAR(3) NOT
  NULL DEFAULT 'BRL'` + a CHECK, plus an optional `currency` field on
  `UpdateProfileRequest`. The string round-trips through `GET /me`.

### What you learn

- Why pure pipes break reactivity when the dependency lives outside the
  pipe's inputs.
- How to use signal-derived `computed` values to keep a global concern
  (currency, theme) reactive without an event-bus subscription.
- The trap of "code that reads a signal eventually but not during the
  effect/render that depends on it" — fix by hoisting the read.

---

## 18. Slice 14 - Custom month boundary day

### Decisions

- **Per-user "month start day"** (1-31). The user's accounting month
  starts on this day each calendar month. Drives the dashboard
  "This month" / "Last month" presets and the Budgets widget label.
- **Previous-anchor semantics**: today = 2026-05-15, boundary = 28
  → cycle = `[Apr 28, May 27]`. Today = 2026-05-28 → cycle =
  `[May 28, Jun 25]` (Jun 28 is Sunday → snaps to Fri Jun 26 → cycle
  ends day before next snap).
- **Weekend snap**: if the anchor lands on Sat or Sun, snap to the
  previous Friday. Applied to BOTH the cycle start AND the next cycle's
  start so cycles partition the timeline with no overlaps or gaps.
- **Day > last-day-of-month clamping**: 31 in February maps to Feb 28 / 29.
- **Backend** adds V11 migration: `users.month_boundary_day INTEGER NOT
  NULL DEFAULT 1` with `CHECK (1..31)`. Default 1 collapses to a regular
  calendar month — every existing user is backfilled with no behaviour
  change.
- **Frontend** keeps the calendar logic in pure functions
  (`monthRangeForBoundary`, `previousMonthRangeForBoundary`,
  `rangeForPresetWithBoundary` in `period.ts`) with a unit-test file. The
  page-level code just passes the user's value in.

### What you learn

- Modelling a domain concept (an "accounting cycle") that doesn't line up
  with the obvious primitive (calendar month).
- Why testing date math is annoying: leap days, weekend boundaries, month
  lengths, clamp-then-snap order. Pure functions + Jest cases keep it
  manageable.
- How to retrofit a per-user preference into UI that already shipped:
  default the new field to the value that preserves the old behaviour,
  let everything else pull from the same signal.

### QA pass (post-Slice-14)

After Slice 14 we ran a multi-round QA pass on the live Docker stack to
exercise every visible feature with at least one create / mutate /
verify cycle. It turned up nine real bugs across the auth flow, the
reports page, the recurring page, the transactions list, and the
dashboard. All were fixed TDD-first — the failing test or the live-app
reproduction was captured before the code change — and merged in PRs
33-37. See [`CHANGELOG.md`](CHANGELOG.md#unreleased--2026-05-24--post-slice-14-qa-hardening)
for the full list.

The biggest lesson from that pass:

- **Writing a signal from inside `effect()` throws NG0600 silently** in
  Angular 17. If you see "the page rendered but no HTTP request fired"
  with no logged error, suspect an effect that calls a method which
  internally `.set()`s a signal. Replace with `ngOnInit` + explicit
  handlers, OR (last resort) opt in with `{ allowSignalWrites: true }`.

---

## 19. Cross-cutting habits to build

These don't belong to any one slice but apply everywhere.

### Conventional commits

Every commit message looks like `<type>(<scope>): <imperative summary>`:

```
feat(auth): add JWT refresh endpoint
fix(transactions): correct daily bucket grouping for DST boundary
refactor(reports): extract CSV writer to dedicated service
docs(slice-8): amend decisions for security review fixes
test(account): add reset token reuse case
chore(deps): bump spring-boot to 3.3.5
```

Why: PRs read like a changelog. Releases write themselves.

### Test-driven micro-cycle

For each new method:

1. Write the test first. It should compile but fail (the method doesn't
   exist yet).
2. Implement the minimum to make it pass.
3. Refactor until the code is clean. Test still passes.

You'll resist this at first - "I'll just write the code and add a test
later." Try it for one slice. The bug count drops dramatically.

### One feature, one branch, one PR

Never do work on `main`. Always `git checkout -b feature/<slice-name>`,
push, open a PR via `gh pr create`, wait for CI, merge, delete the branch.
The branch-per-PR discipline keeps history clean and makes reverts trivial.

### Use the ApiResponse envelope religiously

Every endpoint, success or failure, returns the same envelope shape. The
frontend writes one error-handling function and reuses it everywhere.

### Never trust input at the boundary

Validate with Bean Validation (`@NotBlank`, `@Email`, `@Size`,
`@Pattern`). Trim and lowercase emails. Reject overly-large request
bodies. Rate-limit anonymous endpoints. The system boundary is the only
place where untrusted data exists; defend it once, and the rest of the
code can be trusting.

---

## 20. Troubleshooting reference

| Symptom | Likely cause | Fix |
|---|---|---|
| Backend won't boot, "JWT_SECRET environment variable is required" | You forgot `.env`. | `cp .env.docker.example .env && openssl rand -base64 48 \| pbcopy` to generate one. |
| Backend won't boot, "Could not connect to database" | Postgres isn't running. | `docker compose up -d postgres`. |
| Frontend `ng build` fails with "Cannot find name X" | Probably a forgotten import. | TypeScript will tell you the file and line; add the missing `import`. |
| Tests fail randomly, "Connection refused" | Testcontainers can't start a container. | Make sure Docker Desktop is running. |
| CodeQL flags CSRF disabled | False positive for stateless JWT APIs. | Already suppressed in `.github/codeql/codeql-config.yml` with documented rationale. |
| "I changed the schema and JPA complains" | You forgot a migration. | Add `V<N+1>__description.sql`; never edit a past migration. |
| 401 on an endpoint that worked yesterday | Your access token expired. | Refresh, or log in again. |
| 429 RATE_LIMITED on auth endpoints | You hit `/login` / `/forgot-password` more than 5 times in 15 min. | Wait, or in tests set `app.rate-limit.enabled=false`. |
| PG 500 "could not determine data type of parameter $N" | A JPQL `:foo IS NULL OR ...` against a nullable parameter. | Use `COALESCE(:foo, t.column)` so the column types the bind. See PR #36. |
| Component spec dies with "Cannot read 'ngModule' of null" | Jest config used the silent-typo `setupFilesAfterEach`. | The valid Jest 29 option is `setupFilesAfterEnv`. |
| Stat-card value never updates when parent passes new `[value]` | A `computed()` read a plain `@Input` (signals don't track non-signal property reads). | Replace with a method, or convert to signal-inputs (Angular 17+). |
| Reports / dashboard load but show no data | Possibly an `effect()` calling code that `.set()`s a signal — NG0600 throws silently. | Move the work to `ngOnInit` + explicit handlers, or set `allowSignalWrites: true` on the effect. |

---

## 21. Where to go next

After completing all 14 slices you've built a small but real product. The
README's "What's next" section lists candidates for future work:

- **Observability** (Micrometer / Prometheus / OpenTelemetry tracing).
- **Persistent outbox** for SMTP retries on transient failures.
- **Per-device session list** with granular revocation.
- **Other verticals** - health/fitness, diary, reminders, goals.

The **best** next move is to deploy what you have. Pick Fly.io, follow
[`docs/deployment.md`](docs/deployment.md), and put a real domain on
your app. The number of things you'll learn in that deploy is staggering:
TLS, DNS, secrets rotation, log shipping, deployment rollbacks. None of
which fit in a tutorial - but all of which you only learn by doing them.

Good luck.
