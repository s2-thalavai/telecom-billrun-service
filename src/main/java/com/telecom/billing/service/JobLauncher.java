package com.telecom.billing.service;

import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.event.JobSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

@Component
public class JobLauncher {

    private static final Logger log = LoggerFactory.getLogger(JobLauncher.class);

    private final BillRunWorker worker;
    private final JobStateService state;
    /** Futures of jobs started by THIS instance (needed for interrupting cancellation). */
    private final ConcurrentHashMap<UUID, Future<JobStatus>> running = new ConcurrentHashMap<>();

    public JobLauncher(BillRunWorker worker, JobStateService state) {
        this.worker = worker;
        this.state = state;
    }

    /** AFTER_COMMIT: the job row is durable and visible before any worker looks for it. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmitted(JobSubmittedEvent event) {
        launch(event.jobId());
    }

    public void launch(UUID jobId) {
        try {
            running.put(jobId, worker.run(jobId));            // @Async proxy: returns immediately
        } catch (TaskRejectedException full) {
            log.warn("Job {} rejected: job executor queue is full", jobId);
            state.finish(jobId, JobStatus.FAILED, "Job queue is full; please resubmit later");
        }
    }

    /** Interrupting cancellation. Only effective on the instance that runs the job. */
    public boolean interrupt(UUID jobId) {
        Future<JobStatus> future = running.get(jobId);
        return future != null && future.cancel(true);
    }

    public int trackedJobs() {
        return running.size();
    }

    @Scheduled(fixedDelay = 30_000)
    void forgetFinished() {
        running.entrySet().removeIf(entry -> entry.getValue().isDone());
    }
}
