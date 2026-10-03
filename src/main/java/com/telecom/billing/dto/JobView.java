package com.telecom.billing.dto;

import com.telecom.billing.entity.BackgroundJob;
import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.entity.JobType;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Progress view: percent counts processed AND failed items (both are "done"); rate and ETA from elapsed time. */
public record JobView(UUID id, JobType type, String parameters, JobStatus status, boolean cancelRequested,
                      long totalItems, long processedItems, long failedItems, double percent,
                      Double itemsPerSecond, Long etaSeconds, String checkpoint, String message, String requestedBy,
                      Instant createdAt, Instant startedAt, Instant finishedAt, Instant heartbeatAt) {

    public static JobView from(BackgroundJob j) {
        return from(j, Instant.now());
    }

    public static JobView from(BackgroundJob j, Instant now) {
        long done = j.getProcessedItems() + j.getFailedItems();
        double percent;
        if (j.getStatus() == JobStatus.COMPLETED) {
            percent = 100.0;
        } else if (j.getTotalItems() == 0) {
            percent = 0.0;
        } else {
            percent = Math.min(100.0, 100.0 * done / j.getTotalItems());
        }

        Double rate = null;
        Long eta = null;
        if (j.getStartedAt() != null && done > 0) {
            Instant end = j.getFinishedAt() != null ? j.getFinishedAt() : now;
            double seconds = Math.max(0.001, Duration.between(j.getStartedAt(), end).toMillis() / 1000.0);
            rate = done / seconds;
            if (j.getStatus() == JobStatus.RUNNING) {
                eta = Math.max(0, Math.round((j.getTotalItems() - done) / rate));
            }
        }
        return new JobView(j.getId(), j.getType(), j.getParameters(), j.getStatus(), j.isCancelRequested(),
                j.getTotalItems(), j.getProcessedItems(), j.getFailedItems(), Math.round(percent * 10) / 10.0,
                rate == null ? null : Math.round(rate * 10) / 10.0, eta, j.getCheckpoint(), j.getMessage(),
                j.getRequestedBy(), j.getCreatedAt(), j.getStartedAt(), j.getFinishedAt(), j.getHeartbeatAt());
    }
}
