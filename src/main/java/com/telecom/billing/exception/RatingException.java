package com.telecom.billing.exception;

/** A single account could not be rated: counted as failed, the run continues. */
public class RatingException extends RuntimeException {
    public RatingException(String message) {
        super(message);
    }
}
