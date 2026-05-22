package com.julio.lifeorganizer.common.exception;

public class InvalidTokenException extends AuthException {

    public InvalidTokenException(String message) {
        super(message, "INVALID_TOKEN");
    }
}
