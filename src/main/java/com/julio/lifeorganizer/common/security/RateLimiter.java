package com.julio.lifeorganizer.common.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Simple in-memory sliding-window rate limiter.
 * Per (IP, endpoint key): allow up to N requests within a fixed window.
 *
 * Single-instance only; distributed limiting is deferred to a hosting slice.
 */
@Component
public class RateLimiter {

    public static final int LIMIT = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    /**
     * Records a hit and returns true if the request is within the limit.
     * Returns false if the request should be rejected.
     */
    public boolean tryAcquire(String ip, String endpoint) {
        return tryAcquire(ip, endpoint, Instant.now());
    }

    // Package-private overload exists for tests to inject a deterministic clock.
    boolean tryAcquire(String ip, String endpoint, Instant now) {
        String key = ip + "::" + endpoint;
        Deque<Instant> bucket = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (bucket) {
            Instant cutoff = now.minus(WINDOW);
            while (!bucket.isEmpty() && bucket.peekFirst().isBefore(cutoff)) {
                bucket.pollFirst();
            }
            if (bucket.size() >= LIMIT) {
                return false;
            }
            bucket.addLast(now);
            return true;
        }
    }

    /** For tests: wipe state. */
    void reset() {
        windows.clear();
    }
}
