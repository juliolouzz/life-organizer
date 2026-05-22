# Module: `common`

## Purpose

Cross-cutting scaffolding used by every other module: response envelope, exception hierarchy, global exception handler, request-id MDC filter.

> Spec sections: §5 (envelope), §8 AC-X1..X4

## Package Tree

```
com.julio.lifeorganizer.common
├── api/
│   ├── ApiResponse.java                 // record envelope { success, data, message, meta }
│   └── PageMeta.java                    // record { nextCursor, limit }
├── exception/
│   ├── DomainException.java             // abstract base, RuntimeException
│   ├── NotFoundException.java
│   ├── ConflictException.java
│   ├── ValidationException.java
│   ├── InvalidQueryException.java       // extends ValidationException
│   ├── AuthException.java               // abstract auth base
│   ├── InvalidCredentialsException.java
│   ├── InvalidTokenException.java
│   ├── TokenExpiredException.java
│   ├── UnauthorizedException.java
│   ├── UserNotFoundForTokenException.java
│   └── GlobalExceptionHandler.java      // @RestControllerAdvice
└── logging/
    ├── RequestIdFilter.java             // OncePerRequestFilter, MDC.requestId
    └── LoggingConfig.java               // FilterRegistrationBean with order
```

## `ApiResponse<T>` (spec §5)

```java
public record ApiResponse<T>(
    boolean success,
    T data,
    String message,
    Map<String, Object> meta
) {
    public static <T> ApiResponse<T> ok(T data);
    public static <T> ApiResponse<T> ok(T data, Map<String, Object> meta);
    public static <T> ApiResponse<T> paged(List<T> items, PageMeta page); // wraps items as data
    public static <T> ApiResponse<T> error(String message, String code);
    public static <T> ApiResponse<T> validationError(Map<String, String> fieldErrors);
}
```

**Every endpoint** in Slice 1 returns this shape — success and error alike (AC-X1).

## `PageMeta`

```java
public record PageMeta(String nextCursor, int limit) {}
```

Used only by paginated list endpoints. Embedded in `ApiResponse.meta`.

## Exception Hierarchy (ADR-007)

```
RuntimeException
└── DomainException(message, errorCode)
    ├── NotFoundException                  -> 404
    ├── ConflictException                  -> 409
    ├── ValidationException                -> 400
    │   └── InvalidQueryException          -> 400 INVALID_QUERY
    └── AuthException                      -> 401
        ├── InvalidCredentialsException    -> 401 INVALID_CREDENTIALS
        ├── InvalidTokenException          -> 401 INVALID_TOKEN
        ├── TokenExpiredException          -> 401 TOKEN_EXPIRED
        ├── UnauthorizedException          -> 401 UNAUTHORIZED
        └── UserNotFoundForTokenException  -> 401 USER_NOT_FOUND
```

Every exception carries a string `errorCode()` mirrored in `meta.code`. The class hierarchy determines the HTTP status; the `errorCode` field disambiguates within the status.

## `GlobalExceptionHandler` (ADR-007)

`@RestControllerAdvice` mapping:

| Exception (or category) | HTTP | Body |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `validationError(fieldMap)` |
| `HttpMessageNotReadableException` | 400 | `error("Malformed request body", "MALFORMED_REQUEST")` |
| `MethodArgumentTypeMismatchException` | 400 | `error("Invalid query parameter", "INVALID_QUERY")` |
| `ConstraintViolationException` | 400 | `validationError(fieldMap)` |
| `NotFoundException` | 404 | `error(ex.getMessage(), ex.errorCode())` |
| `ConflictException` | 409 | same |
| `ValidationException` / `InvalidQueryException` | 400 | same |
| `AuthException` and subtypes | 401 | same |
| `Exception` (fallback) | 500 | `error("An unexpected error occurred", "INTERNAL_ERROR")` — no stack, no SQL leaked (AC-X3) |

### Logging contract (AC-X4)

| Status range | Level | What's logged |
|---|---|---|
| 4xx | WARN | request URI, exception class, error code |
| 5xx | ERROR | request URI, MDC.userId (if available), full stack trace |

`requestId` is auto-attached by MDC from `RequestIdFilter`.

## `RequestIdFilter`

`OncePerRequestFilter` ordered **before** Spring Security's filter chain. Generates `UUID.randomUUID().toString()`, puts it in `MDC` under key `requestId`, and clears in a `finally`. Logback pattern in `application.yml` includes `%X{requestId}`.

## Acceptance Criteria Coverage

| AC | Owned by |
|---|---|
| AC-X1 (envelope) | `ApiResponse` + every controller |
| AC-X2 (validation flat map) | `GlobalExceptionHandler.handleValidation` |
| AC-X3 (no leak on 500) | `GlobalExceptionHandler.handleFallback` |
| AC-X4 (structured ERROR log) | `GlobalExceptionHandler` + `RequestIdFilter` |

## Tests

| Test class | Tier | What it covers |
|---|---|---|
| `ApiResponseTest` | unit | each factory returns correct shape |
| `PageMetaTest` | unit | record values |
| `DomainExceptionHierarchyTest` | unit | each subclass carries correct errorCode |
| `AuthExceptionTest` | unit | parameterized over the 5 auth exceptions |
| `GlobalExceptionHandlerValidationTest` | unit (mvc) | 400 with flat field map |
| `GlobalExceptionHandlerLoggingTest` | unit | log level + URI assertions via Logback ListAppender |
| `RequestIdFilterTest` | unit | MDC populated then cleared |
| `RequestIdFilterIntegrationTest` | full int | requestId appears in actuator log lines |
