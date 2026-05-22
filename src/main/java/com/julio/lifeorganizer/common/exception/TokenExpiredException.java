package com.julio.lifeorganizer.common.exception;

public class TokenExpiredException extends AuthException {

    public TokenExpiredException() {
        super("Token has expired", "TOKEN_EXPIRED");
    }
}
