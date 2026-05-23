package com.julio.lifeorganizer.insights.persistence;

import com.julio.lifeorganizer.transactions.domain.TransactionType;
import java.math.BigDecimal;

/** Projection row for category aggregation. Mapped from JPQL constructor expression. */
public record CategoryTotalRow(String category, TransactionType type, BigDecimal total, long count) {
}
