package com.julio.lifeorganizer.recurring.web;

import com.julio.lifeorganizer.auth.security.AuthenticatedUser;
import com.julio.lifeorganizer.common.api.ApiResponse;
import com.julio.lifeorganizer.common.exception.UnauthorizedException;
import com.julio.lifeorganizer.recurring.service.RecurringService;
import com.julio.lifeorganizer.recurring.web.dto.RecurringRequest;
import com.julio.lifeorganizer.recurring.web.dto.RecurringResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recurring")
@Tag(name = "Recurring Transactions",
        description = "Recurring transaction templates (Slice 6) that auto-materialise into "
                + "real transactions on every list call past their due date.")
public class RecurringController {

    private final RecurringService service;

    public RecurringController(RecurringService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<RecurringResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(service.list(requireUser(principal).id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RecurringResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody RecurringRequest request) {
        return ApiResponse.ok(service.create(requireUser(principal).id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<RecurringResponse> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody RecurringRequest request) {
        return ApiResponse.ok(service.update(requireUser(principal).id(), id, request));
    }

    @PostMapping("/{id}/pause")
    public ApiResponse<RecurringResponse> pause(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        return ApiResponse.ok(service.pause(requireUser(principal).id(), id));
    }

    @PostMapping("/{id}/resume")
    public ApiResponse<RecurringResponse> resume(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        return ApiResponse.ok(service.resume(requireUser(principal).id(), id));
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
