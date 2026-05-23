package com.julio.lifeorganizer.common.security;

import com.julio.lifeorganizer.common.exception.RateLimitedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Throttles POST requests to a small set of public auth endpoints per client IP.
 * Routes RateLimitedException through HandlerExceptionResolver so the response
 * goes through GlobalExceptionHandler (returns 429 envelope).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // Endpoints under rate limit. Slice 8 covers the entire public-write auth surface:
    // both the "request a token" endpoints (login/register/forgot-password/resend) and
    // the "consume a token" endpoints (reset-password/verify-email). Limiting the
    // consume endpoints is defense in depth against brute-forcing the HS256 signature
    // and against DoS via repeated bcrypt work.
    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification");

    private final RateLimiter limiter;
    private final HandlerExceptionResolver resolver;
    private final boolean enabled;

    public RateLimitFilter(RateLimiter limiter, HandlerExceptionResolver resolver, boolean enabled) {
        this.limiter = limiter;
        this.resolver = resolver;
        this.enabled = enabled;
        if (!enabled) {
            log.info("RateLimitFilter is DISABLED (app.rate-limit.enabled=false).");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || !shouldLimit(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String ip = clientIp(request);
        if (!limiter.tryAcquire(ip, request.getRequestURI())) {
            resolver.resolveException(request, response, null, new RateLimitedException());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean shouldLimit(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && LIMITED_PATHS.contains(request.getRequestURI());
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
