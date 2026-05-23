# Contributing

This is a personal learning project. The bar is: "would I be embarrassed to show
this in a code review?" If yes, fix it before merging.

## Read these before touching code

| File | Why |
|---|---|
| `CLAUDE.md` | Hard rules. Some are enforced by ArchUnit and the JaCoCo gate. |
| `docs/specs/slice-1-spec.txt` | Behavioural contract. If your change contradicts it, amend the spec first (date + reason at the bottom). |
| `docs/specs/slice-1-architecture.md` | 14 ADRs. The "why" behind every design choice. |
| `docs/modules/<area>.md` | Per-module overview (auth, transactions, common, config). |

## Workflow

1. Branch off `main`:
   - `feature/<topic>` for new functionality
   - `fix/<topic>` for bug fixes
   - `refactor/<topic>` for non-functional code changes
   - `chore/<topic>` for tooling, dependencies, CI
2. **Write a failing test first.** Production code only exists to satisfy a named test.
3. Use **Conventional Commits**: `feat:`, `fix:`, `refactor:`, `test:`, `chore:`, `docs:`.
   Imperative mood. Subject under 72 characters. No period. No emojis.
4. `mvn verify` must be green locally before opening a PR.
5. Open the PR using the template. Reference the spec section / acceptance criteria
   you closed.

## Hard rules (CI will reject violations)

| Rule | Enforced by |
|---|---|
| Constructor injection only - no `@Autowired` on fields | `ArchitectureTest.noFieldsAreAnnotatedWithAutowired` |
| All DTOs are Java records | `ArchitectureTest.allDtoClasses_areRecords` |
| Persistence never calls upward into service or web | `ArchitectureTest.persistence_doesNotCallUpward_intoServiceOrWeb` |
| Controllers never depend on repositories | `ArchitectureTest.controllersOnlyDelegateToServices_neverDirectlyAccessRepositories` |
| Service fields are private + final | `ArchitectureTest.serviceFieldsAreFinalAndPrivate` |
| 80%+ line coverage on `*.service.*` and `*.web.*` | JaCoCo `check-coverage` execution |
| No `printStackTrace` / `System.out.println` / hardcoded secrets | Hygiene scan + code review |
| No emojis in code, comments, commit messages, or docs | Code review |

## Spec discipline

If the change is **purely a bug fix** that brings code back in line with the spec:
just open the PR and reference the spec section.

If the change requires **changing behaviour the spec mandated**:
1. Open a separate spec amendment first (edit `docs/specs/slice-N-spec.txt`,
   add an entry to the AMENDMENTS LOG at the bottom with date and reason).
2. Get that approved.
3. Then open the code PR referencing the amendment.

## Local commands

```bash
mvn test                              # unit tests only (fast)
mvn verify                            # full: unit + integration + JaCoCo gate + ArchUnit
docker compose up -d postgres         # start Postgres 16 for `mvn spring-boot:run`
mvn spring-boot:run                   # boot the API on :8080 (needs JWT_SECRET env)
```

## Reporting issues

Use GitHub Issues. Include:
- Spec section / AC this relates to (if any)
- Reproduction steps or failing test
- Expected vs actual behaviour
