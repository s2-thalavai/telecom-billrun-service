package com.telecom.billing.event;

import java.util.UUID;

public record JobSubmittedEvent(UUID jobId) {
}
