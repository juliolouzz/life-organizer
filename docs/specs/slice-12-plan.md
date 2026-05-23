# Slice 12 - Implementation Plan

| Phase | Scope | Verify |
|---|---|---|
| 1 | V9 migration + UserEntity.tokenVersion + bumpTokenVersion() | `mvn test` green |
| 2 | JwtService: add tv claim to all generate* methods | Unit tests for tv presence on each token type |
| 3 | AuthService.login / register / refresh: pass tv on issuance | mvn verify - existing tests still pass |
| 4 | JwtAuthenticationFilter: tv check (reuses existing user lookup) | Integration test: tampered tv -> 401 |
| 5 | AuthService.refresh: tv check on refresh | Integration test |
| 6 | AccountService.changePassword: auto-bump after persist | Integration test: old refresh token invalidated |
| 7 | AuthService.resetPassword: auto-bump after persist | Integration test |
| 8 | LogoutAllRequest DTO + AccountService.logoutAllSessions + AccountController endpoint | Integration test |
| 9 | RateLimitFilter: add /me/sessions/logout-all | smoke |
| 10 | Frontend: "Sign out of all devices" button on /profile (with confirmation dialog) | Manual click-through |
| 11 | SchemaMigrationTest update for V9 | Test green |
| 12 | mvn verify + ng build/lint/test + push + PR | All 4 CI checks green; no new CodeQL alerts |

## AC coverage

| AC | Phase |
|---|---|
| AC-12-1 (tv in all tokens) | 2, 3 |
| AC-12-2, AC-12-3 (logout-all happy + wrong password) | 8, 12 |
| AC-12-4 (stale access token rejected) | 4 |
| AC-12-5 (stale refresh token rejected) | 5 |
| AC-12-6 (password change auto-bump) | 6 |
| AC-12-7 (password reset auto-bump) | 7 |
| AC-12-8 (login/register tv matches) | 3 |
| AC-12-9 (deletion gate still works) | 4 (composition test) |
| AC-12-10 (rate limit) | 9 |
