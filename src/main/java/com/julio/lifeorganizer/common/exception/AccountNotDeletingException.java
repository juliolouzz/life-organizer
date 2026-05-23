package com.julio.lifeorganizer.common.exception;

/**
 * Raised when an account-restore request targets an account that has no
 * pending deletion to cancel. Maps to 409 Conflict.
 */
public class AccountNotDeletingException extends ConflictException {

    public AccountNotDeletingException() {
        super("Account has no pending deletion to restore", "ACCOUNT_NOT_DELETING");
    }
}
