package com.julio.lifeorganizer.reports.service;

import com.julio.lifeorganizer.auth.persistence.UserEntity;
import com.julio.lifeorganizer.auth.persistence.UserRepository;
import com.julio.lifeorganizer.reports.web.dto.CategoryAmount;
import com.julio.lifeorganizer.reports.web.dto.DailyBucket;
import com.julio.lifeorganizer.reports.web.dto.SummaryReport;
import com.julio.lifeorganizer.transactions.persistence.TransactionEntity;
import com.julio.lifeorganizer.transactions.persistence.TransactionRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.opencsv.CSVWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * CSV and PDF writers for Slice 10. Returns raw byte arrays the controller wraps
 * in ResponseEntity with Content-Type / Content-Disposition headers.
 */
@Service
@Transactional(readOnly = true)
public class ReportsExportService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ReportsService reports;
    private final TransactionRepository transactions;
    private final UserRepository users;
    private final TemplateEngine templateEngine;

    public ReportsExportService(ReportsService reports,
                                TransactionRepository transactions,
                                UserRepository users,
                                TemplateEngine templateEngine) {
        this.reports = reports;
        this.transactions = transactions;
        this.users = users;
        this.templateEngine = templateEngine;
    }

    /** Resolves the display name used in PDF headers, falling back to the email. */
    public String displayNameFor(long userId, String emailFallback) {
        return users.findById(userId).map(UserEntity::getDisplayName).orElse(emailFallback);
    }

    public byte[] summaryCsv(long userId, int year, int month) {
        SummaryReport report = reports.monthlySummary(userId, year, month);
        StringWriter sw = new StringWriter();
        try (CSVWriter csv = new CSVWriter(sw, ',', '"', '\\', "\n")) {
            csv.writeNext(new String[]{"Totals"});
            csv.writeNext(new String[]{"income", report.totals().income().toPlainString()});
            csv.writeNext(new String[]{"expense", report.totals().expense().toPlainString()});
            csv.writeNext(new String[]{"savings", report.totals().savings().toPlainString()});
            csv.writeNext(new String[]{"net", report.totals().net().toPlainString()});
            csv.writeNext(new String[]{"transactionCount", String.valueOf(report.totals().transactionCount())});
            csv.writeNext(new String[]{});

            csv.writeNext(new String[]{"Top Categories"});
            csv.writeNext(new String[]{"name", "type", "amount"});
            for (CategoryAmount cat : report.topCategories()) {
                csv.writeNext(new String[]{cat.name(), cat.type().name(), cat.amount().toPlainString()});
            }
            csv.writeNext(new String[]{});

            csv.writeNext(new String[]{"Daily"});
            csv.writeNext(new String[]{"date", "income", "expense", "savings"});
            for (DailyBucket d : report.daily()) {
                csv.writeNext(new String[]{
                        d.date().toString(),
                        d.income().toPlainString(),
                        d.expense().toPlainString(),
                        d.savings().toPlainString()});
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Transactions CSV in the Slice 7 import format. Round-trip-safe: download,
     * edit, re-import without losing rows.
     */
    public byte[] transactionsCsv(long userId, LocalDate from, LocalDate to) {
        List<TransactionEntity> rows = transactions.findInWindowForTrends(userId, from, to);
        StringWriter sw = new StringWriter();
        try (CSVWriter csv = new CSVWriter(sw, ',', '"', '\\', "\n")) {
            csv.writeNext(new String[]{"date", "type", "amount", "category", "description"});
            for (TransactionEntity row : rows) {
                csv.writeNext(new String[]{
                        row.getTransactionDate().toString(),
                        row.getType().name(),
                        row.getAmount().toPlainString(),
                        row.getCategory(),
                        row.getDescription() == null ? "" : row.getDescription()
                });
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] summaryPdf(long userId, int year, int month, String displayName) {
        SummaryReport report = reports.monthlySummary(userId, year, month);
        String html = renderHtml(report, displayName);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return out.toByteArray();
    }

    private String renderHtml(SummaryReport report, String displayName) {
        Context ctx = new Context(Locale.ROOT);
        Map<String, Object> model = new HashMap<>();
        model.put("displayName", displayName);
        model.put("year", report.year());
        model.put("monthLabel", monthLabel(report.month()));

        Map<String, String> totals = new HashMap<>();
        totals.put("income", brl(report.totals().income()));
        totals.put("expense", brl(report.totals().expense()));
        totals.put("savings", brl(report.totals().savings()));
        totals.put("net", brl(report.totals().net()));
        totals.put("count", String.valueOf(report.totals().transactionCount()));
        model.put("totals", totals);

        model.put("topCategories", report.topCategories().stream()
                .map(c -> Map.of(
                        "name", c.name(),
                        "type", c.type().name(),
                        "amount", brl(c.amount())))
                .toList());
        model.put("dailyBars", buildDailyBars(report.daily()));
        ctx.setVariables(model);
        return templateEngine.process("reports/summary", ctx);
    }

    private static String brl(BigDecimal v) {
        return "R$ " + String.format(Locale.US, "%,.2f", v == null ? BigDecimal.ZERO : v);
    }

    private static String monthLabel(int month) {
        return switch (month) {
            case 1 -> "January"; case 2 -> "February"; case 3 -> "March";
            case 4 -> "April"; case 5 -> "May"; case 6 -> "June";
            case 7 -> "July"; case 8 -> "August"; case 9 -> "September";
            case 10 -> "October"; case 11 -> "November"; case 12 -> "December";
            default -> "Month " + month;
        };
    }

    /**
     * Produces a list of bar specs the template can render as inline-block
     * divs so the PDF gets a daily breakdown chart without needing JS.
     */
    private static List<Map<String, Object>> buildDailyBars(List<DailyBucket> daily) {
        BigDecimal max = daily.stream()
                .map(d -> d.income().max(d.expense()).max(d.savings()))
                .reduce(BigDecimal.ZERO, BigDecimal::max);
        if (max.signum() == 0) max = BigDecimal.ONE;
        List<Map<String, Object>> bars = new java.util.ArrayList<>(daily.size());
        for (DailyBucket d : daily) {
            int incomePct = scaleToPct(d.income(), max);
            int expensePct = scaleToPct(d.expense(), max);
            int savingsPct = scaleToPct(d.savings(), max);
            bars.add(Map.of(
                    "label", String.valueOf(d.date().getDayOfMonth()),
                    "incomePct", incomePct,
                    "expensePct", expensePct,
                    "savingsPct", savingsPct));
        }
        return bars;
    }

    private static int scaleToPct(BigDecimal v, BigDecimal max) {
        return v.multiply(BigDecimal.valueOf(100))
                .divide(max, 0, java.math.RoundingMode.HALF_UP)
                .intValue();
    }
}
