<!--
  Keep PRs focused. One logical change per PR. Reference the spec.
-->

## Summary

<!-- One or two sentences. WHY this change exists. The code shows HOW. -->

Closes #

## Spec & Acceptance Criteria

<!-- Which ACs from docs/specs/slice-N-spec.txt does this PR close or touch? -->
- AC-

## Type

- [ ] feat (new functionality)
- [ ] fix (bug fix)
- [ ] refactor (no behaviour change)
- [ ] test (test-only change)
- [ ] docs
- [ ] chore (build, deps, CI)

## Testing

- [ ] `mvn verify` green locally
- [ ] New unit tests added (or N/A and explained)
- [ ] New integration tests added (or N/A and explained)
- [ ] JaCoCo gate still met on `*.service.*` and `*.web.*` (>= 80% line coverage)

## Checklist

- [ ] Spec or ADR amended if behaviour changed (with date + reason)
- [ ] No hardcoded secrets
- [ ] No `printStackTrace` / `System.out.println`
- [ ] Conventional commit messages (`feat:`, `fix:`, ...)
- [ ] Constructor injection only; all DTOs are records
- [ ] No emojis in code, commits, or PR description

## Reviewer notes

<!-- Anything reviewers should know: trade-offs, accepted risks, follow-ups. -->
