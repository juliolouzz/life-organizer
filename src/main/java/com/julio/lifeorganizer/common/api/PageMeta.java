package com.julio.lifeorganizer.common.api;

// Pagination metadata embedded in ApiResponse.meta by paged list endpoints.
// nextCursor is null when the current page is the last one.
public record PageMeta(String nextCursor, int limit) {
}
