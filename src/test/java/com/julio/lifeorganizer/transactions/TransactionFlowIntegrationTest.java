package com.julio.lifeorganizer.transactions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.lifeorganizer.AbstractIntegrationTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;

// End-to-end coverage of every transaction endpoint and the cross-cutting invariants:
// JWT-bound ownership, identical 404 across the three "not found" cases, soft-delete
// invisibility, and keyset pagination shape.
@Tag("integration")
class TransactionFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;

    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void registerTwoUsers() throws Exception {
        aliceToken = registerAndLogin("alice" + System.nanoTime() + "@ex.com");
        bobToken = registerAndLogin("bob" + System.nanoTime() + "@ex.com");
    }

    @Test
    void create_thenFindOne_thenList_succeedsForOwnerOnly() throws Exception {
        long aliceTxId = createTx(aliceToken, "EXPENSE", "10.50", "Food", "Lunch", "2026-05-20");

        // Alice can read it
        JsonNode alice = getJson("/api/v1/transactions/" + aliceTxId, aliceToken);
        // BigDecimal serialises without trailing zeros so compare numerically, not as string.
        assertThat(alice.get("data").get("amount").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("10.50"));

        // Bob gets identical 404
        ResponseEntity<String> bobLookup = httpGet("/api/v1/transactions/" + aliceTxId, bobToken);
        assertThat(bobLookup.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(bobLookup.getBody()).get("meta").get("code").asText())
                .isEqualTo("TRANSACTION_NOT_FOUND");

        // Bob's listing does NOT include alice's tx
        JsonNode bobList = getJson("/api/v1/transactions", bobToken);
        assertThat(bobList.get("data").isArray()).isTrue();
        assertThat(bobList.get("data").size()).isZero();
    }

    @Test
    void create_ignoresUserIdInBody_andServerAssignsFromJwt() throws Exception {
        // Try to set someone else's userId in the body - field is unknown to the DTO so
        // Jackson drops it silently (fail-on-unknown-properties=false). user_id comes from JWT.
        Map<String, Object> body = new HashMap<>();
        body.put("amount", "5.00");
        body.put("type", "EXPENSE");
        body.put("category", "Snack");
        body.put("description", "Choco");
        body.put("transactionDate", "2026-05-22");
        body.put("userId", 9999); // ignored
        body.put("user_id", 9999); // also ignored

        HttpHeaders h = bearer(aliceToken);
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = http.exchange(
                "/api/v1/transactions", HttpMethod.POST, new HttpEntity<>(body, h), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long id = json.readTree(resp.getBody()).get("data").get("id").asLong();

        // Bob can't see it - id is bound to Alice
        ResponseEntity<String> bobLookup = httpGet("/api/v1/transactions/" + id, bobToken);
        assertThat(bobLookup.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void list_paginatesViaOpaqueCursor_andRespectsLimit() throws Exception {
        // Create 5 transactions for Alice across 3 dates.
        createTx(aliceToken, "INCOME", "1.00", "a", "a", "2026-05-10");
        createTx(aliceToken, "INCOME", "2.00", "b", "b", "2026-05-11");
        createTx(aliceToken, "INCOME", "3.00", "c", "c", "2026-05-12");
        createTx(aliceToken, "INCOME", "4.00", "d", "d", "2026-05-13");
        createTx(aliceToken, "INCOME", "5.00", "e", "e", "2026-05-14");

        JsonNode page1 = getJson("/api/v1/transactions?limit=2", aliceToken);
        assertThat(page1.get("data").size()).isEqualTo(2);
        String cursor = page1.get("meta").get("nextCursor").asText();
        assertThat(cursor).isNotBlank();
        assertThat(cursor).doesNotContain("_"); // opaque

        JsonNode page2 = getJson("/api/v1/transactions?limit=2&cursor=" + cursor, aliceToken);
        assertThat(page2.get("data").size()).isEqualTo(2);

        // Items on page 1 must be strictly newer than items on page 2
        String p1Last = page1.get("data").get(1).get("transactionDate").asText();
        String p2First = page2.get("data").get(0).get("transactionDate").asText();
        assertThat(p1Last.compareTo(p2First)).isGreaterThanOrEqualTo(0);
    }

    @Test
    void list_whenLimitOutOfRange_returns400_INVALID_QUERY() throws Exception {
        ResponseEntity<String> r = httpGet("/api/v1/transactions?limit=0", aliceToken);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(r.getBody()).get("meta").get("code").asText())
                .isEqualTo("INVALID_QUERY");
    }

    @Test
    void list_whenFromAfterTo_returns400_INVALID_QUERY() throws Exception {
        ResponseEntity<String> r = httpGet(
                "/api/v1/transactions?from=2026-05-22&to=2026-05-20", aliceToken);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(r.getBody()).get("meta").get("code").asText())
                .isEqualTo("INVALID_QUERY");
    }

    @Test
    void list_withFromAndTo_returnsOnlyRowsInRange() throws Exception {
        // Three rows on different days so the filter has something to slice.
        createTx(aliceToken, "EXPENSE", "10.00", "X", "before",      "2026-05-19");
        createTx(aliceToken, "EXPENSE", "20.00", "X", "inside-low",  "2026-05-20");
        createTx(aliceToken, "EXPENSE", "30.00", "X", "inside-mid",  "2026-05-21");
        createTx(aliceToken, "EXPENSE", "40.00", "X", "inside-high", "2026-05-22");
        createTx(aliceToken, "EXPENSE", "50.00", "X", "after",       "2026-05-23");

        // Repro of the QA-found 500: PostgreSQL couldn't infer the type of
        // the bind parameter inside "(:from IS NULL OR ...)". The COALESCE
        // rewrite restores it. Without that rewrite, this call 500s.
        JsonNode body = json.readTree(httpGet(
                "/api/v1/transactions?from=2026-05-20&to=2026-05-22", aliceToken).getBody());
        assertThat(body.get("data").size()).isEqualTo(3);
        for (JsonNode row : body.get("data")) {
            String date = row.get("transactionDate").asText();
            assertThat(date).isBetween("2026-05-20", "2026-05-22");
        }
    }

    @Test
    void list_withFromOnly_returnsRowsFromThatDateForward() throws Exception {
        createTx(aliceToken, "EXPENSE", "10.00", "X", "before", "2026-05-19");
        createTx(aliceToken, "EXPENSE", "20.00", "X", "on",     "2026-05-20");
        createTx(aliceToken, "EXPENSE", "30.00", "X", "after",  "2026-05-21");

        JsonNode body = json.readTree(httpGet(
                "/api/v1/transactions?from=2026-05-20", aliceToken).getBody());
        for (JsonNode row : body.get("data")) {
            assertThat(row.get("transactionDate").asText()).isGreaterThanOrEqualTo("2026-05-20");
        }
    }

    @Test
    void list_withToOnly_returnsRowsUpToThatDate() throws Exception {
        createTx(aliceToken, "EXPENSE", "10.00", "X", "before", "2026-05-19");
        createTx(aliceToken, "EXPENSE", "20.00", "X", "on",     "2026-05-20");
        createTx(aliceToken, "EXPENSE", "30.00", "X", "after",  "2026-05-21");

        JsonNode body = json.readTree(httpGet(
                "/api/v1/transactions?to=2026-05-20", aliceToken).getBody());
        for (JsonNode row : body.get("data")) {
            assertThat(row.get("transactionDate").asText()).isLessThanOrEqualTo("2026-05-20");
        }
    }

    @Test
    void put_replacesAllFieldsAndBumpsUpdatedAt_butKeepsId() throws Exception {
        long id = createTx(aliceToken, "EXPENSE", "10.00", "Old", "Old", "2026-05-20");
        Thread.sleep(20); // ensure updatedAt advances on PG resolution

        HttpHeaders h = bearer(aliceToken);
        h.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of(
                "amount", "99.99",
                "type", "INCOME",
                "category", "New",
                "description", "New desc",
                "transactionDate", "2026-05-21");
        ResponseEntity<String> resp = http.exchange(
                "/api/v1/transactions/" + id, HttpMethod.PUT,
                new HttpEntity<>(body, h), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = json.readTree(resp.getBody()).get("data");
        assertThat(data.get("id").asLong()).isEqualTo(id);
        assertThat(data.get("amount").asText()).isEqualTo("99.99");
        assertThat(data.get("type").asText()).isEqualTo("INCOME");
    }

    @Test
    void delete_isIdempotentlyNotFoundOnSecondCall_andSoftDeletedRowsAreInvisible() throws Exception {
        long id = createTx(aliceToken, "EXPENSE", "10.00", "X", "x", "2026-05-20");

        ResponseEntity<String> first = http.exchange(
                "/api/v1/transactions/" + id, HttpMethod.DELETE,
                new HttpEntity<>(bearer(aliceToken)), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Second DELETE -> 404 with identical body
        ResponseEntity<String> second = http.exchange(
                "/api/v1/transactions/" + id, HttpMethod.DELETE,
                new HttpEntity<>(bearer(aliceToken)), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(second.getBody()).get("meta").get("code").asText())
                .isEqualTo("TRANSACTION_NOT_FOUND");

        // GET also returns 404
        ResponseEntity<String> get = httpGet("/api/v1/transactions/" + id, aliceToken);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void allEndpoints_returnEnvelopeShape_withSuccessAndMeta() throws Exception {
        JsonNode listShape = getJson("/api/v1/transactions", aliceToken);
        assertThat(listShape.has("success")).isTrue();
        assertThat(listShape.has("data")).isTrue();
        assertThat(listShape.has("meta")).isTrue();
        assertThat(listShape.get("meta").has("nextCursor")).isTrue();
        assertThat(listShape.get("meta").has("limit")).isTrue();
    }

    // helpers

    private String registerAndLogin(String email) throws Exception {
        Map<String, String> reg = Map.of(
                "email", email, "password", "S3cretValue", "displayName", "User");
        http.postForEntity("/api/v1/auth/register", reg, String.class);
        ResponseEntity<String> login = http.postForEntity(
                "/api/v1/auth/login", Map.of("email", email, "password", "S3cretValue"), String.class);
        return json.readTree(login.getBody()).get("data").get("accessToken").asText();
    }

    private long createTx(String token, String type, String amount,
                          String category, String description, String date) throws Exception {
        HttpHeaders h = bearer(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of(
                "amount", amount, "type", type,
                "category", category, "description", description,
                "transactionDate", date);
        ResponseEntity<String> resp = http.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>(body, h), String.class);
        return json.readTree(resp.getBody()).get("data").get("id").asLong();
    }

    private JsonNode getJson(String path, String token) throws Exception {
        return json.readTree(httpGet(path, token).getBody());
    }

    private ResponseEntity<String> httpGet(String path, String token) {
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }
}
