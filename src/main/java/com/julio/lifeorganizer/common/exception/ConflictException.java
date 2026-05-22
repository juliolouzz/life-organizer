package com.julio.lifeorganizer.common.exception;

public class ConflictException extends DomainException {

    public ConflictException(String message, String errorCode) {
        super(message, errorCode);
    }
}
