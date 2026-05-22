package com.julio.lifeorganizer.common.exception;

public class InvalidCredentialsException extends AuthException {

    // Identical message for wrong-password AND unknown-email cases to prevent enumeration.
    public InvalidCredentialsException() {
        super("Invalid email or password", "INVALID_CREDENTIALS");
    }
}
