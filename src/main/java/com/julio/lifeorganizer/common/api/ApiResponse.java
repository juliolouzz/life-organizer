package com.julio.lifeorganizer.common.api;

import java.util.List;
import java.util.Map;

// Single response envelope (spec section 5). Every Slice 1 endpoint - success and error
// alike - serialises to this shape. data is non-null only on success; meta carries
// pagination cursors, validation field maps, or { code: ERROR_CODE } depending on outcome.
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        Map<String, Object> meta
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, Map<String, Object> meta) {
        return new ApiResponse<>(true, data, null, meta);
    }

    public static <T> ApiResponse<List<T>> paged(List<T> items, PageMeta page) {
        return new ApiResponse<>(true, items, null, Map.of(
                "nextCursor", page.nextCursor() == null ? "" : page.nextCursor(),
                "limit", page.limit()
        ));
    }

    public static <T> ApiResponse<T> error(String message, String code) {
        return new ApiResponse<>(false, null, message, Map.of("code", code));
    }

    public static <T> ApiResponse<T> validationError(Map<String, String> fieldErrors) {
        return new ApiResponse<>(false, null, "Validation failed",
                Map.copyOf(fieldErrors));
    }
}
