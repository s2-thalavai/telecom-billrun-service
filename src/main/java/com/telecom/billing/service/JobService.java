package com.telecom.billing.service;

import com.telecom.billing.dto.JobView;
import com.telecom.billing.dto.PageResponse;
import com.telecom.billing.entity.BackgroundJob;
import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.entity.JobType;
import com.telecom.billing.event.JobSubmittedEvent;
import com.telecom.billing.exception.JobConflictException;
import com.telecom.billing.exception.JobNotFoundException;
import com.telecom.billing.repository.JobRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class JobService {

    private final JobRepository jobs;
    private final ApplicationEventPublisher events;

    public JobService(JobRepository jobs, ApplicationEventPublisher events) {
        this.jobs = jobs;
        this.events = events;
    }

    /**
     * Creates the job; JobLauncher starts the worker only AFTER this transaction commits.
     * The exists-check is a fast path: in production also enforce "one active run per cycle" with a
     * database constraint (see docs/design.md).
     */
    @Transactional
    public JobView submitBillRun(String cycle, String requestedBy) {
        if (jobs.existsByTypeAndParametersAndStatusIn(JobType.BILL_RUN, cycle, JobStatus.ACTIVE)) {
            throw new JobConflictException("A bill run for " + cycle + " is already queued or running");
        }
        BackgroundJob job = jobs.save(BackgroundJob.queued(JobType.BILL_RUN, cycle, requestedBy));
        events.publishEvent(new JobSubmittedEvent(job.getId()));
        return JobView.from(job);
    }

    @Transactional(readOnly = true)
    public JobView view(UUID id) {
        return jobs.findById(id).map(JobView::from).orElseThrow(() -> new JobNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<JobView> list(JobStatus status, Pageable pageable) {
        return PageResponse.from((status == null ? jobs.findAll(pageable) : jobs.findByStatus(status, pageable))
                .map(JobView::from));
    }
}
