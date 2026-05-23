package com.julio.lifeorganizer.reports.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyBucket(
        LocalDate date,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal savings) {

    public static DailyBucket zero(LocalDate date) {
        return new DailyBucket(date, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
