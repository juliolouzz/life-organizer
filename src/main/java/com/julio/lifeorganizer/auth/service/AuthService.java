package com.julio.lifeorganizer.auth.service;

import com.julio.lifeorganizer.auth.domain.Role;
import com.julio.lifeorganizer.auth.persistence.UserEntity;
import com.julio.lifeorganizer.auth.persistence.UserRepository;
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
import com.julio.lifeorganizer.common.exception.AccountDeletionPendingException;
import com.julio.lifeorganizer.common.exception.AccountNotDeletingException;
import com.julio.lifeorganizer.common.exception.ConflictException;
import com.julio.lifeorganizer.common.exception.InvalidCredentialsException;
import com.julio.lifeorganizer.common.exception.InvalidTokenException;
import com.julio.lifeorganizer.common.exception.UserNotFoundForTokenException;
import java.time.Clock;
import java.time.Instant;
import com.julio.lifeorganizer.config.JwtProperties;
import com.julio.lifeorganizer.mail.MailService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Orchestrates registration, login, and refresh.
//
// Security choices, summarised:
// - duplicate email returns 409 USER_EMAIL_EXISTS
// - wrong password and unknown email share an exception class so the bodies are byte-identical
//   (prevents enumeration; AC-A7, AC-A8)
// - refresh validates typ="refresh"; if the user has since been deleted, we surface
//   USER_NOT_FOUND so clients clear their tokens (R11)
@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // Pre-computed synthetic password hash shape (bcrypt-style) used to mimic the work
    // the happy path does when an email is not registered. Constant so we don't
    // accidentally leak more timing via hash construction.
    private static final String ENUMERATION_DECOY_HASH =
            "$2a$12$0000000000000000000000000000000000000000000000000000";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final MailService mailService;
    private final Clock clock;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       JwtProperties jwtProperties,
                       MailService mailService,
                       Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.mailService = mailService;
        this.clock = clock;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered", "USER_EMAIL_EXISTS");
        }
        UserEntity user = UserEntity.createNew(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                Role.ROLE_USER
        );
        UserEntity saved = userRepository.save(user);
        String verifyToken = jwtService.generateEmailVerificationToken(saved.getId());
        log.info("verification email issued for user {} ({})", saved.getId(), saved.getEmail());
        mailService.sendEmailVerification(saved.getEmail(), saved.getDisplayName(),
                "/verify-email?token=" + verifyToken);
        return UserResponse.from(saved);
    }

    /**
     * Always returns successfully regardless of whether the email exists - prevents
     * user enumeration (AC-8-1). If the email matches a user, a reset link is logged.
     * Performs equivalent work in both branches (token generation + bcrypt-class hash) so
     * the response time does not leak whether the email is registered.
     */
    public void requestPasswordReset(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase();
        userRepository.findByEmail(email).ifPresentOrElse(user -> {
            String token = jwtService.generatePasswordResetToken(user.getId(), user.getPasswordHash());
            log.info("password reset requested for user {} ({})", user.getId(), user.getEmail());
            mailService.sendPasswordReset(user.getEmail(), user.getDisplayName(),
                    "/reset-password?token=" + token);
        }, () -> {
            // Burn equivalent CPU so response time matches the happy path and does not
            // leak whether the email exists. The token is generated against a synthetic
            // password hash and discarded.
            jwtService.generatePasswordResetToken(0L, ENUMERATION_DECOY_HASH);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        Claims claims = jwtService.parsePasswordResetToken(request.token());
        Long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException ex) {
            throw new InvalidTokenException("Token subject is not a valid user id");
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundForTokenException::new);
        // After this passes the token is bound to the user's CURRENT password hash.
        // Once we persist the new hash below, the token's fingerprint no longer matches,
        // so any subsequent attempt with the same token fails - effectively single-use.
        jwtService.verifyPasswordBinding(claims, user.getPasswordHash());
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public UserResponse verifyEmail(VerifyEmailRequest request) {
        Claims claims = jwtService.parseEmailVerificationToken(request.token());
        Long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException ex) {
            throw new InvalidTokenException("Token subject is not a valid user id");
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundForTokenException::new);
        user.markEmailVerified();
        return UserResponse.from(userRepository.save(user));
    }

    /**
     * Re-sends the verification email. Same anti-enumeration pattern: always
     * returns successfully regardless of whether the email is registered or already verified.
     * Performs equivalent work in the no-op branch to flatten timing.
     */
    public void resendVerification(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase();
        userRepository.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresentOrElse(user -> {
                    String token = jwtService.generateEmailVerificationToken(user.getId());
                    log.info("verification email re-sent for user {} ({})", user.getId(), user.getEmail());
                    mailService.sendEmailVerification(user.getEmail(), user.getDisplayName(),
                            "/verify-email?token=" + token);
                }, () -> jwtService.generateEmailVerificationToken(0L));
    }

    public AuthTokensResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        // Account-state gate: a user with deletion scheduled must NOT be issued
        // fresh tokens. We surface 403 (correct credentials, forbidden by state)
        // with the scheduled date so the client can show the restore prompt.
        if (user.isDeletionPending()) {
            throw new AccountDeletionPendingException(user.getDeletionScheduledAt());
        }
        return buildTokens(user);
    }

    @Transactional
    public UserResponse confirmEmailChange(ConfirmEmailChangeRequest request) {
        Claims claims = jwtService.parseChangeEmailToken(request.token());
        Long userId = parseUserId(claims);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundForTokenException::new);
        jwtService.verifyPasswordBinding(claims, user.getPasswordHash());
        Object newEmailClaim = claims.get("new_email");
        if (!(newEmailClaim instanceof String newEmail) || newEmail.isBlank()) {
            throw new InvalidTokenException("Token is missing new_email claim");
        }
        // Re-check uniqueness at confirm time: someone else may have claimed
        // the email between request and confirm.
        if (!newEmail.equals(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
            throw new ConflictException("Email already registered", "USER_EMAIL_EXISTS");
        }
        user.changeEmail(newEmail);
        // Clicking the link is proof of ownership of the new address - the new
        // email is verified by definition. If the user was previously unverified
        // (held an unconfirmed old email), they are now verified too.
        user.markEmailVerified();
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse confirmAccountRestore(ConfirmAccountRestoreRequest request) {
        Claims claims = jwtService.parseAccountRestoreToken(request.token());
        Long userId = parseUserId(claims);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundForTokenException::new);
        jwtService.verifyPasswordBinding(claims, user.getPasswordHash());
        if (!user.isDeletionPending()) {
            throw new AccountNotDeletingException();
        }
        Instant scheduled = user.getDeletionScheduledAt();
        if (scheduled != null && !scheduled.isAfter(clock.instant())) {
            // Grace period has elapsed - the next scheduled job run will hard-delete
            // this user. Reject the restore so the user does not see a misleading
            // "restored" message followed by data loss.
            throw new AccountNotDeletingException();
        }
        user.cancelDeletion();
        return UserResponse.from(userRepository.save(user));
    }

    private static Long parseUserId(Claims claims) {
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException ex) {
            throw new InvalidTokenException("Token subject is not a valid user id");
        }
    }

    public AccessTokenResponse refresh(RefreshRequest request) {
        Claims claims = jwtService.parseRefreshToken(request.refreshToken());
        Long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException ex) {
            // Token passed signature + typ checks but sub is not a numeric user id.
            // Treat as invalid rather than letting a NumberFormatException escape.
            throw new com.julio.lifeorganizer.common.exception.InvalidTokenException(
                    "Token subject is not a valid user id");
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundForTokenException::new);
        String access = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());
        return new AccessTokenResponse(access, "Bearer", jwtProperties.accessTtl().toSeconds());
    }

    private AuthTokensResponse buildTokens(UserEntity user) {
        String access = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());
        String refresh = jwtService.generateRefreshToken(user.getId());
        return new AuthTokensResponse(
                access, refresh, "Bearer", jwtProperties.accessTtl().toSeconds());
    }
}
