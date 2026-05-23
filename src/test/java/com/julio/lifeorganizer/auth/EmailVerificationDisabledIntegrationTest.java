package com.julio.lifeorganizer.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.lifeorganizer.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the "personal-use" mode of the
 * {@code app.auth.email-verification.enabled=false} flag: new users are
 * auto-verified at register and no verification email is dispatched.
 *
 * <p>Lives in its own class so {@link AuthCompletenessIntegrationTest} can keep
 * exercising the enabled flow (which the application-test.yml default keeps on).
 */
@Tag("integration")
@TestPropertySource(properties = "app.auth.email-verification.enabled=false")
class EmailVerificationDisabledIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;

    @Test
    void register_whenVerificationDisabled_marksUserVerified() throws Exception {
        String email = "auto-verified-" + System.nanoTime() + "@example.com";
        Map<String, String> body = Map.of(
                "email", email,
                "password", "S3cretValue",
                "displayName", "Auto");

        ResponseEntity<String> reg = http.postForEntity(
                "/api/v1/auth/register", body, String.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode data = json.readTree(reg.getBody()).get("data");
        assertThat(data.get("emailVerified").asBoolean()).isTrue();

        // Login and confirm /me agrees - no banner-triggering state ever existed.
        String access = json.readTree(http.postForEntity("/api/v1/auth/login",
                        Map.of("email", email, "password", "S3cretValue"),
                        String.class).getBody())
                .get("data").get("accessToken").asText();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(access);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> me = http.exchange("/api/v1/me",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(me.getBody()).get("data").get("emailVerified").asBoolean())
                .isTrue();
    }
}
