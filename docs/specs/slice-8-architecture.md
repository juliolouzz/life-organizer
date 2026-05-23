# Slice 8 - Architecture (delivered)

This document captures the design decisions actually shipped in PR #15. The
behavioural contract is in `slice-8-spec.txt` (with the post-review amendments
appended at the bottom).

## ADRs

### ADR-S8-01 - Stateless JWT for reset and verify tokens

Reset and email-verification tokens are signed HS256 JWTs that reuse the same
`JwtProperties.secret` as access / refresh tokens. A `typ` claim
(`password_reset` / `verify_email`) distinguishes them from auth tokens and the
parser rejects a token used at the wrong endpoint.

**Alternatives considered:** a separate `password_reset_tokens` table with a
random nonce. Rejected because it doubles the persistence surface and adds a
write on every reset request - and we already have a working JWT signing key.
Trade-off: stateless tokens cannot be revoked individually, mitigated by the
pwdv binding (ADR-S8-02) and the 1h / 24h TTLs.

### ADR-S8-02 - Reset tokens bound to a password-hash fingerprint

The `pwdv` claim is the first 8 bytes of `SHA-256(passwordHash)`, hex-encoded.
On `/reset-password` we parse the JWT, fetch the user, then call
`JwtService.verifyPasswordBinding(claims, user.passwordHash)`. After a
successful reset the password hash changes, the fingerprint changes, and any
previously-issued reset token is rejected as `INVALID_TOKEN`.

**Why 8 bytes:** 64 bits of entropy on a value the attacker cannot influence is
far more than we need to detect a password change. The shorter claim keeps the
token compact.

**Constant-time comparison:** `MessageDigest.isEqual` is used (not
`String.equals`), so a malicious token cannot leak the expected fingerprint by
timing.

### ADR-S8-03 - In-memory sliding-window rate limiter

`RateLimiter` keeps a `Map<String, Deque<Instant>>` keyed by `ip::endpoint`
and prunes entries older than 15 minutes on each `tryAcquire` call. The cap is
5 requests per window per (ip, endpoint).

**`RateLimitFilter`** wraps the limiter as an `OncePerRequestFilter`,
applies it only to POST requests on a whitelist of auth endpoints (all six
public-write paths), and routes `RateLimitedException` through
`HandlerExceptionResolver` so the response goes through `GlobalExceptionHandler`
and lands as the standard 429 envelope.

**Single-instance only.** Distributed limiting would need Redis or a shared
store - deferred to the operational slice that introduces real hosting.

**Disabled in tests** via `app.rate-limit.enabled=false` in
`application-test.yml`. The limiter's pure logic is covered by
`RateLimiterTest` (no Spring context).

### ADR-S8-04 - Tokens never logged; opt-in file delivery instead

`AuthDevDeliveryProperties` is a `@ConfigurationProperties`-bound bean that
appends one line per issued link to `.tmp/auth-dev-links.txt`. The standard
SLF4J logger only records non-sensitive metadata (kind, userId, email).

**Default off.** Production deployments without SMTP would set it off; the link
never leaves the JVM. The `local` profile enables it so the self-hosted owner
can still retrieve their links.

**Threat model addressed:** CodeQL `java/sensitive-log` (CWE-532). Anyone with
log access (docker logs, log aggregators, stdout over someone's shoulder) could
previously hijack any account during the token's TTL.

### ADR-S8-05 - CSRF stays disabled; documented and CodeQL-suppressed

The API is pure JSON authenticated only by JWTs in the `Authorization` header.
Browsers do not auto-attach `Authorization` headers cross-site, so the ambient-
credential precondition for CSRF does not hold. CSRF protection stays
`.csrf(csrf -> csrf.disable())`.

CodeQL flags `java/spring-disabled-csrf-protection` as high severity. It is
suppressed via `.github/codeql/codeql-config.yml` with the rationale documented
inline in `SecurityConfig.java`. If cookie / session auth is ever introduced,
this suppression must be removed and CSRF re-enabled.

### ADR-S8-06 - Referrer-Policy: no-referrer; token stripped from URL

Reset and verify URLs carry the token as a query parameter. To prevent it from
leaking, two defenses are layered:

1. `Referrer-Policy: no-referrer` is set globally by Spring Security so the
   browser does not send the `Referer` header when the user clicks any outbound
   link from these pages.
2. The Angular `/reset-password` and `/verify-email` pages call
   `Router.navigate([], { queryParams: { token: null }, replaceUrl: true })`
   immediately after reading the token, so it does not persist in browser
   history / session restore / bookmarks.

### ADR-S8-07 - Constant-time anti-enumeration

`/forgot-password` and `/resend-verification` do an equivalent JWT-signing
operation in the no-user branch (against a synthetic decoy password hash).
Response time does not leak whether the email is registered.

## Package layout (delta from Slice 1)

```
com.julio.lifeorganizer
├── auth
│   ├── service
│   │   ├── AuthService.java          [+5 methods: forgot/reset/verify/resend]
│   │   └── JwtService.java           [+ generate/parse for password_reset and verify_email types; PWDV_CLAIM helper]
│   ├── web
│   │   ├── AuthController.java       [+4 endpoints]
│   │   └── dto
│   │       ├── ForgotPasswordRequest.java   (new)
│   │       ├── ResetPasswordRequest.java    (new)
│   │       └── VerifyEmailRequest.java      (new)
│   └── persistence
│       └── UserEntity.java           [+ email_verified column, isEmailVerified, markEmailVerified, changePasswordHash]
├── common
│   ├── exception
│   │   └── RateLimitedException.java (new)
│   └── security
│       ├── RateLimiter.java          (new)
│       └── RateLimitFilter.java      (new)
└── config
    ├── AuthDevDeliveryProperties.java (new - opt-in dev sink)
    └── SecurityConfig.java            [+ rate-limit filter wiring, Referrer-Policy, CSRF doc comment]
```

## Filter chain (delta)

```
HTTP request
   v
CorsFilter
   v
RateLimitFilter             [new in S8 - 429 on 6th call/15min]
   v
JwtAuthenticationFilter
   v
UsernamePasswordAuthenticationFilter (unused)
   v
Controller
```

## Database migration

`V7__users_add_email_verified.sql` adds `email_verified BOOLEAN NOT NULL DEFAULT FALSE`
and backfills existing rows to `TRUE` so the few users that pre-date this slice
are grandfathered in as verified.

## Test coverage

- `RateLimiterTest` - 4 pure unit tests for the sliding-window logic
- `AuthCompletenessIntegrationTest` - 7 end-to-end tests through `@SpringBootTest`
  including the reset-token reuse case (AC-8-14)
- `SchemaMigrationTest` updated to expect V7 and the new column
- JaCoCo gate (80% on service+web) still green

## Risks accepted

| # | Risk | Mitigation |
|---|------|-----------|
| R-S8-1 | Stateless tokens cannot be revoked individually | Short TTL (1h) + pwdv binding makes reset tokens single-use |
| R-S8-2 | Refresh tokens are NOT password-bound, so a leaked refresh token survives password change | Out of scope for S8; would need a `token_version` column - candidate for a future "logout everywhere" slice |
| R-S8-3 | Rate limiter state lives in JVM heap, so a restart resets the windows | Acceptable for single-node deploy; revisit when adding horizontal scale |
| R-S8-4 | `.tmp/auth-dev-links.txt` holds raw tokens while the local profile is active | File is gitignored; only the local profile writes; the prod default is `enabled=false` |
| R-S8-5 | CSRF stays disabled | Architectural property of stateless JWT API; suppression documented; would break if cookie auth is later added |
