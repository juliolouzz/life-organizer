package com.julio.lifeorganizer.insights.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BucketTotal(
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate bucket,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal net
) {
}
