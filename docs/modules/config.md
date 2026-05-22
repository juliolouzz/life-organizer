# Module: `config`

## Purpose

Spring `@Configuration` beans, `@ConfigurationProperties` records, and Spring Security setup. No business logic — just wiring.

## Package Tree

```
com.julio.lifeorganizer.config
├── JwtProperties.java          // @ConfigurationProperties("app.jwt"), @Validated record
├── PaginationProperties.java   // @ConfigurationProperties("app.pagination")
├── SecurityConfig.java         // SecurityFilterChain + PasswordEncoder bean
└── JacksonConfig.java          // (if needed) — module hardening; mostly via application.yml
```

## `JwtProperties` (ADR-011)

```java
@ConfigurationProperties("app.jwt")
@Validated
public record JwtProperties(
    @NotBlank @Size(min = 32) String secret,
    @NotNull Duration accessTtl,
    @NotNull Duration refreshTtl,
    @NotNull Duration clockSkew
) {}
```

- `app.jwt.secret` bound from env var `${JWT_SECRET}`
- `app.jwt.access-ttl: 15m` (default)
- `app.jwt.refresh-ttl: 7d` (default)
- `app.jwt.clock-skew: 30s` (default)

`@Validated` triggers Bean Validation at bind time — missing or short secrets **fail boot** (AC-A15, R1).

## `PaginationProperties`

```java
@ConfigurationProperties("app.pagination")
public record PaginationProperties(int defaultLimit, int maxLimit) {}
```

- `app.pagination.default-limit: 20`
- `app.pagination.max-limit: 100`

## `SecurityConfig`

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    JwtAuthenticationFilter jwtFilter,
                                    JwtAuthenticationEntryPoint entryPoint,
                                    JwtAccessDeniedHandler accessDenied) throws Exception { ... }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    AuthenticationManager authManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}
```

### Filter chain order

```
RequestIdFilter (order = -100)        // MDC.requestId
  -> JwtAuthenticationFilter           // BeforeFilter(UsernamePasswordAuthenticationFilter)
    -> ExceptionTranslationFilter      // routes AuthException to advice via HandlerExceptionResolver
      -> FilterSecurityInterceptor
        -> @RestController dispatch
```

### Public matchers (permitAll)

- `/api/v1/auth/register`
- `/api/v1/auth/login`
- `/api/v1/auth/refresh`
- `/actuator/health`
- (favicon and other static — N/A in API-only slice)

Everything else: `authenticated()`. CSRF disabled (stateless API). Session policy: `STATELESS`.

## application.yml structure

```yaml
spring:
  application.name: life-organizer
  datasource:
    url:      ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
  jpa:
    open-in-view: false                 # R7
    hibernate.ddl-auto: validate        # Flyway is source of truth
    properties.hibernate.jdbc.time_zone: UTC
  jackson:
    deserialization.fail-on-unknown-properties: false   # R10
    serialization.write-dates-as-timestamps: false

app:
  jwt:
    secret:      ${JWT_SECRET}
    access-ttl:  15m
    refresh-ttl: 7d
    clock-skew:  30s
  pagination:
    default-limit: 20
    max-limit:     100

management:
  endpoints.web.exposure.include: health
  endpoint.health.show-details: when-authorized
```

## Profiles

- `local` — overrides datasource URL for `localhost:5432`, sets human-readable logs, enables debug for `com.julio.lifeorganizer`
- `test` — used by integration tests; quieter Hibernate SQL logging

## Tests

| Test class | Tier | What it covers |
|---|---|---|
| `JwtPropertiesTest` | full int | blank secret -> BindException, < 32 chars -> BindException |
| `SecurityBeansTest` | full int | PasswordEncoder is BCryptPasswordEncoder strength 12 |
| `SecurityConfigTest` | full int | protected route -> 401 envelope, public route -> 200 |
| `ApplicationYamlTest` | full int | missing JWT_SECRET fails fast |
| `ProfileLoadingTest` | full int | test profile overrides bind correctly |
| `JacksonAndJpaDefaultsTest` | unit | unknown JSON fields silently dropped, dates serialize ISO |
