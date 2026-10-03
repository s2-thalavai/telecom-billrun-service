package com.telecom.billing.service;

import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.exception.JobConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static com.telecom.billing.TestJobs.job;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobCancellationServiceTest {

    private static final UUID ID = UUID.randomUUID();

    @Mock
    private JobStateService state;
    @Mock
    private JobLauncher launcher;

    @InjectMocks
    private JobCancellationService service;

    @Test
    void queuedJobIsCancelledImmediately() {
        when(state.load(ID)).thenReturn(job(JobStatus.QUEUED, "2026-09"));

        service.cancel(ID, false);

        verify(state).requestCancel(ID);
        verify(state).finish(ID, JobStatus.CANCELLED, "Cancelled before start");
        verify(launcher, never()).interrupt(any());
    }

    @Test
    void runningJobGetsTheCooperativeFlagOnly() {
        when(state.load(ID)).thenReturn(job(JobStatus.RUNNING, "2026-09"));

        service.cancel(ID, false);

        verify(state).requestCancel(ID);
        verify(state, never()).finish(any(), any(), any());                  // the worker finishes it
        verify(launcher, never()).interrupt(any());
    }

    @Test
    void forceAlsoInterruptsTheWorker() {
        when(state.load(ID)).thenReturn(job(JobStatus.RUNNING, "2026-09"));

        service.cancel(ID, true);

        verify(state).requestCancel(ID);
        verify(launcher).interrupt(ID);
    }

    @Test
    void finishedJobsCannotBeCancelled() {
        when(state.load(ID)).thenReturn(job(JobStatus.COMPLETED, "2026-09"));

        assertThatThrownBy(() -> service.cancel(ID, false)).isInstanceOf(JobConflictException.class);
        verify(state, never()).requestCancel(any());
    }
}
