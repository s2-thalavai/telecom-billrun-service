package com.telecom.billing.service;

import com.telecom.billing.dto.JobView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE progress. Reads the job from the DATABASE on every push, so it works whichever instance runs the job.
 */
@Component
public class JobProgressBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(JobProgressBroadcaster.class);

    private final ConcurrentHashMap<UUID, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final JobService jobs;

    public JobProgressBroadcaster(JobService jobs) {
        this.jobs = jobs;
    }

    public SseEmitter subscribe(UUID jobId) {
        JobView first = jobs.view(jobId);                                  // 404 for unknown jobs
        SseEmitter emitter = new SseEmitter(Duration.ofHours(3).toMillis());
        subscribers.computeIfAbsent(jobId, id -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(jobId, emitter));
        emitter.onTimeout(() -> remove(jobId, emitter));
        emitter.onError(ex -> remove(jobId, emitter));
        send(jobId, emitter, first);                                       // immediate first event
        return emitter;
    }

    @Scheduled(fixedDelayString = "${telecom.jobs.progress-push-interval-ms:1000}")
    public void push() {
        subscribers.forEach((jobId, emitters) -> {
            JobView view = jobs.view(jobId);
            emitters.forEach(emitter -> send(jobId, emitter, view));
        });
    }

    public int subscriberCount(UUID jobId) {
        return subscribers.getOrDefault(jobId, List.of()).size();
    }

    private void send(UUID jobId, SseEmitter emitter, JobView view) {
        try {
            emitter.send(SseEmitter.event().name("progress").data(view));
            if (view.status().isFinal()) {
                emitter.complete();
                remove(jobId, emitter);
            }
        } catch (IOException | IllegalStateException clientGone) {
            log.debug("Dropping progress subscriber of {}", jobId);
            remove(jobId, emitter);
        }
    }

    private void remove(UUID jobId, SseEmitter emitter) {
        subscribers.computeIfPresent(jobId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
