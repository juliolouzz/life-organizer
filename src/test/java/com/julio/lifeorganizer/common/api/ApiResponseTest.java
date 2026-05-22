package com.julio.lifeorganizer.common.api;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ApiResponseTest {

    @Test
    void ok_whenDataProvided_returnsSuccessTrueAndNullMessage() {
        ApiResponse<String> r = ApiResponse.ok("hello");
        assertThat(r.success()).isTrue();
        assertThat(r.data()).isEqualTo("hello");
        assertThat(r.message()).isNull();
        assertThat(r.meta()).isNull();
    }

    @Test
    void error_returnsFalseAndPlacesCodeInMeta() {
        ApiResponse<Object> r = ApiResponse.error("not found", "TX_NOT_FOUND");
        assertThat(r.success()).isFalse();
        assertThat(r.data()).isNull();
        assertThat(r.message()).isEqualTo("not found");
        assertThat(r.meta()).containsEntry("code", "TX_NOT_FOUND");
    }

    @Test
    void validationError_placesFieldErrorsAsFlatMapInMeta() {
        ApiResponse<Object> r = ApiResponse.validationError(Map.of("email", "must be valid"));
        assertThat(r.success()).isFalse();
        assertThat(r.message()).isEqualTo("Validation failed");
        assertThat(r.meta()).containsEntry("email", "must be valid");
    }

    @Test
    void paged_wrapsItemsAsDataAndPlacesCursorAndLimitInMeta() {
        PageMeta page = new PageMeta("OPAQUE_TOKEN", 20);
        ApiResponse<List<Integer>> r = ApiResponse.paged(List.of(1, 2, 3), page);
        assertThat(r.success()).isTrue();
        assertThat(r.data()).containsExactly(1, 2, 3);
        assertThat(r.meta()).containsEntry("nextCursor", "OPAQUE_TOKEN");
        assertThat(r.meta()).containsEntry("limit", 20);
    }
}
