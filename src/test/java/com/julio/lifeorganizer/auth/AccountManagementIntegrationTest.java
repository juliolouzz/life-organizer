package com.julio.lifeorganizer.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.lifeorganizer.AbstractIntegrationTest;
import com.julio.lifeorganizer.auth.persistence.UserEntity;
import com.julio.lifeorganizer.auth.persistence.UserRepository;
import com.julio.lifeorganizer.auth.service.AccountLifecycleJob;
import com.julio.lifeorganizer.auth.service.JwtService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

@Tag("integration")
class AccountManagementIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountLifecycleJob lifecycleJob;
    @LocalServerPort private int port;

    private String email;
    private String accessToken;
    private long userId;

    @BeforeEach
    void setUp() throws Exception {
        email = "slice9-" + System.nanoTime() + "@example.com";
        http.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", "S3cretValue", "displayName", "Slice9"),
                String.class);
        accessToken = json.readTree(http.postForEntity("/api/v1/auth/login",
                        Map.of("email", email, "password", "S3cretValue"), String.class).getBody())
                .get("data").get("accessToken").asText();
        userId = json.readTree(http.exchange("/api/v1/me",
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders(accessToken)),
                        String.class).getBody())
                .get("data").get("id").asLong();
    }

    @Test
    void patchMe_updatesDisplayName() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = patchJsonAuthed(client, "/api/v1/me",
                Map.of("displayName", "Renamed"));
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(json.readTree(resp.body()).get("data").get("displayName").asText())
                .isEqualTo("Renamed");
    }

    @Test
    void patchMe_blankDisplayName_returns400() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = patchJsonAuthed(client, "/api/v1/me",
                Map.of("displayName", ""));
        assertThat(resp.statusCode()).isEqualTo(400);
    }

    @Test
    void changePassword_happyPath_thenLoginWorksWithNewPassword() {
        ResponseEntity<String> resp = http.exchange("/api/v1/me/password",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "currentPassword", "S3cretValue",
                        "newPassword", "NewS3cret123"),
                        authHeaders(accessToken)),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> login = http.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "NewS3cret123"), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void changePassword_wrongCurrent_returns401AndDoesNotChangeHash() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = postJsonAuthed(client, "/api/v1/me/password",
                Map.of("currentPassword", "WrongCurrent99", "newPassword", "NewS3cret123"));
        // InvalidCredentialsException is mapped to 401 via AuthException handler
        assertThat(resp.statusCode()).isEqualTo(401);
        assertThat(json.readTree(resp.body()).get("meta").get("code").asText())
                .isEqualTo("INVALID_CREDENTIALS");

        ResponseEntity<String> login = http.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "S3cretValue"), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void changeEmail_happyPath_thenConfirm_updatesEmail() throws Exception {
        String newEmail = "newaddr-" + System.nanoTime() + "@example.com";
        ResponseEntity<String> req = http.exchange("/api/v1/me/email",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("newEmail", newEmail, "currentPassword", "S3cretValue"),
                        authHeaders(accessToken)),
                String.class);
        assertThat(req.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Email is NOT changed yet
        UserEntity before = userRepository.findById(userId).orElseThrow();
        assertThat(before.getEmail()).isEqualTo(email);

        String token = jwtService.generateChangeEmailToken(userId, newEmail, before.getPasswordHash());
        ResponseEntity<String> confirm = http.postForEntity(
                "/api/v1/auth/confirm-email-change", Map.of("token", token), String.class);
        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);

        UserEntity after = userRepository.findById(userId).orElseThrow();
        assertThat(after.getEmail()).isEqualTo(newEmail);
    }

    @Test
    void changeEmail_alreadyTaken_returns409() throws Exception {
        // Register another user to occupy the target email
        String taken = "taken-" + System.nanoTime() + "@example.com";
        http.postForEntity("/api/v1/auth/register",
                Map.of("email", taken, "password", "S3cretValue", "displayName", "Other"),
                String.class);

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = postJsonAuthed(client, "/api/v1/me/email",
                Map.of("newEmail", taken, "currentPassword", "S3cretValue"));
        assertThat(resp.statusCode()).isEqualTo(409);
        assertThat(json.readTree(resp.body()).get("meta").get("code").asText())
                .isEqualTo("USER_EMAIL_EXISTS");
    }

    @Test
    void changeEmail_tokenInvalidatedAfterPasswordChange() throws Exception {
        UserEntity before = userRepository.findById(userId).orElseThrow();
        String newEmail = "later-" + System.nanoTime() + "@example.com";
        String token = jwtService.generateChangeEmailToken(userId, newEmail, before.getPasswordHash());

        // Change password between issue and confirm
        http.exchange("/api/v1/me/password",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "currentPassword", "S3cretValue",
                        "newPassword", "Different123"), authHeaders(accessToken)),
                String.class);

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = postJson(client, "/api/v1/auth/confirm-email-change",
                Map.of("token", token));
        assertThat(resp.statusCode()).isEqualTo(401);
        assertThat(json.readTree(resp.body()).get("meta").get("code").asText())
                .isEqualTo("INVALID_TOKEN");
    }

    @Test
    void deleteAccount_schedulesAndBlocksLogin() throws Exception {
        ResponseEntity<String> resp = http.exchange("/api/v1/me/delete",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("password", "S3cretValue"), authHeaders(accessToken)),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Instant scheduledAt = Instant.parse(
                json.readTree(resp.getBody()).get("data").get("deletionScheduledAt").asText());
        assertThat(scheduledAt).isAfter(Instant.now().plus(29, ChronoUnit.DAYS));

        // Login is now forbidden
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> login = postJson(client, "/api/v1/auth/login",
                Map.of("email", email, "password", "S3cretValue"));
        assertThat(login.statusCode()).isEqualTo(403);
        JsonNode body = json.readTree(login.body());
        assertThat(body.get("meta").get("code").asText()).isEqualTo("ACCOUNT_DELETION_PENDING");
    }

    @Test
    void deleteThenFilter_rejectsInflightToken() throws Exception {
        http.exchange("/api/v1/me/delete",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("password", "S3cretValue"), authHeaders(accessToken)),
                String.class);

        // Same access token, now rejected by JwtAuthenticationFilter
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/me"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(403);
        assertThat(json.readTree(resp.body()).get("meta").get("code").asText())
                .isEqualTo("ACCOUNT_DELETION_PENDING");
    }

    @Test
    void cancelOwnDeletion_clearsScheduledAt_andLoginWorksAgain() {
        http.exchange("/api/v1/me/delete",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("password", "S3cretValue"), authHeaders(accessToken)),
                String.class);

        // Restore via authenticated path - but the in-flight token is now blocked
        // by the filter. We need a fresh login... which is blocked too. So the only
        // authenticated way to restore is to do it BEFORE the next request lands
        // through the filter. Realistic flow: use the anonymous confirm endpoint.
        UserEntity user = userRepository.findById(userId).orElseThrow();
        String token = jwtService.generateAccountRestoreToken(userId, user.getPasswordHash());

        ResponseEntity<String> restore = http.postForEntity(
                "/api/v1/auth/confirm-account-restore",
                Map.of("token", token), String.class);
        assertThat(restore.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> login = http.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "S3cretValue"), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void confirmRestore_onAccountThatIsNotDeleting_returns409() throws Exception {
        UserEntity user = userRepository.findById(userId).orElseThrow();
        String token = jwtService.generateAccountRestoreToken(userId, user.getPasswordHash());

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = postJson(client, "/api/v1/auth/confirm-account-restore",
                Map.of("token", token));
        assertThat(resp.statusCode()).isEqualTo(409);
        assertThat(json.readTree(resp.body()).get("meta").get("code").asText())
                .isEqualTo("ACCOUNT_NOT_DELETING");
    }

    @Test
    void hardDeleteJob_removesUsersPastDeadline_andLeavesOthersAlone() {
        // User A: schedule deletion in the past so the job picks them up.
        UserEntity userA = userRepository.findById(userId).orElseThrow();
        userA.scheduleDeletion(Instant.now().minus(1, ChronoUnit.DAYS));
        userRepository.saveAndFlush(userA);
        // Sanity: verify the field is actually persisted in past.
        UserEntity reloaded = userRepository.findById(userId).orElseThrow();
        assertThat(reloaded.getDeletionScheduledAt()).isBefore(Instant.now());

        // User B: a separate active account that must remain.
        String bEmail = "keep-" + System.nanoTime() + "@example.com";
        http.postForEntity("/api/v1/auth/register",
                Map.of("email", bEmail, "password", "S3cretValue", "displayName", "Keep"),
                String.class);
        Long userBId = userRepository.findByEmail(bEmail).orElseThrow().getId();

        int removed = lifecycleJob.runOnce();
        assertThat(removed).isEqualTo(1);

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(userRepository.findById(userBId)).isPresent();
    }

    // ----- helpers -----

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

    private HttpResponse<String> postJsonAuthed(HttpClient client, String path, Object body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patchJsonAuthed(HttpClient client, String path, Object body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
