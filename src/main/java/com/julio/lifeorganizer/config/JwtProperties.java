package com.julio.lifeorganizer.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// JWT configuration bound from application.yml under "app.jwt".
// Secret length is validated at boot - the app fails fast if JWT_SECRET is missing
// or too short, instead of crashing the first time a token is signed (AC-A15, R1).
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "JWT secret must be at least 32 characters") String secret,
        @NotNull Duration accessTtl,
        @NotNull Duration refreshTtl,
        @NotNull Duration clockSkew
) {
}
