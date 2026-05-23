# Slice 9 - Implementation Plan

Phased so each phase ends with a runnable build and a meaningful commit.

| Phase | Scope | Verify |
|---|---|---|
| 1 | Migration V8 + UserEntity column + UserResponse field | `mvn test` green (existing tests still pass) |
| 2 | JwtService: change_email + account_restore token types with pwdv binding | New unit tests in `JwtServiceTest` |
| 3 | AccountService + AccountController: PATCH /me, /me/password, /me/email | Unit tests + smoke through MockMvc |
| 4 | AuthService + AuthController: confirm-email-change endpoint | Integration test covers happy path + pwdv-invalidation |
| 5 | AccountService: /me/delete + /me/restore; JwtAuthenticationFilter check; login check | Integration test covers the 403/401 paths |
| 6 | AuthController: confirm-account-restore (anonymous) | Integration test |
| 7 | AccountLifecycleJob: @Scheduled hard-delete job + unit test with fake Clock | Job test passes; manual run wipes correct rows |
| 8 | RateLimitFilter: add /me/email + /me/delete to LIMITED_PATHS | RateLimiterTest unchanged; smoke test |
| 9 | Frontend: PasswordResetService extended OR new AccountService; types | `npx ng build` clean |
| 10 | Frontend: /account page with 4 sections | Manual click-through |
| 11 | Frontend: /confirm-email-change, /restore-account, /deletion-pending pages + routes | Build + lint clean |
| 12 | Frontend: dashboard banner extension; login error mapping | Manual flow test |
| 13 | Full `mvn verify` + `npx ng build && npm test && npx eslint` | All green |
| 14 | PR + CodeQL clean | CI green, security review |

Each backend phase ends with `mvn verify`. Each frontend phase ends with
`npx ng build && npx eslint && npx jest`.

## Acceptance criteria coverage

| AC | Phase |
|---|---|
| AC-9-1, AC-9-2 (PATCH /me) | 3, 13 |
| AC-9-3, AC-9-4 (password change) | 3, 13 |
| AC-9-5..9 (email change flow) | 3, 4, 13 |
| AC-9-10..15 (delete + restore) | 5, 6, 13 |
| AC-9-16, AC-9-17 (scheduled hard delete) | 7 |
| AC-9-18 (rate limit) | 8 |
| AC-9-19 (frontend per-section forms) | 10, 13 |
| AC-9-20 (dashboard deletion banner) | 12, 13 |
