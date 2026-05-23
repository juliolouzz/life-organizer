# Slice 2 — Architecture (Frontend)

Concise companion to `slice-2-spec.txt`. Only the decisions that need a documented "why".

## ADR-F-001: Standalone components + signals over NgModules + RxJS state

**Decision**: every component is standalone; state held in `signal()`s; derived
state via `computed()`; effects via `effect()`. RxJS only at the HTTP boundary.

**Why**: NgModules were soft-deprecated in Angular 17. Signals removed the need
for `BehaviorSubject` + `async` pipe in 95% of cases. Less indirection, fewer
subscribe leaks.

**Consequences**: every test imports the standalone component directly; no
`declarations:` arrays. Use `TestBed.configureTestingModule({ imports: [Cmp] })`.

---

## ADR-F-002: Service-level signals, no global store

**Decision**: `AuthService` owns auth-related signals (`accessToken`,
`refreshToken`, `currentUser`). `TransactionsService` is stateless — pages own
their own list signals.

**Why**: NgRx is overkill for Slice 2's scope (one resource, one user). Sharing
list state across routes would create cache invalidation problems
(stale data after edit/delete from another tab). Pages re-fetch on entry.

**Consequences**: trade-off accepted: extra HTTP round-trip on route entry,
in exchange for zero state-sync bugs.

---

## ADR-F-003: Token storage — refresh in localStorage, access in memory

**Decision**: refresh token persisted to `localStorage`; access token kept only
in an in-memory signal.

**Alternatives considered**:
- both in memory → forces re-login on every page reload (bad UX)
- both in localStorage → access token survives close-tab → wider XSS window
- HttpOnly cookies → not possible: backend uses `Authorization: Bearer` header
  and there's no plan to change that

**Why this combination**: refresh token grants only the ability to mint short
access tokens; an XSS-stolen refresh would need the same window's network to
exfiltrate before the page closes. The short-lived access token is the more
sensitive credential; keeping it in memory means it's gone on tab close.

**Consequences**: documented XSS risk on the refresh token (R-F-1, accepted).
A Content-Security-Policy header would harden this further (deferred to prod
profile in backend).

---

## ADR-F-004: Refresh-on-401 interceptor with mutex

**Decision**: a functional `refreshInterceptor` catches 401, calls `/auth/refresh`
once, then retries the original request. A `BehaviorSubject<boolean>` mutex
ensures concurrent 401s queue behind a single refresh.

**Why**: stale tokens are the most common cause of 401 mid-session. Forcing
re-login is hostile UX. The mutex prevents N refreshes when a page loads N
requests in parallel.

**Failure path**: if refresh itself returns 401 (refresh expired or
USER_NOT_FOUND), the interceptor clears tokens and routes to `/login` via the
`Router`.

---

## ADR-F-005: Material 3 with custom seed palette

**Decision**: use `@angular/material@17`. Generate a custom palette via the
Material 3 colour role system with seed `#4F46E5` (indigo). Apply via Sass
mixins in `styles.scss`. Override default font to Inter (UI) + JetBrains Mono
(numerals).

**Why**: Material 3 gives a polished default and answers most UX questions out
of the box (datepicker, snackbar, dialog, table). Custom palette + custom font
prevents the generic Material look.

**Consequences**: locked into Material's component API. Migrating away later
means a rewrite of every page. Accepted: Slice 2 is exploratory; a future slice
could replace components without breaking the API contract.

---

## ADR-F-006: Reactive Forms, no template-driven

**Decision**: every form is `FormBuilder.group({...})` with explicit `Validators`.
Server errors flow into the form via `setErrors({ server: 'msg' })` on the
relevant control.

**Why**: template-driven forms make it harder to drive validation programmatically
and harder to test. Reactive forms compose with signals (we can `signal(form.value)`
where needed).

---

## ADR-F-007: Lazy-loaded routes via standalone APIs

**Decision**: every feature route declares its component via
`loadComponent: () => import('./...').then(m => m.Page)`.

**Why**: cold start matters more than tree-shaking on a SPA; lazy routes keep
the initial bundle small. Standalone APIs make it trivial — no module to load.

---

## ADR-F-008: Jest over Karma+Jasmine

**Decision**: replace Angular CLI's default Karma + Jasmine with Jest via
`jest-preset-angular`.

**Why**: faster (no browser launch), better watch mode, broader ecosystem,
matches what the user has used for TypeScript projects per CLAUDE.md
(`typescript/testing.md` cites Jest).

