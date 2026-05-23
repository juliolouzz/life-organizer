# Slice 10 - Architecture

Companion to `slice-10-spec.txt`. Records the design choices before the code lands.

## ADRs

### ADR-S10-01 - Read-only feature; no schema changes

Every Slice 10 endpoint is a `GET` against existing tables. No new columns,
no new tables, no migration. The aggregations (monthly totals, YoY pairs,
trend series) are SQL-shaped questions that JPQL can express directly.
Caching layer is deliberately out of scope - recomputing is cheap at
personal-finance data volumes (tens of thousands of rows per user, not
millions).

### ADR-S10-02 - PDF via OpenHTMLtoPDF + Thymeleaf

```
ReportsExportService
   |
   v
  Thymeleaf template -> XHTML string
   |
   v
  OpenHTMLtoPDF (PdfRendererBuilder) -> byte[]
```

**Why OpenHTMLtoPDF over iText / PDFBox direct:**
- Apache 2.0 license (iText 7 is AGPL or paid commercial)
- Renders a subset of CSS, so layout work happens in HTML/CSS, not Java
- Output is identical regardless of the client's browser

**Why Thymeleaf as the templating engine:**
- Already on the classpath in Spring Boot starters
- Same data binding patterns as if we ever serve HTML directly
- Easy to preview the template by rendering to HTML in a dev tool

**Dependency additions:**
```
com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10
com.openhtmltopdf:openhtmltopdf-slf4j:1.0.10
org.springframework.boot:spring-boot-starter-thymeleaf
```

### ADR-S10-03 - CSV is import-compatible by design

The transactions CSV export uses byte-for-byte the same column layout
as the Slice 7 import (`date,type,amount,category,description`). The
"round-trip" property is testable: download a CSV, re-import it, and the
net effect must be zero new rows.

The summary CSV is a different shape (totals + top categories + daily)
because it serves a different purpose - sharing analysis, not raw data.
It is NOT a round-trip format; trying to import it via /transactions/import
will fail header validation, which is correct.

### ADR-S10-04 - Per-tab lazy loading on the frontend

The /reports page mounts the period selector and the tab strip on first
visit but only fetches the active tab's data. Switching to a new tab
fires its load; switching back to an already-loaded tab does not refetch
unless the period changed.

Implementation: each tab is a signal-backed view-model in the ReportsPage
component. The active tab's view-model lazily fires its HTTP call via a
`computed()` that depends on `{ tab, period }`. The Slice 3 dashboard
already uses this pattern.

### ADR-S10-05 - Reports endpoints reuse existing aggregation queries

Slice 3 added insights queries (CategoryTotalRow, DailyBucketRow,
TypeSumRow) for the dashboard. Slice 10 wires new endpoints to those
same row projections - the only new SQL is the YoY pair query and the
trends pivot.

This keeps the surface small and means the report numbers always match
the dashboard for the same period.

### ADR-S10-06 - Decimal handling

All amounts are `BigDecimal(scale=2)` end-to-end. JSON serialisation uses
the existing Jackson configuration (no scientific notation). CSV writes
the raw decimal via `BigDecimal.toPlainString()`. PDF renders via a
`R$` prefix and `String.format("%,.2f", amount)` - no Locale dependency
on the host JVM.

### ADR-S10-07 - Filename convention for downloads

```
summary-{year}-{MM}.csv
summary-{year}-{MM}.pdf
transactions-{from}-to-{to}.csv
```

Where `{MM}` is zero-padded. This sorts naturally in a downloads folder
and is unambiguous when shared.

## Package layout (delta from Slice 9)

```
com.julio.lifeorganizer
└── reports                          (new)
    ├── service
    │   ├── ReportsService.java          (aggregation -> DTOs)
    │   ├── ReportsExportService.java    (CSV writer + PDF renderer)
    │   └── ReportsTemplateModel.java    (Thymeleaf context object)
    ├── web
    │   ├── ReportsController.java       (JSON endpoints)
    │   ├── ReportsExportController.java (CSV / PDF endpoints)
    │   └── dto
    │       ├── SummaryReport.java
    │       ├── YearOverYearReport.java
    │       ├── CategoryTrendsReport.java
    │       ├── TotalsBlock.java
    │       ├── CategoryAmount.java
    │       ├── DailyBucket.java
    │       ├── DeltaBlock.java
    │       └── CategoryTrendSeries.java
    └── persistence
        └── ReportRow projections (reused from insights.persistence)
```

The export controller is split out so the JSON endpoints can keep a
clean ApiResponse envelope while the file endpoints write raw bytes
with the right Content-Type / Content-Disposition.

## Filter chain (no change)

```
HTTP request
   v
CorsFilter
   v
RateLimitFilter             (NOT applied to /reports/*)
   v
JwtAuthenticationFilter     (still enforces auth + deletion gate)
   v
Controller
```

## Test plan summary

- `ReportsServiceTest` - unit tests for the aggregation logic with a
  fixed `Clock` and a small in-memory dataset (when feasible via JPA
  slice test).
- `ReportsIntegrationTest` - end-to-end through Testcontainers:
  - summary with / without data, including the future-month zero case
  - YoY happy path + last-is-zero percent-null case
  - trends 6 vs 12 months
  - CSV round-trip via /reports/transactions.csv -> /transactions/import
  - PDF magic-byte and content-length sanity check
  - 401 on anonymous calls
- `ReportsExportServiceTest` - unit test for CSV writing edge cases
  (BRL decimals, commas in descriptions get quoted).

## Risks accepted

| # | Risk | Mitigation |
|---|------|-----------|
| R-S10-1 | PDF rendering adds ~3 MB to the WAR | Acceptable; PDFs are an explicit-opt-in feature |
| R-S10-2 | Thymeleaf adds another templating engine to maintain | Single template; if it grows complex, switch to a simpler builder |
| R-S10-3 | No caching; large datasets re-aggregate on every call | Personal-use scale only; revisit when adding multi-tenant or larger volumes |
| R-S10-4 | CSV with description containing commas needs proper quoting | Use opencsv writer for correctness, not manual `String.join`. |
| R-S10-5 | PDF generation can OOM on huge transaction lists | Hard-cap top-categories to 5 and daily array to 31 rows. PDF size is bounded. |
