package com.julio.lifeorganizer.reports.service;

import com.julio.lifeorganizer.insights.persistence.CategoryTotalRow;
import com.julio.lifeorganizer.insights.persistence.DailyBucketRow;
import com.julio.lifeorganizer.insights.persistence.TypeSumRow;
import com.julio.lifeorganizer.reports.web.dto.CategoryAmount;
import com.julio.lifeorganizer.reports.web.dto.CategoryTrendsReport;
import com.julio.lifeorganizer.reports.web.dto.CategoryTrendsReport.CategoryTrendSeries;
import com.julio.lifeorganizer.reports.web.dto.CategoryTrendsReport.TrendPoint;
import com.julio.lifeorganizer.reports.web.dto.DailyBucket;
import com.julio.lifeorganizer.reports.web.dto.DeltaBlock;
import com.julio.lifeorganizer.reports.web.dto.SummaryReport;
import com.julio.lifeorganizer.reports.web.dto.TotalsBlock;
import com.julio.lifeorganizer.reports.web.dto.YearOverYearReport;
import com.julio.lifeorganizer.reports.web.dto.YearOverYearReport.CategoryDelta;
import com.julio.lifeorganizer.reports.web.dto.YearOverYearReport.Deltas;
import com.julio.lifeorganizer.reports.web.dto.YearOverYearReport.PeriodTotals;
import com.julio.lifeorganizer.transactions.domain.TransactionType;
import com.julio.lifeorganizer.transactions.persistence.TransactionEntity;
import com.julio.lifeorganizer.transactions.persistence.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side aggregation for Slice 10 reports. All queries scope by JWT
 * subject (the caller passes the userId from the principal). Soft-deleted
 * transactions are filtered out at the SQL level.
 */
@Service
@Transactional(readOnly = true)
public class ReportsService {

    private static final int TOP_CATEGORY_LIMIT = 5;
    private static final int DECIMAL_SCALE = 2;

    private final TransactionRepository transactions;

