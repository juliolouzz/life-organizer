# Slice 13 - Implementation Plan

| Phase | Scope | Verify |
|---|---|---|
| 1 | V10 migration + Currency enum | mvn test passes existing tests |
| 2 | UserEntity + UserResponse + RegisterRequest + UpdateProfileRequest | Compile |
| 3 | AuthService.register + AccountService.updateProfile accept optional currency | Unit + integration tests |
| 4 | ReportsExportService passes currency into the PDF template | PDF asserts updated |
| 5 | Boot warning in FileMailService when dev-delivery off | Log line visible on boot |
| 6 | Frontend: AuthenticatedUser.currency, Money pipe overhaul | ng build clean |
| 7 | Frontend: register form currency picker | Manual smoke |
| 8 | Frontend: /account profile section currency dropdown | Manual smoke |
| 9 | Schema migration test update for V10 | Test green |
| 10 | mvn verify + ng build/lint/test + PR | All CI green |

## AC coverage

| AC | Phase |
|---|---|
| AC-13-1, AC-13-2 (register currency) | 3, 10 |
| AC-13-3, AC-13-4 (profile patch) | 3, 10 |
| AC-13-5 (transactions unchanged) | inherent |
| AC-13-6, AC-13-7 (PDF + CSV) | 4 |
| AC-13-8 (register UI) | 7 |
| AC-13-9 (profile UI) | 8 |
| AC-13-10 (mail-config warn) | 5 |
