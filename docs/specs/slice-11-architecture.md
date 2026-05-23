# Slice 11 - Architecture

Companion to `slice-11-spec.txt`. Records the design choices before the code
lands.

## ADRs

### ADR-S11-01 - MailService abstraction with two interchangeable backends

```
AuthService / AccountService
        |
        v
   MailService (interface)
   /              \
FileMailService   SmtpMailService
   |                  |
   v                  v
.tmp/auth-dev-     JavaMailSender ---> SMTP server
   links.txt           (Thymeleaf-rendered HTML)
```

Selected by `app.mail.provider`:
- `file` (default) - keeps the existing dev workflow
- `smtp` - production delivery via any SMTP server

**Why Spring's `JavaMailSender` rather than a vendor SDK:** any SMTP server
works. The operator picks Gmail, SendGrid, Mailgun, Mailtrap (dev), SES,
or self-hosted Postfix - the code doesn't change. The provider lock-in
debt is paid entirely by environment variables, where lock-in is cheap.

### ADR-S11-02 - Mail failures never surface to callers

`SmtpMailService.send*` catches `MailException` and logs a WARN with the
message id, recipient, and SMTP code. The calling endpoint still returns
its documented success response. This matches the anti-enumeration
behavior Slice 8 established for `/forgot-password`: a 200 response means
"we tried", not "we delivered." If we surfaced delivery failures we would
also leak whether the email exists (slow on hit, fast on miss).

A future operational slice can introduce a persistent outbox / retry, at
which point the WARN becomes the trigger for a re-send job.

### ADR-S11-03 - HTML + plain text mail bodies via Thymeleaf

Each link type gets one HTML template (`templates/mail/<kind>.html`). The
SMTP service renders the template, then uses Spring's `MimeMessageHelper`
to attach a plain-text alternative derived from the HTML (strip tags +
condense whitespace). Spam filters strongly prefer mails with both.

Templates render with a minimal model (`displayName`, `link`, optional
`expiryHuman`). No CSS frameworks - just inline styles that work in Gmail,
Apple Mail, and Outlook.

### ADR-S11-04 - Profile-driven config, JSON logs gated by prod

`application-prod.yml` is the only profile-specific config that changes
delivery / logging defaults:

```
spring:
  main:
    banner-mode: off
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
server:
  error:
    include-message: never
    include-stacktrace: never
    include-exception: false
app:
  mail:
    provider: smtp
  auth:
    dev-delivery:
      enabled: false
logging:
  config: classpath:logback-spring.xml
```

`logback-spring.xml` defines two appenders. The `<springProfile name="prod">`
block enables the `LogstashEncoder` JSON appender; everything else falls
through to the existing human-readable pattern.

### ADR-S11-05 - Non-root, minimal Docker image

Stage 1 (build): `eclipse-temurin:21-jdk-alpine` + Maven runs
`mvn package -DskipTests`. Skipping tests is safe because CI has
already run them on the same commit before the image is built.

Stage 2 (runtime): `eclipse-temurin:21-jre-alpine` adds:
- `tini` (PID 1) for proper signal forwarding
- A dedicated `lifeorg` user (`uid=10001 gid=10001`)
- `COPY --chown=lifeorg:lifeorg` to keep file ownership tight
- `HEALTHCHECK CMD wget -qO- http://localhost:8080/actuator/health || exit 1`
- `ENTRYPOINT ["tini","--","java","-jar","/app/app.jar"]`
- `JAVA_TOOL_OPTIONS` set to `-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom`

The same image runs on a 256 MB Fly.io machine and a 4 GB VPS - the JVM
respects the cgroup limit.

### ADR-S11-06 - No refresh-token revocation yet

The token-version column / logout-everywhere story is intentionally
**out of scope**. It's a small, isolated slice and bundling it in here
would slow the SMTP wiring that actually unblocks sharing. Documented
in the spec; remains the leading candidate for Slice 12.

## Package layout (delta from Slice 10)

```
com.julio.lifeorganizer
└── mail                                      (new)
    ├── MailService.java                      (interface)
    ├── FileMailService.java                  (@ConditionalOnProperty file, default)
    ├── SmtpMailService.java                  (@ConditionalOnProperty smtp)
    └── MailProperties.java                   (@ConfigurationProperties("app.mail"))
```

The existing `AuthDevDeliveryProperties` stays. `FileMailService` consumes
it; `SmtpMailService` ignores it.

## Filter chain (no change)

Mail delivery happens after the controller responds. The HTTP filter
stack is unchanged.

## Test plan summary

- `FileMailServiceTest` - unit: writes each link kind to a temp file.
- `SmtpMailServiceTest` - unit: GreenMail in-memory SMTP server,
  asserts subject + HTML body + plain text alternative.
- `MailServiceWiringTest` - integration: with `app.mail.provider=file`
  the FileMailService bean is active; with `=smtp` the SmtpMailService
  bean is active. Verified via @ConditionalOnProperty.
- Existing `AuthCompletenessIntegrationTest` and
  `AccountManagementIntegrationTest` keep passing without changes; they
  observe the user-facing 200 responses, not the mail mechanism.

## Risks accepted

| # | Risk | Mitigation |
|---|------|-----------|
| R-S11-1 | Best-effort SMTP can drop mails on transient failures | Documented; future slice adds outbox + retry |
| R-S11-2 | Operators with broken SMTP get silent delivery failures | WARN log surfaces them; deployment guide recommends sending a test mail post-deploy |
| R-S11-3 | Alpine + glibc edge cases | eclipse-temurin builds for alpine target are stable; no native libs in the app |
| R-S11-4 | JSON log format change may break existing log shippers | Only active in prod profile; local + test profiles unchanged |
| R-S11-5 | spring-boot-starter-mail brings in JavaMail + activation API | ~2 MB of deps, accepted |
