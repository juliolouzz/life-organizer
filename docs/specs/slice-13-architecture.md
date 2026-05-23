# Slice 13 - Architecture

Companion to `slice-13-spec.txt`. Short and mechanical - this slice is
small.

## ADRs

### ADR-S13-01 - Per-user setting, display-only

Stored as `users.currency VARCHAR(3) CHECK IN (BRL, USD, EUR)`. The Java
side mirrors it as an enum so every layer sees a closed set. No FX rates,
no per-transaction override, no historical re-conversion - only the
presentation layer (frontend pipe, PDF template) reads the value.

### ADR-S13-02 - CHECK constraint, not a separate currencies table

A 3-value enum doesn't justify a join. The CHECK keeps malformed data out
of the DB without a referential lookup. Adding USD-CHF or AUD later is
two lines: update the enum + update the CHECK in a new migration.

### ADR-S13-03 - Frontend MoneyPipe reads from AuthService.currentUser()

The pipe takes `value: number` and an optional `currency` override; if
the override is null it falls back to `auth.currentUser()?.currency ??
'BRL'`. Components don't have to pass the currency on every usage; the
pipe is cohesive with the signed-in user.

The existing `MoneyBrlPipe` is renamed to `Money` (the pipe still works
on legacy templates that import it under the old name via a thin
alias file).

### ADR-S13-04 - Boot warning when no mail delivery is configured

Tangential to the slice but bundled because it surfaces the same way as
the email issue that prompted this work. `FileMailService` exposes a
`@PostConstruct` that inspects `AuthDevDeliveryProperties.enabled`; if
false AND no SMTP override is in play, it logs a single WARN at startup
naming the env vars the operator should set. This is purely informational
- no behaviour change.

## Package layout

```
com.julio.lifeorganizer
├── auth
│   ├── domain
│   │   └── Currency.java                (new - enum BRL, USD, EUR)
│   ├── persistence
│   │   └── UserEntity.java              [+ currency field + setter]
│   ├── service
│   │   ├── AuthService.java             [register accepts optional currency]
│   │   └── AccountService.java          [updateProfile accepts optional currency]
│   └── web
│       ├── dto
│       │   ├── RegisterRequest.java     [+ optional currency]
│       │   ├── UpdateProfileRequest.java [+ optional currency]
│       │   └── UserResponse.java         [+ currency]
└── mail
    └── FileMailService.java             [+ boot-time WARN if dev-delivery off]
```

## Database migration

```sql
-- V10__users_add_currency.sql
ALTER TABLE users ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'BRL';
ALTER TABLE users ADD CONSTRAINT users_currency_check
    CHECK (currency IN ('BRL', 'USD', 'EUR'));
```

## Test plan

- Unit: `UserEntity.changeCurrency()` behaves like the other change methods.
- Integration: register-with-currency happy + invalid value, profile
  PATCH happy + verifying the field round-trips.
- Schema test: V10 + new column.
- PDF: assert the rendered HTML contains the right currency token when
  the user is configured for USD vs BRL.
