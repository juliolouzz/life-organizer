package com.julio.lifeorganizer.common.exception;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ExceptionHierarchyTest {

    @Test
    void notFoundException_carriesMessageAndErrorCode() {
        NotFoundException ex = new NotFoundException("Transaction not found", "TRANSACTION_NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("Transaction not found");
        assertThat(ex.errorCode()).isEqualTo("TRANSACTION_NOT_FOUND");
    }

    @Test
    void invalidQueryException_alwaysUsesInvalidQueryCode() {
        assertThat(new InvalidQueryException("limit out of range").errorCode())
                .isEqualTo("INVALID_QUERY");
    }

    @Test
    void invalidCredentialsException_messageIsIdenticalRegardlessOfCause() {
        // Two instances must produce byte-identical message + code to prevent enumeration.
        InvalidCredentialsException a = new InvalidCredentialsException();
        InvalidCredentialsException b = new InvalidCredentialsException();
        assertThat(a.getMessage()).isEqualTo(b.getMessage());
        assertThat(a.errorCode()).isEqualTo(b.errorCode()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void allAuthExceptionsCarryDocumentedCodes() {
        assertThat(new InvalidTokenException("bad sig").errorCode()).isEqualTo("INVALID_TOKEN");
        assertThat(new TokenExpiredException().errorCode()).isEqualTo("TOKEN_EXPIRED");
        assertThat(new UnauthorizedException().errorCode()).isEqualTo("UNAUTHORIZED");
        assertThat(new UserNotFoundForTokenException().errorCode()).isEqualTo("USER_NOT_FOUND");
    }
}
