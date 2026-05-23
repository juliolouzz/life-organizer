package com.julio.lifeorganizer.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.lifeorganizer.AbstractIntegrationTest;
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

// End-to-end smoke of the auth subsystem: register a user, login, hit /me, refresh.
// Asserts the full ApiResponse envelope, JWT claim shape, and security boundaries.
@Tag("integration")
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;
    @LocalServerPort private int port;

    private String uniqueEmail;

    @BeforeEach
    void setUp() {
        uniqueEmail = "user" + System.nanoTime() + "@example.com";
    }

    @Test
    void registerLoginMeRefresh_happyPath() throws Exception {
        // REGISTER
        Map<String, String> regBody = Map.of(
                "email", uniqueEmail,
                "password", "S3cretValue",
                "displayName", "Julio");
        ResponseEntity<String> reg = http.postForEntity("/api/v1/auth/register", regBody, String.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode regNode = json.readTree(reg.getBody());
        assertThat(regNode.get("success").asBoolean()).isTrue();
        assertThat(regNode.get("data").get("email").asText()).isEqualTo(uniqueEmail);
        assertThat(regNode.get("data").get("role").asText()).isEqualTo("ROLE_USER");

        // LOGIN
        Map<String, String> loginBody = Map.of("email", uniqueEmail, "password", "S3cretValue");
        ResponseEntity<String> login = http.postForEntity("/api/v1/auth/login", loginBody, String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode tokens = json.readTree(login.getBody()).get("data");
        String accessToken = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(tokens.get("expiresIn").asLong()).isEqualTo(900L);

        // GET /me with the access token
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(accessToken);
        ResponseEntity<String> me = http.exchange(
                "/api/v1/me", HttpMethod.GET, new HttpEntity<>(authHeaders), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(me.getBody()).get("data").get("email").asText()).isEqualTo(uniqueEmail);

        // REFRESH
        Map<String, String> refreshBody = Map.of("refreshToken", refreshToken);
        ResponseEntity<String> refresh = http.postForEntity("/api/v1/auth/refresh", refreshBody, String.class);
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(refresh.getBody()).get("data").get("accessToken").asText()).isNotBlank();
    }

    @Test
    void register_whenEmailAlreadyExists_returns409_USER_EMAIL_EXISTS() throws Exception {
        Map<String, String> body = Map.of(
                "email", uniqueEmail, "password", "S3cretValue", "displayName", "User");
        http.postForEntity("/api/v1/auth/register", body, String.class);

        ResponseEntity<String> dup = http.postForEntity("/api/v1/auth/register", body, String.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode node = json.readTree(dup.getBody());
        assertThat(node.get("success").asBoolean()).isFalse();
        assertThat(node.get("meta").get("code").asText()).isEqualTo("USER_EMAIL_EXISTS");
    }

    @Test
    void login_whenWrongPassword_returns401_INVALID_CREDENTIALS_identicalToUnknownEmail() throws Exception {
        Map<String, String> reg = Map.of(
                "email", uniqueEmail, "password", "S3cretValue", "displayName", "User");
        http.postForEntity("/api/v1/auth/register", reg, String.class);

        // Use java.net.http.HttpClient because the JDK's HttpURLConnection (used by
        // TestRestTemplate) refuses to read a 401 response body on a streamed POST.
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> wrong = postJson(client,
                Map.of("email", uniqueEmail, "password", "WrongPass1"));
        HttpResponse<String> unknown = postJson(client,
                Map.of("email", "nobody-" + System.nanoTime() + "@example.com", "password", "WrongPass1"));

        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(unknown.statusCode()).isEqualTo(401);

        // bodies must be byte-identical (no enumeration leak)
        assertThat(wrong.body()).isEqualTo(unknown.body());
        assertThat(json.readTree(wrong.body()).get("meta").get("code").asText())
                .isEqualTo("INVALID_CREDENTIALS");
    }

    private HttpResponse<String> postJson(HttpClient client, Map<String, String> body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void getMe_whenNoBearerHeader_returns401_UNAUTHORIZED_envelope() throws Exception {
        ResponseEntity<String> me = http.getForEntity("/api/v1/me", String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json.readTree(me.getBody()).get("meta").get("code").asText())
                .isEqualTo("UNAUTHORIZED");
    }

    @Test
    void register_whenPasswordPolicyFails_returns400_withFieldMap() throws Exception {
        Map<String, String> body = Map.of(
                "email", "shorty" + System.nanoTime() + "@example.com",
                "password", "alllowercase", // missing digit
                "displayName", "User");
        ResponseEntity<String> r = http.postForEntity("/api/v1/auth/register", body, String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode meta = json.readTree(r.getBody()).get("meta");
        assertThat(meta.has("password")).isTrue();
    }

    @Test
    void register_withCurrency_persistsAndReturnsIt() throws Exception {
        Map<String, String> body = Map.of(
                "email", "usr" + System.nanoTime() + "@example.com",
                "password", "S3cretValue",
                "displayName", "USDUser",
                "currency", "USD");
        ResponseEntity<String> r = http.postForEntity("/api/v1/auth/register", body, String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json.readTree(r.getBody()).get("data").get("currency").asText()).isEqualTo("USD");
    }

    @Test
    void register_withoutCurrency_defaultsToBRL() throws Exception {
        Map<String, String> body = Map.of(
                "email", "usr" + System.nanoTime() + "@example.com",
                "password", "S3cretValue",
                "displayName", "Default");
        ResponseEntity<String> r = http.postForEntity("/api/v1/auth/register", body, String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json.readTree(r.getBody()).get("data").get("currency").asText()).isEqualTo("BRL");
    }

    @Test
    void register_withInvalidCurrency_returns400() throws Exception {
        Map<String, String> body = Map.of(
                "email", "usr" + System.nanoTime() + "@example.com",
                "password", "S3cretValue",
                "displayName", "Bad",
                "currency", "GBP");
        ResponseEntity<String> r = http.postForEntity("/api/v1/auth/register", body, String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(r.getBody()).get("meta").has("currency")).isTrue();
    }
}
