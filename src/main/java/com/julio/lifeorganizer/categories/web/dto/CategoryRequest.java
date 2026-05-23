package com.julio.lifeorganizer.categories.web.dto;

import com.julio.lifeorganizer.categories.domain.CategoryKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "must not be blank")
        @Size(max = 50, message = "must be at most 50 characters")
        String name,

        @NotNull(message = "must not be null")
        CategoryKind kind
) {
}
