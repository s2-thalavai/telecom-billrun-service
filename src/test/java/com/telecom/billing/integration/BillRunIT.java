package com.telecom.billing.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BillRunIT extends AbstractJobIT {

    @LocalServerPort
    private int port;

    @Test
    void billRunRunsInTheBackgroundAndCompletes() {
        String cycle = nextCycle();

        long started = System.nanoTime();
        ResponseEntity<JsonNode> accepted = startBillRun(cycle);
        long submitMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(submitMs).isLessThan(1000);                                // the ~3 s of work is NOT in the request
        UUID id = UUID.fromString(accepted.getBody().get("id").asText());
        assertThat(accepted.getHeaders().getLocation()).hasToString("/api/v1/jobs/" + id);

        JsonNode midway = awaitJob(id, j -> j.get("status").asText().equals("RUNNING")
                && j.get("processedItems").asLong() > 0, Duration.ofSeconds(10));
        assertThat(midway.get("percent").asDouble()).isBetween(0.1, 100.0);
        assertThat(midway.has("etaSeconds")).isTrue();

        JsonNode done = awaitStatus(id, Set.of("COMPLETED", "FAILED", "CANCELLED"));
        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.get("totalItems").asLong()).isEqualTo(ACCOUNTS);
        assertThat(done.get("processedItems").asLong()).isEqualTo(BILLABLE);
        assertThat(done.get("failedItems").asLong()).isEqualTo(FAILING);
        assertThat(done.get("percent").asDouble()).isEqualTo(100.0);
        assertThat(invoices.countByCycle(cycle)).isEqualTo(BILLABLE);

        JsonNode summary = rest.getForObject("/api/v1/bill-runs/" + cycle + "/summary", JsonNode.class);
        assertThat(summary.get("invoices").asLong()).isEqualTo(BILLABLE);
    }

    @Test
    void rerunningACompletedCycleIsIdempotent() {
        String cycle = nextCycle();
        awaitStatus(start(cycle), Set.of("COMPLETED"));

        JsonNode rerun = awaitStatus(start(cycle), Set.of("COMPLETED"));

        assertThat(rerun.get("processedItems").asLong()).isEqualTo(BILLABLE);   // all skipped as already invoiced
        assertThat(invoices.countByCycle(cycle)).isEqualTo(BILLABLE);           // no double billing
    }

    @Test
    void secondActiveRunForTheSameCycleIs409() {
        String cycle = nextCycle();
        start(cycle);

        assertThat(startBillRun(cycle).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void progressIsStreamedOverSseUntilTheJobFinishes() throws Exception {
        UUID id = start(nextCycle());
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/jobs/" + id + "/progress"))
                .timeout(Duration.ofSeconds(30)).GET().build();

        // the stream ends when the job reaches a final status (the server completes the emitter)
        List<String> dataLines = client.send(request, HttpResponse.BodyHandlers.ofLines()).body()
                .filter(line -> line.startsWith("data:"))
                .toList();

        assertThat(dataLines.size()).isGreaterThan(2);
        assertThat(dataLines.getLast()).contains("\"status\":\"COMPLETED\"");
    }

    @Test
    void invalidRequestsAndUnknownJobs() {
        assertThat(startBillRun("2030-13").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.getForEntity("/api/v1/jobs/" + UUID.randomUUID(), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
