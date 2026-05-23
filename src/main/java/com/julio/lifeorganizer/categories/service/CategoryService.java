package com.julio.lifeorganizer.categories.service;

import com.julio.lifeorganizer.categories.persistence.CategoryEntity;
import com.julio.lifeorganizer.categories.persistence.CategoryRepository;
import com.julio.lifeorganizer.categories.web.dto.CategoryRequest;
import com.julio.lifeorganizer.categories.web.dto.CategoryResponse;
import com.julio.lifeorganizer.common.exception.ConflictException;
import com.julio.lifeorganizer.common.exception.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private static final String NOT_FOUND_CODE = "CATEGORY_NOT_FOUND";

    private final CategoryRepository repository;

    public CategoryService(CategoryRepository repository) {
        this.repository = repository;
    }

    public List<CategoryResponse> list(Long userId) {
        return repository.findByUserIdAndArchivedFalseOrderByNameAsc(userId).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse create(Long userId, CategoryRequest request) {
        String name = request.name().trim();
        repository.findByUserAndNameIgnoreCase(userId, name).ifPresent(existing -> {
            throw new ConflictException(
                    "Category '" + name + "' already exists", "CATEGORY_EXISTS");
        });
        CategoryEntity saved = repository.save(CategoryEntity.createNew(userId, name, request.kind()));
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse update(Long userId, Long id, CategoryRequest request) {
        CategoryEntity entity = require(userId, id);
        String newName = request.name().trim();
        if (!entity.getName().equalsIgnoreCase(newName)) {
            repository.findByUserAndNameIgnoreCase(userId, newName).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new ConflictException(
                            "Category '" + newName + "' already exists", "CATEGORY_EXISTS");
                }
            });
        }
        entity.rename(newName);
        entity.changeKind(request.kind());
        return CategoryResponse.from(repository.save(entity));
    }

    @Transactional
    public void archive(Long userId, Long id) {
        CategoryEntity entity = require(userId, id);
        entity.archive();
        repository.save(entity);
    }

    private CategoryEntity require(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Category not found", NOT_FOUND_CODE));
    }

    public CategoryEntity requireExisting(Long userId, Long id) {
        return require(userId, id);
    }
}
