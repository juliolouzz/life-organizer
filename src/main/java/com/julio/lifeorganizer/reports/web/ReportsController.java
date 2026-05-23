package com.julio.lifeorganizer.reports.web;

import com.julio.lifeorganizer.auth.security.AuthenticatedUser;
import com.julio.lifeorganizer.common.api.ApiResponse;
import com.julio.lifeorganizer.common.exception.UnauthorizedException;
import com.julio.lifeorganizer.reports.service.ReportsService;
import com.julio.lifeorganizer.reports.web.dto.CategoryTrendsReport;
import com.julio.lifeorganizer.reports.web.dto.SummaryReport;
import com.julio.lifeorganizer.reports.web.dto.YearOverYearReport;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON report endpoints (Slice 10). File-format endpoints (CSV / PDF) live in
 * {@link ReportsExportController} so they can write raw bytes rather than the
 * ApiResponse envelope.
 */
@RestController
@RequestMapping("/api/v1/reports")
@Validated
@Tag(name = "Reports",
        description = "Slice 10 analytical views: monthly summary, year-over-year, and "
                + "category trends. Read-only aggregations over the existing transactions "
                + "table; no schema changes.")
public class ReportsController {

    private final ReportsService reportsService;

    public ReportsController(ReportsService reportsService) {
        this.reportsService = reportsService;
    }

    @GetMapping("/summary")
    public ApiResponse<SummaryReport> summary(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam @Min(1970) @Max(9999) int year,
            @RequestParam @Min(1) @Max(12) int month) {
        requirePrincipal(principal);
        return ApiResponse.ok(reportsService.monthlySummary(principal.id(), year, month));
    }

    @GetMapping("/yoy")
    public ApiResponse<YearOverYearReport> yearOverYear(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam @Min(1970) @Max(9999) int year,
            @RequestParam @Min(1) @Max(12) int month) {
        requirePrincipal(principal);
        return ApiResponse.ok(reportsService.yearOverYear(principal.id(), year, month));
    }

    @GetMapping("/trends")
    public ApiResponse<CategoryTrendsReport> trends(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "12") int months) {
        requirePrincipal(principal);
        return ApiResponse.ok(reportsService.trends(principal.id(), months));
    }

    private static void requirePrincipal(AuthenticatedUser principal) {
        if (principal == null) {
            throw new UnauthorizedException();
        }
    }
}
