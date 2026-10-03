package com.telecom.billing.service;

import com.telecom.billing.config.JobProperties;
import com.telecom.billing.dto.ChunkResult;
import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.exception.JobInterruptedException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.telecom.billing.TestJobs.job;
import static com.telecom.billing.TestJobs.withCheckpoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The worker loop called directly (no proxy, no threads): chunking, checkpoints, cancellation, deadline, failures. */
@ExtendWith(MockitoExtension.class)
class BillRunWorkerTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String CYCLE = "2026-09";

    @Mock
    private JobStateService state;
    @Mock
    private BillingService billing;
    @Mock
    private InvoiceNotificationService notifications;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T00:00:00Z"));
    private BillRunWorker worker;

    @BeforeEach
    void setUp() {
        JobProperties properties = new JobProperties(2, Duration.ofHours(1), Duration.ofMinutes(2), 1, 1);
        worker = new BillRunWorker(state, billing, notifications, properties, registry, clock);
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    private void startable(String checkpoint) {
        when(state.load(ID)).thenReturn(withCheckpoint(job(JobStatus.QUEUED, CYCLE), checkpoint));
        when(billing.countBillable()).thenReturn(3L);
        when(state.markRunning(ID, 3L)).thenReturn(true);
    }

    @Test
    void processesAllChunksRecordsProgressAndCompletes() throws Exception {
        startable(null);
        when(billing.nextAccounts(null, 2)).thenReturn(List.of("ACC-000001", "ACC-000002"));
        when(billing.nextAccounts("ACC-000002", 2)).thenReturn(List.of("ACC-000003"));
        when(billing.nextAccounts("ACC-000003", 2)).thenReturn(List.of());
        when(billing.billChunk(CYCLE, List.of("ACC-000001", "ACC-000002"))).thenReturn(new ChunkResult(2, 0, 0));
        when(billing.billChunk(CYCLE, List.of("ACC-000003"))).thenReturn(new ChunkResult(0, 0, 1));

        JobStatus result = worker.run(ID).get();

        assertThat(result).isEqualTo(JobStatus.COMPLETED);
        verify(state).recordProgress(ID, 2, 0, "ACC-000002");
        verify(state).recordProgress(ID, 0, 1, "ACC-000003");
        verify(state).finish(eq(ID), eq(JobStatus.COMPLETED), contains("finished"));
        verify(notifications).notifyChunkBilled(CYCLE, 2);
        assertThat(registry.counter("telecom.jobs.items", "type", "BILL_RUN", "outcome", "billed").count()).isEqualTo(2);
        assertThat(registry.counter("telecom.jobs.items", "type", "BILL_RUN", "outcome", "failed").count()).isEqualTo(1);
    }

    @Test
    void resumesAfterTheCheckpoint() throws Exception {
        startable("ACC-000002");
        when(billing.nextAccounts("ACC-000002", 2)).thenReturn(List.of("ACC-000003"));
        when(billing.nextAccounts("ACC-000003", 2)).thenReturn(List.of());
        when(billing.billChunk(CYCLE, List.of("ACC-000003"))).thenReturn(new ChunkResult(1, 0, 0));

        assertThat(worker.run(ID).get()).isEqualTo(JobStatus.COMPLETED);
        verify(billing, never()).nextAccounts(null, 2);
    }

    @Test
    void doesNotStartWhenCancelledWhileQueued() throws Exception {
        when(state.load(ID)).thenReturn(job(JobStatus.QUEUED, CYCLE), job(JobStatus.CANCELLED, CYCLE));
        when(billing.countBillable()).thenReturn(3L);
        when(state.markRunning(ID, 3L)).thenReturn(false);

        assertThat(worker.run(ID).get()).isEqualTo(JobStatus.CANCELLED);
        verify(billing, never()).billChunk(anyString(), any());
        verify(state, never()).finish(any(), any(), any());
    }

    @Test
    void cooperativeCancellationStopsAtTheNextChunkBoundary() throws Exception {
        startable(null);
        when(state.isCancelRequested(ID)).thenReturn(false, true);
        when(billing.nextAccounts(null, 2)).thenReturn(List.of("ACC-000001", "ACC-000002"));
        when(billing.billChunk(CYCLE, List.of("ACC-000001", "ACC-000002"))).thenReturn(new ChunkResult(2, 0, 0));

        assertThat(worker.run(ID).get()).isEqualTo(JobStatus.CANCELLED);
        verify(state).recordProgress(ID, 2, 0, "ACC-000002");               // the finished chunk is kept
        verify(billing, never()).nextAccounts("ACC-000002", 2);
        verify(state).finish(ID, JobStatus.CANCELLED, "Cancelled on request");
    }

    @Test
    void exceedingMaxRuntimeFailsTheJob() throws Exception {
        startable(null);
        when(billing.nextAccounts(null, 2)).thenReturn(List.of("ACC-000001", "ACC-000002"));
        when(billing.billChunk(CYCLE, List.of("ACC-000001", "ACC-000002"))).thenAnswer(inv -> {
            clock.advance(Duration.ofHours(2));                              // this chunk took "2 hours"
            return new ChunkResult(2, 0, 0);
        });

        assertThat(worker.run(ID).get()).isEqualTo(JobStatus.FAILED);
        verify(state).finish(eq(ID), eq(JobStatus.FAILED), contains("Exceeded max runtime"));
    }

    @Test
    void interruptionCancelsAndClearsTheInterruptFlag() throws Exception {
        startable(null);
        when(billing.nextAccounts(null, 2)).thenReturn(List.of("ACC-000001", "ACC-000002"));
        when(billing.billChunk(anyString(), any())).thenAnswer(inv -> {
            Thread.currentThread().interrupt();                              // forced cancel arrives mid-chunk
            throw new JobInterruptedException("Rating interrupted");
        });

        assertThat(worker.run(ID).get()).isEqualTo(JobStatus.CANCELLED);
        verify(state).finish(eq(ID), eq(JobStatus.CANCELLED), contains("Interrupted"));
        verify(state, never()).recordProgress(any(), anyLong(), anyLong(), anyString());   // chunk rolled back
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void unexpectedErrorFailsTheJobWithItsMessage() throws Exception {
        startable(null);
        when(billing.nextAccounts(null, 2)).thenThrow(new IllegalStateException("database unavailable"));

        assertThat(worker.run(ID).get()).isEqualTo(JobStatus.FAILED);
        verify(state).finish(eq(ID), eq(JobStatus.FAILED), contains("database unavailable"));
        verify(notifications, never()).notifyChunkBilled(anyString(), anyInt());
    }

    /** Clock that tests can move forward. */
    static final class MutableClock extends Clock {

        private final AtomicReference<Instant> now;

        MutableClock(Instant start) {
            this.now = new AtomicReference<>(start);
        }

        void advance(Duration duration) {
            now.updateAndGet(i -> i.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
