package com.julio.lifeorganizer.transactions.web.dto;

import java.util.List;

/**
 * Outcome of a CSV import (Slice 7).
 * - inserted: rows that became real transactions
 * - skipped: rows that were rejected for validation reasons
 * - errors: per-line message; line numbers are 1-based and count the header row
 */
public record ImportResult(int inserted, int skipped, List<RowError> errors) {

    public record RowError(int line, String message) {
    }
}
