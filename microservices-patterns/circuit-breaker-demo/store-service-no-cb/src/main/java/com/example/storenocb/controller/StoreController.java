package com.example.storenocb.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
public class StoreController {

    private static final Logger log = LoggerFactory.getLogger(StoreController.class);

    private final RestTemplate restTemplate;

    @Value("${supplier.url}")
    private String supplierUrl;

    public StoreController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/store/products")
    public Object getProducts() {
        String thread = Thread.currentThread().getName();
        log.info(">>> [{}] Calling supplier service at {}...", thread, supplierUrl);

        long start = System.currentTimeMillis();
        Object result = restTemplate.getForObject(supplierUrl + "/api/products", Object.class);
        long elapsed = System.currentTimeMillis() - start;

        log.info(">>> [{}] Supplier responded in {}ms", thread, elapsed);
        return result;
    }

    @GetMapping("/store/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "circuitBreaker", "NONE",
                "info", "This service has NO circuit breaker protection"
        );
    }
}
