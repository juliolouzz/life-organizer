package com.julio.lifeorganizer.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "must not be blank") String email,
        @NotBlank(message = "must not be blank") String password
) {
}
