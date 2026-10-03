package com.telecom.billing.service;

import com.telecom.billing.dto.JobView;
import com.telecom.billing.entity.BackgroundJob;
import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.exception.JobConflictException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Cooperative by default: a durable flag the worker checks between chunks (works from any instance).
 * force=true additionally interrupts the worker thread (only on the instance running it).
 */
@Service
public class JobCancellationService {

    private final JobStateService state;
    private final JobLauncher launcher;

    public JobCancellationService(JobStateService state, JobLauncher launcher) {
        this.state = state;
        this.launcher = launcher;
    }

    public JobView cancel(UUID jobId, boolean force) {
        BackgroundJob job = state.load(jobId);
        if (job.getStatus().isFinal()) {
            throw new JobConflictException("Job is already " + job.getStatus());
        }
        state.requestCancel(jobId);
        if (job.getStatus() == JobStatus.QUEUED) {
            // markRunning refuses jobs with the flag set; finish now so pollers see CANCELLED immediately
            state.finish(jobId, JobStatus.CANCELLED, "Cancelled before start");
        }
        if (force) {
            launcher.interrupt(jobId);
        }
        return JobView.from(state.load(jobId));
    }
}