**Consequences**: small upfront config cost (`jest.config.ts`, `setup-jest.ts`).
Angular Material components require `noAnimationsModule` in tests.

---

## ADR-F-009: Playwright over Cypress for E2E

**Decision**: Playwright is the E2E framework.

**Why**: faster on macOS, native cross-browser, better debugging, no flake
attributable to Cypress's iframe sandbox. Matches CLAUDE.md `common/agents.md`
(`e2e-runner` agent uses Playwright).

---

## ADR-F-010: Dev-time CORS allowed by backend; prod same-origin

**Decision**: backend `application-local.yml` adds a CORS allowed origin
`http://localhost:4200`. Production deployment serves the built frontend from
the same domain as the API, so CORS isn't needed there.

**Why**: dev proxy via `proxy.conf.json` is another option but adds an Angular-
CLI-specific layer. Explicit backend CORS is simpler and clearer.

---

## Package tree

```
frontend/
├── angular.json
├── package.json
├── tsconfig.json + tsconfig.app.json + tsconfig.spec.json
├── proxy.conf.json                 (dev-time / fallback)
├── jest.config.ts
├── playwright.config.ts
├── e2e/
│   └── full-flow.spec.ts
├── src/
│   ├── main.ts
│   ├── index.html
│   ├── styles.scss                 (Material 3 theme + global tokens)
│   ├── environments/
│   │   ├── environment.ts
│   │   └── environment.production.ts
│   └── app/
│       ├── app.component.ts        (shell + router-outlet)
│       ├── app.config.ts           (provideRouter, provideHttpClient,
│       │                            interceptors, animations)
│       ├── app.routes.ts
│       ├── core/
│       │   ├── api/
│       │   │   ├── api-response.ts
│       │   │   ├── page-meta.ts
│       │   │   └── error-codes.ts
│       │   ├── auth/
│       │   │   ├── auth.service.ts
│       │   │   ├── authenticated-user.ts
│       │   │   ├── auth.guard.ts
│       │   │   ├── anonymous.guard.ts
│       │   │   ├── auth.interceptor.ts
│       │   │   ├── refresh.interceptor.ts
│       │   │   └── token-store.ts            (localStorage wrapper)
│       │   ├── http/
│       │   │   ├── error.interceptor.ts
│       │   │   └── loading.interceptor.ts
│       │   ├── theme/
│       │   │   └── theme.service.ts
│       │   └── ui/
│       │       └── loading.service.ts
│       ├── shared/
│       │   ├── components/
│       │   │   ├── empty-state/
│       │   │   ├── confirm-dialog/
│       │   │   └── page-header/
│       │   └── pipes/
│       │       └── currency-brl.pipe.ts
│       ├── layout/
│       │   ├── app-shell.component.ts        (sidenav + topbar)
│       │   └── topbar.component.ts
│       └── features/
│           ├── auth/
│           │   ├── login/
│           │   │   ├── login.page.ts
│           │   │   ├── login.page.html
│           │   │   └── login.page.scss
│           │   └── register/
│           │       └── ...
│           ├── transactions/
│           │   ├── list/
│           │   │   ├── transactions-list.page.ts
│           │   │   ├── transactions-list.page.html
│           │   │   └── transactions-list.page.scss
│           │   ├── form/
│           │   │   └── ...
│           │   └── transactions.service.ts
│           ├── profile/
│           │   └── profile.page.ts
│           └── not-found/
│               └── not-found.page.ts
```

---

## Risk register

| ID | Risk | Mitigation |
|---|---|---|
| R-F-1 | XSS exfiltrates refresh token from localStorage | Strict CSP in prod backend (Slice 3 polish); document accepted risk |
| R-F-2 | Concurrent 401s trigger N refreshes | mutex in `refreshInterceptor` (ADR-F-004) |
| R-F-3 | Material 3 component API instability across minor versions | Pin `@angular/material` exact version in package.json |
| R-F-4 | Dark mode flash on first paint | apply theme class from inline script in index.html before app bootstraps |
| R-F-5 | Cursor encoding mismatch between client and backend | Treat cursor as opaque string — never decode or construct on the client |
| R-F-6 | Sensitive data in console.log accidentally committed | ESLint rule `no-console: error`, enforced by CI |

---

## Build & dev commands

```bash
cd frontend
npm install
npm run start            # ng serve on :4200, proxy.conf.json forwards /api -> :8080
npm run build            # produces frontend/dist/
npm test                 # jest unit tests
npm run test:watch
npm run e2e              # playwright (assumes backend on :8080)
npm run lint
```
