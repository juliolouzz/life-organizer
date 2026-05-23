package com.julio.lifeorganizer.reports.web.dto;

import com.julio.lifeorganizer.transactions.domain.TransactionType;
import java.math.BigDecimal;

public record CategoryAmount(String name, TransactionType type, BigDecimal amount) {
}
