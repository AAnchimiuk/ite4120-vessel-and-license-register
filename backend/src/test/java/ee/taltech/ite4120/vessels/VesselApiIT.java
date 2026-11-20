package ee.taltech.ite4120.vessels;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * End-to-end over real HTTP, with the application talking to its OWN built-in mock
 * X-Road / population registry ({@code /mock-registry}) through the real
 * {@code HttpOwnerRegistryAdapter} — nothing mocked. Proves the wiring: security,
 * JSON, problem+json errors, Location headers, and the registry round trip.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
            "server.port=18491",
            "animals.owner-registry.mock-server=true",
            "animals.owner-registry.mode=http",
            "animals.owner-registry.url=http://localhost:18491/mock-registry"
        })
@ActiveProfiles("test")
class VesselApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final String BASE = "http://localhost:18491";
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void theApiRequiresAuthentication() throws Exception {
        assertThat(call("GET", "/api/vessels", null, false).statusCode()).isEqualTo(401);
    }

    @Test
    void referenceListsAreServed() throws Exception {
        assertThat(call("GET", "/api/vessels/types", null, true).body()).contains("FISHING", "TUG");
        assertThat(call("GET", "/api/vessels/retirement-reasons", null, true).body()).contains("SCRAPPED");
        assertThat(call("GET", "/api/licenses/types", null, true).body()).contains("NAVIGATION");
    }

    @Test
    void fullLifecycleThroughHttpAgainstTheMockRegistry() throws Exception {
        // register, with an owner the mock registry knows (Jaan Tamm)
        HttpResponse<String> created = call("POST", "/api/vessels", """
                {"registryCode":"API-001","name":"Api Vessel","typeCode":"CARGO","buildYear":2015,
                 "homePort":"Tallinn","grossTonnage":250.5,"ownerIsikukood":"38102130265"}""", true);
        assertThat(created.statusCode()).isEqualTo(201);
        String id = idOf(created.body());
        assertThat(created.headers().firstValue("Location")).hasValue("/api/vessels/" + id);

        // duplicate code -> 409 problem+json
        HttpResponse<String> dup = call("POST", "/api/vessels", """
                {"registryCode":"API-001","name":"Dup","typeCode":"CARGO"}""", true);
        assertThat(dup.statusCode()).isEqualTo(409);
        assertThat(dup.headers().firstValue("Content-Type").orElse("")).contains("problem+json");

        // shape validation -> 400
        assertThat(call("POST", "/api/vessels", """
                {"registryCode":"API-002","name":"Bad","typeCode":"CARGO","ownerIsikukood":"12"}""", true)
                .statusCode()).isEqualTo(400);

        // owner comes live from the mock X-Road registry
        HttpResponse<String> owner = call("GET", "/api/vessels/" + id + "/owner", null, true);
        assertThat(owner.statusCode()).isEqualTo(200);
        assertThat(owner.body()).contains("Jaan", "Tamm");

        // transfer: unknown person -> 400, nothing changes
        LocalDate yesterday = LocalDate.now().minusDays(1);
        assertThat(call("POST", "/api/vessels/" + id + "/ownership",
                "{\"newOwnerIsikukood\":\"11111111111\",\"transferDate\":\"" + LocalDate.now() + "\"}", true)
                .statusCode()).isEqualTo(400);

        // transfer to Mari Kask -> 201; the vessel started its ownership today, so use today's date? it must be AFTER
        // the open period start, so first back-date is impossible: expect 400 for today, then it works tomorrow-less.
        HttpResponse<String> sameDay = call("POST", "/api/vessels/" + id + "/ownership",
                "{\"newOwnerIsikukood\":\"47503121234\",\"transferDate\":\"" + LocalDate.now() + "\"}", true);
        assertThat(sameDay.statusCode()).as("not after the period that opened today").isEqualTo(400);
        assertThat(yesterday).isBefore(LocalDate.now());

        // license: add, list with derived status, revoke, revoke again
        HttpResponse<String> lic = call("POST", "/api/vessels/" + id + "/licenses", """
                {"licenseNumber":"API-L1","licenseType":"NAVIGATION",
                 "issuedOn":"%s","validUntil":"%s"}""".formatted(LocalDate.now().minusDays(5), LocalDate.now().plusDays(10)), true);
        assertThat(lic.statusCode()).isEqualTo(201);
        assertThat(lic.body()).contains("EXPIRING_SOON");
        String licenseId = idOf(lic.body());
        assertThat(call("GET", "/api/licenses/expiring?withinDays=30&licenseType=NAVIGATION", null, true).body())
                .contains("API-L1");
        assertThat(call("GET", "/api/licenses/expiring?withinDays=0", null, true).statusCode()).isEqualTo(400);

        String revoke = "{\"revokedOn\":\"" + LocalDate.now() + "\",\"reason\":\"Failed inspection\"}";
        HttpResponse<String> revoked = call("POST", "/api/vessels/" + id + "/licenses/" + licenseId + "/revocation", revoke, true);
        assertThat(revoked.statusCode()).isEqualTo(200);
        assertThat(revoked.body()).contains("REVOKED");
        assertThat(call("POST", "/api/vessels/" + id + "/licenses/" + licenseId + "/revocation", revoke, true)
                .statusCode()).isEqualTo(409);
        assertThat(call("POST", "/api/vessels/" + id + "/licenses/" + licenseId + "/revocation",
                "{\"revokedOn\":\"" + LocalDate.now() + "\",\"reason\":\"  \"}", true).statusCode()).isEqualTo(400);

        // retire -> 200; second retire -> 409; frozen -> 409; still readable; code reusable
        String retire = "{\"retiredOn\":\"" + LocalDate.now() + "\",\"reason\":\"SCRAPPED\"}";
        assertThat(call("POST", "/api/vessels/" + id + "/retirement", retire, true).statusCode()).isEqualTo(200);
        assertThat(call("POST", "/api/vessels/" + id + "/retirement", retire, true).statusCode()).isEqualTo(409);
        assertThat(call("POST", "/api/vessels/" + id + "/licenses", """
                {"licenseNumber":"X","licenseType":"NAVIGATION","issuedOn":"2026-01-01","validUntil":"2027-01-01"}""", true)
                .statusCode()).isEqualTo(409);
        HttpResponse<String> read = call("GET", "/api/vessels/" + id, null, true);
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(read.body()).contains("SCRAPPED");
        assertThat(call("POST", "/api/vessels", """
                {"registryCode":"API-001","name":"Reborn","typeCode":"TUG"}""", true).statusCode()).isEqualTo(201);

        // unknown vessel -> 404 problem+json
        assertThat(call("GET", "/api/vessels/999999999", null, true).statusCode()).isEqualTo(404);
    }

    @Test
    void unknownPersonInTheMockRegistryIsA404OnTheOwnerEndpoint() throws Exception {
        // 50011020017 (Peeter Sepp) is known; a made-up but well-formed code is not
        HttpResponse<String> created = call("POST", "/api/vessels", """
                {"registryCode":"API-GHOST","name":"Ghost owner","typeCode":"TUG","ownerIsikukood":"99999999999"}""", true);
        String id = idOf(created.body());
        assertThat(call("GET", "/api/vessels/" + id + "/owner", null, true).statusCode()).isEqualTo(404);
    }

    private static String idOf(String json) {
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(json);
        assertThat(m.find()).as("id in " + json).isTrue();
        return m.group(1);
    }

    private HttpResponse<String> call(String method, String path, String body, boolean auth) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + path)).header("Accept", "application/json");
        if (auth) {
            b.header("Authorization", "Bearer tester");
        }
        if (body != null) {
            b.header("Content-Type", "application/json");
        }
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
