# Slice 3 — Architecture

Concise companion to `slice-3-spec.txt`. Six ADRs.

## ADR-I-001: Server-side aggregation via JPQL SUM/GROUP BY

**Decision**: aggregation done with JPQL projections returning DTOs; no client-side maths.

**Why**: a future "all time" view could span millions of rows. Shipping 50k transactions to the browser to compute a single total is wasteful and slow. PG's planner can use the existing partial index `idx_transactions_user_active` for date-range aggregations.

**Consequences**: each endpoint runs one query. The client just renders.

---

## ADR-I-002: Three focused endpoints over one fat endpoint

**Decision**: `/summary`, `/by-category`, `/by-period` as separate resources.

**Why**: each has different lifetimes (summary changes on every write; by-category changes less often; by-period changes with new entries). Separate endpoints can be cached independently (Slice N), each is testable in isolation, and the frontend can re-fetch only what it needs when a single widget refreshes.

**Trade-off**: dashboard load fans out 3 + recent-transactions = 4 requests. Acceptable — HTTP/2 multiplexes them and the JWT auth header is cached.

---

## ADR-I-003: Previous-period window calculated server-side

**Decision**: `GET /insights/summary` computes `previousPeriod` itself: same span length ending the day before `from`.

**Why**: the rule is non-trivial (length-preserving, calendar-aware for "this month → last month"). Doing it once on the backend keeps every client identical.

---

## ADR-I-004: Empty-bucket fill in `/by-period`

**Decision**: the response includes one row per bucket in [from, to], with zeros for buckets that had no transactions.

**Why**: charts look broken when the X axis has gaps. Filling on the backend keeps the frontend dumb and the data shape stable.

---

## ADR-I-005: Chart.js v4 + ng2-charts (frontend)

**Decision**: Chart.js v4 via the `ng2-charts` Angular wrapper.

**Alternatives considered**:
- ngx-charts (D3-based): polished but maintenance has slowed
- ApexCharts: gorgeous but ~170 KB minified, and theming is brittle vs CSS variables
- D3 directly: too much code for 2 charts

**Why this**: Chart.js is small (~60 KB minified), the Angular wrapper supports standalone components, supports `responsive: true` cleanly, and re-reads CSS custom properties on every render so dark-mode just works.

---

## ADR-I-006: BigDecimal serialised as JSON string

**Decision**: all money fields in insights responses are strings (`"1234.56"`), not numbers.

**Why**: JavaScript's `number` loses precision past 2^53; the existing transaction endpoints already use string-formatted decimals (Slice 1 spec assumed BigDecimal). Consistency wins.

**Consequence**: the frontend parses with `Number(s)` when it has to do arithmetic for the savings-rate card; we keep precision until the last step.

---

## Package additions

### Backend
```
com.julio.lifeorganizer.insights
├── web/
│   ├── InsightsController.java
│   └── dto/
│       ├── SummaryResponse.java
│       ├── PeriodTotals.java
│       ├── CategoryTotal.java
│       └── BucketTotal.java
├── service/
│   └── InsightsService.java
└── persistence/
    ├── CategoryTotalProjection.java    (interface projection)
    └── BucketTotalProjection.java
```

The `transactions` package gets a few extra repository methods rather than a new entity:
- `sumByUserAndDateRange(userId, from, to)`
- `groupByCategoryAndType(userId, from, to)`
- `groupByDay(userId, from, to)` / `groupByWeek` / `groupByMonth`

### Frontend
```
src/app/features/dashboard/
├── dashboard.page.ts
├── dashboard.page.html
├── dashboard.page.scss
├── insights.service.ts
├── period-selector/
│   └── period-selector.component.ts
└── widgets/
    ├── stat-card.component.ts
    ├── income-expense-chart.component.ts
    └── category-donut.component.ts
```

Charts live as small components that take their `data` input and re-render on change — they don't fetch.

---

## Risk register

| ID | Risk | Mitigation |
|---|---|---|
| R-I-1 | Big period (all time) takes long to aggregate | Test against 10k+ row dataset; current partial index covers it. If it's slow, add materialised view later (deferred). |
| R-I-2 | Chart.js bundle pushes initial bundle past 800 KB budget | Lazy-load the dashboard route; verified by build. |
| R-I-3 | Currency parsing in JS loses precision | Use string throughout; only `Number(...)` for the savings-rate percentage display. |
| R-I-4 | Empty-bucket fill on the backend uses memory proportional to range | Cap range internally — e.g. reject ranges > 5 years. Defer until needed. |
| R-I-5 | Aggregation returns rows for other users via misconfigured JPQL | All queries embed `t.userId = :userId` and exclude `deleted_at`; identical pattern to Slice 1. |
