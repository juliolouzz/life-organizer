package com.julio.lifeorganizer.auth.service;

import com.julio.lifeorganizer.common.exception.InvalidTokenException;
import com.julio.lifeorganizer.common.exception.TokenExpiredException;
import com.julio.lifeorganizer.config.JwtProperties;
import io.jsonwebtoken.Claims;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class JwtServiceTest {

    private static final String SECRET = "unit-test-jwt-secret-must-be-at-least-32-bytes-long-padding";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final Duration SKEW = Duration.ofSeconds(30);

    private JwtService newService(Clock clock) {
        return new JwtService(new JwtProperties(SECRET, ACCESS_TTL, REFRESH_TTL, SKEW), clock);
    }

    @Test
    void generateAccessToken_signsWithExpectedClaimsAndTtl() {
        Instant fixedNow = Instant.parse("2026-05-22T12:00:00Z");
        JwtService svc = newService(Clock.fixed(fixedNow, ZoneOffset.UTC));

        String token = svc.generateAccessToken(42L, "user@example.com", "ROLE_USER", 0);
        Claims claims = svc.parseAccessToken(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get(JwtService.EMAIL_CLAIM)).isEqualTo("user@example.com");
        assertThat(claims.get(JwtService.ROLE_CLAIM)).isEqualTo("ROLE_USER");
        assertThat(claims.get(JwtService.TYP_CLAIM)).isEqualTo(JwtService.TYP_ACCESS);
        long ttlSec = (claims.getExpiration().toInstant().toEpochMilli()
                - claims.getIssuedAt().toInstant().toEpochMilli()) / 1000;
        assertThat(ttlSec).isEqualTo(ACCESS_TTL.toSeconds());
    }

    @Test
    void parseAccessToken_whenRefreshTokenProvided_throwsInvalidToken() {
        JwtService svc = newService(Clock.systemUTC());
        String refresh = svc.generateRefreshToken(99L, 0);

        assertThatThrownBy(() -> svc.parseAccessToken(refresh))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parseAccessToken_whenExpiredBeyondSkew_throwsTokenExpired() {
        // issue token 10 minutes in the PAST relative to verifier, expiry was 5m later -> expired by 5m
        Instant signTime = Instant.parse("2026-05-22T12:00:00Z");
        Instant verifyTime = signTime.plus(Duration.ofMinutes(20));

        JwtService signer = newService(Clock.fixed(signTime, ZoneOffset.UTC));
        String token = signer.generateAccessToken(7L, "e", "ROLE_USER", 0);

        JwtService verifier = newService(Clock.fixed(verifyTime, ZoneOffset.UTC));
        assertThatThrownBy(() -> verifier.parseAccessToken(token))
                .isInstanceOf(TokenExpiredException.class);
    }

    @Test
    void parseAccessToken_when15sPastExpiry_succeedsBecauseOfSkewTolerance() {
        Instant signTime = Instant.parse("2026-05-22T12:00:00Z");
        // 15 minutes + 15 seconds after signing - within the 30s skew tolerance
        Instant verifyTime = signTime.plus(ACCESS_TTL).plusSeconds(15);

        JwtService signer = newService(Clock.fixed(signTime, ZoneOffset.UTC));
        String token = signer.generateAccessToken(7L, "e", "ROLE_USER", 0);

        JwtService verifier = newService(Clock.fixed(verifyTime, ZoneOffset.UTC));
        assertThat(verifier.parseAccessToken(token).getSubject()).isEqualTo("7");
    }

    @Test
    void parseAccessToken_whenSignedByDifferentSecret_throwsInvalidToken() {
        JwtService legit = newService(Clock.systemUTC());
        String stolen = legit.generateAccessToken(1L, "e", "ROLE_USER", 0);

        JwtProperties otherProps = new JwtProperties(
                "completely-different-jwt-secret-also-32+chars-padding",
                ACCESS_TTL, REFRESH_TTL, SKEW);
        JwtService imposter = new JwtService(otherProps, Clock.systemUTC());

        assertThatThrownBy(() -> imposter.parseAccessToken(stolen))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parseRefreshToken_whenAccessTokenProvided_throwsInvalidToken() {
        JwtService svc = newService(Clock.systemUTC());
        String access = svc.generateAccessToken(99L, "e", "ROLE_USER", 0);
        assertThatThrownBy(() -> svc.parseRefreshToken(access))
                .isInstanceOf(InvalidTokenException.class);
    }
}
