package com.telecom.billing.dto;

import com.telecom.billing.entity.JobStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.telecom.billing.TestJobs.job;
import static com.telecom.billing.TestJobs.withProgress;
import static org.assertj.core.api.Assertions.assertThat;

class JobViewTest {

    private static final Instant START = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    void runningJobHasPercentRateAndEta() {
        // 300 of 1000 done (290 processed + 10 failed) in 60s -> 5/s -> 700 left -> 140s
        JobView view = JobView.from(withProgress(job(JobStatus.RUNNING, "2026-09"), 1000, 290, 10, START, null),
                START.plusSeconds(60));

        assertThat(view.percent()).isEqualTo(30.0);
        assertThat(view.itemsPerSecond()).isEqualTo(5.0);
        assertThat(view.etaSeconds()).isEqualTo(140L);
    }

    @Test
    void completedJobIs100PercentEvenWithFailures() {
        JobView view = JobView.from(withProgress(job(JobStatus.COMPLETED, "2026-09"), 600, 594, 6,
                START, START.plusSeconds(30)));

        assertThat(view.percent()).isEqualTo(100.0);
        assertThat(view.etaSeconds()).isNull();                  // no ETA once finished
        assertThat(view.itemsPerSecond()).isEqualTo(20.0);       // rate uses the finish time, not "now"
    }

    @Test
    void queuedJobHasNoRateOrEta() {
        JobView view = JobView.from(job(JobStatus.QUEUED, "2026-09"));

        assertThat(view.percent()).isZero();
        assertThat(view.itemsPerSecond()).isNull();
        assertThat(view.etaSeconds()).isNull();
    }

    @Test
    void percentNeverExceeds100() {
        JobView view = JobView.from(withProgress(job(JobStatus.RUNNING, "2026-09"), 10, 15, 0, START, null),
                START.plusSeconds(1));

        assertThat(view.percent()).isEqualTo(100.0);
        assertThat(view.etaSeconds()).isZero();
    }
}
