package com.julio.lifeorganizer.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email")
        @Size(max = 255, message = "must be at most 255 characters")
        String email,

        @NotBlank(message = "must not be blank")
        @Size(min = 8, max = 100, message = "must be between 8 and 100 characters")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$",
                message = "must contain at least one letter and one digit")
        String password,

        @NotBlank(message = "must not be blank")
        @Size(min = 2, max = 100, message = "must be between 2 and 100 characters")
        String displayName,

        /**
         * Slice 13: optional. Accepted values: BRL, USD, EUR. Omitting the
         * field (or any unknown value) defaults the user to BRL.
         */
        @Pattern(regexp = "BRL|USD|EUR", message = "must be one of BRL, USD, EUR")
        String currency
) {
}
