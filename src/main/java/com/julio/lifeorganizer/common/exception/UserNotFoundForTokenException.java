package com.julio.lifeorganizer.common.exception;

// Token is valid and unexpired, but the user it references no longer exists (R11).
public class UserNotFoundForTokenException extends AuthException {

    public UserNotFoundForTokenException() {
        super("User not found", "USER_NOT_FOUND");
    }
}
