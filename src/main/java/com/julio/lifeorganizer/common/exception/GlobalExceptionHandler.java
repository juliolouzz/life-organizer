package com.julio.lifeorganizer.common.exception;

import com.julio.lifeorganizer.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// Maps every exception that escapes a controller into an ApiResponse envelope.
// Domain exceptions carry their own errorCode; framework exceptions map to documented codes.
// The fallback handler returns 500 with a generic message - never a stack trace (AC-X3).
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(),
                    fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
        }
        warn(req, "validation", fieldErrors.keySet().toString());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.validationError(fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> {
            String path = v.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            fieldErrors.put(field, v.getMessage());
        });
        warn(req, "constraint-violation", fieldErrors.keySet().toString());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.validationError(fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleMalformedJson(
            HttpMessageNotReadableException ex, HttpServletRequest req) {
        warn(req, "malformed-json", ex.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Malformed request body", "MALFORMED_REQUEST"));
    }

    @ExceptionHandler({ MethodArgumentTypeMismatchException.class, TypeMismatchException.class })
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(
            Exception ex, HttpServletRequest req) {
        warn(req, "type-mismatch", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Invalid query parameter", "INVALID_QUERY"));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        warn(req, "not-found", ex.errorCode());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), ex.errorCode()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Object>> handleConflict(ConflictException ex, HttpServletRequest req) {
        warn(req, "conflict", ex.errorCode());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), ex.errorCode()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDomainValidation(
            ValidationException ex, HttpServletRequest req) {
        warn(req, "domain-validation", ex.errorCode());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), ex.errorCode()));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuth(AuthException ex, HttpServletRequest req) {
        warn(req, "auth", ex.errorCode());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage(), ex.errorCode()));
    }

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ApiResponse<Object>> handleRateLimited(
            RateLimitedException ex, HttpServletRequest req) {
        warn(req, "rate-limited", ex.errorCode());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(ex.getMessage(), ex.errorCode()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleFallback(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", sanitize(req.getMethod()), sanitize(req.getRequestURI()), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred", "INTERNAL_ERROR"));
    }

    private static void warn(HttpServletRequest req, String category, String detail) {
        log.warn("{} on {} {} :: {}",
                category, sanitize(req.getMethod()), sanitize(req.getRequestURI()), sanitize(detail));
    }

    // Strips CR / LF / TAB from values that flow into log lines to defeat log injection.
    // An attacker could otherwise craft a URI with %0a to forge fake log entries.
    private static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('\n', '_').replace('\r', '_').replace('\t', '_');
    }
}
