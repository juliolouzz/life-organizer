package com.julio.lifeorganizer.auth.web.dto;

import com.julio.lifeorganizer.auth.persistence.UserEntity;
import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String displayName,
        String role,
        boolean emailVerified,
        Instant deletionScheduledAt,
        String currency,
        int monthBoundaryDay) {

    public static UserResponse from(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.isEmailVerified(),
                user.getDeletionScheduledAt(),
                user.getCurrency().name(),
                user.getMonthBoundaryDay()
        );
    }
}
