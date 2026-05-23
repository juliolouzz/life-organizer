package com.julio.lifeorganizer.insights.persistence;

import com.julio.lifeorganizer.transactions.domain.TransactionType;
import java.math.BigDecimal;

/** Projection row for sum + count grouped by type. */
public record TypeSumRow(TransactionType type, BigDecimal total, long count) {
}
