package com.example.cqrsvisual.controller;

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

    @Value("${services.ticket.url}")
    private String ticketUrl;

    @Value("${services.delivery.url}")
    private String deliveryUrl;

    @Value("${services.accounting.url}")
    private String accountingUrl;

    @Value("${services.order-history.url}")
    private String orderHistoryUrl;

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

    @PutMapping("/orders/{id}/confirm")
    public ResponseEntity<?> confirmOrder(@PathVariable Long id) {
        try {
            ResponseEntity<Object> res = rt.exchange(orderUrl + "/orders/" + id + "/confirm",
                    HttpMethod.PUT, null, Object.class);
            return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    /* =================== TICKET SERVICE =================== */

    @PostMapping("/tickets")
    public ResponseEntity<?> createTicket(@RequestBody Map<String, Object> body) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Object> res = rt.postForEntity(ticketUrl + "/tickets", new HttpEntity<>(body, h), Object.class);
            return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tickets")
    public ResponseEntity<?> getTickets() {
        try {
            return ResponseEntity.ok(rt.getForObject(ticketUrl + "/tickets", Object.class));
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/tickets/{id}/accept")
    public ResponseEntity<?> acceptTicket(@PathVariable Long id) {
        try {
            ResponseEntity<Object> res = rt.exchange(ticketUrl + "/tickets/" + id + "/accept",
                    HttpMethod.PUT, null, Object.class);
            return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    /* =================== DELIVERY SERVICE =================== */

    @PostMapping("/deliveries")
    public ResponseEntity<?> createDelivery(@RequestBody Map<String, Object> body) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Object> res = rt.postForEntity(deliveryUrl + "/deliveries", new HttpEntity<>(body, h), Object.class);
            return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/deliveries")
    public ResponseEntity<?> getDeliveries() {
        try {
            return ResponseEntity.ok(rt.getForObject(deliveryUrl + "/deliveries", Object.class));
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/deliveries/{id}/pickup")
    public ResponseEntity<?> pickupDelivery(@PathVariable Long id) {
        try {
            ResponseEntity<Object> res = rt.exchange(deliveryUrl + "/deliveries/" + id + "/pickup",
                    HttpMethod.PUT, null, Object.class);
            return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/deliveries/{id}/deliver")
    public ResponseEntity<?> deliverDelivery(@PathVariable Long id) {
        try {
            ResponseEntity<Object> res = rt.exchange(deliveryUrl + "/deliveries/" + id + "/deliver",
                    HttpMethod.PUT, null, Object.class);
            return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    /* =================== ACCOUNTING SERVICE =================== */

    @PostMapping("/invoices")
    public ResponseEntity<?> createInvoice(@RequestBody Map<String, Object> body) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Object> res = rt.postForEntity(accountingUrl + "/invoices", new HttpEntity<>(body, h), Object.class);
            return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/invoices")
    public ResponseEntity<?> getInvoices() {
        try {
            return ResponseEntity.ok(rt.getForObject(accountingUrl + "/invoices", Object.class));
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    /* =================== ORDER HISTORY (READ SIDE) =================== */

    @GetMapping("/order-history")
    public ResponseEntity<?> getOrderHistory() {
        try {
            return ResponseEntity.ok(rt.getForObject(orderHistoryUrl + "/order-history", Object.class));
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/order-history/{orderId}")
    public ResponseEntity<?> getOrderHistoryById(@PathVariable Long orderId) {
        try {
            return ResponseEntity.ok(rt.getForObject(orderHistoryUrl + "/order-history/" + orderId, Object.class));
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }
}
