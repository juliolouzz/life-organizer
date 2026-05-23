package com.julio.lifeorganizer.auth.domain;

/**
 * The set of currencies a user can pick (Slice 13). Display-only - amounts
 * are stored raw and the symbol changes only how the frontend / PDF report
 * formats them. Adding a new currency is two lines: add an enum value and
 * extend the CHECK constraint in a new migration.
 */
public enum Currency {
    BRL,
    USD,
    EUR;

    public static Currency parseOrDefault(String raw) {
        if (raw == null || raw.isBlank()) return BRL;
        try {
            return Currency.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BRL;
        }
    }
}
