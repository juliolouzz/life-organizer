package com.julio.lifeorganizer.budgets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.lifeorganizer.AbstractIntegrationTest;
import java.time.LocalDate;
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

// End-to-end test for Slice 6: categories + budgets + recurring + materialiser.
@Tag("integration")
class BudgetAndRecurringIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String email = "budget" + System.nanoTime() + "@example.com";
        http.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", "S3cretValue", "displayName", "Budget User"),
                String.class);
        token = json.readTree(http.postForEntity("/api/v1/auth/login",
                        Map.of("email", email, "password", "S3cretValue"), String.class).getBody())
                .get("data").get("accessToken").asText();
    }

    @Test
    void categories_crud_roundTrips() throws Exception {
        // Create
        ResponseEntity<String> created = post("/api/v1/categories",
                Map.of("name", "Groceries", "kind", "EXPENSE"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long catId = json.readTree(created.getBody()).get("data").get("id").asLong();

        // List
        JsonNode list = getJson("/api/v1/categories");
        assertThat(list.get("data").isArray()).isTrue();
        assertThat(list.get("data").size()).isEqualTo(1);
        assertThat(list.get("data").get(0).get("name").asText()).isEqualTo("Groceries");

        // Duplicate name -> 409
        ResponseEntity<String> dup = post("/api/v1/categories",
                Map.of("name", "groceries", "kind", "EXPENSE"));
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(dup.getBody()).get("meta").get("code").asText())
                .isEqualTo("CATEGORY_EXISTS");

        // Update (rename)
        ResponseEntity<String> updated = put("/api/v1/categories/" + catId,
                Map.of("name", "Groceries & Food", "kind", "EXPENSE"));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(updated.getBody()).get("data").get("name").asText())
                .isEqualTo("Groceries & Food");

        // Archive (DELETE -> 204)
        ResponseEntity<String> archived = http.exchange("/api/v1/categories/" + catId,
                HttpMethod.DELETE, new HttpEntity<>(authHeaders()), String.class);
        assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // List now empty (archived hidden)
        assertThat(getJson("/api/v1/categories").get("data").size()).isZero();
    }

    @Test
    void budget_status_reflectsActualExpenses() throws Exception {
        long catId = createCategory("Groceries", "EXPENSE");
        createTransaction("EXPENSE", "150.00", "Groceries", "Week 1", "2026-05-07");
        createTransaction("EXPENSE", "200.00", "Groceries", "Week 2", "2026-05-14");
        // budget 500 for Groceries / May 2026
        long budgetId = json.readTree(post("/api/v1/budgets",
                Map.of("categoryId", catId, "amount", "500.00", "month", 5, "year", 2026)).getBody())
                .get("data").get("id").asLong();

        JsonNode status = getJson("/api/v1/budgets/status?year=2026&month=5");
        JsonNode data = status.get("data");
        assertThat(data.size()).isEqualTo(1);
        JsonNode row = data.get(0);
        assertThat(row.get("budgetId").asLong()).isEqualTo(budgetId);
        assertThat(row.get("budgeted").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(row.get("spent").decimalValue()).isEqualByComparingTo("350.00");
        assertThat(row.get("remaining").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(row.get("percent").asInt()).isEqualTo(70);
    }

    @Test
    void recurring_monthly_materialisesOnTransactionList() throws Exception {
        long catId = createCategory("Salary", "INCOME");

        // Create a MONTHLY recurring that starts 2 months ago.
        LocalDate twoMonthsAgo = LocalDate.now().minusMonths(2).withDayOfMonth(1);
        ResponseEntity<String> created = post("/api/v1/recurring",
                Map.of("categoryId", catId, "amount", "5000.00", "type", "INCOME",
                        "description", "Salary", "frequency", "MONTHLY",
                        "startDate", twoMonthsAgo.toString()));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Listing transactions triggers materialiser. Expect 3 rows (months 0, 1, 2 ago through today).
        JsonNode txList = getJson("/api/v1/transactions?limit=100");
        int count = 0;
        for (JsonNode tx : txList.get("data")) {
            if ("Salary".equals(tx.get("category").asText())) count++;
        }
        assertThat(count).isGreaterThanOrEqualTo(2);

        // Pause + list again -> no new rows
        long recurringId = json.readTree(created.getBody()).get("data").get("id").asLong();
        ResponseEntity<String> paused = http.exchange("/api/v1/recurring/" + recurringId + "/pause",
                HttpMethod.POST, new HttpEntity<>(authHeaders()), String.class);
        assertThat(paused.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(paused.getBody()).get("data").get("paused").asBoolean()).isTrue();
    }

    @Test
    void allSlice6Endpoints_return401WithoutBearer() {
        for (String url : new String[]{
                "/api/v1/categories",
                "/api/v1/budgets?year=2026&month=5",
                "/api/v1/budgets/status?year=2026&month=5",
                "/api/v1/recurring"}) {
            ResponseEntity<String> r = http.getForEntity(url, String.class);
            assertThat(r.getStatusCode())
                    .as("expected 401 for " + url).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // helpers

    private long createCategory(String name, String kind) throws Exception {
        ResponseEntity<String> r = post("/api/v1/categories", Map.of("name", name, "kind", kind));
        return json.readTree(r.getBody()).get("data").get("id").asLong();
    }

    private void createTransaction(String type, String amount, String category,
                                   String description, String date) {
        post("/api/v1/transactions", Map.of(
                "amount", amount, "type", type, "category", category,
                "description", description, "transactionDate", date));
    }

    private ResponseEntity<String> post(String path, Object body) {
        HttpHeaders h = authHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    private ResponseEntity<String> put(String path, Object body) {
        HttpHeaders h = authHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, h), String.class);
    }

    private JsonNode getJson(String path) throws Exception {
        return json.readTree(http.exchange(path, HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class).getBody());
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }
}
