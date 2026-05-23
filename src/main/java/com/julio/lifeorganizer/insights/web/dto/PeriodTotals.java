package com.julio.lifeorganizer.insights.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PeriodTotals(
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate from,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate to,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal net
) {
}
