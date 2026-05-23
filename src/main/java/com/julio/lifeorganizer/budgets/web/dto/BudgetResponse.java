package com.julio.lifeorganizer.budgets.web.dto;

import com.julio.lifeorganizer.budgets.persistence.BudgetEntity;
import java.math.BigDecimal;

public record BudgetResponse(
        Long id,
        Long categoryId,
        String categoryName,
        BigDecimal amount,
        int month,
        int year
) {

    public static BudgetResponse from(BudgetEntity entity, String categoryName) {
        return new BudgetResponse(entity.getId(), entity.getCategoryId(), categoryName,
                entity.getAmount(), entity.getMonth(), entity.getYear());
    }
}
