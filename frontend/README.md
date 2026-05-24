# Life Organizer — Frontend

Angular 17 + TypeScript (strict) + Angular Material 17 (Material 3 theming).
Standalone components and signals throughout; Reactive Forms for inputs;
Jest 29 for unit testing; Playwright for e2e.

See the top-level [`README.md`](../README.md) for the project overview and
the docker / dev-mode boot instructions. This file is the day-to-day
frontend reference.

## Dev server

```bash
npm ci --legacy-peer-deps     # one-time
npm start                     # ng serve on http://localhost:4200
```

`proxy.conf.json` forwards `/api/*` to `http://localhost:8080`, so a backend
running locally (or via `docker compose up -d postgres backend`) is enough.

## Useful scripts

| Command | What it does |
|---|---|
| `npm start` | `ng serve` (dev server with HMR) |
| `npm run build` | Production bundle to `dist/frontend/` |
| `npm test` | Run all Jest unit specs (34 tests today) |
| `npm run lint` | ESLint on `src/**/*.{ts,html}` |
| `npm run e2e` | Playwright e2e (needs a backend on `:8080`) |

## Architecture cheat sheet

- `app.config.ts` provides the router, HTTP client, all interceptors
  (auth → refresh → error → loading), Material animations, and an
  `APP_INITIALIZER` that calls `AuthService.bootstrap()` so a hard reload
  with a valid refresh token doesn't log the user out.
- `core/auth/` — `AuthService`, `TokenStore`, `authGuard` (preserves
  `returnUrl`), `anonymousGuard`, two interceptors (attach bearer +
  retry on 401 via refresh).
- `core/http/` — global error toaster + loading-bar interceptors.
- `core/theme/` — dark / light toggle, persisted to localStorage.
- `features/` — one folder per page (auth, dashboard, transactions,
  budgets, recurring, categories, reports, profile, not-found).
- `shared/components/` — page-header, empty-state, confirm-dialog,
  banners.
- `shared/pipes/` — `MoneyBrlPipe` (currency-aware, impure so it
  re-runs on signal change).

## Conventions

- TypeScript strict mode. Inputs typed as signals where new code is
  added; legacy components migrate to signal inputs as touched.
- OnPush change detection on every component (`changeDetection: ChangeDetectionStrategy.OnPush`).
- Reactive forms (`FormBuilder.nonNullable.group`), never template forms.
- Money is rendered via `MoneyBrlPipe` — never raw `toFixed(2)`. The
  pipe reads `AuthService.currencySymbol()` + `currencyLocale()` so a
  currency change in /profile propagates without a reload.
- Signal reads inside async callbacks (Chart.js, RxJS subscribes) are NOT
  tracked by Angular's reactive system. Hoist the read to the top of
  the enclosing function so the dependency is registered before the
  callback is defined.

## Tests

```bash
npm test                                                  # all specs
npx jest src/app/features/dashboard/period.spec.ts        # one file
npx jest -t "updates the rendered amount"                 # by name
```

Jest config: `jest.config.js` + `setup-jest.ts`. The `setupFilesAfterEnv`
hook wires up the Angular TestBed.
