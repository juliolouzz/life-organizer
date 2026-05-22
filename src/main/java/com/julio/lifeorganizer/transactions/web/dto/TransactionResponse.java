package com.julio.lifeorganizer.transactions.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.julio.lifeorganizer.transactions.domain.TransactionType;
import com.julio.lifeorganizer.transactions.persistence.TransactionEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransactionResponse(
        Long id,
        BigDecimal amount,
        TransactionType type,
        String category,
        String description,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate transactionDate,
        Instant createdAt,
        Instant updatedAt
) {

    public static TransactionResponse from(TransactionEntity entity) {
        return new TransactionResponse(
                entity.getId(),
                entity.getAmount(),
                entity.getType(),
                entity.getCategory(),
                entity.getDescription(),
                entity.getTransactionDate(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
