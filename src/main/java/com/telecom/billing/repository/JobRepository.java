package com.telecom.billing.repository;

import com.telecom.billing.entity.BackgroundJob;
import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.entity.JobType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Every state change is a conditional UPDATE: a compare-and-set that is safe across threads and instances. */
public interface JobRepository extends JpaRepository<BackgroundJob, UUID> {

    boolean existsByTypeAndParametersAndStatusIn(JobType type, String parameters, Collection<JobStatus> statuses);

    Page<BackgroundJob> findByStatus(JobStatus status, Pageable pageable);

    List<BackgroundJob> findByStatusIn(Collection<JobStatus> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update BackgroundJob j set j.status = :running, j.totalItems = :total,
                  j.startedAt = coalesce(j.startedAt, :now), j.heartbeatAt = :now
           where j.id = :id and j.status = :queued and j.cancelRequested = false""")
    int markRunning(@Param("id") UUID id, @Param("total") long total, @Param("now") Instant now,
                    @Param("queued") JobStatus queued, @Param("running") JobStatus running);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update BackgroundJob j set j.processedItems = j.processedItems + :processed,
                  j.failedItems = j.failedItems + :failed, j.checkpoint = :checkpoint, j.heartbeatAt = :now
           where j.id = :id""")
    int addProgress(@Param("id") UUID id, @Param("processed") long processed, @Param("failed") long failed,
                    @Param("checkpoint") String checkpoint, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update BackgroundJob j set j.status = :to, j.message = :message, j.finishedAt = :now
           where j.id = :id and j.status in :from""")
    int transition(@Param("id") UUID id, @Param("from") Collection<JobStatus> from, @Param("to") JobStatus to,
                   @Param("message") String message, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BackgroundJob j set j.cancelRequested = true where j.id = :id and j.status in :active")
    int requestCancel(@Param("id") UUID id, @Param("active") Collection<JobStatus> active);

    @Query("select j.cancelRequested from BackgroundJob j where j.id = :id")
    boolean isCancelRequested(@Param("id") UUID id);

    @Query("select j.id from BackgroundJob j where j.status = :running and j.heartbeatAt < :cutoff")
    List<UUID> findStale(@Param("running") JobStatus running, @Param("cutoff") Instant cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update BackgroundJob j set j.status = :queued
           where j.id = :id and j.status = :running and j.heartbeatAt < :cutoff""")
    int requeueIfStale(@Param("id") UUID id, @Param("cutoff") Instant cutoff,
                       @Param("running") JobStatus running, @Param("queued") JobStatus queued);
}
