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
    public static final String EMAIL_CLAIM = "email";
    public static final String ROLE_CLAIM = "role";

    // Slice 8 token TTLs (decision 2).
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);
    private static final Duration VERIFY_EMAIL_TTL = Duration.ofHours(24);

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

    public String generatePasswordResetToken(Long userId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TYP_CLAIM, TYP_PASSWORD_RESET)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(PASSWORD_RESET_TTL)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parsePasswordResetToken(String token) {
        Claims claims = parse(token);
        requireTyp(claims, TYP_PASSWORD_RESET);
        return claims;
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
}
