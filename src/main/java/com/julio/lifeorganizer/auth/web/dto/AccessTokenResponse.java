package com.julio.lifeorganizer.auth.web.dto;

public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) {
}
