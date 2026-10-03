package com.telecom.billing.service;

import com.telecom.billing.dto.JobView;
import com.telecom.billing.entity.BackgroundJob;
import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.entity.JobType;
import com.telecom.billing.event.JobSubmittedEvent;
import com.telecom.billing.exception.JobConflictException;
import com.telecom.billing.exception.JobNotFoundException;
import com.telecom.billing.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobs;
    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private JobService service;

    @Test
    void submitCreatesAQueuedJobAndPublishesTheEvent() {
        when(jobs.save(any(BackgroundJob.class))).thenAnswer(inv -> inv.getArgument(0));

        JobView view = service.submitBillRun("2026-09", "alice");

        assertThat(view.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(view.requestedBy()).isEqualTo("alice");
        verify(events).publishEvent(new JobSubmittedEvent(view.id()));
    }

    @Test
    void secondActiveRunForTheSameCycleIsRejected() {
        when(jobs.existsByTypeAndParametersAndStatusIn(JobType.BILL_RUN, "2026-09", JobStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.submitBillRun("2026-09", "alice")).isInstanceOf(JobConflictException.class);
        verify(jobs, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void unknownJobIsNotFound() {
        UUID id = UUID.randomUUID();
        when(jobs.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.view(id)).isInstanceOf(JobNotFoundException.class);
    }
}
