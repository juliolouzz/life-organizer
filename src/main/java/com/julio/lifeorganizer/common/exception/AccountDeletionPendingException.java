package com.julio.lifeorganizer.common.exception;

import java.time.Instant;

/**
 * Raised when a request is made for a user account whose deletion has been
 * scheduled and not yet cleared. Always returns 403 - the token may be
 * valid but the account state forbids access. Handled by a dedicated
 * branch in GlobalExceptionHandler.
 */
public class AccountDeletionPendingException extends AuthException {

    private final Instant deletionScheduledAt;

    public AccountDeletionPendingException(Instant deletionScheduledAt) {
        super("Account is scheduled for deletion", "ACCOUNT_DELETION_PENDING");
        this.deletionScheduledAt = deletionScheduledAt;
    }

    public Instant getDeletionScheduledAt() {
        return deletionScheduledAt;
    }
}
