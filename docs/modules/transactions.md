# Module: `transactions`

## Purpose

Per-user financial transactions (income/expense) with soft delete and keyset pagination.

> Spec sections: §4.2 (transactions table), §6.5–6.9 (endpoints), §8 AC-T1..T23

## Package Tree

```
com.julio.lifeorganizer.transactions
├── domain/
│   ├── TransactionType.java            // enum INCOME, EXPENSE
│   └── CursorCodec.java                // opaque base64 <date>_<id> codec
├── persistence/
│   ├── TransactionEntity.java          // @Entity for transactions
│   └── TransactionRepository.java      // findPage JPQL with composite keyset
├── service/
│   ├── TransactionService.java         // create, list, findOne, update, softDelete
│   └── TransactionMapper.java          // entity <-> dto
└── web/
    ├── TransactionController.java
    └── dto/
        ├── CreateTransactionRequest.java
        ├── UpdateTransactionRequest.java
        ├── TransactionResponse.java
        └── ListTransactionsQuery.java
```

## Data Model (spec §4.2)

| Column | Type | Constraint |
|---|---|---|
| `id` | `BIGINT IDENTITY` | PK |
| `user_id` | `BIGINT` | NOT NULL, FK -> users.id |
| `amount` | `NUMERIC(15, 2)` | NOT NULL, `> 0` |
| `type` | `VARCHAR(10)` | NOT NULL, in (`INCOME`, `EXPENSE`) |
| `category` | `VARCHAR(50)` | NOT NULL |
| `description` | `VARCHAR(255)` | NOT NULL |
| `transaction_date` | `DATE` | NOT NULL |
| `deleted_at` | `TIMESTAMPTZ` | NULL (soft delete marker) |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT now() |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT now() |

### Indexes

- PK on `id`
- `idx_transactions_user_id` on `(user_id)` — generic FK index
- **Partial index** `idx_transactions_user_active`:
  ```sql
  ON transactions (user_id, transaction_date DESC, id DESC)
  WHERE deleted_at IS NULL
  ```
  Backs the canonical list query.

## Ownership Predicate (ADR-005)

Every query embeds `user_id = :userId AND deleted_at IS NULL` directly in JPQL. No Hibernate `@SQLRestriction` (deliberate — explicit predicates are debuggable, see CLAUDE.md "Personal Preferences").

`userId` always comes from `SecurityContext.authentication.principal.id()`, never from the request body.

## Cursor Pagination (Amendment 1)

- Cursor is an **opaque base64 token** encoding `<transactionDate>_<id>`.
- Composite keyset predicate (ordering: `transaction_date DESC, id DESC`):
  ```
    (transaction_date < cursorDate)
  OR (transaction_date = cursorDate AND id < cursorId)
  ```
- `limit` default 20, max 100, min 1.
- `meta.nextCursor`: base64 of last row's `<transactionDate>_<id>`, or `null` when fewer than `limit` rows returned.

### `CursorCodec` contract

```java
public final class CursorCodec {
    static String encode(LocalDate date, Long id);              // base64(<iso>_<id>)
    static DecodedCursor decode(String token) throws InvalidQueryException;
    record DecodedCursor(LocalDate date, Long id) {}
}
```

Throws `InvalidQueryException` on:
- non-base64 input
- decoded string not matching `^\d{4}-\d{2}-\d{2}_\d+$`
- date parse failure
- id not a positive long

## Canonical List JPQL (ADR-009)

```java
@Query("""
    SELECT t FROM TransactionEntity t
    WHERE t.userId = :userId
      AND t.deletedAt IS NULL
      AND (:from IS NULL OR t.transactionDate >= :from)
      AND (:to   IS NULL OR t.transactionDate <= :to)
      AND (
            :cursorDate IS NULL
         OR  t.transactionDate <  :cursorDate
         OR (t.transactionDate = :cursorDate AND t.id < :cursorId)
      )
    ORDER BY t.transactionDate DESC, t.id DESC
    """)
List<TransactionEntity> findPage(
    @Param("userId") Long userId,
    @Param("from") LocalDate from,
    @Param("to") LocalDate to,
    @Param("cursorDate") LocalDate cursorDate,
    @Param("cursorId") Long cursorId,
    Pageable pageable);
```

Service requests `Pageable.ofSize(limit + 1)` to detect "has more"; trims the extra row and emits `nextCursor` only when present.

## Identical 404 (R8)

All three "transaction not found" cases return **byte-identical** JSON:

1. id genuinely missing
2. id exists but `user_id != JWT subject`
3. id exists, owned, but `deleted_at IS NOT NULL`

```json
{ "success": false, "data": null, "message": "Transaction not found", "meta": { "code": "TRANSACTION_NOT_FOUND" } }
```

This prevents owner enumeration. Tested in `TransactionControllerTest.notFoundBodies_acrossAllThreeCases_areByteIdentical()`.

## Error Codes Owned by This Module

| HTTP | `meta.code` | When |
|---|---|---|
| 400 | validation field-map | invalid amount/type/category/description/transactionDate |
| 400 | `INVALID_QUERY` | `limit` out of [1,100], `from > to`, malformed `cursor` |
| 401 | (auth module codes) | any auth failure |
| 404 | `TRANSACTION_NOT_FOUND` | the three identical-body cases above |

## State Transitions (spec §7.2)

```
[non-existent] --POST--> [ACTIVE]
[ACTIVE]       --PUT-->  [ACTIVE]   (fields replaced, updatedAt bumped)
[ACTIVE]       --DEL--> [SOFT_DELETED]   (deleted_at = now())
[SOFT_DELETED] --any-->  404
```

Undelete is **not** supported in Slice 1.

## Acceptance Criteria Coverage

Closes AC-T1..T23 (23 acceptance criteria). See `docs/specs/slice-1-plan.md` §AC Coverage Checklist.

## Tests

| Test class | Tier | What it covers |
|---|---|---|
| `CursorCodecTest` | unit | encode roundtrip, malformed inputs |
| `TransactionServiceCreateTest` | unit | userId from principal, ignores body userId |
| `TransactionServiceListTest` | unit | composite keyset, filters, has-more |
| `TransactionServiceFindOneTest` | unit | identical NotFound for 3 cases |
| `TransactionServiceUpdateTest` | unit | replaces all 5 fields, bumps updatedAt |
| `TransactionServiceSoftDeleteTest` | unit | sets deleted_at, second delete 404 |
| `CreateTransactionRequestValidationTest` | unit | 5+ negative cases per spec §6.5 |
| `TransactionControllerTest` | web slice | 201 envelope, identical 404 bodies |
| `TransactionRepositoryTest` | jpa slice | findPage against real Postgres |
| `TransactionsListIntegrationTest` | full int | end-to-end pagination across 2 users |

## Risks Accepted

- **R3** — no unique constraints affected by soft delete (none defined).
- **R4** — partial index requires exact predicate match in JPQL; explicit predicate ensures usage.
- **R8** — identical 404 enforced; covered by a dedicated test.
- **R9** — composite cursor design (Amendment 1); resolved before any code written.
- **R12** — no rate limiting; accepted.
