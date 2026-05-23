package com.julio.lifeorganizer.auth.service;

import com.julio.lifeorganizer.common.exception.InvalidTokenException;
import com.julio.lifeorganizer.common.exception.TokenExpiredException;
import com.julio.lifeorganizer.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

// Signs and verifies HS256 JWTs. Access and refresh tokens are distinguished by the
// custom "typ" claim ("access" / "refresh"). 30s clock skew is tolerated on parse (R5).
// Tokens are self-contained - there is no server-side revocation store in Slice 1 (R2).
@Service
public class JwtService {

    public static final String TYP_CLAIM = "typ";
    public static final String TYP_ACCESS = "access";
    public static final String TYP_REFRESH = "refresh";
    public static final String TYP_PASSWORD_RESET = "password_reset";
    public static final String TYP_VERIFY_EMAIL = "verify_email";
    public static final String TYP_CHANGE_EMAIL = "change_email";
    public static final String TYP_ACCOUNT_RESTORE = "account_restore";
    public static final String EMAIL_CLAIM = "email";
    public static final String NEW_EMAIL_CLAIM = "new_email";
    public static final String ROLE_CLAIM = "role";
    // Short fingerprint of the user's current password hash. Embedded in
    // password-reset, change-email, and account-restore tokens so they
    // auto-invalidate after the password is changed.
    public static final String PWDV_CLAIM = "pwdv";

    // Slice 8 token TTLs (decision 2).
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);
    private static final Duration VERIFY_EMAIL_TTL = Duration.ofHours(24);
    // Slice 9 token TTLs (decision 4, 5).
    private static final Duration CHANGE_EMAIL_TTL = Duration.ofHours(24);
    private static final Duration ACCOUNT_RESTORE_TTL = Duration.ofDays(30);

    private final SecretKey signingKey;
    private final JwtProperties props;
    private final Clock clock;

    public JwtService(JwtProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String email, String role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TYP_CLAIM, TYP_ACCESS)
                .claim(EMAIL_CLAIM, email)
                .claim(ROLE_CLAIM, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTtl())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TYP_CLAIM, TYP_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.refreshTtl())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseAccessToken(String token) {
        Claims claims = parse(token);
        requireTyp(claims, TYP_ACCESS);
        return claims;
    }

    public Claims parseRefreshToken(String token) {
        Claims claims = parse(token);
        requireTyp(claims, TYP_REFRESH);
        return claims;
    }

    public String generatePasswordResetToken(Long userId, String passwordHash) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TYP_CLAIM, TYP_PASSWORD_RESET)
                .claim(PWDV_CLAIM, passwordFingerprint(passwordHash))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(PASSWORD_RESET_TTL)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    // Parses signature/typ/expiry only - does not check the password binding because
    // doing so requires loading the user. Callers must call verifyPasswordBinding once
    // they have the user's current password hash.
    public Claims parsePasswordResetToken(String token) {
        Claims claims = parse(token);
        requireTyp(claims, TYP_PASSWORD_RESET);
        return claims;
    }

    // Throws InvalidTokenException if the token's pwdv claim doesn't match a fingerprint
    // of the current password hash. After a password change the fingerprint differs,
    // so any previously-issued reset token is rejected (effectively single-use).
    public void verifyPasswordBinding(Claims claims, String currentPasswordHash) {
        Object pwdv = claims.get(PWDV_CLAIM);
        if (!(pwdv instanceof String tokenFingerprint)) {
            throw new InvalidTokenException("Token is missing password binding");
        }
        String currentFingerprint = passwordFingerprint(currentPasswordHash);
        if (!MessageDigest.isEqual(
                tokenFingerprint.getBytes(StandardCharsets.UTF_8),
                currentFingerprint.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidTokenException("Token does not match current credentials");
        }
    }

    public String generateEmailVerificationToken(Long userId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TYP_CLAIM, TYP_VERIFY_EMAIL)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(VERIFY_EMAIL_TTL)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseEmailVerificationToken(String token) {
        Claims claims = parse(token);
        requireTyp(claims, TYP_VERIFY_EMAIL);
        return claims;
    }

    public String generateChangeEmailToken(Long userId, String newEmail, String passwordHash) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TYP_CLAIM, TYP_CHANGE_EMAIL)
                .claim(NEW_EMAIL_CLAIM, newEmail)
                .claim(PWDV_CLAIM, passwordFingerprint(passwordHash))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(CHANGE_EMAIL_TTL)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseChangeEmailToken(String token) {
        Claims claims = parse(token);
        requireTyp(claims, TYP_CHANGE_EMAIL);
        return claims;
    }

    public String generateAccountRestoreToken(Long userId, String passwordHash) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TYP_CLAIM, TYP_ACCOUNT_RESTORE)
                .claim(PWDV_CLAIM, passwordFingerprint(passwordHash))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ACCOUNT_RESTORE_TTL)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseAccountRestoreToken(String token) {
        Claims claims = parse(token);
        requireTyp(claims, TYP_ACCOUNT_RESTORE);
        return claims;
    }

    private Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .clockSkewSeconds(props.clockSkew().toSeconds())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new TokenExpiredException();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Token is invalid");
        }
    }

    private static void requireTyp(Claims claims, String expected) {
        Object typ = claims.get(TYP_CLAIM);
        if (!expected.equals(typ)) {
            throw new InvalidTokenException("Token type mismatch");
        }
    }

    // First 16 hex chars of SHA-256(passwordHash). 64 bits is far more entropy than we need
    // to detect a password change, and short enough to keep the token compact.
    private static String passwordFingerprint(String passwordHash) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
