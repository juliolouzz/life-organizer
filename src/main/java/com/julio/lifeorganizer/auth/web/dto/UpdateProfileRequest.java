package com.julio.lifeorganizer.auth.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "must not be blank")
        @Size(min = 2, max = 100, message = "must be between 2 and 100 characters")
        String displayName,

        /**
         * Slice 13: optional. Omit to keep the current currency. Accepted
         * values: BRL, USD, EUR.
         */
        @Pattern(regexp = "BRL|USD|EUR", message = "must be one of BRL, USD, EUR")
        String currency,

        /**
         * Slice 14: optional anchor day for the user's accounting month
         * (1-31). Omit to keep the current value. Days that don't exist in
         * a given month are clamped to that month's last day by the client.
         */
        @Min(value = 1, message = "must be between 1 and 31")
        @Max(value = 31, message = "must be between 1 and 31")
        Integer monthBoundaryDay
) {
}
