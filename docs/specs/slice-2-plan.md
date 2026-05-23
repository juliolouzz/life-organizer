# Slice 2 — Implementation Plan

Each phase is one mergeable commit (or a small batch). TDD where it adds value:
service tests + interceptor tests + critical form validators. Visual components
are validated by Playwright E2E.

| Phase | Scope | Deliverable |
|---|---|---|
| 0 | Backend CORS for dev | One commit on the backend allowing http://localhost:4200 in local profile |
| 1 | Angular scaffold + Material 3 theme | `frontend/` boots a blank themed shell on `:4200` |
| 2 | Core: ApiResponse types, env config, TokenStore, AuthService skeleton | Compile-only, unit tests for TokenStore + ApiResponse parsing |
| 3 | HTTP interceptors (auth + refresh + error + loading) | Unit tests with HttpTestingController |
| 4 | Routing + guards + app shell layout | App shell renders, guards redirect correctly |
| 5 | Login + Register pages | Reactive forms with validation, success path, error path |
| 6 | Profile page + logout flow | Read /me, logout clears tokens |
| 7 | Transactions list page | Material table, cursor pagination, filters, empty state |
| 8 | Transaction form page (create + edit) | Unified component, MatDatepicker, type toggle, validators |
| 9 | Theme service + dark mode toggle | Material 3 light/dark, persisted preference, no FOUC |
| 10 | Polish: snackbar, loading bar, animations, empty states | Final UI sweep |
| 11 | Tests: jest unit + Playwright E2E full flow | 70%+ coverage on services + components |
| 12 | CI: extend ci.yml with frontend job | Lint + test + build in CI |
| 13 | Docs polish + merge slice-2 to main | README + CLAUDE.md updated; tag v0.2.0 |

Each phase ends with `npm run lint && npm test` green; phases 7–10 also smoke-checked manually against a live backend.

## Acceptance criteria coverage

| AC | Phase |
|---|---|
| F-A1..F-A12 (auth flow) | 4, 5, 6 |
| F-T1..F-T14 (transactions) | 7, 8 |
| F-X1..F-X4 (theme + loading + errors) | 9, 10 |
| F-X5..F-X8 (no console, no any, reactive forms, lazy routes) | enforced by lint config in phase 1 |
| F-X9 (bundle < 500 KB) | verified in phase 11 / 12 |
| F-X10 (Lighthouse a11y >= 95) | verified manually + Playwright accessibility checks in phase 11 |
| F-X11 (keyboard a11y) | Material components handle most; verified manually |
| F-X12 (Playwright E2E full flow) | phase 11 |
