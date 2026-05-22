package com.julio.lifeorganizer.transactions.domain;

import com.julio.lifeorganizer.common.exception.InvalidQueryException;
import java.time.LocalDate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class CursorCodecTest {

    @Test
    void encode_then_decode_roundtripsValueExactly() {
        LocalDate date = LocalDate.of(2026, 5, 22);
        Long id = 1234L;
        String token = CursorCodec.encode(date, id);

        CursorCodec.DecodedCursor decoded = CursorCodec.decode(token);

        assertThat(decoded.date()).isEqualTo(date);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void encode_doesNotLeakRawFormat() {
        String token = CursorCodec.encode(LocalDate.of(2026, 5, 22), 99L);
        assertThat(token).doesNotContain("_");
        assertThat(token).doesNotContain("2026-05-22");
    }

    @Test
    void decode_whenTokenIsNotBase64_throwsInvalidQuery() {
        assertThatThrownBy(() -> CursorCodec.decode("not%%base64$$"))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void decode_whenShapeIsWrong_throwsInvalidQuery() {
        // base64 of "not-a-cursor" - parses, but doesn't match the date_id pattern
        String malformed = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("not-a-cursor".getBytes());
        assertThatThrownBy(() -> CursorCodec.decode(malformed))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void decode_whenBlank_throwsInvalidQuery() {
        assertThatThrownBy(() -> CursorCodec.decode(""))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void decode_whenIdIsZeroOrNegative_throwsInvalidQuery() {
        String token = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("2026-05-22_0".getBytes());
        assertThatThrownBy(() -> CursorCodec.decode(token))
                .isInstanceOf(InvalidQueryException.class);
    }
}
