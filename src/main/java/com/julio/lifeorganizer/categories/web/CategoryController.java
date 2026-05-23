package com.julio.lifeorganizer.categories.web;

import com.julio.lifeorganizer.auth.security.AuthenticatedUser;
import com.julio.lifeorganizer.categories.service.CategoryService;
import com.julio.lifeorganizer.categories.web.dto.CategoryRequest;
import com.julio.lifeorganizer.categories.web.dto.CategoryResponse;
import com.julio.lifeorganizer.common.api.ApiResponse;
import com.julio.lifeorganizer.common.exception.UnauthorizedException;
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
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(service.list(requireUser(principal).id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(service.create(requireUser(principal).id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(service.update(requireUser(principal).id(), id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        service.archive(requireUser(principal).id(), id);
    }

    private static AuthenticatedUser requireUser(AuthenticatedUser p) {
        if (p == null) throw new UnauthorizedException();
        return p;
    }
}
