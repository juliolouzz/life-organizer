package com.julio.lifeorganizer.common.exception;

public class UnauthorizedException extends AuthException {

    public UnauthorizedException() {
        super("Authentication required", "UNAUTHORIZED");
    }
}
