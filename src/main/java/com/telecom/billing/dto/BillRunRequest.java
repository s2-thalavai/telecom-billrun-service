package com.telecom.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BillRunRequest(@NotBlank @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "must be YYYY-MM") String cycle) {
}
