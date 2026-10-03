package com.telecom.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** A chunk must always finish well within heartbeatTimeout, otherwise a healthy job looks dead. */
@ConfigurationProperties(prefix = "telecom.jobs")
public record JobProperties(
        @DefaultValue("100") int chunkSize,
        @DefaultValue("2h") Duration maxRuntime,
        @DefaultValue("2m") Duration heartbeatTimeout,
        @DefaultValue("2") int workers,
        @DefaultValue("20") int queueCapacity) {
}
