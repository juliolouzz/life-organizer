package com.julio.lifeorganizer.reports.web.dto;

import java.util.List;

public record SummaryReport(
        int year,
        int month,
        TotalsBlock totals,
        List<CategoryAmount> topCategories,
        List<DailyBucket> daily) {
}
