package com.telecom.billing.dto;

import java.math.BigDecimal;

public record BillRunSummary(String cycle, long invoices, BigDecimal totalAmount) {
}
