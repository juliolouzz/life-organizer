package com.julio.lifeorganizer.auth.web.dto;

import com.julio.lifeorganizer.auth.persistence.UserEntity;

public record UserResponse(Long id, String email, String displayName, String role) {

    public static UserResponse from(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name()
        );
    }
}
