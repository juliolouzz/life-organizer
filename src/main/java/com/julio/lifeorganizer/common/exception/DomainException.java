package com.julio.lifeorganizer.common.exception;

// Base for every exception thrown by the service layer that maps to a 4xx response.
// errorCode mirrors into ApiResponse.meta.code so clients can switch on it programmatically.
public abstract class DomainException extends RuntimeException {

    private final String errorCode;

    protected DomainException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
