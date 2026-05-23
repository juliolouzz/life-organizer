# Slice 8 - Implementation Plan (delivered)

This is the as-built plan. The original sequencing was loose; what actually
shipped split cleanly into the phases below across PR #15.

| Phase | Scope | Verify |
|---|---|---|
| 1 | Backend: V7 migration, UserEntity changes, JwtService extensions (typ + pwdv) | `mvn test` green |
| 2 | Backend: AuthService methods (forgot/reset/verify/resend), DTOs, AuthController endpoints | Unit tests for each branch |
| 3 | Backend: RateLimiter + RateLimitFilter wired into SecurityConfig | `RateLimiterTest` covers the window logic |
| 4 | Backend integration tests: `AuthCompletenessIntegrationTest` end-to-end through Testcontainers | `mvn verify` green; JaCoCo + ArchUnit green |
| 5 | Frontend: `PasswordResetService` with SKIP_AUTH context | Jest unit tests |
| 6 | Frontend: /forgot-password, /reset-password, /verify-email pages + routes + "Forgot password?" link on login | `npm run build` clean |
| 7 | Frontend: dashboard verify-email banner + `AuthenticatedUser.emailVerified` propagation | Build + lint clean |
| 8 | Security review: pwdv binding, constant-time enumeration defenses, rate-limit expansion, Referrer-Policy, token-URL stripping, token-log removal -> opt-in file delivery | New AC (AC-8-14) test added; CodeQL clean |
| 9 | Docs: spec amendments (A-1..A-6), architecture, this plan, README "What's next" | Docs PR green |

Each backend phase ended with `mvn verify` passing. Each frontend phase ended
with `npx ng build`, ESLint, and Jest passing. The final security-review phase
introduced the CodeQL config file at `.github/codeql/codeql-config.yml`.

## Acceptance criteria coverage

| AC | Phase |
|---|---|
| AC-8-1 (forgot-password anti-enumeration) | 2, 4, 8 |
| AC-8-2..4 (reset-password happy/error paths) | 2, 4 |
| AC-8-5..7 (verify-email + emailVerified flag) | 1, 2, 4 |
| AC-8-8..9 (rate limit) | 3 |
| AC-8-10..13 (frontend pages + banner) | 5, 6, 7 |
| AC-8-14 (reset token single-use after change) | 8 |
