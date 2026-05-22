package com.julio.lifeorganizer.common.exception;

public class ValidationException extends DomainException {

    public ValidationException(String message, String errorCode) {
        super(message, errorCode);
    }
}
