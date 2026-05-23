package com.julio.lifeorganizer.budgets.web;

import com.julio.lifeorganizer.auth.security.AuthenticatedUser;
import com.julio.lifeorganizer.budgets.service.BudgetService;
import com.julio.lifeorganizer.budgets.web.dto.BudgetRequest;
import com.julio.lifeorganizer.budgets.web.dto.BudgetResponse;
import com.julio.lifeorganizer.budgets.web.dto.BudgetStatusItem;
import com.julio.lifeorganizer.common.api.ApiResponse;
import com.julio.lifeorganizer.common.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "Budgets",
        description = "Monthly budgets per category (Slice 6). Includes a /status endpoint "
                + "with the running spend vs budget for each category in a given month.")
public class BudgetController {

    private final BudgetService service;

    public BudgetController(BudgetService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<BudgetResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("year") int year,
            @RequestParam("month") int month) {
        return ApiResponse.ok(service.list(requireUser(principal).id(), year, month));
    }

    @GetMapping("/status")
    public ApiResponse<List<BudgetStatusItem>> status(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("year") int year,
            @RequestParam("month") int month) {
        return ApiResponse.ok(service.statusFor(requireUser(principal).id(), year, month),
                Map.of("year", year, "month", month));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BudgetResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody BudgetRequest request) {
        return ApiResponse.ok(service.create(requireUser(principal).id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<BudgetResponse> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody BudgetRequest request) {
        return ApiResponse.ok(service.update(requireUser(principal).id(), id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        service.delete(requireUser(principal).id(), id);
    }

    private static AuthenticatedUser requireUser(AuthenticatedUser p) {
        if (p == null) throw new UnauthorizedException();
        return p;
    }
}
