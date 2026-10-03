package com.telecom.billing.service;

import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.event.JobSubmittedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.util.UUID;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobLauncherTest {

    private static final UUID ID = UUID.randomUUID();

    @Mock
    private BillRunWorker worker;
    @Mock
    private JobStateService state;

    @InjectMocks
    private JobLauncher launcher;

    @Test
    @SuppressWarnings("unchecked")
    void submittedJobIsLaunchedAndCanBeInterrupted() {
        Future<JobStatus> future = mock(Future.class);
        when(worker.run(ID)).thenReturn(future);
        when(future.cancel(true)).thenReturn(true);

        launcher.onSubmitted(new JobSubmittedEvent(ID));

        assertThat(launcher.interrupt(ID)).isTrue();
        verify(future).cancel(true);                          // true = interrupt the running thread
    }

    @Test
    void rejectedLaunchMarksTheJobFailed() {
        when(worker.run(ID)).thenThrow(new TaskRejectedException("queue full"));

        launcher.launch(ID);

        verify(state).finish(eq(ID), eq(JobStatus.FAILED), contains("queue is full"));
        assertThat(launcher.interrupt(ID)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void finishedFuturesAreForgotten() {
        Future<JobStatus> future = mock(Future.class);
        when(worker.run(ID)).thenReturn(future);
        when(future.isDone()).thenReturn(true);
        launcher.launch(ID);

        launcher.forgetFinished();

        assertThat(launcher.trackedJobs()).isZero();
    }
}
