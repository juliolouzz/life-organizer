package com.julio.lifeorganizer.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.pagination")
public record PaginationProperties(
        @Min(1) @Max(100) int defaultLimit,
        @Min(1) @Max(1000) int maxLimit
) {
}
