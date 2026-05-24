# Changelog

All notable changes to this project. Format inspired by Keep a Changelog;
versions follow semantic versioning.

## [Unreleased] — 2026-05-24 — Post-Slice-14 QA hardening

Multi-round QA pass on the running Docker stack. Every visible feature
exercised through at least one create / mutate / verify path; 9 real
bugs surfaced and fixed TDD-first with live confirmation. CI green on
every PR.

### Fixed

- **Login lost the originally requested URL** (#34). `authGuard` now
  redirects anonymous users with `?returnUrl=<original>`; LoginPage
  honours the query param via `navigateByUrl` and defaults to
  `/dashboard` for direct logins. Guard suppresses returnUrl when the
  target IS `/login` (no self-loop).
- **Hard reload / direct-URL navigation logged the user out** (#34).
  Access tokens live in memory only; a full page load wiped the token
  and the guard bounced to `/login` even with a valid refresh token in
  localStorage. Added an `APP_INITIALIZER` that awaits
  `AuthService.bootstrap()` before any route resolves.
- **Budgets page hardcoded `R$` prefix** regardless of currency (#34).
  Replaced with the `currencySymbol()` signal pattern used everywhere
  else.
- **Reports page rendered every tab empty** (#35). The lazy-load was
  wired inside `effect(() => loadActive(...))`; the call wrote signals
  which Angular 17 forbids with NG0600, so the chain threw silently and
  no HTTP request fired. Replaced with explicit `ngOnInit` +
  tab-setter dispatch; no signal writes from inside an effect anywhere
  on the page.
- **Recurring page category dropdown showed every kind** (#35).
  Selecting Expense still listed INCOME-only `salary`. Added an
  `eligibleCategories` computed driven by `toSignal(type.valueChanges)`
  that filters to `kind === type || kind === 'BOTH'`. Stale
  `categoryId` clears on toggle so the form can't submit a no-longer-
  eligible selection.
- **Transactions list filter returned HTTP 500** when from/to were
  set (#36). Repository JPQL used
  `(:from IS NULL OR t.transactionDate >= :from)`; Postgres refused
  the prepared statement (SQLState 42P18 — could not determine
  parameter type). Rewritten as
  `t.transactionDate >= COALESCE(:from, t.transactionDate)` so the
  column types the bind. Applied to both `findFirstPage` and the
  keyset-pagination `findPageAfterCursor`. Three new integration
  tests pin the contract.
- **Transactions list ignored `?from=` / `?to=` URL params** (#37).
  `ngOnInit` now reads `route.snapshot.queryParamMap` and patches the
  filter form with a local-midnight YYYY-MM-DD parser before the
  initial fetch (no double fetch).

### Tests added during QA

- Backend integration tests for the tx-list filter (3 new in
  `TransactionFlowIntegrationTest`).
- Jest specs for `authGuard` (3), `StatCardComponent` (5),
  `TransactionsListPage` query-param parser (3).
- Backend: **93 tests pass** (was 90).
- Frontend Jest: **34 tests pass** (was 23).

### Notes

- `setupFilesAfterEach` in `jest.config.js` was a silent typo; the
  correct Jest 29 option is `setupFilesAfterEnv`. Renamed so
  component specs using `TestBed.createComponent` no longer fail with
  "Cannot read 'ngModule' of null".

## [0.14.0] — 2026-05-24 — Slice 14: Custom month boundary day

### Added

- **Per-user month start day** (1-31). The day each calendar month
  the user's accounting cycle starts on. Drives the dashboard
  "This month" / "Last month" presets and the Budgets widget label.
- **Previous-anchor semantics**: today = 2026-05-15, boundary = 28
  → cycle = [Apr 28, May 27]; today = 2026-05-28, boundary = 28
  → cycle = [May 28, Jun 25] (Jun 28 is Sunday, snaps to Fri
  Jun 26, cycle ends day before the next snap).
- **Weekend snap**: if the anchor lands on Sat or Sun, the cycle
  starts on the previous Friday. Applied to BOTH the cycle start
  and the next cycle's start so cycles partition the timeline
  with no overlaps or gaps.
- **Day > last-day-of-month clamping**: day = 31 in February maps
  to Feb 28 / 29 automatically.
- **Backend**: V11 migration adds `users.month_boundary_day INTEGER
  NOT NULL DEFAULT 1` with `CHECK (1..31)`. `UpdateProfileRequest`
  validates 1-31. `GET /me` + `PATCH /me` surface it.
- **Frontend**: number input on /profile with concrete bolded
  examples. Period helper `monthRangeForBoundary` + drop-in
  `rangeForPresetWithBoundary` used by the dashboard and the
  budgets widget label.

### Fixed (Slice 14 follow-ups)

- Dashboard totals semantics aligned to the user's mental model
  (#33): Net = Income − (EXPENSE + SAVINGS); the Expenses card
  includes SAVINGS rows; the Saved card stays as the savings
  subset.
- Stat-card amount didn't update when the parent passed a new
  `[value]` (#33). The `rendered` was a `computed()` reading a
  non-signal `@Input`; replaced with a method so it re-runs on
  every change-detection pass. Regression test in
  `stat-card.component.spec.ts`.
- Custom-range race condition (#32). Dashboard fetches now flow
  through a single `Subject + switchMap` so a new range cancels
  any in-flight forkJoin; stat cards, chart, donut, recent list
  can no longer be from two different fetches.
- Hover tooltips on Net / Income / Expenses / Saved cards with
  2 s delay (#31).
- Profile "Month boundary day" hint moved out of the cramped
  mat-hint subscript area into a dedicated `.field-help`
  paragraph; label renamed to "Month start day" (#31).

## [0.13.0] — 2026-05-24 — Slice 13: Per-user currency

### Added

- **Currency selector** on /profile: BRL (R$), USD ($), EUR (€).
  Display-only — no FX conversion; existing transactions keep
  their stored amount.
- **AuthService** exposes `currencySymbol()` + `currencyLocale()`
  computed signals; the rest of the app (`MoneyBrlPipe`, stat
  cards, chart tooltips and axes, donut tooltip, transaction /
  recurring / budget form prefixes) reads from those signals so a
  currency change in /profile propagates live without a reload.
- **Backend**: V10 migration adds `users.currency VARCHAR(3) NOT
  NULL DEFAULT 'BRL'` with a CHECK constraint. `UpdateProfileRequest`
  accepts an optional `currency` field; `GET /me` returns it.

### Fixed (Slice 13 follow-ups)

- `MoneyBrlPipe` made currency-aware and `pure: false` so it
  re-runs when the signed-in user's currency changes (#28).
- Chart components hoist `currencySymbol()` / `currencyLocale()`
  reads to the top of `chartOptions()` so Angular's signal tracker
  picks the dependency up — without this the values were read
  inside Chart.js callbacks (outside Angular's reactive context)
  and the chart never repainted on currency change (#28).
- Budgets / recurring widgets refresh their symbol the same way
  (#23).
- CORS `allowedMethods` includes PATCH so `PATCH /me` no longer
  returns 403 to browsers (#27).
- Mail health probe disabled when SMTP is unconfigured (#26).
- Profile updates feed the PATCH response straight into
  `setCurrentUser()` instead of relying on a follow-up `GET /me`,
  so the UI reformats on the same change-detection tick (#26).
- Local Spring profile honours `DB_URL` / `APP_CORS_ALLOWED_ORIGINS`
  env vars so the same profile name works in Docker (#25).
- Email verification is now optional behind
  `app.auth.email-verification.enabled` (#24). Defaults to
  `false` for self-host single-operator deployments and `true` for
  `prod`; auto-marks new users verified at register and skips the
  verification email.
- Dashboard "Spending by category" donut + section spacing fixes
  (#29); recurring page's Expense / Income / Savings frame no
  longer stretches edge-to-edge.
- CSV import auto-detects a **bank-statement format**
  (`Date, Details, Debit, Credit, Balance`) in addition to the
  native shape (#29). Debit rows become expenses, credit rows
  become income, balance is ignored, opening-balance rows
  (no debit / no credit) are silently skipped. Bank rows land
  under an auto-created `Uncategorized` category.

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
