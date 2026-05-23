package com.julio.lifeorganizer.budgets.web.dto;

import java.math.BigDecimal;

/** One row of "budget vs actual" for the dashboard widget. */
public record BudgetStatusItem(
        Long budgetId,
        Long categoryId,
        String categoryName,
        BigDecimal budgeted,
        BigDecimal spent,
        BigDecimal remaining,
        int percent
) {
}
