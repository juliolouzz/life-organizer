# Slice 3 — Implementation Plan

| Phase | Scope | Verify |
|---|---|---|
| 1 | Backend insights: service + controller + 3 endpoints + DTOs | `mvn verify` green; new tests added |
| 2 | Frontend insights service + types | Compile clean |
| 3 | Frontend dashboard widgets (stat card, chart, donut) | Build clean |
| 4 | Frontend dashboard page + period selector + routing | Boot manually, run through period changes |
| 5 | Default landing change (/transactions -> /dashboard) | Update sidenav order |
| 6 | Tests: backend integration + frontend service unit tests | All green |
| 7 | Docs (README, CHANGELOG) + merge + tag v0.3.0 | CI green, PR merged |

Each phase ends with `mvn verify` (backend) or `npm run lint && npm test && npm run build` (frontend) passing.

## Acceptance criteria coverage

| AC | Phase |
|---|---|
| AC-I1..I9 (backend insights) | 1, 6 |
| AC-DF1 (default landing) | 5 |
| AC-DF2 (period default = current month) | 4 |
| AC-DF3..DF9 (cards + charts + recent + empty) | 3, 4 |
| AC-DF10 (period change re-fetches) | 4 |
| AC-DF11 (light + dark) | 4 (CSS variables already cover it) |
| AC-DF12 (lint clean) | 6 |
