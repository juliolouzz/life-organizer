package com.julio.lifeorganizer.transactions.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.julio.lifeorganizer.transactions.domain.TransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

// PUT is full-replace; identical validation as CreateTransactionRequest. Kept as a
// separate type so future versions can diverge (PATCH would change this).
public record UpdateTransactionRequest(
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @NotNull TransactionType type,
        @NotBlank @Size(max = 50) String category,
        @NotBlank @Size(max = 255) String description,
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate transactionDate
) {
}
