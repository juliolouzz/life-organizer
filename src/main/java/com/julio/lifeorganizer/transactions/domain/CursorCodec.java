package com.julio.lifeorganizer.transactions.domain;

import com.julio.lifeorganizer.common.exception.InvalidQueryException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.regex.Pattern;

// Encodes the keyset position as an opaque base64 token (Amendment 1). Clients see only
// the token; the encoded form "<iso-date>_<id>" never appears in the API surface.
public final class CursorCodec {

    private static final Pattern PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})_(\\d+)$");

    private CursorCodec() {
    }

    public static String encode(LocalDate date, Long id) {
        String raw = date.toString() + "_" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static DecodedCursor decode(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidQueryException("cursor token must not be blank");
        }
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new InvalidQueryException("cursor token is not valid base64");
        }
        var matcher = PATTERN.matcher(raw);
        if (!matcher.matches()) {
            throw new InvalidQueryException("cursor token has wrong shape");
        }
        try {
            LocalDate date = LocalDate.parse(matcher.group(1));
            long id = Long.parseLong(matcher.group(2));
            if (id <= 0) {
                throw new InvalidQueryException("cursor id must be positive");
            }
            return new DecodedCursor(date, id);
        } catch (DateTimeParseException | NumberFormatException ex) {
            throw new InvalidQueryException("cursor token has unparseable components");
        }
    }

    public record DecodedCursor(LocalDate date, Long id) {
    }
}
