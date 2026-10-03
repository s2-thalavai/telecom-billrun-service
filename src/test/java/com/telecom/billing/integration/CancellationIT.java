package com.telecom.billing.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationIT extends AbstractJobIT {

    private static final int CHUNK_SIZE = 50;

    private ResponseEntity<JsonNode> cancel(UUID id, boolean force) {
        return rest.postForEntity("/api/v1/jobs/" + id + "/cancel?force=" + force, null, JsonNode.class);
    }

    private void awaitSomeProgress(UUID id) {
        awaitJob(id, j -> j.get("processedItems").asLong() > 0, Duration.ofSeconds(10));
    }

    @Test
    void cooperativeCancelStopsAtAChunkBoundary() {
        String cycle = nextCycle();
        UUID id = start(cycle);
        awaitSomeProgress(id);

        assertThat(cancel(id, false).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode done = awaitStatus(id, Set.of("CANCELLED", "COMPLETED"));

        assertThat(done.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(done.get("message").asText()).isEqualTo("Cancelled on request");
        // stopped between chunks: committed invoices and recorded progress match exactly
        assertThat(invoices.countByCycle(cycle)).isEqualTo(done.get("processedItems").asLong());
        assertThat(invoices.countByCycle(cycle)).isLessThan(BILLABLE);
    }

    @Test
    void forcedCancelInterruptsTheWorker() {
        String cycle = nextCycle();
        UUID id = start(cycle);
        awaitSomeProgress(id);

        cancel(id, true);
        JsonNode done = awaitStatus(id, Set.of("CANCELLED", "COMPLETED"));

        assertThat(done.get("status").asText()).isEqualTo("CANCELLED");
        // the interrupted chunk rolled back; at most one chunk can be committed but not yet counted
        long gap = invoices.countByCycle(cycle) - done.get("processedItems").asLong();
        assertThat(gap).isBetween(0L, (long) CHUNK_SIZE);
    }

    @Test
    void queuedJobIsCancelledBeforeItEverStarts() {
        UUID running = start(nextCycle());
        awaitStatus(running, Set.of("RUNNING"));
        String queuedCycle = nextCycle();
        UUID queued = start(queuedCycle);
        assertThat(job(queued).get("status").asText()).isEqualTo("QUEUED");      // single worker is busy

        JsonNode cancelled = cancel(queued, false).getBody();
        cancel(running, true);
        awaitStatus(running, Set.of("CANCELLED", "COMPLETED"));

        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        JsonNode after = job(queued);
        assertThat(after.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(after.has("startedAt")).isFalse();                            // the worker refused to start it
        assertThat(invoices.countByCycle(queuedCycle)).isZero();
    }

    @Test
    void finishedJobsCannotBeCancelled() {
        UUID id = start(nextCycle());
        awaitStatus(id, Set.of("COMPLETED"));

        assertThat(cancel(id, false).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
