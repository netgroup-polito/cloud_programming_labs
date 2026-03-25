package com.example.storecb.controller;

import com.example.storecb.service.SupplierClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class StoreController {

    private static final Logger log = LoggerFactory.getLogger(StoreController.class);

    private final SupplierClient supplierClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public StoreController(SupplierClient supplierClient,
                           CircuitBreakerRegistry circuitBreakerRegistry) {
        this.supplierClient = supplierClient;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @GetMapping("/store/products")
    public Object getProducts() {
        return supplierClient.getProducts();
    }

    @GetMapping("/store/health")
    public Map<String, Object> health() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("supplierService");
        CircuitBreaker.Metrics metrics = cb.getMetrics();

        return Map.of(
                "status", "UP",
                "circuitBreaker", Map.of(
                        "state", cb.getState().name(),
                        "failureRate", String.format("%.1f%%", metrics.getFailureRate()),
                        "bufferedCalls", metrics.getNumberOfBufferedCalls(),
                        "failedCalls", metrics.getNumberOfFailedCalls(),
                        "successfulCalls", metrics.getNumberOfSuccessfulCalls(),
                        "notPermittedCalls", metrics.getNumberOfNotPermittedCalls()
                )
        );
    }
}
