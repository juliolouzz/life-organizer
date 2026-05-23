package com.julio.lifeorganizer.auth.web;

import com.julio.lifeorganizer.auth.service.AuthService;
import com.julio.lifeorganizer.auth.web.dto.AccessTokenResponse;
import com.julio.lifeorganizer.auth.web.dto.AuthTokensResponse;
import com.julio.lifeorganizer.auth.web.dto.ConfirmAccountRestoreRequest;
import com.julio.lifeorganizer.auth.web.dto.ConfirmEmailChangeRequest;
import com.julio.lifeorganizer.auth.web.dto.ForgotPasswordRequest;
import com.julio.lifeorganizer.auth.web.dto.LoginRequest;
import com.julio.lifeorganizer.auth.web.dto.RefreshRequest;
import com.julio.lifeorganizer.auth.web.dto.RegisterRequest;
import com.julio.lifeorganizer.auth.web.dto.ResetPasswordRequest;
import com.julio.lifeorganizer.auth.web.dto.UserResponse;
import com.julio.lifeorganizer.auth.web.dto.VerifyEmailRequest;
import com.julio.lifeorganizer.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth",
        description = "Anonymous auth endpoints: register, login, refresh, password reset, "
                + "email verification, change-email and account-restore confirmation. "
                + "Most return 200 with an anti-enumeration message regardless of input validity.")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokensResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AccessTokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    /**
     * Always returns 200 with the same body shape to prevent user enumeration.
     * If the email is registered, a reset link is generated and logged server-side.
     */
    @PostMapping("/forgot-password")
    public ApiResponse<Object> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request);
        return new ApiResponse<>(true, null,
                "If that email is registered, a reset link has been sent.", null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Object> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return new ApiResponse<>(true, null, "Password updated.", null);
    }

    @PostMapping("/verify-email")
    public ApiResponse<UserResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ApiResponse.ok(authService.verifyEmail(request));
    }

    /**
     * Re-sends the verification link for the requested email. Same anti-enumeration
     * pattern as forgot-password: always 200, same body shape.
     */
    @PostMapping("/resend-verification")
    public ApiResponse<Object> resendVerification(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.resendVerification(request);
        return new ApiResponse<>(true, null,
                "If that email is registered and unverified, a new link has been sent.", null);
    }

    @PostMapping("/confirm-email-change")
    public ApiResponse<UserResponse> confirmEmailChange(
            @Valid @RequestBody ConfirmEmailChangeRequest request) {
        return ApiResponse.ok(authService.confirmEmailChange(request));
    }

    @PostMapping("/confirm-account-restore")
    public ApiResponse<UserResponse> confirmAccountRestore(
            @Valid @RequestBody ConfirmAccountRestoreRequest request) {
        return ApiResponse.ok(authService.confirmAccountRestore(request));
    }
}
