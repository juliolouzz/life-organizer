package com.julio.lifeorganizer.insights.web.dto;

import com.julio.lifeorganizer.transactions.domain.TransactionType;
import java.math.BigDecimal;

public record CategoryTotal(String category, TransactionType type, BigDecimal total, long count) {
}
