package com.julio.lifeorganizer.auth.service;

import com.julio.lifeorganizer.auth.persistence.UserEntity;
import com.julio.lifeorganizer.auth.persistence.UserRepository;
import com.julio.lifeorganizer.auth.web.dto.ChangeEmailRequest;
import com.julio.lifeorganizer.auth.web.dto.ChangePasswordRequest;
import com.julio.lifeorganizer.auth.web.dto.DeleteAccountRequest;
import com.julio.lifeorganizer.auth.web.dto.DeleteAccountResponse;
import com.julio.lifeorganizer.auth.web.dto.UpdateProfileRequest;
import com.julio.lifeorganizer.auth.web.dto.UserResponse;
import com.julio.lifeorganizer.common.exception.AccountNotDeletingException;
import com.julio.lifeorganizer.common.exception.ConflictException;
import com.julio.lifeorganizer.common.exception.InvalidCredentialsException;
import com.julio.lifeorganizer.common.exception.NotFoundException;
import com.julio.lifeorganizer.mail.MailService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service operations on the authenticated user's own account: profile edits,
 * password change, email change request, soft delete and restore. The user id is
 * always sourced from the JWT subject - never from a request body - so no
 * authorization is needed beyond "is authenticated".
 */
@Service
@Transactional(readOnly = true)
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private static final Duration DELETION_GRACE_PERIOD = Duration.ofDays(30);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailService mailService;
    private final Clock clock;

    public AccountService(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          MailService mailService,
                          Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mailService = mailService;
        this.clock = clock;
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        UserEntity user = loadUser(userId);
        user.changeDisplayName(request.displayName().trim());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        UserEntity user = loadUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void requestEmailChange(Long userId, ChangeEmailRequest request) {
        UserEntity user = loadUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        String newEmail = request.newEmail().trim().toLowerCase();
        if (newEmail.equals(user.getEmail())) {
            throw new ConflictException("New email is the same as the current email",
                    "USER_EMAIL_UNCHANGED");
        }
        if (userRepository.existsByEmail(newEmail)) {
            throw new ConflictException("Email already registered", "USER_EMAIL_EXISTS");
        }
        String token = jwtService.generateChangeEmailToken(
                user.getId(), newEmail, user.getPasswordHash());
        log.info("email change requested for user {} to a new address", user.getId());
        mailService.sendEmailChangeConfirmation(newEmail, user.getDisplayName(),
                "/confirm-email-change?token=" + token);
    }

    @Transactional
    public DeleteAccountResponse requestAccountDeletion(Long userId, DeleteAccountRequest request) {
        UserEntity user = loadUser(userId);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (user.isDeletionPending()) {
            // Already scheduled - report the existing date so the client cannot
            // extend the window by repeated requests.
            return new DeleteAccountResponse(user.getDeletionScheduledAt());
        }
        Instant scheduledAt = clock.instant().plus(DELETION_GRACE_PERIOD);
        user.scheduleDeletion(scheduledAt);
        userRepository.save(user);
        String token = jwtService.generateAccountRestoreToken(user.getId(), user.getPasswordHash());
        log.warn("account deletion requested for user {} - scheduled at {}",
                user.getId(), scheduledAt);
        mailService.sendAccountRestore(user.getEmail(), user.getDisplayName(),
                "/restore-account?token=" + token);
        return new DeleteAccountResponse(scheduledAt);
    }

    @Transactional
    public UserResponse cancelOwnDeletion(Long userId) {
        UserEntity user = loadUser(userId);
        if (!user.isDeletionPending()) {
            throw new AccountNotDeletingException();
        }
        user.cancelDeletion();
        log.info("account deletion cancelled for user {} via authenticated request", user.getId());
        return UserResponse.from(userRepository.save(user));
    }

    private UserEntity loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(
                        "User not found: " + userId, "USER_NOT_FOUND"));
    }
}
