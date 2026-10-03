package com.telecom.billing;

import com.telecom.billing.entity.BackgroundJob;
import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.entity.JobType;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

/** Builds BackgroundJob instances in any state for unit tests. */
public final class TestJobs {

    private TestJobs() {
    }

    public static BackgroundJob job(JobStatus status, String cycle) {
        BackgroundJob job = BackgroundJob.queued(JobType.BILL_RUN, cycle, "test");
        ReflectionTestUtils.setField(job, "status", status);
        return job;
    }

    public static BackgroundJob withProgress(BackgroundJob job, long total, long processed, long failed,
                                             Instant startedAt, Instant finishedAt) {
        ReflectionTestUtils.setField(job, "totalItems", total);
        ReflectionTestUtils.setField(job, "processedItems", processed);
        ReflectionTestUtils.setField(job, "failedItems", failed);
        ReflectionTestUtils.setField(job, "startedAt", startedAt);
        ReflectionTestUtils.setField(job, "finishedAt", finishedAt);
        return job;
    }

    public static BackgroundJob withCheckpoint(BackgroundJob job, String checkpoint) {
        ReflectionTestUtils.setField(job, "checkpoint", checkpoint);
        return job;
    }
}
