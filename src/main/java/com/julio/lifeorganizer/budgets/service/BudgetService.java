package com.julio.lifeorganizer.budgets.service;

import com.julio.lifeorganizer.budgets.persistence.BudgetEntity;
import com.julio.lifeorganizer.budgets.persistence.BudgetRepository;
import com.julio.lifeorganizer.budgets.web.dto.BudgetRequest;
import com.julio.lifeorganizer.budgets.web.dto.BudgetResponse;
import com.julio.lifeorganizer.budgets.web.dto.BudgetStatusItem;
import com.julio.lifeorganizer.categories.persistence.CategoryEntity;
import com.julio.lifeorganizer.categories.persistence.CategoryRepository;
import com.julio.lifeorganizer.categories.service.CategoryService;
import com.julio.lifeorganizer.common.exception.ConflictException;
import com.julio.lifeorganizer.common.exception.NotFoundException;
import com.julio.lifeorganizer.transactions.persistence.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BudgetService {

    private static final String NOT_FOUND_CODE = "BUDGET_NOT_FOUND";

    private final BudgetRepository repository;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final TransactionRepository transactionRepository;

    public BudgetService(BudgetRepository repository,
                         CategoryRepository categoryRepository,
                         CategoryService categoryService,
                         TransactionRepository transactionRepository) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.categoryService = categoryService;
        this.transactionRepository = transactionRepository;
    }

    public List<BudgetResponse> list(Long userId, int year, int month) {
        List<BudgetEntity> rows = repository.findByUserIdAndYearAndMonthOrderByCategoryIdAsc(userId, year, month);
        Map<Long, String> nameById = categoryNamesFor(userId, rows);
        return rows.stream()
                .map(b -> BudgetResponse.from(b, nameById.getOrDefault(b.getCategoryId(), "(deleted)")))
                .toList();
    }

    @Transactional
    public BudgetResponse create(Long userId, BudgetRequest request) {
        CategoryEntity category = categoryService.requireExisting(userId, request.categoryId());
        repository.findByUserIdAndCategoryIdAndYearAndMonth(
                userId, request.categoryId(), request.year(), request.month())
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "Budget already exists for that category and month", "BUDGET_EXISTS");
                });
        BudgetEntity saved = repository.save(BudgetEntity.createNew(
                userId, category.getId(), request.amount(), request.month(), request.year()));
        return BudgetResponse.from(saved, category.getName());
    }

    @Transactional
    public BudgetResponse update(Long userId, Long id, BudgetRequest request) {
        BudgetEntity entity = require(userId, id);
        // Only amount mutates - moving a budget across categories/months means delete + recreate.
        entity.changeAmount(request.amount());
        BudgetEntity saved = repository.save(entity);
        String name = categoryRepository.findByIdAndUserId(entity.getCategoryId(), userId)
                .map(CategoryEntity::getName).orElse("(deleted)");
        return BudgetResponse.from(saved, name);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(require(userId, id));
    }

    public List<BudgetStatusItem> statusFor(Long userId, int year, int month) {
        List<BudgetEntity> budgets =
                repository.findByUserIdAndYearAndMonthOrderByCategoryIdAsc(userId, year, month);
        if (budgets.isEmpty()) return List.of();

        Map<Long, String> names = categoryNamesFor(userId, budgets);

        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        // Fetch actuals per category (EXPENSE only - savings doesn't count against an expense budget).
        Map<String, BigDecimal> spentByCategoryLower = new HashMap<>();
        transactionRepository.sumByCategoryAndType(userId, from, to).forEach(row -> {
            if (row.type() == com.julio.lifeorganizer.transactions.domain.TransactionType.EXPENSE) {
                spentByCategoryLower.merge(row.category().toLowerCase(),
                        row.total().setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
            }
        });

        List<BudgetStatusItem> out = new ArrayList<>(budgets.size());
        for (BudgetEntity b : budgets) {
            String name = names.getOrDefault(b.getCategoryId(), "(deleted)");
            BigDecimal spent = spentByCategoryLower.getOrDefault(name.toLowerCase(),
                    BigDecimal.ZERO.setScale(2));
            BigDecimal remaining = b.getAmount().subtract(spent).setScale(2, RoundingMode.HALF_UP);
            int percent = b.getAmount().signum() == 0
                    ? 0
                    : spent.multiply(BigDecimal.valueOf(100))
                        .divide(b.getAmount(), 0, RoundingMode.HALF_UP).intValue();
            out.add(new BudgetStatusItem(b.getId(), b.getCategoryId(), name,
                    b.getAmount().setScale(2, RoundingMode.HALF_UP), spent, remaining, percent));
        }
        return out;
    }

    private BudgetEntity require(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Budget not found", NOT_FOUND_CODE));
    }

    private Map<Long, String> categoryNamesFor(Long userId, List<BudgetEntity> budgets) {
        Map<Long, String> out = new HashMap<>();
        budgets.forEach(b -> categoryRepository.findByIdAndUserId(b.getCategoryId(), userId)
                .ifPresent(c -> out.put(c.getId(), c.getName())));
        return out;
    }
}
