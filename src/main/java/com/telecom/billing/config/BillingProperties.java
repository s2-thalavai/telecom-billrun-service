package com.telecom.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "telecom.billing")
public record BillingProperties(
        @DefaultValue("20ms") Duration ratingLatency,
        @DefaultValue("0") int failingEvery,
        @DefaultValue("5000") int seedAccounts) {
}
