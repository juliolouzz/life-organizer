package com.julio.lifeorganizer.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.lifeorganizer.AbstractIntegrationTest;
import com.julio.lifeorganizer.auth.persistence.UserRepository;
import com.julio.lifeorganizer.auth.service.JwtService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

// End-to-end tests for Slice 8: forgot/reset password and email verification.
// Rate-limiter behaviour is covered by RateLimiterTest (pure unit test) since the
// limiter is a singleton across the test run and rate-limit-integration tests would
// poison other tests that hit /auth/* repeatedly.
@Tag("integration")
class AuthCompletenessIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @LocalServerPort private int port;

    private String email;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        email = "slice8-" + System.nanoTime() + "@example.com";
        http.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", "S3cretValue", "displayName", "Slice8"),
                String.class);
        accessToken = json.readTree(http.postForEntity("/api/v1/auth/login",
                        Map.of("email", email, "password", "S3cretValue"), String.class).getBody())
                .get("data").get("accessToken").asText();
    }

    @Test
    void forgotPassword_alwaysReturns200_evenForUnknownEmail() throws Exception {
        ResponseEntity<String> existing = http.postForEntity("/api/v1/auth/forgot-password",
                Map.of("email", email), String.class);
        ResponseEntity<String> unknown = http.postForEntity("/api/v1/auth/forgot-password",
                Map.of("email", "ghost-" + System.nanoTime() + "@example.com"), String.class);

        assertThat(existing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Same body shape (anti-enumeration).
        assertThat(json.readTree(existing.getBody()).get("success").asBoolean()).isTrue();
        assertThat(json.readTree(unknown.getBody()).get("success").asBoolean()).isTrue();
        assertThat(existing.getBody()).isEqualTo(unknown.getBody());
    }

    @Test
    void resetPassword_validToken_changesPassword_oldPasswordRejected() throws Exception {
        // Find the user's id from /me
        long userId = userIdOfCurrentUser();

        String token = jwtService.generatePasswordResetToken(userId, passwordHashFor(userId));
        ResponseEntity<String> reset = http.postForEntity("/api/v1/auth/reset-password",
                Map.of("token", token, "newPassword", "NewPass123"), String.class);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Old password rejected
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> oldLogin = postJson(client, "/api/v1/auth/login",
                Map.of("email", email, "password", "S3cretValue"));
        assertThat(oldLogin.statusCode()).isEqualTo(401);

        // New password accepted
        ResponseEntity<String> newLogin = http.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "NewPass123"), String.class);
        assertThat(newLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void resetPassword_tokenCannotBeReusedAfterPasswordChange() throws Exception {
        long userId = userIdOfCurrentUser();
        String token = jwtService.generatePasswordResetToken(userId, passwordHashFor(userId));

        // First use - succeeds.
        ResponseEntity<String> first = http.postForEntity("/api/v1/auth/reset-password",
                Map.of("token", token, "newPassword", "FirstNew123"), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second use of the SAME token - the password binding no longer matches
        // because the password (and therefore its fingerprint) has changed.
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> second = postJson(client, "/api/v1/auth/reset-password",
                Map.of("token", token, "newPassword", "AnotherTry123"));
        assertThat(second.statusCode()).isEqualTo(401);
        assertThat(json.readTree(second.body()).get("meta").get("code").asText())
                .isEqualTo("INVALID_TOKEN");
    }

    @Test
    void resetPassword_invalidToken_returns401() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = postJson(client, "/api/v1/auth/reset-password",
                Map.of("token", "not-a-real-jwt", "newPassword", "Whatever123"));
        assertThat(resp.statusCode()).isEqualTo(401);
        JsonNode body = json.readTree(resp.body());
        assertThat(body.get("meta").get("code").asText()).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void resetPassword_weakNewPassword_returns400() throws Exception {
        long userId = userIdOfCurrentUser();
        String token = jwtService.generatePasswordResetToken(userId, passwordHashFor(userId));

        ResponseEntity<String> resp = http.postForEntity("/api/v1/auth/reset-password",
                Map.of("token", token, "newPassword", "weak"),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(resp.getBody()).get("meta").has("newPassword")).isTrue();
    }

    @Test
    void newUser_emailVerifiedIsFalse_thenVerifyMakesItTrue() throws Exception {
        // /me right after register: emailVerified = false
        JsonNode meBefore = json.readTree(http.exchange("/api/v1/me",
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders(accessToken)),
                        String.class).getBody());
        assertThat(meBefore.get("data").get("emailVerified").asBoolean()).isFalse();

        long userId = meBefore.get("data").get("id").asLong();
        String token = jwtService.generateEmailVerificationToken(userId);

        // Verify
        ResponseEntity<String> verify = http.postForEntity("/api/v1/auth/verify-email",
                Map.of("token", token), String.class);
        assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(verify.getBody()).get("data").get("emailVerified").asBoolean()).isTrue();

        // /me reflects it
        JsonNode meAfter = json.readTree(http.exchange("/api/v1/me",
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders(accessToken)),
                        String.class).getBody());
        assertThat(meAfter.get("data").get("emailVerified").asBoolean()).isTrue();
    }

    @Test
    void verifyEmail_wrongTokenType_returns401() throws Exception {
        // Pass a REFRESH token to /verify-email -> should be rejected
        HttpClient client = HttpClient.newHttpClient();
        long userId = json.readTree(http.exchange("/api/v1/me",
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders(accessToken)),
                        String.class).getBody())
                .get("data").get("id").asLong();
        String wrongTypeToken = jwtService.generateRefreshToken(userId);

        HttpResponse<String> resp = postJson(client, "/api/v1/auth/verify-email",
                Map.of("token", wrongTypeToken));
        assertThat(resp.statusCode()).isEqualTo(401);
        assertThat(json.readTree(resp.body()).get("meta").get("code").asText())
                .isEqualTo("INVALID_TOKEN");
    }

    // helpers

    private long userIdOfCurrentUser() throws Exception {
        return json.readTree(http.exchange("/api/v1/me",
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders(accessToken)),
                        String.class).getBody())
                .get("data").get("id").asLong();
    }

    private String passwordHashFor(long userId) {
        return userRepository.findById(userId).orElseThrow().getPasswordHash();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpResponse<String> postJson(HttpClient client, String path, Object body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
