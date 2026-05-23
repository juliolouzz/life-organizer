package com.julio.lifeorganizer.reports.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record YearOverYearReport(
        PeriodTotals thisYear,
        PeriodTotals lastYear,
        Deltas deltas,
        List<CategoryDelta> topCategoryDeltas) {

    public record PeriodTotals(int year, int month, TotalsBlock totals) {
    }

    public record Deltas(
            DeltaBlock income,
            DeltaBlock expense,
            DeltaBlock savings,
            DeltaBlock net) {
    }

    public record CategoryDelta(
            String name,
            BigDecimal thisYear,
            BigDecimal lastYear,
            DeltaBlock delta) {
    }
}
