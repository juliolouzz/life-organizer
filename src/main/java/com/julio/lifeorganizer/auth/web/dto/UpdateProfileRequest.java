package com.julio.lifeorganizer.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "must not be blank")
        @Size(min = 2, max = 100, message = "must be between 2 and 100 characters")
        String displayName
) {
}
