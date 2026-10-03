package com.telecom.billing.exception;

/** 409: a run for the cycle is already active, or the job is already finished. */
public class JobConflictException extends RuntimeException {
    public JobConflictException(String message) {
        super(message);
    }
}
