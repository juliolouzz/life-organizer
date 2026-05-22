package com.julio.lifeorganizer.auth.web.dto;

public record AuthTokensResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
