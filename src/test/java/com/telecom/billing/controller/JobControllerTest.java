package com.telecom.billing.controller;

import com.telecom.billing.dto.BillRunSummary;
import com.telecom.billing.dto.JobView;
import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.exception.JobConflictException;
import com.telecom.billing.exception.JobNotFoundException;
import com.telecom.billing.service.BillingService;
import com.telecom.billing.service.JobCancellationService;
import com.telecom.billing.service.JobProgressBroadcaster;
import com.telecom.billing.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static com.telecom.billing.TestJobs.job;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JobService jobs;
    @MockitoBean
    private JobCancellationService cancellations;
    @MockitoBean
    private JobProgressBroadcaster progress;
    @MockitoBean
    private BillingService billing;

    @Test
    void startingABillRunReturns202WithTheJobLocation() throws Exception {
        JobView queued = JobView.from(job(JobStatus.QUEUED, "2026-09"));
        when(jobs.submitBillRun("2026-09", "alice")).thenReturn(queued);

        mvc.perform(post("/api/v1/bill-runs").header("X-User", "alice")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cycle\":\"2026-09\"}"))
           .andExpect(status().isAccepted())
           .andExpect(header().string("Location", "/api/v1/jobs/" + queued.id()))
           .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void invalidCycleIs400() throws Exception {
        mvc.perform(post("/api/v1/bill-runs").contentType(MediaType.APPLICATION_JSON).content("{\"cycle\":\"2026-13\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors.cycle").value("must be YYYY-MM"));
        verifyNoInteractions(jobs);
    }

    @Test
    void secondActiveRunIs409() throws Exception {
        when(jobs.submitBillRun(any(), any())).thenThrow(new JobConflictException("already running"));

        mvc.perform(post("/api/v1/bill-runs").contentType(MediaType.APPLICATION_JSON).content("{\"cycle\":\"2026-09\"}"))
           .andExpect(status().isConflict());
    }

    @Test
    void unknownJobIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobs.view(id)).thenThrow(new JobNotFoundException(id));

        mvc.perform(get("/api/v1/jobs/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void cancelPassesTheForceFlag() throws Exception {
        UUID id = UUID.randomUUID();
        when(cancellations.cancel(id, true)).thenReturn(JobView.from(job(JobStatus.RUNNING, "2026-09")));

        mvc.perform(post("/api/v1/jobs/" + id + "/cancel").param("force", "true"))
           .andExpect(status().isAccepted());
        verify(cancellations).cancel(id, true);
    }

    @Test
    void cancellingAFinishedJobIs409() throws Exception {
        when(cancellations.cancel(any(), anyBoolean())).thenThrow(new JobConflictException("Job is already COMPLETED"));

        mvc.perform(post("/api/v1/jobs/" + UUID.randomUUID() + "/cancel")).andExpect(status().isConflict());
    }

    @Test
    void summaryValidatesTheCycle() throws Exception {
        when(billing.summary("2026-09")).thenReturn(new BillRunSummary("2026-09", 594, new BigDecimal("350000.00")));

        mvc.perform(get("/api/v1/bill-runs/2026-09/summary"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.invoices").value(594));
        mvc.perform(get("/api/v1/bill-runs/september/summary")).andExpect(status().isBadRequest());
    }
}
