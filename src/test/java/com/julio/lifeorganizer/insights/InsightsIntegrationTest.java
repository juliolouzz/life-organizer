package com.julio.lifeorganizer.insights;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.lifeorganizer.AbstractIntegrationTest;
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

// End-to-end coverage of /api/v1/insights endpoints against a live Postgres.
@Tag("integration")
class InsightsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String email = "insights" + System.nanoTime() + "@example.com";
        http.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", "S3cretValue", "displayName", "Insights"),
                String.class);
        ResponseEntity<String> login = http.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "S3cretValue"), String.class);
        token = json.readTree(login.getBody()).get("data").get("accessToken").asText();

        // Seed: 3 income + 4 expenses + 2 savings (Slice 5)
        createTx("INCOME",  "5000.00", "Salary",    "Monthly salary",  "2026-05-01");
        createTx("INCOME",   "200.00", "Bonus",     "Project bonus",   "2026-05-15");
        createTx("INCOME",   "300.00", "Bonus",     "Late bonus",      "2026-05-15");
        createTx("EXPENSE", "1500.00", "Rent",      "May rent",        "2026-05-05");
        createTx("EXPENSE",  "412.50", "Groceries", "Week 1 shop",     "2026-05-07");
        createTx("EXPENSE",  "150.00", "Groceries", "Week 2 shop",     "2026-05-14");
        createTx("EXPENSE",   "60.00", "Transport", "Taxi",            "2026-05-20");
        createTx("SAVINGS",  "500.00", "Emergency", "Monthly transfer", "2026-05-02");
        createTx("SAVINGS",  "200.00", "Travel",    "Trip fund",       "2026-05-25");
    }

    @Test
    void summary_returnsTotalsAndPreviousPeriod() throws Exception {
        JsonNode body = get("/api/v1/insights/summary?from=2026-05-01&to=2026-05-31");
        JsonNode d = body.get("data");

        assertThat(d.get("totalIncome").decimalValue()).isEqualByComparingTo("5500.00");
        assertThat(d.get("totalExpense").decimalValue()).isEqualByComparingTo("2122.50");
        assertThat(d.get("totalSavings").decimalValue()).isEqualByComparingTo("700.00");
        // Slice 5: net = income - expense - savings
        assertThat(d.get("net").decimalValue()).isEqualByComparingTo("2677.50");
        assertThat(d.get("incomeCount").asLong()).isEqualTo(3L);
        assertThat(d.get("expenseCount").asLong()).isEqualTo(4L);
        assertThat(d.get("savingsCount").asLong()).isEqualTo(2L);

        JsonNode prev = d.get("previousPeriod");
        assertThat(prev.get("from").asText()).isEqualTo("2026-03-31");
        assertThat(prev.get("to").asText()).isEqualTo("2026-04-30");
        assertThat(prev.get("totalIncome").decimalValue()).isEqualByComparingTo("0");
        assertThat(prev.get("totalExpense").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void byCategory_aggregatesPerCategoryAndType_sortedByTotalDesc() throws Exception {
        JsonNode body = get("/api/v1/insights/by-category?from=2026-05-01&to=2026-05-31");
        JsonNode data = body.get("data");

        // Salary is the largest single (income/category) row at 5000
        assertThat(data.get(0).get("category").asText()).isEqualTo("Salary");
        assertThat(data.get(0).get("type").asText()).isEqualTo("INCOME");
        assertThat(data.get(0).get("total").decimalValue()).isEqualByComparingTo("5000.00");

        // "Groceries / EXPENSE" total is 562.50 (412.50 + 150.00) and count 2
        boolean foundGroceries = false;
        for (JsonNode row : data) {
            if ("Groceries".equals(row.get("category").asText())
                    && "EXPENSE".equals(row.get("type").asText())) {
                assertThat(row.get("total").decimalValue()).isEqualByComparingTo("562.50");
                assertThat(row.get("count").asLong()).isEqualTo(2L);
                foundGroceries = true;
            }
        }
        assertThat(foundGroceries).isTrue();
    }

    @Test
    void byPeriod_dailyGranularity_fillsEmptyBucketsWithZero() throws Exception {
        JsonNode body = get("/api/v1/insights/by-period?from=2026-05-01&to=2026-05-07");
        JsonNode meta = body.get("meta");
        JsonNode data = body.get("data");

        assertThat(meta.get("granularity").asText()).isEqualTo("DAY");
        // 7 days, all present
        assertThat(data.size()).isEqualTo(7);

        // 2026-05-01: income 5000, expense 0
        JsonNode may1 = data.get(0);
        assertThat(may1.get("bucket").asText()).isEqualTo("2026-05-01");
        assertThat(may1.get("income").decimalValue()).isEqualByComparingTo("5000.00");
        assertThat(may1.get("expense").decimalValue()).isEqualByComparingTo("0");

        // 2026-05-02: only the 500 savings transfer, expense 0
        JsonNode may2 = data.get(1);
        assertThat(may2.get("bucket").asText()).isEqualTo("2026-05-02");
        assertThat(may2.get("income").decimalValue()).isEqualByComparingTo("0");
        assertThat(may2.get("expense").decimalValue()).isEqualByComparingTo("0");
        assertThat(may2.get("savings").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(may2.get("net").decimalValue()).isEqualByComparingTo("-500.00");

        // 2026-05-05: expense 1500
        JsonNode may5 = data.get(4);
        assertThat(may5.get("expense").decimalValue()).isEqualByComparingTo("1500.00");
        assertThat(may5.get("net").decimalValue()).isEqualByComparingTo("-1500.00");
    }

    @Test
    void createTransaction_withoutDescription_succeeds_andStoresEmptyString() throws Exception {
        // Slice 5 AC-5-4: description is optional. Posting a row with no description field
        // returns 201; reading it back shows description = "".
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = http.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "amount", "9.90",
                        "type", "EXPENSE",
                        "category", "Coffee",
                        "transactionDate", "2026-05-22"
                        // intentionally no "description"
                ), h),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode data = json.readTree(resp.getBody()).get("data");
        assertThat(data.get("description").asText()).isEqualTo("");
    }

    @Test
    void byPeriod_largeRange_autoSelectsMonthlyGranularity() throws Exception {
        // 365 days > 90 -> MONTH
        JsonNode body = get("/api/v1/insights/by-period?from=2026-01-01&to=2026-12-31");
        assertThat(body.get("meta").get("granularity").asText()).isEqualTo("MONTH");
        assertThat(body.get("data").size()).isEqualTo(12);
    }

    @Test
    void summary_whenFromAfterTo_returns400_INVALID_QUERY() throws Exception {
        ResponseEntity<String> r = httpGet("/api/v1/insights/summary?from=2026-05-31&to=2026-05-01");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(r.getBody()).get("meta").get("code").asText())
                .isEqualTo("INVALID_QUERY");
    }

    @Test
    void allInsightsEndpoints_returns401_withoutBearerToken() {
        for (String url : new String[]{
                "/api/v1/insights/summary?from=2026-05-01&to=2026-05-31",
                "/api/v1/insights/by-category?from=2026-05-01&to=2026-05-31",
                "/api/v1/insights/by-period?from=2026-05-01&to=2026-05-31"}) {
            ResponseEntity<String> r = http.getForEntity(url, String.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // helpers

    private JsonNode get(String path) throws Exception {
        ResponseEntity<String> r = httpGet(path);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(r.getBody());
    }

    private ResponseEntity<String> httpGet(String path) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    private void createTx(String type, String amount, String category, String description, String date)
            throws Exception {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        http.exchange("/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "amount", amount, "type", type,
                        "category", category, "description", description,
                        "transactionDate", date), h), String.class);
    }
}
