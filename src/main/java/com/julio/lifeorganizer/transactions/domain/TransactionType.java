package com.julio.lifeorganizer.transactions.domain;

public enum TransactionType {
    INCOME,
    EXPENSE,
    /** Money set aside as savings. Reduces net cash but is still the user's money. */
    SAVINGS
}
