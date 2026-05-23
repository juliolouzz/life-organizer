package com.julio.lifeorganizer.recurring.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.julio.lifeorganizer.recurring.domain.Frequency;
import com.julio.lifeorganizer.recurring.persistence.RecurringTransactionEntity;
import com.julio.lifeorganizer.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringResponse(
        Long id,
        Long categoryId,
        String categoryName,
        BigDecimal amount,
        TransactionType type,
        String description,
        Frequency frequency,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate nextDueDate,
        boolean paused
) {

    public static RecurringResponse from(RecurringTransactionEntity entity, String categoryName) {
        return new RecurringResponse(
                entity.getId(), entity.getCategoryId(), categoryName,
                entity.getAmount(), entity.getType(), entity.getDescription(),
                entity.getFrequency(), entity.getStartDate(), entity.getEndDate(),
                entity.getNextDueDate(), entity.isPaused());
    }
}
