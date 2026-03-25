package com.example.customerservice.config;

import com.example.customerservice.model.Customer;
import com.example.customerservice.repository.CustomerRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initCustomers(CustomerRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                repository.save(new Customer("Alice", new BigDecimal("1000.00")));
                repository.save(new Customer("Bob", new BigDecimal("500.00")));
                log.info(">>> Initialized customers: Alice (credit limit 1000), Bob (credit limit 500)");
            }
        };
    }
}
