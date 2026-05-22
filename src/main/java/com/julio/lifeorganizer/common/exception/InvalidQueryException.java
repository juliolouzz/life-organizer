package com.julio.lifeorganizer.common.exception;

public class InvalidQueryException extends ValidationException {

    public InvalidQueryException(String message) {
        super(message, "INVALID_QUERY");
    }
}
