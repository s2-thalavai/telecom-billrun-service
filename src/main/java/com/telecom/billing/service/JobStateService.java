package com.telecom.billing.service;

import com.telecom.billing.entity.BackgroundJob;
import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.exception.JobNotFoundException;
import com.telecom.billing.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Short, independent transactions for job state. REQUIRES_NEW because some callers run in an
 * AFTER_COMMIT listener (the surrounding transaction is already committed) and progress must be committed
 * and visible to pollers immediately, independent of any other work.
 */
@Service
public class JobStateService {

    private final JobRepository jobs;

    public JobStateService(JobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public BackgroundJob load(UUID id) {
        return jobs.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    /** QUEUED -> RUNNING unless cancelled meanwhile or already taken by another worker. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markRunning(UUID id, long total) {
        return jobs.markRunning(id, total, Instant.now(), JobStatus.QUEUED, JobStatus.RUNNING) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordProgress(UUID id, long processed, long failed, String checkpoint) {
        jobs.addProgress(id, processed, failed, checkpoint, Instant.now());
    }

    /** @return true if this call moved the job into the final status */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean finish(UUID id, JobStatus status, String message) {
        return jobs.transition(id, JobStatus.ACTIVE, status, message, Instant.now()) == 1;
    }

    @Transactional(readOnly = true)
    public boolean isCancelRequested(UUID id) {
        return jobs.isCancelRequested(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean requestCancel(UUID id) {
        return jobs.requestCancel(id, JobStatus.ACTIVE) == 1;
    }
}
