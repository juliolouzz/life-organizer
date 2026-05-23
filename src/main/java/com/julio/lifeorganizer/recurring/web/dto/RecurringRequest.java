package com.julio.lifeorganizer.recurring.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.julio.lifeorganizer.recurring.domain.Frequency;
import com.julio.lifeorganizer.transactions.domain.TransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringRequest(
        @NotNull Long categoryId,
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @NotNull TransactionType type,
        @Size(max = 255) String description,
        @NotNull Frequency frequency,
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate
) {
}
