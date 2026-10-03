package com.telecom.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TelecomBillRunApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelecomBillRunApplication.class, args);
    }
}
