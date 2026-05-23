package com.julio.lifeorganizer.insights.web;

import com.julio.lifeorganizer.auth.security.AuthenticatedUser;
import com.julio.lifeorganizer.common.api.ApiResponse;
import com.julio.lifeorganizer.common.exception.UnauthorizedException;
import com.julio.lifeorganizer.insights.service.InsightsService;
import com.julio.lifeorganizer.insights.service.InsightsService.ByPeriodResult;
import com.julio.lifeorganizer.insights.service.InsightsService.Granularity;
import com.julio.lifeorganizer.insights.web.dto.CategoryTotal;
import com.julio.lifeorganizer.insights.web.dto.SummaryResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/insights")
@Validated
@Tag(name = "Insights",
        description = "Dashboard aggregations (Slice 3): summary totals, by-period buckets, "
                + "top categories. Inputs are arbitrary from/to dates; output is amounts in "
                + "BRL units with scale 2.")
public class InsightsController {

    private final InsightsService service;

    public InsightsController(InsightsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<SummaryResponse> summary(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam @NotNull(message = "must not be null")
                @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate from,
            @RequestParam @NotNull(message = "must not be null")
                @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate to) {
        return ApiResponse.ok(service.summary(requireUser(principal).id(), from, to));
    }

    @GetMapping("/by-category")
    public ApiResponse<List<CategoryTotal>> byCategory(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam @NotNull @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate from,
            @RequestParam @NotNull @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate to) {
        List<CategoryTotal> data = service.byCategory(requireUser(principal).id(), from, to);
        return ApiResponse.ok(data, Map.of("from", from.toString(), "to", to.toString()));
    }

    @GetMapping("/by-period")
    public ApiResponse<Object> byPeriod(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam @NotNull @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate from,
            @RequestParam @NotNull @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate to,
            @RequestParam(value = "granularity", required = false) Granularity granularity) {
        ByPeriodResult result = service.byPeriod(requireUser(principal).id(), from, to, granularity);
        return new ApiResponse<>(true, result.data(), null, Map.of(
                "from", from.toString(),
                "to", to.toString(),
                "granularity", result.granularity().name()));
    }

    private static AuthenticatedUser requireUser(AuthenticatedUser principal) {
        if (principal == null) throw new UnauthorizedException();
        return principal;
    }
}
