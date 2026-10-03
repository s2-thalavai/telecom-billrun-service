package com.telecom.billing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Fire-and-forget on task-*. Called by the worker only AFTER a chunk has committed. */
@Service
public class InvoiceNotificationService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceNotificationService.class);

    @Async
    public void notifyChunkBilled(String cycle, int invoices) {
        if (invoices > 0) {
            log.info("Queued {} bill-ready SMS for cycle {} on {}", invoices, cycle, Thread.currentThread().getName());
        }
    }
}
