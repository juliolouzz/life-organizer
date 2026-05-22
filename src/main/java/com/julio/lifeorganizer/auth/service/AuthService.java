package com.julio.lifeorganizer.auth.service;

import com.julio.lifeorganizer.auth.domain.Role;
import com.julio.lifeorganizer.auth.persistence.UserEntity;
import com.julio.lifeorganizer.auth.persistence.UserRepository;
import com.julio.lifeorganizer.auth.web.dto.AccessTokenResponse;
import com.julio.lifeorganizer.auth.web.dto.AuthTokensResponse;
import com.julio.lifeorganizer.auth.web.dto.LoginRequest;
import com.julio.lifeorganizer.auth.web.dto.RefreshRequest;
import com.julio.lifeorganizer.auth.web.dto.RegisterRequest;
import com.julio.lifeorganizer.auth.web.dto.UserResponse;
import com.julio.lifeorganizer.common.exception.ConflictException;
import com.julio.lifeorganizer.common.exception.InvalidCredentialsException;
import com.julio.lifeorganizer.common.exception.UserNotFoundForTokenException;
import com.julio.lifeorganizer.config.JwtProperties;
import io.jsonwebtoken.Claims;
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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
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
        return UserResponse.from(userRepository.save(user));
    }

    public AuthTokensResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return buildTokens(user);
    }

    public AccessTokenResponse refresh(RefreshRequest request) {
        Claims claims = jwtService.parseRefreshToken(request.refreshToken());
        Long userId = Long.parseLong(claims.getSubject());
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
