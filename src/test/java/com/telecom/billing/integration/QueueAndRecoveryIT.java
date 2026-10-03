package com.telecom.billing.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.telecom.billing.entity.BackgroundJob;
import com.telecom.billing.entity.JobType;
import com.telecom.billing.entity.SubscriberAccount;
import com.telecom.billing.service.BillingService;
import com.telecom.billing.service.StaleJobRecovery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class QueueAndRecoveryIT extends AbstractJobIT {

    @Autowired
    private BillingService billing;

    @Autowired
    private StaleJobRecovery recovery;

    @Test
    void jobsBeyondWorkersPlusQueueAreRejectedAsFailed() {
        UUID running = start(nextCycle());
        awaitStatus(running, Set.of("RUNNING"));
        UUID queued = start(nextCycle());
        UUID rejected = start(nextCycle());                    // 1 worker + queue of 1 -> no room

        assertThat(job(queued).get("status").asText()).isEqualTo("QUEUED");
        JsonNode failed = awaitStatus(rejected, Set.of("FAILED"));
        assertThat(failed.get("message").asText()).contains("queue is full");
    }

    @Test
    void orphanedJobIsResumedFromItsCheckpointWithoutDoubleBilling() {
        String cycle = nextCycle();
        // simulate a crash: accounts 1..150 were billed (committed), but progress was only recorded up to 100
        List<String> firstAccounts = IntStream.rangeClosed(1, 150).mapToObj(SubscriberAccount::idFor).toList();
        billing.billChunk(cycle, firstAccounts);
        long invoicedBeforeCrash = invoices.countByCycle(cycle);                       // 149 (ACC-000100 fails)
        UUID id = jobRepository.save(BackgroundJob.orphanedRunning(JobType.BILL_RUN, cycle, ACCOUNTS, 99,
                SubscriberAccount.idFor(100), Instant.now().minus(Duration.ofMinutes(10)))).getId();

        recovery.recover();                                                             // stale heartbeat -> requeue + launch

        JsonNode done = awaitStatus(id, Set.of("COMPLETED", "FAILED", "CANCELLED"));
        assertThat(invoicedBeforeCrash).isEqualTo(149);
        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(invoices.countByCycle(cycle)).isEqualTo(BILLABLE);                  // 594: nothing billed twice
        assertThat(done.get("processedItems").asLong()).isEqualTo(BILLABLE);           // 99 + 50 skipped + 445 billed
    }

    @Test
    void healthyRunningJobIsNotTouchedByRecovery() {
        UUID id = start(nextCycle());
        awaitStatus(id, Set.of("RUNNING"));

        recovery.recover();

        assertThat(awaitStatus(id, Set.of("COMPLETED", "FAILED", "CANCELLED")).get("status").asText())
                .isEqualTo("COMPLETED");
    }
}
