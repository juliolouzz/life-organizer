package com.julio.lifeorganizer.transactions.web;

import com.julio.lifeorganizer.auth.security.AuthenticatedUser;
import com.julio.lifeorganizer.common.api.ApiResponse;
import com.julio.lifeorganizer.common.exception.UnauthorizedException;
import com.julio.lifeorganizer.common.exception.ValidationException;
import com.julio.lifeorganizer.transactions.service.CsvImportService;
import com.julio.lifeorganizer.transactions.web.dto.ImportResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions Import",
        description = "Bulk import of transactions from a CSV upload (Slice 7). "
                + "Accepts ISO or BR-format dates and dot or comma decimals; "
                + "auto-creates missing categories; returns per-row error reporting.")
public class TransactionImportController {

    private final CsvImportService csvImportService;

    public TransactionImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<ImportResult> importCsv(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("file") MultipartFile file) throws IOException {
        if (principal == null) throw new UnauthorizedException();
        if (file == null || file.isEmpty()) {
            throw new ValidationException("file is required", "MISSING_FILE");
        }
        return ApiResponse.ok(csvImportService.importFor(principal.id(), file.getInputStream()));
    }
}
