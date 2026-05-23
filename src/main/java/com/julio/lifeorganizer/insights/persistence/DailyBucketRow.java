package com.julio.lifeorganizer.insights.persistence;

import com.julio.lifeorganizer.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Per-day, per-type aggregate row. Service post-processes into BucketTotal. */
public record DailyBucketRow(LocalDate day, TransactionType type, BigDecimal total) {
}
