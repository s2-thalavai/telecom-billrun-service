package com.telecom.billing.service;

import com.telecom.billing.dto.BillRunSummary;
import com.telecom.billing.dto.ChunkResult;
import com.telecom.billing.entity.Invoice;
import com.telecom.billing.exception.RatingException;
import com.telecom.billing.repository.InvoiceRepository;
import com.telecom.billing.repository.SubscriberAccountRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** The business work. One short transaction per chunk: a failure rolls back that chunk only. */
@Service
public class BillingService {

    private final SubscriberAccountRepository accounts;
    private final InvoiceRepository invoices;
    private final RatingEngine rating;

    public BillingService(SubscriberAccountRepository accounts, InvoiceRepository invoices, RatingEngine rating) {
        this.accounts = accounts;
        this.invoices = invoices;
        this.rating = rating;
    }

    @Transactional(readOnly = true)
    public long countBillable() {
        return accounts.countByActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<String> nextAccounts(String afterAccountId, int limit) {
        return accounts.findIdsAfter(afterAccountId == null ? "" : afterAccountId, PageRequest.of(0, limit));
    }

    /** Idempotent: accounts already invoiced for the cycle are skipped (resume / re-run safe). */
    @Transactional
    public ChunkResult billChunk(String cycle, List<String> accountIds) {
        int billed = 0;
        int skipped = 0;
        int failed = 0;
        for (String accountId : accountIds) {
            if (invoices.existsByAccountIdAndCycle(accountId, cycle)) {
                skipped++;
                continue;
            }
            try {
                BigDecimal amount = rating.rateCycle(accountId, cycle);  // JobInterruptedException aborts the chunk
                invoices.save(new Invoice(accountId, cycle, amount));
                billed++;
            } catch (RatingException perAccountProblem) {
                failed++;                                                 // one bad account doesn't stop the run
            }
        }
        return new ChunkResult(billed, skipped, failed);
    }

    @Transactional(readOnly = true)
    public BillRunSummary summary(String cycle) {
        return new BillRunSummary(cycle, invoices.countByCycle(cycle), invoices.totalForCycle(cycle));
    }
}
