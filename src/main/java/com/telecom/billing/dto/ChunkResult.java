package com.telecom.billing.dto;

/** billed = new invoices, skipped = already invoiced (resume/re-run), failed = rating errors. */
public record ChunkResult(int billed, int skipped, int failed) {

    public int processed() {
        return billed + skipped;
    }
}
