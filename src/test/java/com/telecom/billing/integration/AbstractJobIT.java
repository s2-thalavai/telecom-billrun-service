package com.telecom.billing.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.telecom.billing.entity.BackgroundJob;
import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.repository.InvoiceRepository;
import com.telecom.billing.repository.JobRepository;
import com.telecom.billing.service.JobCancellationService;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.awaitility.Awaitility.await;

/**
 * Real application on a random port. Test profile: 600 seeded accounts, 5 ms rating each (~3 s per run),
 * every 100th account fails rating, chunk size 50, ONE job worker with a queue of ONE.
 * Every test uses its own billing cycle; after each test all active jobs are cancelled.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class AbstractJobIT {

    static final int ACCOUNTS = 600;
    static final int FAILING = 6;                       // ACC-000100, 200, ..., 600
    static final int BILLABLE = ACCOUNTS - FAILING;
    private static final AtomicInteger CYCLES = new AtomicInteger();

    @Autowired
    protected TestRestTemplate rest;
    @Autowired
    protected JobRepository jobRepository;
    @Autowired
    protected InvoiceRepository invoices;
    @Autowired
    protected JobCancellationService cancellations;

    /** Unique, valid cycle per test: 2030-01, 2030-02, ... */
    protected static String nextCycle() {
        int n = CYCLES.getAndIncrement();
        return "%d-%02d".formatted(2030 + n / 12, n % 12 + 1);
    }

    protected ResponseEntity<JsonNode> startBillRun(String cycle) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User", "it");
        return rest.postForEntity("/api/v1/bill-runs", new HttpEntity<>(Map.of("cycle", cycle), headers), JsonNode.class);
    }

    protected UUID start(String cycle) {
        return UUID.fromString(startBillRun(cycle).getBody().get("id").asText());
    }

    protected JsonNode job(UUID id) {
        return rest.getForObject("/api/v1/jobs/" + id, JsonNode.class);
    }

    protected JsonNode awaitJob(UUID id, Predicate<JsonNode> condition, Duration timeout) {
        await().atMost(timeout).pollInterval(Duration.ofMillis(100)).until(() -> condition.test(job(id)));
        return job(id);
    }

    protected JsonNode awaitStatus(UUID id, Set<String> statuses) {
        return awaitJob(id, j -> statuses.contains(j.get("status").asText()), Duration.ofSeconds(30));
    }

    @AfterEach
    void cancelEverythingStillActive() {
        for (BackgroundJob active : jobRepository.findByStatusIn(JobStatus.ACTIVE)) {
            try {
                cancellations.cancel(active.getId(), true);
            } catch (RuntimeException alreadyFinished) {
                // raced with completion: fine
            }
        }
        await().atMost(Duration.ofSeconds(15))
               .until(() -> jobRepository.findByStatusIn(JobStatus.ACTIVE).isEmpty());
    }
}
