# Slice 9 - Architecture

Companion to `slice-9-spec.txt`. Captures the design decisions before
implementation so the code can be reviewed against the intent.

## ADRs

### ADR-S9-01 - Two-step email change via stateless JWT

The email-change flow is intentionally typo-safe: the new address is NOT
written to `users.email` at request time. Instead the new address is embedded
in a `typ="change_email"` JWT (with the pwdv binding from Slice 8) sent to the
new address. Only on click does the change apply.

**Why JWT instead of a `pending_email` column:** Slice 8 already established
the stateless-token pattern (`password_reset`, `verify_email`). Reusing it
keeps the schema flat and avoids the "what if the user starts a second change
before the first resolves" edge case (the latest token always wins; older ones
keep working until they expire, which is fine because the user controls both).

**Pwdv binding:** As with reset tokens, a SHA-256 fingerprint of the current
password hash is in the claim. If the user changes their password between
request and confirm, the change_email token is silently invalidated. Defense
against a leaked link.

### ADR-S9-02 - Soft delete with grace period; hard delete via @Scheduled job

`users.deletion_scheduled_at TIMESTAMPTZ NULL` is the single source of truth
for deletion state. NULL = active, future = pending deletion, past = should
be hard-deleted (the scheduled job will pick it up on its next run).

Two restore paths:

1. **Authenticated** - `POST /api/v1/me/restore` for the case where the user
   still has a valid access token from before the delete request (window is
   the access TTL = 15 minutes). Fast path - no email needed.
2. **Anonymous** - `POST /api/v1/auth/confirm-account-restore` with a JWT
   token sent to the user's email. Works for the entire 30-day grace period,
   even after every access / refresh token has expired.

**Hard delete is explicit DELETEs in a single transaction**, not CASCADE.
Auditing what got removed is much easier when each table is named in the log.

```java
@Scheduled(cron = "0 0 3 * * *", zone = "UTC")
@Transactional
public void runHardDelete() { ... }
```

Daily at 03:00 UTC. Stateless - the next run picks up anything missed by a
crash.

### ADR-S9-03 - Login + JwtAuthenticationFilter both check deletion_scheduled_at

A user with deletion pending must not be able to use the app, even if their
access token is still valid. Two checkpoints:

1. **Login** - reject with 403 `ACCOUNT_DELETION_PENDING` so the frontend can
   show the deletion date + restore-link reminder.
2. **JwtAuthenticationFilter** - on every authenticated request, load the user
   (already loaded today) and reject with 401 `ACCOUNT_DELETION_PENDING` if
   deletion is set and in the future.

The filter check is the important one: without it, an attacker with a stolen
access token could keep using the account until the access TTL elapses.

### ADR-S9-04 - Sensitive ops require current password; display name does not

| Operation | Password required? |
|-----------|---------------------|
| Change display name | No |
| Change password | Yes (currentPassword) |
| Change email | Yes (currentPassword) |
| Delete account | Yes (password) |

This matches user expectation (every banking and identity provider does the
same) and ensures a stolen access token alone cannot lock the legitimate user
out of their account.

### ADR-S9-05 - Rate limit extension to the two riskiest /me/* endpoints

