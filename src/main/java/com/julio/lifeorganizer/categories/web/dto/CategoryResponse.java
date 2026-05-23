package com.julio.lifeorganizer.categories.web.dto;

import com.julio.lifeorganizer.categories.domain.CategoryKind;
import com.julio.lifeorganizer.categories.persistence.CategoryEntity;

public record CategoryResponse(Long id, String name, CategoryKind kind, boolean archived) {

    public static CategoryResponse from(CategoryEntity entity) {
        return new CategoryResponse(entity.getId(), entity.getName(), entity.getKind(), entity.isArchived());
    }
}
