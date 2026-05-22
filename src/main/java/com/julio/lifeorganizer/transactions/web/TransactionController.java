package com.julio.lifeorganizer.transactions.web;

import com.julio.lifeorganizer.auth.security.AuthenticatedUser;
import com.julio.lifeorganizer.common.api.ApiResponse;
import com.julio.lifeorganizer.common.exception.UnauthorizedException;
import com.julio.lifeorganizer.transactions.service.TransactionService;
import com.julio.lifeorganizer.transactions.web.dto.CreateTransactionRequest;
import com.julio.lifeorganizer.transactions.web.dto.TransactionResponse;
import com.julio.lifeorganizer.transactions.web.dto.UpdateTransactionRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateTransactionRequest request) {
        return ApiResponse.ok(service.create(requireUser(principal).id(), request));
    }

    @GetMapping
    public ApiResponse<Object> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "from", required = false)
                @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate from,
            @RequestParam(value = "to", required = false)
                @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate to) {
        TransactionService.PageResult page = service.list(
                requireUser(principal).id(),
                new TransactionService.ListQuery(cursor, limit, from, to));
        return new ApiResponse<>(true, page.items(), null, Map.of(
                "nextCursor", page.meta().nextCursor() == null ? "" : page.meta().nextCursor(),
                "limit", page.meta().limit()
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<TransactionResponse> findOne(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        return ApiResponse.ok(service.findOne(requireUser(principal).id(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TransactionResponse> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateTransactionRequest request) {
        return ApiResponse.ok(service.update(requireUser(principal).id(), id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        service.softDelete(requireUser(principal).id(), id);
    }

    private static AuthenticatedUser requireUser(AuthenticatedUser principal) {
        if (principal == null) {
            throw new UnauthorizedException();
        }
        return principal;
    }
}