    public ReportsService(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    public SummaryReport monthlySummary(long userId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        return new SummaryReport(
                year,
                month,
                totalsForPeriod(userId, ym),
                topCategoriesForPeriod(userId, ym),
                dailyBucketsForPeriod(userId, ym));
    }

    public YearOverYearReport yearOverYear(long userId, int year, int month) {
        YearMonth current = YearMonth.of(year, month);
        YearMonth previous = current.minusYears(1);
        TotalsBlock currentTotals = totalsForPeriod(userId, current);
        TotalsBlock previousTotals = totalsForPeriod(userId, previous);
        return new YearOverYearReport(
                new PeriodTotals(current.getYear(), current.getMonthValue(), currentTotals),
                new PeriodTotals(previous.getYear(), previous.getMonthValue(), previousTotals),
                new Deltas(
                        delta(currentTotals.income(), previousTotals.income()),
                        delta(currentTotals.expense(), previousTotals.expense()),
                        delta(currentTotals.savings(), previousTotals.savings()),
                        delta(currentTotals.net(), previousTotals.net())),
                topCategoryDeltas(userId, current, previous));
    }

    public CategoryTrendsReport trends(long userId, int monthsBack) {
        if (monthsBack != 6 && monthsBack != 12) {
            throw new IllegalArgumentException("monthsBack must be 6 or 12");
        }
        YearMonth end = YearMonth.now();
        YearMonth start = end.minusMonths(monthsBack - 1L);
        LocalDate from = start.atDay(1);
        LocalDate to = end.atEndOfMonth();

        // Aggregate (year, month, category, type) -> sum in memory. Volumes are
        // tens of rows per category per month at personal-data scale, so the
        // savings from doing this in SQL would be marginal and the dialect
        // portability is not worth the EXTRACT-vs-year(...) compatibility work.
        record Key(int year, int month, String category, TransactionType type) {
        }
        Map<Key, BigDecimal> sums = new LinkedHashMap<>();
        for (TransactionEntity t : transactions.findInWindowForTrends(userId, from, to)) {
            Key key = new Key(
                    t.getTransactionDate().getYear(),
                    t.getTransactionDate().getMonthValue(),
                    t.getCategory(),
                    t.getType());
            sums.merge(key, t.getAmount(), BigDecimal::add);
        }

        // Re-group by (category, type) so each series collects its own points.
        record SeriesKey(String category, TransactionType type) {
        }
        Map<SeriesKey, List<TrendPoint>> grouped = new LinkedHashMap<>();
        sums.forEach((key, total) -> grouped
                .computeIfAbsent(new SeriesKey(key.category(), key.type()), k -> new ArrayList<>())
                .add(new TrendPoint(key.year(), key.month(), scale(total))));

        List<CategoryTrendSeries> series = new ArrayList<>(grouped.size());
        grouped.forEach((key, points) -> series.add(
                new CategoryTrendSeries(key.category(), key.type(), points)));
        series.sort(Comparator.comparing(CategoryTrendSeries::name));
        return new CategoryTrendsReport(monthsBack, series);
    }

    // --- internals --------------------------------------------------------

    private TotalsBlock totalsForPeriod(long userId, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<TypeSumRow> rows = transactions.sumByType(userId, from, to);
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        BigDecimal savings = BigDecimal.ZERO;
        long count = 0;
        for (TypeSumRow row : rows) {
            BigDecimal total = scale(row.total());
            switch (row.type()) {
                case INCOME -> income = total;
                case EXPENSE -> expense = total;
                case SAVINGS -> savings = total;
            }
            count += row.count();
        }
        BigDecimal net = income.subtract(expense).subtract(savings);
        return new TotalsBlock(income, expense, savings, net, count);
    }

    private List<CategoryAmount> topCategoriesForPeriod(long userId, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<CategoryTotalRow> rows = transactions.sumByCategoryAndType(userId, from, to);
        List<CategoryAmount> out = new ArrayList<>();
        for (CategoryTotalRow row : rows) {
            if (out.size() >= TOP_CATEGORY_LIMIT) break;
            out.add(new CategoryAmount(row.category(), row.type(), scale(row.total())));
        }
        return out;
    }

    private List<DailyBucket> dailyBucketsForPeriod(long userId, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        Map<LocalDate, BigDecimal[]> byDay = new TreeMap<>();
        for (DailyBucketRow row : transactions.sumByDayAndType(userId, from, to)) {
            BigDecimal[] slot = byDay.computeIfAbsent(row.day(),
                    d -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal value = scale(row.total());
            switch (row.type()) {
                case INCOME -> slot[0] = value;
                case EXPENSE -> slot[1] = value;
                case SAVINGS -> slot[2] = value;
            }
        }
        List<DailyBucket> out = new ArrayList<>(ym.lengthOfMonth());
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate date = ym.atDay(day);
            BigDecimal[] slot = byDay.get(date);
            if (slot == null) {
                out.add(DailyBucket.zero(date));
            } else {
                out.add(new DailyBucket(date, slot[0], slot[1], slot[2]));
            }
        }
        return out;
    }

    private List<CategoryDelta> topCategoryDeltas(long userId, YearMonth current, YearMonth previous) {
        Map<String, BigDecimal> currentMap = expenseByCategory(userId, current);
        Map<String, BigDecimal> previousMap = expenseByCategory(userId, previous);
        Map<String, BigDecimal> merged = new HashMap<>();
        currentMap.forEach((k, v) -> merged.merge(k, v, BigDecimal::add));
        previousMap.forEach((k, v) -> merged.merge(k, v, BigDecimal::add));

        List<CategoryDelta> deltas = new ArrayList<>();
        for (String category : merged.keySet()) {
            BigDecimal thisYear = currentMap.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal lastYear = previousMap.getOrDefault(category, BigDecimal.ZERO);
            deltas.add(new CategoryDelta(category, thisYear, lastYear, delta(thisYear, lastYear)));
        }
        deltas.sort((a, b) ->
                b.delta().absolute().abs().compareTo(a.delta().absolute().abs()));
        return deltas.size() <= TOP_CATEGORY_LIMIT
                ? deltas
                : new ArrayList<>(deltas.subList(0, TOP_CATEGORY_LIMIT));
    }

    private Map<String, BigDecimal> expenseByCategory(long userId, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        Map<String, BigDecimal> out = new HashMap<>();
        for (CategoryTotalRow row : transactions.sumByCategoryAndType(userId, from, to)) {
            if (row.type() == TransactionType.EXPENSE) {
                out.merge(row.category(), scale(row.total()), BigDecimal::add);
            }
        }
        return out;
    }

    private DeltaBlock delta(BigDecimal current, BigDecimal previous) {
        BigDecimal absolute = scale(current.subtract(previous));
        BigDecimal percent = previous.signum() == 0
                ? null
                : absolute.multiply(BigDecimal.valueOf(100))
                        .divide(previous, DECIMAL_SCALE, RoundingMode.HALF_UP);
        return new DeltaBlock(absolute, percent);
    }

    private BigDecimal scale(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO.setScale(DECIMAL_SCALE);
        return value.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
    }
}
