package com.julio.lifeorganizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class HealthEndpointIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;

    @Test
    void getHealth_returns200_withStatusUp() throws Exception {
        ResponseEntity<String> resp = http.getForEntity("/actuator/health", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode node = json.readTree(resp.getBody());
        assertThat(node.get("status").asText()).isEqualTo("UP");
    }
}
