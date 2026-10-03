package com.telecom.billing.entity;

import java.util.List;

public enum JobStatus {
    QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED;

    public static final List<JobStatus> ACTIVE = List.of(QUEUED, RUNNING);

    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
