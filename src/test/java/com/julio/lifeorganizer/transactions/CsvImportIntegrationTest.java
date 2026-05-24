package com.julio.lifeorganizer.transactions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.lifeorganizer.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class CsvImportIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;
    @LocalServerPort private int port;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String email = "csv" + System.nanoTime() + "@example.com";
        http.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", "S3cretValue", "displayName", "CSV User"),
                String.class);
        token = json.readTree(http.postForEntity("/api/v1/auth/login",
                        Map.of("email", email, "password", "S3cretValue"), String.class).getBody())
                .get("data").get("accessToken").asText();
    }

    @Test
    void import_happyPath_insertsAllRows() throws Exception {
        String csv = """
                date,amount,type,category,description
                2026-05-01,5000.00,INCOME,Salary,May salary
                2026-05-05,1500.00,EXPENSE,Rent,May rent
                2026-05-07,42.50,EXPENSE,Groceries,Week 1 shop
                2026-05-14,150.00,EXPENSE,Groceries,Week 2 shop
                """;
        JsonNode result = upload(csv).get("data");
        assertThat(result.get("inserted").asInt()).isEqualTo(4);
        assertThat(result.get("skipped").asInt()).isZero();
        assertThat(result.get("errors").size()).isZero();

        // Listing returns the imported rows
        JsonNode list = json.readTree(http.exchange("/api/v1/transactions?limit=10",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class).getBody());
        assertThat(list.get("data").size()).isEqualTo(4);
    }

    @Test
    void import_brazilianDateAndCommaDecimal_isAccepted() throws Exception {
        String csv = """
                date,amount,type,category,description
                01/05/2026,"1.234,56",EXPENSE,Rent,Brazilian formatting
                """;
        JsonNode result = upload(csv).get("data");
        assertThat(result.get("inserted").asInt()).isEqualTo(1);
        assertThat(result.get("skipped").asInt()).isZero();
    }

    @Test
    void import_missingDescriptionColumn_isOk() throws Exception {
        String csv = """
                date,amount,type,category
                2026-05-01,100.00,EXPENSE,Coffee
                """;
        JsonNode result = upload(csv).get("data");
        assertThat(result.get("inserted").asInt()).isEqualTo(1);
        assertThat(result.get("skipped").asInt()).isZero();
    }

    @Test
    void import_perRowErrors_recordedButOthersStillImport() throws Exception {
        String csv = """
                date,amount,type,category,description
                2026-05-01,100.00,EXPENSE,Coffee,ok
                bad-date,50.00,EXPENSE,Coffee,bad date
                2026-05-02,-10.00,EXPENSE,Coffee,negative amount
                2026-05-03,99.00,WRONG_TYPE,Coffee,bad type
                2026-05-04,200.00,EXPENSE,,blank category
                2026-05-05,300.00,EXPENSE,Coffee,ok again
                """;
        JsonNode result = upload(csv).get("data");
        assertThat(result.get("inserted").asInt()).isEqualTo(2);
        assertThat(result.get("skipped").asInt()).isEqualTo(4);
        assertThat(result.get("errors").size()).isEqualTo(4);
    }

    @Test
    void import_missingRequiredColumn_returns400() throws Exception {
        // No "type" column at all
        String csv = """
                date,amount,category,description
                2026-05-01,100.00,Coffee,desc
                """;
        ResponseEntity<String> resp = uploadRaw(csv);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(resp.getBody()).get("meta").get("code").asText())
                .isEqualTo("MISSING_COLUMN");
    }

    @Test
    void import_emptyFile_returns400() throws Exception {
        ResponseEntity<String> resp = uploadRaw("");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void import_withoutBearer_returns401() throws Exception {
        // Use java.net.http.HttpClient because TestRestTemplate (via the JDK's
        // HttpURLConnection) refuses to read a 401 response on a streamed POST.
        String boundary = "----lo-test-" + System.nanoTime();
        String csv = "date,amount,type,category\n";
        String multipart = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"x.csv\"\r\n"
                + "Content-Type: text/csv\r\n\r\n"
                + csv + "\r\n--" + boundary + "--\r\n";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/transactions/import"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(multipart))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(401);
    }

    @Test
    void import_bankStatementFormat_mapsDebitAndCredit() throws Exception {
        // Typical bank export: dd/MM/yyyy dates, Debit=expense, Credit=income,
        // a balance column that varies per row (carried forward),
        // and the description in a "Details" column.
        String csv = """
                Date,Details,Debit,Credit,Balance
                24/05/2026,17MAY UBER *ONE ME,0.66,,1960.62
                24/05/2026,22MAY ALDI 872-054 (,34.53,,
                21/05/2026,SALARY MAY,,2500.00,
                """;
        JsonNode result = upload(csv).get("data");
        assertThat(result.get("inserted").asInt()).isEqualTo(3);
        assertThat(result.get("skipped").asInt()).isZero();
        assertThat(result.get("errors").size()).isZero();

        JsonNode list = json.readTree(http.exchange("/api/v1/transactions?limit=10",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class).getBody());
        assertThat(list.get("data").size()).isEqualTo(3);
    }

    @Test
    void import_bankStatement_balanceOnlyRowIsSilentlySkipped() throws Exception {
        // A "carry-forward balance" line has neither debit nor credit. The importer
        // should skip it without counting it as an error.
        String csv = """
                Date,Details,Debit,Credit,Balance
                24/05/2026,OPENING BALANCE,,,5000.00
                24/05/2026,COFFEE,4.50,,4995.50
                """;
        JsonNode result = upload(csv).get("data");
        assertThat(result.get("inserted").asInt()).isEqualTo(1);
        assertThat(result.get("skipped").asInt()).isZero();
        assertThat(result.get("errors").size()).isZero();
    }

    @Test
    void import_bankStatement_rowWithBothDebitAndCredit_isError() throws Exception {
        String csv = """
                Date,Details,Debit,Credit
                24/05/2026,AMBIGUOUS,10.00,5.00
                """;
        JsonNode result = upload(csv).get("data");
        assertThat(result.get("inserted").asInt()).isZero();
        assertThat(result.get("skipped").asInt()).isEqualTo(1);
        assertThat(result.get("errors").size()).isEqualTo(1);
    }

    @Test
    void import_autoCreatesUnknownCategories() throws Exception {
        String csv = """
                date,amount,type,category,description
                2026-05-01,100.00,EXPENSE,BrandNewCat,row1
                """;
        upload(csv);

        // Now /categories should include "BrandNewCat"
        JsonNode cats = json.readTree(http.exchange("/api/v1/categories",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class).getBody());
        boolean found = false;
        for (JsonNode c : cats.get("data")) {
            if ("BrandNewCat".equals(c.get("name").asText())) found = true;
        }
        assertThat(found).isTrue();
    }

    // helpers

    private JsonNode upload(String csv) throws Exception {
        ResponseEntity<String> resp = uploadRaw(csv);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(resp.getBody());
    }

    private ResponseEntity<String> uploadRaw(String csv) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return "import.csv"; }
        });
        HttpHeaders h = authHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.exchange("/api/v1/transactions/import",
                HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }
}
