package com.julio.lifeorganizer.reports.web;

import com.julio.lifeorganizer.auth.security.AuthenticatedUser;
import com.julio.lifeorganizer.common.exception.UnauthorizedException;
import com.julio.lifeorganizer.reports.service.ReportsExportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * File-format reports endpoints (Slice 10). Returns raw byte arrays with the
 * appropriate Content-Type / Content-Disposition rather than the ApiResponse
 * envelope, so curl / browsers / scripts can download directly.
 */
@RestController
@RequestMapping("/api/v1/reports")
@Validated
@Tag(name = "Reports Export",
        description = "Downloadable formats for the Slice 10 reports: summary CSV "
                + "(text/csv), summary PDF (application/pdf via Thymeleaf + OpenHTMLtoPDF), "
                + "and the transactions CSV in the Slice 7 import format for round-trip "
                + "safety.")
public class ReportsExportController {

    public static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=utf-8");

    private final ReportsExportService exports;

    public ReportsExportController(ReportsExportService exports) {
        this.exports = exports;
    }

    @GetMapping(value = "/summary.csv", produces = "text/csv")
    public ResponseEntity<byte[]> summaryCsv(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam @Min(1970) @Max(9999) int year,
            @RequestParam @Min(1) @Max(12) int month) {
        requirePrincipal(principal);
        byte[] body = exports.summaryCsv(principal.id(), year, month);
        return downloadResponse(body, CSV, "summary-" + filename(year, month) + ".csv");
    }

    @GetMapping(value = "/summary.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> summaryPdf(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam @Min(1970) @Max(9999) int year,
            @RequestParam @Min(1) @Max(12) int month) {
        requirePrincipal(principal);
        String displayName = exports.displayNameFor(principal.id(), principal.email());
        byte[] body = exports.summaryPdf(principal.id(), year, month, displayName);
        return downloadResponse(body, MediaType.APPLICATION_PDF,
                "summary-" + filename(year, month) + ".pdf");
    }

    @GetMapping(value = "/transactions.csv", produces = "text/csv")
    public ResponseEntity<byte[]> transactionsCsv(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        requirePrincipal(principal);
        LocalDate effectiveFrom = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate effectiveTo = to != null ? to : LocalDate.of(2999, 12, 31);
        byte[] body = exports.transactionsCsv(principal.id(), effectiveFrom, effectiveTo);
        String range = (from == null ? "all" : from.toString())
                + "-to-" + (to == null ? "all" : to.toString());
        return downloadResponse(body, CSV, "transactions-" + range + ".csv");
    }

    private static ResponseEntity<byte[]> downloadResponse(byte[] body, MediaType type, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, 200);
    }

    private static String filename(int year, int month) {
        return year + "-" + String.format("%02d", month);
    }

    private static void requirePrincipal(AuthenticatedUser principal) {
        if (principal == null) {
            throw new UnauthorizedException();
        }
    }
}
