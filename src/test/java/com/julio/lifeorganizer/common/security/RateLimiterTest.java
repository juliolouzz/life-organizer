package com.julio.lifeorganizer.common.security;

import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Pure unit test for the in-memory rate limiter - no Spring context needed.
// Uses the package-private overload so we can advance time deterministically.
@Tag("unit")
class RateLimiterTest {

    @Test
    void firstFiveRequestsFromSameIp_areAccepted_sixthIsRejected() {
        RateLimiter limiter = new RateLimiter();
        Instant t = Instant.parse("2026-05-23T10:00:00Z");
        for (int i = 0; i < RateLimiter.LIMIT; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4", "/api/v1/auth/login", t.plusSeconds(i)))
                    .as("attempt " + (i + 1) + " should be allowed").isTrue();
        }
        assertThat(limiter.tryAcquire("1.2.3.4", "/api/v1/auth/login", t.plusSeconds(10)))
                .as("6th attempt within window should be rejected").isFalse();
    }

    @Test
    void differentIps_haveIndependentBuckets() {
        RateLimiter limiter = new RateLimiter();
        Instant t = Instant.parse("2026-05-23T10:00:00Z");
        for (int i = 0; i < RateLimiter.LIMIT; i++) {
            limiter.tryAcquire("1.2.3.4", "/api/v1/auth/login", t.plusSeconds(i));
        }
        // Same endpoint, different IP - should still be allowed.
        assertThat(limiter.tryAcquire("5.6.7.8", "/api/v1/auth/login", t.plusSeconds(10))).isTrue();
    }

    @Test
    void differentEndpoints_sameIp_haveIndependentBuckets() {
        RateLimiter limiter = new RateLimiter();
        Instant t = Instant.parse("2026-05-23T10:00:00Z");
        for (int i = 0; i < RateLimiter.LIMIT; i++) {
            limiter.tryAcquire("1.2.3.4", "/api/v1/auth/login", t.plusSeconds(i));
        }
        // Different endpoint - independent bucket.
        assertThat(limiter.tryAcquire("1.2.3.4", "/api/v1/auth/register", t.plusSeconds(10))).isTrue();
    }

    @Test
    void requestsOutsideWindow_slideOut() {
        RateLimiter limiter = new RateLimiter();
        Instant t = Instant.parse("2026-05-23T10:00:00Z");
        for (int i = 0; i < RateLimiter.LIMIT; i++) {
            limiter.tryAcquire("1.2.3.4", "/api/v1/auth/login", t.plusSeconds(i));
        }
        // 16 minutes later - all 5 timestamps fall out of the 15-minute window.
        Instant later = t.plus(RateLimiter.WINDOW).plusSeconds(60);
        assertThat(limiter.tryAcquire("1.2.3.4", "/api/v1/auth/login", later)).isTrue();
    }
}
