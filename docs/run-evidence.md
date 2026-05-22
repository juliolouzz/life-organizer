# Slice 1 — End-to-End Run Evidence

Captured on 2026-05-22 against a fresh `docker compose up -d postgres` and `mvn spring-boot:run`.

This file documents the full user journey working against the live API. It complements (does not replace) `TransactionFlowIntegrationTest` and `AuthFlowIntegrationTest` which exercise the same scenarios via JUnit + Testcontainers.

## Step 0 — Boot

```bash
cp .env.example .env       # fill JWT_SECRET >= 32 chars
docker compose up -d postgres
mvn spring-boot:run
```

The application started in **~1.9 seconds** and applied Flyway migrations V1 (users) + V2 (transactions) on first connect.

## Step 1 — Health

```bash
$ curl http://localhost:8080/actuator/health
{"status":"UP"}
```

## Step 2 — Register

```bash
$ curl -X POST http://localhost:8080/api/v1/auth/register \
       -H 'Content-Type: application/json' \
       -d '{"email":"demo@example.com","password":"S3cretValue","displayName":"Demo User"}'

{
  "success": true,
  "data":    { "id": 1, "email": "demo@example.com", "displayName": "Demo User", "role": "ROLE_USER" },
  "message": null,
  "meta":    null
}
```

Returned 201 Created. Email was lowercased; password stored as BCrypt hash (never serialised).

## Step 3 — Login

```bash
$ curl -X POST http://localhost:8080/api/v1/auth/login \
       -H 'Content-Type: application/json' \
       -d '{"email":"demo@example.com","password":"S3cretValue"}'

{
  "success": true,
  "data": {
    "accessToken":  "eyJhbGciOiJIUzI1NiJ9...rVORUDV2g8evjxdWp01s...EVw",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...UEMknHrEWELNtQpjZ...Axo",
    "tokenType":    "Bearer",
    "expiresIn":    900
  }
}
```

Returned 200 OK. Access token decoded payload:
```json
{ "sub": "1", "typ": "access", "email": "demo@example.com", "role": "ROLE_USER",
  "iat": 1779472755, "exp": 1779473655 }
```
`exp - iat = 900` seconds = 15 minutes (spec section 7).

## Step 4 — GET /me

```bash
$ curl http://localhost:8080/api/v1/me -H "Authorization: Bearer $ACCESS"
{
  "success": true,
  "data": { "id": 1, "email": "demo@example.com", "displayName": "Demo User", "role": "ROLE_USER" }
}
```

## Step 5 — Create transaction

```bash
$ curl -X POST http://localhost:8080/api/v1/transactions \
       -H "Authorization: Bearer $ACCESS" \
       -H "Content-Type: application/json" \
       -d '{"amount":42.50,"type":"EXPENSE","category":"Groceries",
            "description":"Weekly shop","transactionDate":"2026-05-22"}'

{
  "success": true,
  "data": {
    "id": 1, "amount": 42.50, "type": "EXPENSE", "category": "Groceries",
    "description": "Weekly shop", "transactionDate": "2026-05-22",
    "createdAt": "2026-05-22T17:59:15.421026Z", "updatedAt": "2026-05-22T17:59:15.421032Z"
  }
}
```

Returned 201 Created. user_id was bound to the JWT subject (1), not derived from any body field.

## Step 6 — List transactions

```bash
$ curl 'http://localhost:8080/api/v1/transactions?limit=5' -H "Authorization: Bearer $ACCESS"
{
  "success": true,
  "data": [ { ...the single transaction... } ],
  "meta":  { "nextCursor": "", "limit": 5 }
}
```

`nextCursor` empty -> last page.

## Step 7 — Update (PUT, full replace)

```bash
$ curl -X PUT http://localhost:8080/api/v1/transactions/1 \
       -H "Authorization: Bearer $ACCESS" -H "Content-Type: application/json" \
       -d '{"amount":99.00,"type":"INCOME","category":"Bonus",
            "description":"Annual","transactionDate":"2026-05-23"}'
{
  "success": true,
  "data": { "id": 1, "amount": 99.00, "type": "INCOME", "category": "Bonus",
            "description": "Annual", "transactionDate": "2026-05-23", ... }
}
```

All five mutable fields replaced; `id`, `createdAt` unchanged.

## Step 8 — Delete (soft)

```bash
$ curl -X DELETE http://localhost:8080/api/v1/transactions/1 -H "Authorization: Bearer $ACCESS"
   -> 204 No Content
```

## Step 9 — Idempotence check

```bash
$ curl -X DELETE http://localhost:8080/api/v1/transactions/1 -H "Authorization: Bearer $ACCESS"
   -> 404 Not Found  with body { ..., "meta": { "code": "TRANSACTION_NOT_FOUND" } }
```

Second delete is **identical** to a delete on a missing or non-owned id.

## Step 10 — No token

```bash
$ curl http://localhost:8080/api/v1/me
   -> 401 Unauthorized  with body { ..., "meta": { "code": "UNAUTHORIZED" } }
```

## Summary

| Step | Endpoint | Status | Notes |
|---|---|---|---|
| 0 | boot | OK | 1.9s startup, Flyway V1+V2 applied clean |
| 1 | GET /actuator/health | 200 | {"status":"UP"} |
| 2 | POST /auth/register | 201 | BCrypt + lowercased email |
| 3 | POST /auth/login | 200 | HS256 access+refresh pair |
| 4 | GET /me | 200 | principal from JWT subject |
| 5 | POST /transactions | 201 | user_id from JWT, body ignored |
| 6 | GET /transactions | 200 | keyset pagination, meta shape |
| 7 | PUT /transactions/{id} | 200 | full replace |
| 8 | DELETE /transactions/{id} | 204 | soft delete |
| 9 | DELETE same id again | 404 | TRANSACTION_NOT_FOUND |
| 10 | GET /me without bearer | 401 | UNAUTHORIZED |

**Slice 1 is production-shaped for local development.** Production deployment, frontend, and additional verticals are deferred per `docs/specs/slice-1-spec.txt` section 9.
