package com.telecom.billing.config;

import com.telecom.billing.entity.SubscriberAccount;
import com.telecom.billing.repository.SubscriberAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Demo accounts ACC-000001 ... (prod: Flyway V2). */
@Component
@Profile("!prod")
public class DataSeeder implements CommandLineRunner {

    private final SubscriberAccountRepository accounts;
    private final BillingProperties billing;

    public DataSeeder(SubscriberAccountRepository accounts, BillingProperties billing) {
        this.accounts = accounts;
        this.billing = billing;
    }

    @Override
    public void run(String... args) {
        if (accounts.count() > 0) {
            return;
        }
        List<SubscriberAccount> batch = new ArrayList<>();
        for (int n = 1; n <= billing.seedAccounts(); n++) {
            batch.add(new SubscriberAccount(SubscriberAccount.idFor(n), "98%08d".formatted(n),
                    n % 2 == 0 ? "UNL-599" : "DATA-299", true));
            if (batch.size() == 500) {
                accounts.saveAll(batch);
                batch.clear();
            }
        }
        accounts.saveAll(batch);
    }
}
