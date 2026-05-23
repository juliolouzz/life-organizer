package com.julio.lifeorganizer.transactions.service;

import com.julio.lifeorganizer.categories.domain.CategoryKind;
import com.julio.lifeorganizer.categories.persistence.CategoryEntity;
import com.julio.lifeorganizer.categories.persistence.CategoryRepository;
import com.julio.lifeorganizer.common.exception.ValidationException;
import com.julio.lifeorganizer.transactions.domain.TransactionType;
import com.julio.lifeorganizer.transactions.persistence.TransactionEntity;
import com.julio.lifeorganizer.transactions.persistence.TransactionRepository;
import com.julio.lifeorganizer.transactions.web.dto.ImportResult;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports transactions from a CSV file (Slice 7).
 *
 * Expected columns (header row, case-insensitive, order can vary):
 *   date, amount, type, category, description
 * - description is optional; missing column or empty value -> ""
 * - date in either ISO yyyy-MM-dd or dd/MM/yyyy
 * - amount as plain decimal ("42.50"); comma decimal "42,50" also accepted
 * - type in {INCOME, EXPENSE, SAVINGS} (case-insensitive)
 *
 * Categories that don't exist are auto-created with kind=BOTH so they can be used
 * across types. The whole import runs in a single transaction; per-row failures
 * are collected and reported but don't rollback successful rows.
 */
