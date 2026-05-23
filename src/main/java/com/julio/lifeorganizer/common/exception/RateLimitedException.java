package com.julio.lifeorganizer.common.exception;

public class RateLimitedException extends DomainException {

    public RateLimitedException() {
        super("Too many requests. Please try again later.", "RATE_LIMITED");
    }
}
