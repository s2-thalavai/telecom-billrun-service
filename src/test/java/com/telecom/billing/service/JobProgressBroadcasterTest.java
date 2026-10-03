package com.telecom.billing.service;

import com.telecom.billing.dto.JobView;
import com.telecom.billing.entity.JobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static com.telecom.billing.TestJobs.job;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobProgressBroadcasterTest {

    @Mock
    private JobService jobs;

    @InjectMocks
    private JobProgressBroadcaster broadcaster;

    @Test
    void subscribersAreDroppedOnceTheJobIsFinal() {
        UUID id = UUID.randomUUID();
        when(jobs.view(id)).thenReturn(JobView.from(job(JobStatus.RUNNING, "2026-09")),
                JobView.from(job(JobStatus.RUNNING, "2026-09")),
                JobView.from(job(JobStatus.COMPLETED, "2026-09")));

        broadcaster.subscribe(id);
        broadcaster.subscribe(id);
        assertThat(broadcaster.subscriberCount(id)).isEqualTo(2);

        broadcaster.push();                                         // final status: both completed and removed

        assertThat(broadcaster.subscriberCount(id)).isZero();
    }
}