@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);
    private static final int MAX_ROWS = 100_000;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public CsvImportService(TransactionRepository transactionRepository,
                            CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public ImportResult importFor(Long userId, InputStream csvInput) {
        List<ImportResult.RowError> errors = new ArrayList<>();
        int inserted = 0;
        int skipped = 0;

        try (CSVReader reader = new CSVReader(
                new BufferedReader(new InputStreamReader(csvInput, StandardCharsets.UTF_8)))) {

            String[] header = readNext(reader);
            if (header == null) {
                throw new ValidationException("CSV file is empty", "EMPTY_FILE");
            }
            Map<String, Integer> columns = mapColumns(header);
            requireColumn(columns, "date");
            requireColumn(columns, "amount");
            requireColumn(columns, "type");
            requireColumn(columns, "category");
            // description column is optional

            // Cache categories for this user to avoid one query per row.
            Map<String, CategoryEntity> existingByLowerName = new HashMap<>();
            categoryRepository.findByUserIdAndArchivedFalseOrderByNameAsc(userId)
                    .forEach(c -> existingByLowerName.put(c.getName().toLowerCase(Locale.ROOT), c));

            String[] row;
            int lineNumber = 1; // header is line 1; first data row is 2
            while ((row = readNext(reader)) != null) {
                lineNumber++;
                if (lineNumber - 1 > MAX_ROWS) {
                    errors.add(new ImportResult.RowError(lineNumber,
                            "File exceeds " + MAX_ROWS + " row limit; stopping"));
                    break;
                }
                if (isBlankRow(row)) {
                    continue; // silently skip blank lines
                }
                try {
                    TransactionEntity entity = parseRow(userId, row, columns, existingByLowerName);
                    transactionRepository.save(entity);
                    inserted++;
                } catch (ValidationException ex) {
                    errors.add(new ImportResult.RowError(lineNumber, ex.getMessage()));
                    skipped++;
                } catch (RuntimeException ex) {
                    log.warn("Unexpected error parsing CSV line {}: {}", lineNumber, ex.toString());
                    errors.add(new ImportResult.RowError(lineNumber,
                            "Could not parse row: " + ex.getMessage()));
                    skipped++;
                }
            }
        } catch (IOException ex) {
            throw new ValidationException("Could not read CSV: " + ex.getMessage(), "CSV_READ_ERROR");
        }

        return new ImportResult(inserted, skipped, errors);
    }

    private TransactionEntity parseRow(Long userId, String[] row, Map<String, Integer> columns,
                                       Map<String, CategoryEntity> categoryCache) {
        LocalDate date = parseDate(get(row, columns, "date"));
        BigDecimal amount = parseAmount(get(row, columns, "amount"));
        TransactionType type = parseType(get(row, columns, "type"));
        String categoryName = requireNonBlank(get(row, columns, "category"), "category");
        String description = optional(get(row, columns, "description"));

        // Auto-create category if missing.
        ensureCategory(userId, categoryName, categoryCache);

        return TransactionEntity.createNew(userId, amount, type,
                categoryName.trim(), description, date);
    }

    private void ensureCategory(Long userId, String name, Map<String, CategoryEntity> cache) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (cache.containsKey(key)) return;
        CategoryEntity created = categoryRepository.save(
                CategoryEntity.createNew(userId, name.trim(), CategoryKind.BOTH));
        cache.put(key, created);
    }

    // helpers

    private static Map<String, Integer> mapColumns(String[] header) {
        Map<String, Integer> out = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            String key = header[i] == null ? "" : header[i].trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) out.put(key, i);
        }
        return out;
    }

    private static void requireColumn(Map<String, Integer> columns, String name) {
        if (!columns.containsKey(name)) {
            throw new ValidationException(
                    "Missing required column '" + name + "'. Expected columns: " +
                            "date, amount, type, category, [description]",
                    "MISSING_COLUMN");
        }
    }

    private static String get(String[] row, Map<String, Integer> columns, String name) {
        Integer idx = columns.get(name);
        if (idx == null || idx >= row.length) return "";
        String value = row[idx];
        return value == null ? "" : value;
    }

    private static String requireNonBlank(String raw, String field) {
        String v = raw.trim();
        if (v.isEmpty()) throw new ValidationException(field + " must not be blank", "INVALID_ROW");
        return v;
    }

    private static String optional(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static LocalDate parseDate(String raw) {
        String value = raw.trim();
        if (value.isEmpty()) throw new ValidationException("date is required", "INVALID_ROW");
        try {
            return LocalDate.parse(value, ISO);
        } catch (java.time.format.DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(value, BR);
        } catch (java.time.format.DateTimeParseException ex) {
            throw new ValidationException(
                    "date '" + value + "' must be yyyy-MM-dd or dd/MM/yyyy",
                    "INVALID_ROW");
        }
    }

    private static BigDecimal parseAmount(String raw) {
        String value = raw.trim();
        if (value.isEmpty()) throw new ValidationException("amount is required", "INVALID_ROW");
        // Accept both 42.50 and 42,50 (BR locale). Strip thousand separators conservatively:
        // a "." or "," is a decimal separator if it appears in the last 3 chars.
        String cleaned = stripThousandSeparators(value);
        try {
            BigDecimal amount = new BigDecimal(cleaned);
            if (amount.signum() <= 0) {
                throw new ValidationException("amount must be > 0", "INVALID_ROW");
            }
            return amount;
        } catch (NumberFormatException ex) {
            throw new ValidationException("amount '" + value + "' is not a number", "INVALID_ROW");
        }
    }

    private static String stripThousandSeparators(String s) {
        // Find the rightmost separator; treat it as the decimal point.
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        int decimalAt = Math.max(lastDot, lastComma);
        if (decimalAt < 0) return s; // no separators at all
        // Replace all other separators (the thousand ones) with nothing.
        String left = s.substring(0, decimalAt).replace(".", "").replace(",", "");
        String right = s.substring(decimalAt + 1);
        // Whatever the original decimal char was (',' for BR locale, '.' for EN),
        // always emit a '.' so BigDecimal can parse it.
        return left + "." + right;
    }

    private static TransactionType parseType(String raw) {
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) throw new ValidationException("type is required", "INVALID_ROW");
        try {
            return TransactionType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    "type '" + raw + "' must be one of " + Arrays.toString(TransactionType.values()),
                    "INVALID_ROW");
        }
    }

    private static boolean isBlankRow(String[] row) {
        for (String c : row) {
            if (c != null && !c.isBlank()) return false;
        }
        return true;
    }

    private static String[] readNext(CSVReader reader) throws IOException {
        try {
            return reader.readNext();
        } catch (CsvValidationException ex) {
            throw new ValidationException("Malformed CSV: " + ex.getMessage(), "CSV_READ_ERROR");
        }
    }
}
