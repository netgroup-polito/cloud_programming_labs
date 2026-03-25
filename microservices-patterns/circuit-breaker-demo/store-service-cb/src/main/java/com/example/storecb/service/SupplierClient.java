package com.example.storecb.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class SupplierClient {

    private static final Logger log = LoggerFactory.getLogger(SupplierClient.class);

    private final RestTemplate restTemplate;

    @Value("${supplier.url}")
    private String supplierUrl;

    public SupplierClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @CircuitBreaker(name = "supplierService", fallbackMethod = "getProductsFallback")
    public Object getProducts() {
        String thread = Thread.currentThread().getName();
        log.info(">>> [{}] Circuit CLOSED — calling supplier service at {}...", thread, supplierUrl);

        long start = System.currentTimeMillis();
        Object result = restTemplate.getForObject(supplierUrl + "/api/products", Object.class);
        long elapsed = System.currentTimeMillis() - start;

        log.info(">>> [{}] Supplier responded in {}ms", thread, elapsed);
        return result;
    }

    /**
     * Fallback when circuit is OPEN or call fails.
     */
    public Object getProductsFallback(CallNotPermittedException e) {
        log.warn(">>> CIRCUIT BREAKER IS OPEN — returning fallback immediately (request not even attempted)");
        return buildFallbackResponse("Circuit breaker is OPEN — request blocked");
    }

    public Object getProductsFallback(Exception e) {
        log.warn(">>> FALLBACK triggered — supplier call failed: {}", e.getMessage());
        return buildFallbackResponse("Supplier call failed: " + e.getClass().getSimpleName());
    }

    private Object buildFallbackResponse(String reason) {
        return Map.of(
                "fallback", true,
                "reason", reason,
                "products", List.of(
                        Map.of("id", 0, "name", "[CACHED] Laptop", "price", 999.99),
                        Map.of("id", 0, "name", "[CACHED] Smartphone", "price", 699.99)
                ),
                "message", "Returning cached product data — supplier is unavailable"
        );
    }
}
