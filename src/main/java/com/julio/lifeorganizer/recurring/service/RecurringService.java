package com.julio.lifeorganizer.recurring.service;

import com.julio.lifeorganizer.categories.persistence.CategoryEntity;
import com.julio.lifeorganizer.categories.persistence.CategoryRepository;
import com.julio.lifeorganizer.categories.service.CategoryService;
import com.julio.lifeorganizer.common.exception.NotFoundException;
import com.julio.lifeorganizer.common.exception.ValidationException;
import com.julio.lifeorganizer.recurring.persistence.RecurringTransactionEntity;
import com.julio.lifeorganizer.recurring.persistence.RecurringTransactionRepository;
import com.julio.lifeorganizer.recurring.web.dto.RecurringRequest;
import com.julio.lifeorganizer.recurring.web.dto.RecurringResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecurringService {

    private static final String NOT_FOUND_CODE = "RECURRING_NOT_FOUND";

    private final RecurringTransactionRepository repository;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;

    public RecurringService(RecurringTransactionRepository repository,
                            CategoryRepository categoryRepository,
                            CategoryService categoryService) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.categoryService = categoryService;
    }

    public List<RecurringResponse> list(Long userId) {
        List<RecurringTransactionEntity> rows = repository.findByUserIdOrderByNextDueDateAsc(userId);
        Map<Long, String> names = categoryNamesFor(userId, rows);
        return rows.stream()
                .map(r -> RecurringResponse.from(r, names.getOrDefault(r.getCategoryId(), "(deleted)")))
                .toList();
    }

    @Transactional
    public RecurringResponse create(Long userId, RecurringRequest request) {
        validateRange(request);
        CategoryEntity category = categoryService.requireExisting(userId, request.categoryId());
        RecurringTransactionEntity entity = RecurringTransactionEntity.createNew(
                userId, category.getId(), request.amount(), request.type(),
                request.description(), request.frequency(),
                request.startDate(), request.endDate());
        return RecurringResponse.from(repository.save(entity), category.getName());
    }

    @Transactional
    public RecurringResponse update(Long userId, Long id, RecurringRequest request) {
        validateRange(request);
        RecurringTransactionEntity entity = require(userId, id);
        CategoryEntity category = categoryService.requireExisting(userId, request.categoryId());
        entity.replaceWith(request.amount(), request.type(), request.description(),
                request.frequency(), request.endDate(), category.getId());
        return RecurringResponse.from(repository.save(entity), category.getName());
    }

    @Transactional
    public RecurringResponse pause(Long userId, Long id) {
        RecurringTransactionEntity entity = require(userId, id);
        entity.pause();
        String name = categoryRepository.findByIdAndUserId(entity.getCategoryId(), userId)
                .map(CategoryEntity::getName).orElse("(deleted)");
        return RecurringResponse.from(repository.save(entity), name);
    }

    @Transactional
    public RecurringResponse resume(Long userId, Long id) {
        RecurringTransactionEntity entity = require(userId, id);
        entity.resume();
        String name = categoryRepository.findByIdAndUserId(entity.getCategoryId(), userId)
                .map(CategoryEntity::getName).orElse("(deleted)");
        return RecurringResponse.from(repository.save(entity), name);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(require(userId, id));
    }

    private RecurringTransactionEntity require(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Recurring transaction not found", NOT_FOUND_CODE));
    }

    private static void validateRange(RecurringRequest request) {
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new ValidationException("endDate must be on or after startDate", "INVALID_RANGE");
        }
    }

    private Map<Long, String> categoryNamesFor(Long userId, List<RecurringTransactionEntity> rows) {
        Map<Long, String> out = new HashMap<>();
        rows.forEach(r -> categoryRepository.findByIdAndUserId(r.getCategoryId(), userId)
                .ifPresent(c -> out.put(c.getId(), c.getName())));
        return out;
    }
}
