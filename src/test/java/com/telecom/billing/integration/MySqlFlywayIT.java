package com.telecom.billing.integration;

import com.telecom.billing.entity.JobStatus;
import com.telecom.billing.repository.InvoiceRepository;
import com.telecom.billing.repository.JobRepository;
import com.telecom.billing.service.JobService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** PROD profile on MySQL 8.4: Flyway schema + seeded 2,000 accounts + a full bill run. Skipped without Docker. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("prod")
@Testcontainers(disabledWithoutDocker = true)
class MySqlFlywayIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void fastRating(DynamicPropertyRegistry registry) {
        registry.add("telecom.billing.rating-latency", () -> "1ms");
        registry.add("telecom.jobs.chunk-size", () -> "200");
    }

    @Autowired
    private Flyway flyway;
    @Autowired
    private JobService jobs;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private InvoiceRepository invoices;

    @Test
    void fullBillRunOnMySql() {
        assertThat(Arrays.stream(flyway.info().applied()).map(m -> m.getVersion().getVersion())).containsExactly("1", "2");

        UUID id = jobs.submitBillRun("2026-09", "mysql-it").id();

        await().atMost(Duration.ofSeconds(60))
               .until(() -> jobRepository.findById(id).orElseThrow().getStatus().isFinal());
        assertThat(jobRepository.findById(id).orElseThrow().getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(invoices.countByCycle("2026-09")).isEqualTo(2000);
    }
}
