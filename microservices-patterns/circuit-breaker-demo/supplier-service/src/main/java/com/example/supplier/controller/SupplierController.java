package com.example.supplier.controller;

import com.example.supplier.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class SupplierController {

    private static final Logger log = LoggerFactory.getLogger(SupplierController.class);

    private volatile String mode = "HEALTHY";

    private static final List<Product> PRODUCTS = List.of(
            new Product(1L, "Laptop", 999.99),
            new Product(2L, "Smartphone", 699.99),
            new Product(3L, "Headphones", 149.99),
            new Product(4L, "Tablet", 449.99),
            new Product(5L, "Smartwatch", 299.99)
    );

    @GetMapping("/api/products")
    public List<Product> getProducts() throws InterruptedException {
        log.info(">>> [{}] Received request for /api/products", mode);

        if ("SLOW".equals(mode)) {
            log.info(">>> SLOW MODE: Delaying response by 30 seconds...");
            Thread.sleep(30_000);
            log.info(">>> SLOW MODE: Delay completed, returning products");
        }

        return PRODUCTS;
    }

    @PostMapping("/api/simulate/{newMode}")
    public Map<String, String> setMode(@PathVariable String newMode) {
        String previousMode = this.mode;
        this.mode = newMode.toUpperCase();
        log.info(">>> Mode changed: {} -> {}", previousMode, this.mode);
        return Map.of(
                "previousMode", previousMode,
                "currentMode", this.mode
        );
    }

    @GetMapping("/api/status")
    public Map<String, String> getStatus() {
        return Map.of("mode", mode);
    }
}
