package com.telecom.billing.service;

import com.telecom.billing.config.BillingProperties;
import com.telecom.billing.exception.JobInterruptedException;
import com.telecom.billing.exception.RatingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RatingEngineTest {

    private final RatingEngine engine = new RatingEngine(new BillingProperties(Duration.ofMillis(1), 3, 0));

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void ratesDeterministically() {
        assertThat(engine.rateCycle("ACC-000001", "2026-09")).isEqualByComparingTo("236.00");   // 199 + 37
    }

    @Test
    void everyNthAccountFails() {
        assertThatThrownBy(() -> engine.rateCycle("ACC-000003", "2026-09")).isInstanceOf(RatingException.class);
    }

    @Test
    void interruptIsTranslatedAndTheFlagIsKept() {
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> engine.rateCycle("ACC-000001", "2026-09")).isInstanceOf(JobInterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();     // the worker loop must still see it
    }
}
