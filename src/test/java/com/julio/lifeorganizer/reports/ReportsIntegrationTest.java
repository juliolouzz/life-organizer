package com.julio.lifeorganizer.reports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.lifeorganizer.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.LinkedHashMap;
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
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class ReportsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;
    @LocalServerPort private int port;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        String email = "reports-" + System.nanoTime() + "@example.com";
        http.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", "S3cretValue", "displayName", "Reports"),
                String.class);
        accessToken = json.readTree(http.postForEntity("/api/v1/auth/login",
                        Map.of("email", email, "password", "S3cretValue"), String.class).getBody())
                .get("data").get("accessToken").asText();
    }

    @Test
    void summary_emptyMonth_returnsZeroTotalsAndOneRowPerDay() throws Exception {
        int year = 2024;
        int month = 2; // 29 days in 2024
        ResponseEntity<String> resp = http.exchange(
                "/api/v1/reports/summary?year=" + year + "&month=" + month,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = json.readTree(resp.getBody()).get("data");
        assertThat(data.get("totals").get("income").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(data.get("totals").get("expense").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(data.get("totals").get("transactionCount").asLong()).isZero();
        assertThat(data.get("topCategories")).isEmpty();
        assertThat(data.get("daily")).hasSize(29);
    }

    @Test
    void summary_withData_aggregatesCorrectly() throws Exception {
        int year = 2025;
        int month = 6;
        createTransaction("2025-06-01", "INCOME", "100.00", "Salary", null);
        createTransaction("2025-06-03", "EXPENSE", "20.00", "Groceries", "Carrots");
        createTransaction("2025-06-15", "EXPENSE", "30.00", "Groceries", null);
        createTransaction("2025-06-20", "SAVINGS", "10.00", "Vault", null);

        JsonNode data = getJson("/api/v1/reports/summary?year=" + year + "&month=" + month);
        assertThat(data.get("totals").get("income").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(data.get("totals").get("expense").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(data.get("totals").get("savings").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(data.get("totals").get("net").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(data.get("totals").get("transactionCount").asLong()).isEqualTo(4);

        // Top categories are sorted by absolute amount across all types; Salary
        // at 100 outranks Groceries at 50.
        JsonNode top = data.get("topCategories");
        assertThat(top).isNotEmpty();
        assertThat(top.get(0).get("name").asText()).isEqualTo("Salary");
    }

    @Test
    void yoy_computesDeltas() throws Exception {
        createTransaction("2024-05-01", "EXPENSE", "100.00", "Food", null);
        createTransaction("2025-05-01", "EXPENSE", "120.00", "Food", null);

        JsonNode data = getJson("/api/v1/reports/yoy?year=2025&month=5");
        assertThat(data.get("thisYear").get("totals").get("expense").decimalValue())
                .isEqualByComparingTo("120.00");
        assertThat(data.get("lastYear").get("totals").get("expense").decimalValue())
                .isEqualByComparingTo("100.00");
        JsonNode expenseDelta = data.get("deltas").get("expense");
        assertThat(expenseDelta.get("absolute").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(expenseDelta.get("percent").decimalValue()).isEqualByComparingTo("20.00");
    }

    @Test
    void yoy_percentIsNullWhenLastYearIsZero() throws Exception {
        createTransaction("2025-05-01", "EXPENSE", "50.00", "OnlyThisYear", null);

        JsonNode data = getJson("/api/v1/reports/yoy?year=2025&month=5");
        JsonNode expense = data.get("deltas").get("expense");
        assertThat(expense.get("absolute").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(expense.get("percent").isNull()).isTrue();
    }

    @Test
    void trends_excludesCategoriesWithNoActivity() throws Exception {
        // Two recent rows in the same category; ensure the series surfaces.
        LocalDate today = LocalDate.now();
        createTransaction(today.minusMonths(2).withDayOfMonth(5).toString(),
                "EXPENSE", "20.00", "TrendCat", null);
        createTransaction(today.minusMonths(1).withDayOfMonth(5).toString(),
                "EXPENSE", "25.00", "TrendCat", null);

        JsonNode data = getJson("/api/v1/reports/trends?months=6");
        boolean found = false;
        for (JsonNode series : data.get("series")) {
            if ("TrendCat".equals(series.get("name").asText())) {
                found = true;
                assertThat(series.get("points")).isNotEmpty();
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void summaryCsv_includesAllSections() throws Exception {
        createTransaction("2025-07-10", "EXPENSE", "12.50", "Coffee", null);

        ResponseEntity<byte[]> resp = http.exchange(
                "/api/v1/reports/summary.csv?year=2025&month=7",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                byte[].class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getContentType().toString()).contains("text/csv");
        String body = new String(resp.getBody());
        assertThat(body).contains("Totals");
        assertThat(body).contains("Top Categories");
        assertThat(body).contains("Daily");
        assertThat(body).contains("Coffee");
    }

    @Test
    void summaryPdf_returnsMagicBytesAndNonEmpty() throws Exception {
        ResponseEntity<byte[]> resp = http.exchange(
                "/api/v1/reports/summary.pdf?year=2025&month=8",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                byte[].class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        byte[] body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.length).isGreaterThan(1024);
        // %PDF magic bytes
        assertThat(new String(body, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void transactionsCsv_roundTripsThroughImport() throws Exception {
        createTransaction("2025-04-05", "EXPENSE", "9.99", "Books", "Novel");
        createTransaction("2025-04-12", "INCOME", "1000.00", "Salary", null);

        ResponseEntity<byte[]> exportResp = http.exchange(
                "/api/v1/reports/transactions.csv?from=2025-04-01&to=2025-04-30",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                byte[].class);
        assertThat(exportResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String csv = new String(exportResp.getBody());
        assertThat(csv).startsWith("\"date\",\"type\",\"amount\",\"category\",\"description\"");
        assertThat(csv).contains("2025-04-05");
        assertThat(csv).contains("Books");
    }

    @Test
    void anonymous_callIsRejected() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/reports/summary?year=2025&month=1"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(401);
    }

    // ----- helpers -----

    private JsonNode getJson(String path) throws Exception {
        ResponseEntity<String> resp = http.exchange(path, HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(resp.getBody()).get("data");
    }

    private void createTransaction(String date, String type, String amount,
                                   String category, String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transactionDate", date);
        body.put("type", type);
        body.put("amount", amount);
        body.put("category", category);
        if (description != null) body.put("description", description);
        ResponseEntity<String> resp = http.exchange("/api/v1/transactions",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()),
                String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("create transaction failed: %s", resp.getBody())
                .isTrue();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(accessToken);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
