package com.julio.lifeorganizer.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutAllRequest(
        @NotBlank(message = "must not be blank") String password) {
}
