package com.julio.lifeorganizer.reports.web.dto;

import java.math.BigDecimal;

public record TotalsBlock(
        BigDecimal income,
        BigDecimal expense,
        BigDecimal savings,
        BigDecimal net,
        long transactionCount) {

    public static TotalsBlock zero() {
        return new TotalsBlock(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L);
    }
}
