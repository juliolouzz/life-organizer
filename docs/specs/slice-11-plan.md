# Slice 11 - Implementation Plan

| Phase | Scope | Verify |
|---|---|---|
| 1 | Add pom deps: spring-boot-starter-mail, logstash-logback-encoder, greenmail (test) | `mvn compile` clean |
| 2 | MailProperties record + MailService interface | Compile |
| 3 | FileMailService (wraps existing AuthDevDeliveryProperties) | Unit test writes to temp file |
| 4 | Mail Thymeleaf templates (password-reset, verify-email, change-email, account-restore) | Templates load via Spring's TemplateEngine |
| 5 | SmtpMailService using JavaMailSender + MimeMessageHelper | Unit test against GreenMail asserts subject + body parts |
| 6 | Refactor AuthService + AccountService: call MailService instead of devDelivery.write | mvn verify - existing integration tests still pass |
| 7 | application-prod.yml + logback-spring.xml (profile-aware appender) | Boot with --spring.profiles.active=prod and observe JSON output |
| 8 | Dockerfile multi-stage rewrite (alpine, non-root, tini, healthcheck) | `docker build` succeeds; `docker run` boots; whoami==lifeorg |
| 9 | docker-compose.full.yml updates | `docker compose -f docker-compose.full.yml up` boots; /actuator/health OK |
| 10 | .env.prod.example + docs/deployment.md | Linked from README; values match application-prod.yml |
| 11 | README + slice-9 / slice-10 carry-over cleanup | Confirm "What's next" points beyond Slice 11 |
| 12 | mvn verify + ng build/lint/test + push + PR | All 4 CI checks green; no new CodeQL alerts |

Each backend phase ends with `mvn verify`. Each Docker phase ends with a
clean `docker build` + a smoke `docker run`.

## Acceptance criteria coverage

| AC | Phase |
|---|---|
| AC-11-1 (file default) | 3, 6 |
| AC-11-2 (smtp wiring) | 5, 6 |
| AC-11-3 (best-effort SMTP) | 5 |
| AC-11-4 (HTML body + anchor link) | 4, 5 |
| AC-11-5 (prod profile) | 7 |
| AC-11-6 (non-root container) | 8 |
| AC-11-7 (compose smoke) | 9 |
| AC-11-8 (env template) | 10 |
| AC-11-9 (ArchUnit + JaCoCo) | 12 |
| AC-11-10 (CodeQL clean) | 12 |
