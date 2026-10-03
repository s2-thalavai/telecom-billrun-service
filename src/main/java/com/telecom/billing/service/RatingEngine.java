package com.telecom.billing.service;

import com.telecom.billing.config.BillingProperties;
import com.telecom.billing.exception.JobInterruptedException;
import com.telecom.billing.exception.RatingException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Stands in for a blocking call to the rating system. Honours interrupts so a forced cancel stops it. */
@Component
public class RatingEngine {

    private final BillingProperties properties;

    public RatingEngine(BillingProperties properties) {
        this.properties = properties;
    }

    public BigDecimal rateCycle(String accountId, String cycle) {
        try {
            Thread.sleep(properties.ratingLatency().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();                 // keep the flag for the worker loop
            throw new JobInterruptedException("Rating interrupted for " + accountId);
        }
        int number = Integer.parseInt(accountId.substring(accountId.indexOf('-') + 1));
        if (properties.failingEvery() > 0 && number % properties.failingEvery() == 0) {
            throw new RatingException("No usage records for " + accountId + " in " + cycle);
        }
        return new BigDecimal(199 + (number * 37) % 800).setScale(2, RoundingMode.HALF_UP);
    }
}
