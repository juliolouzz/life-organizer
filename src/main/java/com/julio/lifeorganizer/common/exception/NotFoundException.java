package com.julio.lifeorganizer.common.exception;

public class NotFoundException extends DomainException {

    public NotFoundException(String message, String errorCode) {
        super(message, errorCode);
    }
}
