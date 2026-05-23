# Slice 10 - Implementation Plan

Phased so each phase ends with a runnable build and a meaningful commit.

| Phase | Scope | Verify |
|---|---|---|
| 1 | Add dependencies (OpenHTMLtoPDF, openhtmltopdf-slf4j, spring-boot-starter-thymeleaf) | `mvn compile` clean |
| 2 | DTOs: SummaryReport, YearOverYearReport, CategoryTrendsReport, TotalsBlock, DeltaBlock, CategoryAmount, DailyBucket, CategoryTrendSeries | Compile |
| 3 | ReportsService: monthly summary aggregation | Unit tests with a small in-memory dataset |
| 4 | ReportsService: YoY pair + delta calculation | Tests for null-percent edge case |
| 5 | ReportsService: 6/12 month trends pivot | Tests for empty-window case |
| 6 | ReportsController: GET /reports/summary, /reports/yoy, /reports/trends | MockMvc smoke tests |
| 7 | ReportsExportService: CSV writers (summary CSV + transactions CSV) via opencsv | Unit tests for quoting and decimal formatting |
| 8 | Thymeleaf summary template + OpenHTMLtoPDF wrapper | Unit test asserts PDF magic bytes |
| 9 | ReportsExportController: GET /reports/summary.csv, /reports/summary.pdf, /reports/transactions.csv | Integration test |
| 10 | SecurityConfig: allow /reports/* through filter chain (authenticated only) | mvn verify green |
| 11 | Frontend ReportsService HTTP client (JSON + Blob downloads) | Compile |
| 12 | Frontend /reports page shell (period selector + tabs) + route + sidebar entry | Build clean |
| 13 | Frontend Summary tab: stat cards, top categories table, daily chart, export buttons | Manual smoke |
| 14 | Frontend YoY tab: side-by-side cards + delta indicators + top-5 deltas table | Manual smoke |
| 15 | Frontend Trends tab: 6/12 toggle + line charts | Manual smoke |
| 16 | Transactions list page: add "Download CSV" button next to "Import CSV" | Manual smoke |
| 17 | Full `mvn verify` + `npx ng build && npm test && npx eslint` | All green |
| 18 | PR + CodeQL clean | CI green, security review |

Each backend phase ends with `mvn verify`. Each frontend phase ends with
`npx ng build && npx eslint && npx jest`.

## Acceptance criteria coverage

| AC | Phase |
|---|---|
| AC-10-1..3 (summary endpoint) | 3, 6, 17 |
| AC-10-4 (YoY) | 4, 6 |
| AC-10-5..6 (trends) | 5, 6 |
| AC-10-7..8 (auth scoping) | 10, 17 |
| AC-10-9 (summary CSV) | 7, 9, 17 |
| AC-10-10 (summary PDF) | 8, 9, 17 |
| AC-10-11 (transactions CSV round-trip) | 7, 9, 17 |
| AC-10-12..13 (frontend page + lazy load) | 12, 13 |
| AC-10-14 (export buttons) | 13 |
| AC-10-15 (transactions list export) | 16 |
