package com.telecom.billing.service;

import com.telecom.billing.config.JobProperties;
import com.telecom.billing.dto.ChunkResult;
import com.telecom.billing.entity.BackgroundJob;
import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.exception.JobInterruptedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * The long-running operation. Non-transactional orchestrator: each chunk and each progress update is its own
 * short transaction, so a crash or cancel loses at most one chunk and progress is never ahead of the data.
 */
@Component
public class BillRunWorker {

    private static final Logger log = LoggerFactory.getLogger(BillRunWorker.class);

    private final JobStateService state;
    private final BillingService billing;
    private final InvoiceNotificationService notifications;
    private final JobProperties properties;
    private final Clock clock;
    private final LongTaskTimer activeJobs;
    private final Counter itemsBilled;
    private final Counter itemsFailed;

    @Autowired
    public BillRunWorker(JobStateService state, BillingService billing, InvoiceNotificationService notifications,
                         JobProperties properties, MeterRegistry registry) {
        this(state, billing, notifications, properties, registry, Clock.systemUTC());
    }

    /** Test constructor: a controllable clock for the max-runtime check. */
    BillRunWorker(JobStateService state, BillingService billing, InvoiceNotificationService notifications,
                  JobProperties properties, MeterRegistry registry, Clock clock) {
        this.state = state;
        this.billing = billing;
        this.notifications = notifications;
        this.properties = properties;
        this.clock = clock;
        this.activeJobs = LongTaskTimer.builder("telecom.jobs.active").tag("type", "BILL_RUN").register(registry);
        this.itemsBilled = registry.counter("telecom.jobs.items", "type", "BILL_RUN", "outcome", "billed");
        this.itemsFailed = registry.counter("telecom.jobs.items", "type", "BILL_RUN", "outcome", "failed");
    }

    /**
     * Declared as Future (not CompletableFuture) on purpose: Spring then submits a FutureTask to the executor,
     * so Future.cancel(true) interrupts this thread. CompletableFuture.cancel(true) would not.
     */
    @Async("jobExecutor")
    public Future<JobStatus> run(UUID jobId) {
        MDC.put("jobId", jobId.toString());
        try {
            BackgroundJob job = state.load(jobId);
            String cycle = job.getParameters();
            if (!state.markRunning(jobId, billing.countBillable())) {
                log.info("Not started: cancelled while queued, or already running elsewhere");
                return CompletableFuture.completedFuture(state.load(jobId).getStatus());
            }
            LongTaskTimer.Sample active = activeJobs.start();
            try {
                return process(jobId, cycle, job.getCheckpoint());
            } finally {
                active.stop();
            }
        } finally {
            MDC.remove("jobId");
        }
    }

    private Future<JobStatus> process(UUID jobId, String cycle, String resumeAfter) {
        Instant deadline = clock.instant().plus(properties.maxRuntime());
        String checkpoint = resumeAfter;
        if (checkpoint != null) {
            log.info("Resuming bill run {} after {}", cycle, checkpoint);
        }
        try {
            while (true) {
                if (Thread.currentThread().isInterrupted() || state.isCancelRequested(jobId)) {
                    return finish(jobId, JobStatus.CANCELLED, "Cancelled on request");
                }
                if (clock.instant().isAfter(deadline)) {
                    return finish(jobId, JobStatus.FAILED, "Exceeded max runtime " + properties.maxRuntime());
                }
                List<String> accounts = billing.nextAccounts(checkpoint, properties.chunkSize());
                if (accounts.isEmpty()) {
                    break;
                }
                ChunkResult chunk = billing.billChunk(cycle, accounts);            // own transaction (committed)
                checkpoint = accounts.getLast();
                state.recordProgress(jobId, chunk.processed(), chunk.failed(), checkpoint);
                itemsBilled.increment(chunk.billed());
                itemsFailed.increment(chunk.failed());
                notifications.notifyChunkBilled(cycle, chunk.billed());           // only after commit
            }
            return finish(jobId, JobStatus.COMPLETED, "Bill run for " + cycle + " finished");
        } catch (JobInterruptedException ex) {
            return finish(jobId, JobStatus.CANCELLED, "Interrupted (forced cancel or shutdown)");
        } catch (RuntimeException ex) {
            if (Thread.currentThread().isInterrupted()) {
                return finish(jobId, JobStatus.CANCELLED, "Interrupted (forced cancel or shutdown)");
            }
            log.error("Bill run {} failed", cycle, ex);
            return finish(jobId, JobStatus.FAILED, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private Future<JobStatus> finish(UUID jobId, JobStatus status, String message) {
        Thread.interrupted();          // clear the flag so the final DB update isn't interrupted as well
        state.finish(jobId, status, message);
        log.info("Job finished: {} ({})", status, message);
        return CompletableFuture.completedFuture(status);
    }
}
