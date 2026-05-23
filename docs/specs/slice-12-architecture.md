# Slice 12 - Architecture

Companion to `slice-12-spec.txt`. Records the design choices before code.

## ADRs

### ADR-S12-01 - Per-user epoch, not per-token denylist

`users.token_version` is a single integer that increments by 1 on each
revocation event. Every JWT carries a `tv` claim equal to the user's
`token_version` at issuance time. On every authenticated request, the
filter compares them; mismatch == invalid.

**Why an epoch instead of a denylist:**
- Constant DB footprint (one column, no list growth).
- No background cleanup ("when can I forget this revoked id?").
- Logout-everywhere is a single `UPDATE users SET token_version = ...`.
- Plays cleanly with stateless JWTs - we never need to look up a token
  by id; just compare a number.

**Trade-off:** there is no "log out one device only." The unit of
revocation is the user, not the session. Acceptable for a personal app
and listed in OUT OF SCOPE.

### ADR-S12-02 - tv check piggybacks on the existing JwtAuthenticationFilter DB hit

The filter already loads the user from `UserRepository.findById` for
the deletion-pending gate (Slice 9 ADR-S9-03). Adding a `tv` comparison
to that same lookup is essentially free - one extra integer compare.
No new query, no extra round-trip.

### ADR-S12-03 - Password reset and password change auto-bump; restores do not

Password reset (link-driven) and password change (in-app) both indicate
the user has just rotated their credentials. A leaked refresh token
that survives this is a known breach pattern; bumping `token_version`
on both paths closes the gap without requiring the user to remember to
click another button.

Account-restore intentionally does NOT bump. The deletion-pending gate
already invalidated existing sessions while the account was scheduled
for deletion. Once restored, the gate is off and any session that
existed before deletion was already locked out for the gate's whole
window. Bumping again would force an unnecessary re-login on the device
that just restored.

Email-change confirmation does not bump either - the user's credentials
(password) are unchanged, only the identifier.

### ADR-S12-04 - logout-all requires password confirmation

The endpoint takes `{ "password": "..." }` and verifies it before
incrementing. Matches the discipline of `/me/delete`: a stolen access
token alone must not be able to log the legitimate user out everywhere.

## Package layout delta

```
com.julio.lifeorganizer
└── auth
    ├── persistence
    │   └── UserEntity.java        [+ token_version field + bumpTokenVersion()]
    ├── security
    │   └── JwtAuthenticationFilter.java  [+ tv claim check]
    ├── service
    │   ├── AccountService.java    [+ logoutAllSessions(...); changePassword bumps tv]
    │   ├── AuthService.java       [+ refresh tv check; login/register/refresh pass tv; resetPassword bumps tv]
    │   └── JwtService.java        [+ generate*WithTokenVersion overloads or a single tv parameter]
    └── web
        ├── AccountController.java [+ POST /me/sessions/logout-all]
        └── dto
            └── LogoutAllRequest.java     (new - { password })
```

## Migration

```sql
-- V9__users_add_token_version.sql
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
```

Existing rows start at 0; first issued token after deploy has tv=0,
matching. No backfill needed.

## JwtService API shape

The simplest change: add a `tokenVersion` parameter to every `generate*`
method. Callers pass `user.getTokenVersion()`. The new `TV_CLAIM` constant
lives next to `PWDV_CLAIM` in JwtService.

For parsers / verifiers, the tv comparison stays in AuthService /
JwtAuthenticationFilter (where the User row is already loaded). JwtService
just embeds the claim - it doesn't know what value is current.

## Filter chain (no change in order; one extra check inside)

```
HTTP request
   v
CorsFilter
   v
RateLimitFilter             (now also covers /me/sessions/logout-all)
   v
JwtAuthenticationFilter     (deletion gate AND tv check; same DB hit)
   v
Controller
```

## Test plan

- `JwtServiceTest` (existing) - extend with tv-claim assertions.
- `AccountManagementIntegrationTest` (existing) - add cases:
  - logout-all bumps token_version
  - subsequent request with same access token returns 401
  - subsequent refresh with same refresh token returns 401
  - changePassword auto-bumps + invalidates prior refresh token
  - wrong-password on logout-all returns 401 + no bump
- `AuthCompletenessIntegrationTest` (existing) - add:
  - resetPassword auto-bumps + invalidates prior refresh token

## Risks accepted

| # | Risk | Mitigation |
|---|------|-----------|
| R-S12-1 | Filter does a DB lookup on every authenticated request | Already does (deletion gate). Adding tv check is free. |
| R-S12-2 | "Sign out everywhere" includes the current device by design | Documented; UX flow logs the user out + redirects to /login. |
| R-S12-3 | No partial revocation (one device at a time) | Spec accepts it; per-device sessions are a future slice. |
| R-S12-4 | Refresh-token-rotation is still not implemented | Independent improvement; deferred. |
