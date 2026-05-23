package com.julio.lifeorganizer.auth.web;

import com.julio.lifeorganizer.auth.security.AuthenticatedUser;
import com.julio.lifeorganizer.auth.service.UserService;
import com.julio.lifeorganizer.auth.web.dto.UserResponse;
import com.julio.lifeorganizer.common.api.ApiResponse;
import com.julio.lifeorganizer.common.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Account",
        description = "GET /me returns the authenticated user's profile (id, email, "
                + "display name, role, emailVerified, deletionScheduledAt).")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            throw new UnauthorizedException();
        }
        return ApiResponse.ok(userService.findById(principal.id()));
    }
}
