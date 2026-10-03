package com.telecom.billing.repository;

import com.telecom.billing.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    boolean existsByAccountIdAndCycle(String accountId, String cycle);

    long countByCycle(String cycle);

    @Query("select coalesce(sum(i.amount), 0) from Invoice i where i.cycle = :cycle")
    BigDecimal totalForCycle(@Param("cycle") String cycle);
}
