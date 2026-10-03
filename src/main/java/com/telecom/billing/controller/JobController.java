package com.telecom.billing.controller;

import com.telecom.billing.dto.BillRunRequest;
import com.telecom.billing.dto.BillRunSummary;
import com.telecom.billing.dto.JobView;
import com.telecom.billing.dto.PageResponse;
import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.service.BillingService;
import com.telecom.billing.service.JobCancellationService;
import com.telecom.billing.service.JobProgressBroadcaster;
import com.telecom.billing.service.JobService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class JobController {

    private final JobService jobs;
    private final JobCancellationService cancellations;
    private final JobProgressBroadcaster progress;
    private final BillingService billing;

    public JobController(JobService jobs, JobCancellationService cancellations, JobProgressBroadcaster progress,
                         BillingService billing) {
        this.jobs = jobs;
        this.cancellations = cancellations;
        this.progress = progress;
        this.billing = billing;
    }

    /** Starts a bill run in the background: 202 + Location of the job resource. */
    @PostMapping("/bill-runs")
    public ResponseEntity<JobView> startBillRun(@Valid @RequestBody BillRunRequest request,
                                                @RequestHeader(value = "X-User", defaultValue = "ops") String user) {
        JobView job = jobs.submitBillRun(request.cycle(), user);
        return ResponseEntity.accepted().location(URI.create("/api/v1/jobs/" + job.id())).body(job);
    }

    @GetMapping("/bill-runs/{cycle}/summary")
    public BillRunSummary summary(@PathVariable @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$") String cycle) {
        return billing.summary(cycle);
    }

    @GetMapping("/jobs")
    public PageResponse<JobView> list(@RequestParam(required = false) JobStatus status,
                                      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                      Pageable pageable) {
        return jobs.list(status, pageable);
    }

    @GetMapping("/jobs/{id}")
    public JobView job(@PathVariable UUID id) {
        return jobs.view(id);
    }

    @GetMapping(path = "/jobs/{id}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter progress(@PathVariable UUID id) {
        return progress.subscribe(id);
    }

    @PostMapping("/jobs/{id}/cancel")
    public ResponseEntity<JobView> cancel(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.accepted().body(cancellations.cancel(id, force));
    }
}
