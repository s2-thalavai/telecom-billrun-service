package com.telecom.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "subscriber_accounts")
public class SubscriberAccount {

    @Id
    @Column(name = "account_id", length = 20)
    private String accountId;

    @Column(nullable = false, length = 15)
    private String msisdn;

    @Column(name = "plan_code", nullable = false, length = 20)
    private String planCode;

    @Column(nullable = false)
    private boolean active;

    protected SubscriberAccount() {
    }

    public SubscriberAccount(String accountId, String msisdn, String planCode, boolean active) {
        this.accountId = accountId;
        this.msisdn = msisdn;
        this.planCode = planCode;
        this.active = active;
    }

    /** ACC-000001 style ids sort lexicographically in numeric order (keyset pagination relies on it). */
    public static String idFor(int n) {
        return "ACC-%06d".formatted(n);
    }

    public String getAccountId() { return accountId; }
    public String getMsisdn() { return msisdn; }
    public String getPlanCode() { return planCode; }
    public boolean isActive() { return active; }
}
