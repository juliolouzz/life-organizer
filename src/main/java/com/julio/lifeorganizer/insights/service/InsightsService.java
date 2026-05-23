package com.julio.lifeorganizer.insights.service;

import com.julio.lifeorganizer.common.exception.InvalidQueryException;
import com.julio.lifeorganizer.insights.persistence.CategoryTotalRow;
import com.julio.lifeorganizer.insights.persistence.DailyBucketRow;
import com.julio.lifeorganizer.insights.persistence.TypeSumRow;
import com.julio.lifeorganizer.insights.web.dto.BucketTotal;
import com.julio.lifeorganizer.insights.web.dto.CategoryTotal;
import com.julio.lifeorganizer.insights.web.dto.PeriodTotals;
import com.julio.lifeorganizer.insights.web.dto.SummaryResponse;
import com.julio.lifeorganizer.transactions.domain.TransactionType;
import com.julio.lifeorganizer.transactions.persistence.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InsightsService {

    public enum Granularity { DAY, WEEK, MONTH }

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final TransactionRepository repository;

    public InsightsService(TransactionRepository repository) {
        this.repository = repository;
    }

    public SummaryResponse summary(Long userId, LocalDate from, LocalDate to) {
        requireValidRange(from, to);
        Totals current = totalsFor(userId, from, to);

        long span = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(span - 1);
        Totals previous = totalsFor(userId, prevFrom, prevTo);

        return new SummaryResponse(
                from, to,
                current.income(), current.expense(), current.savings(),
                netOf(current),
                current.incomeCount(), current.expenseCount(), current.savingsCount(),
                new PeriodTotals(prevFrom, prevTo,
                        previous.income(), previous.expense(), previous.savings(),
                        netOf(previous))
        );
    }

    private static BigDecimal netOf(Totals t) {
        return t.income().subtract(t.expense()).subtract(t.savings());
    }

    public List<CategoryTotal> byCategory(Long userId, LocalDate from, LocalDate to) {
        requireValidRange(from, to);
        return repository.sumByCategoryAndType(userId, from, to).stream()
                .map(r -> new CategoryTotal(r.category(), r.type(), scale(r.total()), r.count()))
                .toList();
    }

    public ByPeriodResult byPeriod(Long userId, LocalDate from, LocalDate to, Granularity requested) {
        requireValidRange(from, to);
        Granularity granularity = requested != null ? requested : autoGranularity(from, to);

        // Always aggregate by day in SQL; bucket up here. PG's index covers the day query
        // and the in-memory rollup is cheap up to a few years of rows.
        List<DailyBucketRow> daily = repository.sumByDayAndType(userId, from, to);

        Map<LocalDate, Map<TransactionType, BigDecimal>> grouped = new java.util.HashMap<>();
        for (DailyBucketRow row : daily) {
            LocalDate bucket = bucketStart(row.day(), granularity);
            grouped
                    .computeIfAbsent(bucket, k -> new EnumMap<>(TransactionType.class))
                    .merge(row.type(), row.total(), BigDecimal::add);
        }

        List<LocalDate> allBuckets = enumerateBuckets(from, to, granularity);
        List<BucketTotal> result = new ArrayList<>(allBuckets.size());
        for (LocalDate bucket : allBuckets) {
            Map<TransactionType, BigDecimal> sums = grouped.getOrDefault(bucket, Map.of());
            BigDecimal income = scale(sums.getOrDefault(TransactionType.INCOME, ZERO));
            BigDecimal expense = scale(sums.getOrDefault(TransactionType.EXPENSE, ZERO));
            BigDecimal savings = scale(sums.getOrDefault(TransactionType.SAVINGS, ZERO));
            result.add(new BucketTotal(bucket, income, expense, savings,
                    income.subtract(expense).subtract(savings)));
        }
        return new ByPeriodResult(result, granularity);
    }

    private static Granularity autoGranularity(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days <= 31) return Granularity.DAY;
        if (days <= 90) return Granularity.WEEK;
        return Granularity.MONTH;
    }

    private static LocalDate bucketStart(LocalDate d, Granularity g) {
        return switch (g) {
            case DAY -> d;
            case WEEK -> d.with(java.time.DayOfWeek.MONDAY);
            case MONTH -> d.withDayOfMonth(1);
        };
    }

    private static List<LocalDate> enumerateBuckets(LocalDate from, LocalDate to, Granularity g) {
        List<LocalDate> out = new ArrayList<>();
        LocalDate cursor = bucketStart(from, g);
        while (!cursor.isAfter(to)) {
            out.add(cursor);
            cursor = switch (g) {
                case DAY -> cursor.plusDays(1);
                case WEEK -> cursor.plusWeeks(1);
                case MONTH -> cursor.plusMonths(1);
            };
        }
        return out;
    }

    private Totals totalsFor(Long userId, LocalDate from, LocalDate to) {
        BigDecimal income = ZERO;
        BigDecimal expense = ZERO;
        BigDecimal savings = ZERO;
        long incomeCount = 0L;
        long expenseCount = 0L;
        long savingsCount = 0L;
        for (TypeSumRow row : repository.sumByType(userId, from, to)) {
            switch (row.type()) {
                case INCOME -> {
                    income = scale(row.total());
                    incomeCount = row.count();
                }
                case EXPENSE -> {
                    expense = scale(row.total());
                    expenseCount = row.count();
                }
                case SAVINGS -> {
                    savings = scale(row.total());
                    savingsCount = row.count();
                }
            }
        }
        return new Totals(income, expense, savings, incomeCount, expenseCount, savingsCount);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? ZERO : v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static void requireValidRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new InvalidQueryException("from and to are required");
        }
        if (from.isAfter(to)) {
            throw new InvalidQueryException("from must be on or before to");
        }
    }

    public record ByPeriodResult(List<BucketTotal> data, Granularity granularity) {
    }

    private record Totals(
            BigDecimal income, BigDecimal expense, BigDecimal savings,
            long incomeCount, long expenseCount, long savingsCount) {
    }
}
