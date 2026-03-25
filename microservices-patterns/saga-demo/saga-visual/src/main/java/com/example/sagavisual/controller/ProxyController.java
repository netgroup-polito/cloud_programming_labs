package com.example.sagavisual.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/proxy")
public class ProxyController {

    private final RestTemplate rt;

    @Value("${services.order.url}")
    private String orderUrl;

    @Value("${services.customer.url}")
    private String customerUrl;

    public ProxyController(RestTemplate rt) {
        this.rt = rt;
    }

    /* =================== ORDER SERVICE =================== */

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Object> res = rt.postForEntity(orderUrl + "/orders", new HttpEntity<>(body, h), Object.class);
            return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/orders")
    public ResponseEntity<?> getOrders() {
        try {
            return ResponseEntity.ok(rt.getForObject(orderUrl + "/orders", Object.class));
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrder(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(rt.getForObject(orderUrl + "/orders/" + id, Object.class));
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    /* =================== CUSTOMER SERVICE =================== */

    @GetMapping("/customers")
    public ResponseEntity<?> getCustomers() {
        try {
            return ResponseEntity.ok(rt.getForObject(customerUrl + "/customers", Object.class));
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<?> getCustomer(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(rt.getForObject(customerUrl + "/customers/" + id, Object.class));
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }
}
