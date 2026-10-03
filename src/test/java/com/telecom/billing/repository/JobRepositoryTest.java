package com.telecom.billing.repository;

import com.telecom.billing.entity.BackgroundJob;
import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.entity.JobType;
import com.telecom.billing.entity.SubscriberAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The conditional UPDATEs are the concurrency control: verify they behave as compare-and-set. */
@DataJpaTest
@ActiveProfiles("test")
class JobRepositoryTest {

    @Autowired
    private JobRepository jobs;

    @Autowired
    private SubscriberAccountRepository accounts;

    private UUID queued() {
        return jobs.save(BackgroundJob.queued(JobType.BILL_RUN, "2026-09", "test")).getId();
    }

    @Test
    void markRunningOnlyFromQueuedAndOnlyOnce() {
        UUID id = queued();

        assertThat(jobs.markRunning(id, 600, Instant.now(), JobStatus.QUEUED, JobStatus.RUNNING)).isEqualTo(1);
        assertThat(jobs.markRunning(id, 600, Instant.now(), JobStatus.QUEUED, JobStatus.RUNNING)).isZero();
        assertThat(jobs.findById(id).orElseThrow().getStatus()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void cancelRequestPreventsStart() {
        UUID id = queued();
        jobs.requestCancel(id, JobStatus.ACTIVE);

        assertThat(jobs.markRunning(id, 600, Instant.now(), JobStatus.QUEUED, JobStatus.RUNNING)).isZero();
        assertThat(jobs.isCancelRequested(id)).isTrue();
    }

    @Test
    void progressAccumulatesAndFinishHappensOnce() {
        UUID id = queued();
        jobs.markRunning(id, 600, Instant.now(), JobStatus.QUEUED, JobStatus.RUNNING);

        jobs.addProgress(id, 100, 1, "ACC-000100", Instant.now());
        jobs.addProgress(id, 99, 1, "ACC-000200", Instant.now());
        int first = jobs.transition(id, JobStatus.ACTIVE, JobStatus.COMPLETED, "done", Instant.now());
        int second = jobs.transition(id, JobStatus.ACTIVE, JobStatus.CANCELLED, "too late", Instant.now());

        BackgroundJob job = jobs.findById(id).orElseThrow();
        assertThat(job.getProcessedItems()).isEqualTo(199);
        assertThat(job.getFailedItems()).isEqualTo(2);
        assertThat(job.getCheckpoint()).isEqualTo("ACC-000200");
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    void onlyStaleRunningJobsAreRequeuedAndOnlyOnce() {
        Instant now = Instant.now();
        UUID stale = jobs.save(BackgroundJob.orphanedRunning(JobType.BILL_RUN, "2026-08", 600, 50, "ACC-000050",
                now.minus(Duration.ofMinutes(10)))).getId();
        UUID healthy = jobs.save(BackgroundJob.orphanedRunning(JobType.BILL_RUN, "2026-07", 600, 50, "ACC-000050",
                now)).getId();
        Instant cutoff = now.minus(Duration.ofMinutes(2));

        assertThat(jobs.findStale(JobStatus.RUNNING, cutoff)).containsExactly(stale);
        assertThat(jobs.requeueIfStale(stale, cutoff, JobStatus.RUNNING, JobStatus.QUEUED)).isEqualTo(1);
        assertThat(jobs.requeueIfStale(stale, cutoff, JobStatus.RUNNING, JobStatus.QUEUED)).isZero();
        assertThat(jobs.requeueIfStale(healthy, cutoff, JobStatus.RUNNING, JobStatus.QUEUED)).isZero();
    }

    @Test
    void keysetPaginationWalksAccountsInOrder() {
        IntStream.rangeClosed(1, 7).forEach(n -> accounts.save(
                new SubscriberAccount(SubscriberAccount.idFor(n), "98%08d".formatted(n), "UNL-599", n != 4)));

        assertThat(accounts.findIdsAfter("", PageRequest.of(0, 3))).containsExactly("ACC-000001", "ACC-000002", "ACC-000003");
        assertThat(accounts.findIdsAfter("ACC-000003", PageRequest.of(0, 3)))
                .containsExactly("ACC-000005", "ACC-000006", "ACC-000007");     // inactive ACC-000004 skipped
        assertThat(accounts.findIdsAfter("ACC-000007", PageRequest.of(0, 3))).isEmpty();
    }
}
