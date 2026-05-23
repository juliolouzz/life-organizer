package com.julio.lifeorganizer.auth.web;

import com.julio.lifeorganizer.auth.security.AuthenticatedUser;
import com.julio.lifeorganizer.auth.service.AccountService;
import com.julio.lifeorganizer.auth.web.dto.ChangeEmailRequest;
import com.julio.lifeorganizer.auth.web.dto.ChangePasswordRequest;
import com.julio.lifeorganizer.auth.web.dto.DeleteAccountRequest;
import com.julio.lifeorganizer.auth.web.dto.DeleteAccountResponse;
import com.julio.lifeorganizer.auth.web.dto.LogoutAllRequest;
import com.julio.lifeorganizer.auth.web.dto.UpdateProfileRequest;
import com.julio.lifeorganizer.auth.web.dto.UserResponse;
import com.julio.lifeorganizer.common.api.ApiResponse;
import com.julio.lifeorganizer.common.exception.UnauthorizedException;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service /me/* endpoints (Slice 9). Authentication is enforced by the
 * Spring Security chain; this controller treats a null principal as a defense
 * in depth and rejects with 401.
 */
@RestController
@RequestMapping("/api/v1/me")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PatchMapping
    public ApiResponse<UserResponse> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        requirePrincipal(principal);
        return ApiResponse.ok(accountService.updateProfile(principal.id(), request));
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        requirePrincipal(principal);
        accountService.changePassword(principal.id(), request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/email")
    public ApiResponse<Void> requestEmailChange(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangeEmailRequest request) {
        requirePrincipal(principal);
        accountService.requestEmailChange(principal.id(), request);
        return new ApiResponse<>(true, null,
                "Verification link sent to " + request.newEmail(), null);
    }

    @PostMapping("/delete")
    public ApiResponse<DeleteAccountResponse> deleteAccount(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody DeleteAccountRequest request) {
        requirePrincipal(principal);
        DeleteAccountResponse response = accountService.requestAccountDeletion(principal.id(), request);
        return ApiResponse.ok(response, Map.of(
                "deletionScheduledAt", response.deletionScheduledAt()));
    }

    @PostMapping("/restore")
    public ApiResponse<UserResponse> cancelOwnDeletion(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        requirePrincipal(principal);
        return ApiResponse.ok(accountService.cancelOwnDeletion(principal.id()));
    }

    @PostMapping("/sessions/logout-all")
    public ApiResponse<Void> logoutAllSessions(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody LogoutAllRequest request) {
        requirePrincipal(principal);
        accountService.logoutAllSessions(principal.id(), request.password());
        return new ApiResponse<>(true, null, "Signed out of all sessions", null);
    }

    private static void requirePrincipal(AuthenticatedUser principal) {
        if (principal == null) {
            throw new UnauthorizedException();
        }
    }
}
