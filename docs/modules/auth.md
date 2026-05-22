# Module: `auth`

## Purpose

User identity and JWT-based authentication. Provides registration, login, refresh, and `/me`.

> Spec sections: §4.1 (users table), §6.1–6.4 (endpoints), §8 AC-A1..A16

## Package Tree

```
com.julio.lifeorganizer.auth
├── domain/
│   └── Role.java                       // enum ROLE_USER, ROLE_ADMIN
├── persistence/
│   ├── UserEntity.java                 // @Entity for users table
│   └── UserRepository.java             // findByEmail, existsByEmail
├── security/
│   ├── AuthenticatedUser.java          // SecurityContext principal (record)
│   ├── JwtAuthenticationFilter.java    // Bearer parsing -> SecurityContext
│   ├── JwtAuthenticationEntryPoint.java
│   └── JwtAccessDeniedHandler.java
├── service/
│   ├── JwtService.java                 // HS256 sign + parse, 30s skew
│   ├── JpaUserDetailsService.java      // adapter for AuthenticationManager
│   ├── AuthService.java                // register, login, refresh
│   └── UserService.java                // findById for /me
└── web/
    ├── AuthController.java             // POST /auth/{register,login,refresh}
    ├── UserController.java             // GET /me
    └── dto/
        ├── RegisterRequest.java
        ├── LoginRequest.java
        ├── RefreshRequest.java
        ├── UserResponse.java
        ├── AuthTokensResponse.java
        └── AccessTokenResponse.java
```

## JWT Model (spec §4.3, ADR-004)

- Algorithm: **HS256**
- Secret: env var `JWT_SECRET`, ≥ 32 chars, validated at boot
- Access token: `typ=access`, `sub=userId`, `email`, `role`, TTL **15 min**
- Refresh token: `typ=refresh`, `sub=userId`, TTL **7 days**
- Clock skew: 30 seconds tolerance (R5)
- Revocation: **none** — TTL-only (R2 accepted)
- Both tokens self-contained; **no refresh_tokens table**

## Ownership Enforcement (ADR-005)

`SecurityContext.authentication.principal` is an `AuthenticatedUser` (record) carrying `id`, `email`, `role`. Services pull `userId` from there — never from request bodies. Jackson rejects unknown JSON properties globally (`fail-on-unknown-properties=false` ignores them silently, but Jackson does not write them into our record DTOs which have no setter for `userId`).

## Error Codes Owned by This Module

| HTTP | `meta.code` | When |
|---|---|---|
| 400 | `MALFORMED_REQUEST` | broken JSON in body |
| 400 | validation field-map | missing/invalid request fields |
| 401 | `INVALID_CREDENTIALS` | wrong password OR unknown email (identical) |
| 401 | `INVALID_TOKEN` | bad signature, wrong `typ`, missing header |
| 401 | `TOKEN_EXPIRED` | expired access or refresh |
| 401 | `UNAUTHORIZED` | no Bearer header / not authenticated |
| 401 | `USER_NOT_FOUND` | valid token, user deleted since issuance (R11) |
| 409 | `USER_EMAIL_EXISTS` | duplicate registration (case-insensitive) |

## Validation Rules

### `RegisterRequest`
- `email`: `@NotBlank @Email @Size(max=255)` — lowercased before insert
- `password`: `@Size(min=8, max=100) @Pattern(regexp="^(?=.*[A-Za-z])(?=.*\\d).*$")`
- `displayName`: `@NotBlank @Size(min=2, max=100)` (trimmed)

### `LoginRequest`
- `email`, `password`: `@NotBlank` (specific format errors return identical 401, not field map)

### `RefreshRequest`
- `refreshToken`: `@NotBlank`

## Acceptance Criteria Coverage

Closes AC-A1..A16 (16 acceptance criteria). See `docs/specs/slice-1-plan.md` §AC Coverage Checklist for the step-by-step mapping.

## Tests

| Test class | Tier | What it covers |
|---|---|---|
| `JwtServiceTest` | unit | sign/parse, typ mismatch, expiry, skew |
| `JwtAuthenticationFilterTest` | unit | header parsing, principal population, exception delegation |
| `AuthServiceRegisterTest` | unit | lowercase email, BCrypt hash, duplicate -> Conflict |
| `AuthServiceLoginTest` | unit | identical exception for wrong-pwd vs unknown-email |
| `AuthServiceRefreshTest` | unit | typ mismatch, expired, deleted user |
| `UserServiceTest` | unit | findById, missing -> UserNotFoundForToken |
| `RegisterRequestValidationTest` | unit | 5 negative cases per spec §6.1 |
| `AuthControllerRegisterTest` | web slice | 201 envelope, validation map |
| `AuthControllerLoginTest` | web slice | 200 + tokens, identical 401 |
| `AuthControllerRefreshTest` | web slice | 200, typ mismatch, expired |
| `UserControllerTest` | web slice | `/me` 200, no token 401 |
| `UserRepositoryTest` | jpa slice | findByEmail roundtrip |
| `UserEntityMappingTest` | jpa slice | column constraints, role enum |
| `AuthRegisterIntegrationTest` | full int | register → duplicate → 409 |
| `SecurityConfigTest` | full int | protected route returns 401 envelope |

## Risks Accepted

- **R2** — no server-side revocation. Documented in `AuthServiceRefreshTest`.
- **R5** — 30s clock skew allowed by jjwt parser.
- **R6** — HS256 key rotation procedure not coded.
- **R11** — `/me` checks user existence on every request; cost accepted.
