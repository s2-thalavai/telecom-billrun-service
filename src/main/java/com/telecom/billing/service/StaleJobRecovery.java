package com.telecom.billing.service;

import com.telecom.billing.config.JobProperties;
import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RUNNING jobs whose heartbeat stopped (pod crash, kill -9, redeploy) are re-queued and resumed from their
 * checkpoint. requeueIfStale is a compare-and-set, so with several instances only one wins each job.
 */
@Component
public class StaleJobRecovery {

    private static final Logger log = LoggerFactory.getLogger(StaleJobRecovery.class);

    private final JobRepository jobs;
    private final JobLauncher launcher;
    private final JobProperties properties;

    public StaleJobRecovery(JobRepository jobs, JobLauncher launcher, JobProperties properties) {
        this.jobs = jobs;
        this.launcher = launcher;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${telecom.jobs.recovery-interval-ms:60000}")
    @Transactional
    public void recover() {
        Instant cutoff = Instant.now().minus(properties.heartbeatTimeout());
        List<UUID> requeued = new ArrayList<>();
        for (UUID id : jobs.findStale(JobStatus.RUNNING, cutoff)) {
            if (jobs.requeueIfStale(id, cutoff, JobStatus.RUNNING, JobStatus.QUEUED) == 1) {
                log.warn("Job {} lost its worker; resuming from checkpoint", id);
                requeued.add(id);
            }
        }
        if (!requeued.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    requeued.forEach(launcher::launch);
                }
            });
        }
    }
}
