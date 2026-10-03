package com.telecom.billing.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable job record: survives restarts, visible to every instance.
 * State changes go through conditional UPDATEs in JobRepository (compare-and-set in the database).
 */
@Entity
@Table(name = "background_jobs", indexes = {
        @Index(name = "idx_jobs_status", columnList = "status"),
        @Index(name = "idx_jobs_type_params", columnList = "type, parameters")})
public class BackgroundJob {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobType type;

    /** Job parameter: the billing cycle, e.g. "2026-09". */
    @Column(nullable = false, length = 50)
    private String parameters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Column(name = "total_items", nullable = false)
    private long totalItems;

    @Column(name = "processed_items", nullable = false)
    private long processedItems;

    @Column(name = "failed_items", nullable = false)
    private long failedItems;

    /** Last account handled: a restarted job resumes after it. */
    @Column(length = 50)
    private String checkpoint;

    @Column(length = 500)
    private String message;

    @Column(name = "requested_by", length = 50)
    private String requestedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** Updated with every chunk; a stale heartbeat means the worker died. */
    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    protected BackgroundJob() {
    }

    public static BackgroundJob queued(JobType type, String parameters, String requestedBy) {
        BackgroundJob job = new BackgroundJob();
        job.id = UUID.randomUUID();
        job.type = type;
        job.parameters = parameters;
        job.status = JobStatus.QUEUED;
        job.requestedBy = requestedBy;
        job.createdAt = Instant.now();
        return job;
    }

    /** Test/support factory: a job that was running when its worker disappeared. */
    public static BackgroundJob orphanedRunning(JobType type, String parameters, long total, long processed,
                                               String checkpoint, Instant lastHeartbeat) {
        BackgroundJob job = queued(type, parameters, "recovery-test");
        job.status = JobStatus.RUNNING;
        job.totalItems = total;
        job.processedItems = processed;
        job.checkpoint = checkpoint;
        job.startedAt = lastHeartbeat;
        job.heartbeatAt = lastHeartbeat;
        return job;
    }

    public UUID getId() { return id; }
    public JobType getType() { return type; }
    public String getParameters() { return parameters; }
    public JobStatus getStatus() { return status; }
    public boolean isCancelRequested() { return cancelRequested; }
    public long getTotalItems() { return totalItems; }
    public long getProcessedItems() { return processedItems; }
    public long getFailedItems() { return failedItems; }
    public String getCheckpoint() { return checkpoint; }
    public String getMessage() { return message; }
    public String getRequestedBy() { return requestedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getHeartbeatAt() { return heartbeatAt; }
}
