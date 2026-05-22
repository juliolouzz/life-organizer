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

public record CreateTransactionRequest(
        @NotNull(message = "must not be null")
        @Positive(message = "must be greater than 0")
        @Digits(integer = 13, fraction = 2, message = "must have at most 13 integer and 2 decimal digits")
        BigDecimal amount,

        @NotNull(message = "must not be null")
        TransactionType type,

        @NotBlank(message = "must not be blank")
        @Size(max = 50, message = "must be at most 50 characters")
        String category,

        @NotBlank(message = "must not be blank")
        @Size(max = 255, message = "must be at most 255 characters")
        String description,

        @NotNull(message = "must not be null")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate transactionDate
) {
}