`/me/email` and `/me/delete` are added to `RateLimitFilter`. The other /me/*
endpoints are not throttled directly - they require a valid access token,
and login is already throttled, so the upstream throttle bounds them too.

### ADR-S9-06 - No notification to the old email on email change

We have no SMTP. Once SMTP lands in the operational slice, a one-line
notification to the old address ("your email is being changed to X - if
this was not you, click here") becomes a 1-day addition. Today this would
just write more lines to the dev-delivery file, which has no user-facing
value.

## Package layout (delta from Slice 8)

```
com.julio.lifeorganizer
├── auth
│   ├── service
│   │   ├── AccountService.java       (new - all /me write ops)
│   │   ├── AccountLifecycleJob.java  (new - @Scheduled hard delete)
│   │   ├── AuthService.java          [+ confirm-email-change, confirm-account-restore]
│   │   └── JwtService.java           [+ generate/parse for change_email and account_restore]
│   ├── security
│   │   └── JwtAuthenticationFilter.java  [+ deletion_scheduled_at check]
│   ├── web
│   │   ├── AccountController.java    (new - /me/password, /me/email, /me/delete, /me/restore, PATCH /me)
│   │   ├── AuthController.java       [+ /auth/confirm-email-change, /auth/confirm-account-restore]
│   │   └── dto
│   │       ├── ChangePasswordRequest.java         (new)
│   │       ├── ChangeEmailRequest.java            (new)
│   │       ├── ConfirmEmailChangeRequest.java     (new)
│   │       ├── DeleteAccountRequest.java          (new)
│   │       ├── ConfirmAccountRestoreRequest.java  (new)
│   │       ├── UpdateProfileRequest.java          (new - displayName)
│   │       └── UserResponse.java       [+ deletionScheduledAt]
│   └── persistence
│       └── UserEntity.java           [+ deletion_scheduled_at column + methods]
├── common
│   ├── exception
│   │   ├── AccountDeletionPendingException.java   (new)
│   │   └── AccountNotDeletingException.java       (new)
│   └── security
│       └── RateLimitFilter.java      [+ /me/email, /me/delete in LIMITED_PATHS]
└── (no new config)
```

## Filter chain (no change, just an internal check addition)

```
HTTP request
   v
CorsFilter
   v
RateLimitFilter             (now also covers /me/email, /me/delete)
   v
JwtAuthenticationFilter     (now also rejects users with deletion pending)
   v
Controller
```

## Database migration

```sql
-- V8__users_add_deletion_scheduled_at.sql
ALTER TABLE users ADD COLUMN deletion_scheduled_at TIMESTAMPTZ NULL;
CREATE INDEX idx_users_deletion_scheduled
    ON users(deletion_scheduled_at)
    WHERE deletion_scheduled_at IS NOT NULL;
```

The partial index is small - it only contains rows that are pending deletion,
which is rare. Used by the hard-delete job's `findByDeletionScheduledAtBefore`
query.

## Test plan summary

- `AccountServiceTest` - unit tests for each /me write path (display name,
  password, email request, delete). Mocks JwtService + UserRepository.
- `AccountLifecycleJobTest` - unit test for the @Scheduled hard-delete job
  with a fake Clock.
- `AccountManagementIntegrationTest` - end-to-end through Testcontainers:
  - PATCH /me happy + reject password / email
  - /me/password happy + wrong-password
  - /me/email + /auth/confirm-email-change full flow
  - /me/delete + 403 on login + 401 via JwtFilter
  - /me/restore (auth) + /auth/confirm-account-restore (anonymous)
  - Hard-delete job removes all owned rows
- ArchUnit + JaCoCo gate (80% on service + web) stays green.

## Risks accepted

| # | Risk | Mitigation |
|---|------|-----------|
| R-S9-1 | An attacker with a leaked change_email link can hijack the email for 24h | Pwdv binding kills the link the moment the password changes; user is responsible for not sharing links |
| R-S9-2 | A 30-day restore window is a 30-day attack window for a leaked restore token | Pwdv binding; restore can only happen with the email link OR an active session; user can also re-delete |
| R-S9-3 | Hard-delete job is non-idempotent if interrupted mid-transaction | Single @Transactional method = atomic; if the JVM dies, the next run picks it up |
| R-S9-4 | No notification to old email on email change | Out of scope until SMTP; the pwdv binding partially compensates (a leaked password is needed to even request the change) |
| R-S9-5 | Refresh tokens issued before delete still work for their TTL window | JwtAuthenticationFilter rejects on deletion_scheduled_at, so the refresh-token endpoint also rejects (it auths through the filter) - the access tokens themselves cannot be refreshed |
