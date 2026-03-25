package com.example.visual.controller;

import com.example.visual.service.HealthMonitor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Proxy controller that forwards requests to the real microservices.
 * This avoids CORS issues (browser only talks to this server)
 * and wraps responses with timing metadata for the visual dashboard.
 *
 * Health polling is done server-side by HealthMonitor (@Scheduled),
 * not by the browser — this avoids the browser's 6-connection limit
 * blocking health polls when many product requests are in-flight.
 */
@RestController
@RequestMapping("/api/proxy")
public class ProxyController {

    private final RestTemplate longRt;
    private final RestTemplate shortRt;
    private final HealthMonitor healthMonitor;

    @Value("${services.supplier.url}")
    private String supplierUrl;

    @Value("${services.store-no-cb.url}")
    private String storeNoCbUrl;

    @Value("${services.store-cb.url}")
    private String storeCbUrl;

    public ProxyController(@Qualifier("longTimeout") RestTemplate longRt,
                           @Qualifier("shortTimeout") RestTemplate shortRt,
                           HealthMonitor healthMonitor) {
        this.longRt = longRt;
        this.shortRt = shortRt;
        this.healthMonitor = healthMonitor;
    }

    /* =================== CACHED STATUS (server-side polled) =================== */

    /** Returns cached supplier status + CB health. Instant response, no blocking. */
    @GetMapping("/status")
    public ResponseEntity<?> cachedStatus() {
        return ResponseEntity.ok(healthMonitor.getSnapshot());
    }

    /* =================== SUPPLIER =================== */

    @PostMapping("/supplier/simulate/{mode}")
    public ResponseEntity<?> supplierSimulate(@PathVariable String mode) {
        try {
            Object body = shortRt.postForObject(
                    supplierUrl + "/api/simulate/" + mode, null, Object.class);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    /* =================== STORE NO-CB =================== */

    @GetMapping("/no-cb/products")
    public ResponseEntity<?> noCbProducts() {
        long start = System.currentTimeMillis();
        try {
            longRt.getForObject(storeNoCbUrl + "/store/products", Object.class);
            long elapsed = System.currentTimeMillis() - start;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("responseTime", elapsed);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("responseTime", elapsed);
            result.put("error", e.getClass().getSimpleName());
            return ResponseEntity.ok(result);
        }
    }

    /* =================== STORE CB =================== */

    @GetMapping("/cb/products")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> cbProducts() {
        long start = System.currentTimeMillis();
        try {
            Object body = longRt.getForObject(storeCbUrl + "/store/products", Object.class);
            long elapsed = System.currentTimeMillis() - start;

            boolean fallback = body instanceof Map
                    && Boolean.TRUE.equals(((Map<?, ?>) body).get("fallback"));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("responseTime", elapsed);
            result.put("fallback", fallback);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("responseTime", elapsed);
            result.put("fallback", false);
            result.put("error", e.getClass().getSimpleName());
            return ResponseEntity.ok(result);
        }
    }
}
