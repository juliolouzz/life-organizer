package com.julio.lifeorganizer.common.exception;

// Base for everything that maps to HTTP 401.
public abstract class AuthException extends DomainException {

    protected AuthException(String message, String errorCode) {
        super(message, errorCode);
    }
}
