package com.julio.lifeorganizer.budgets.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<BudgetEntity, Long> {

    List<BudgetEntity> findByUserIdAndYearAndMonthOrderByCategoryIdAsc(Long userId, int year, int month);

    Optional<BudgetEntity> findByIdAndUserId(Long id, Long userId);

    Optional<BudgetEntity> findByUserIdAndCategoryIdAndYearAndMonth(
            Long userId, Long categoryId, int year, int month);
}
