package com.julio.lifeorganizer.reports.web.dto;

import com.julio.lifeorganizer.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.util.List;

public record CategoryTrendsReport(int monthsBack, List<CategoryTrendSeries> series) {

    public record CategoryTrendSeries(
            String name,
            TransactionType type,
            List<TrendPoint> points) {
    }

    public record TrendPoint(int year, int month, BigDecimal amount) {
    }
}
