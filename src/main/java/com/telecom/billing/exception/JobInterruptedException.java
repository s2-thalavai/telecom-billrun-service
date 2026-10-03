package com.telecom.billing.exception;

/** The worker thread was interrupted (forced cancel / shutdown) while blocked in a downstream call. */
public class JobInterruptedException extends RuntimeException {
    public JobInterruptedException(String message) {
        super(message);
    }
}
