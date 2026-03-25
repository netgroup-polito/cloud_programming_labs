package com.example.visual.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-side health polling — runs on a background thread every 1.5s,
 * completely independent of browser connections.
 * This avoids the browser's 6-connection-per-host limit that was
 * corrupting health status when many product requests were in-flight.
 */
@Component
public class HealthMonitor {

    private final RestTemplate shortRt;

    @Value("${services.supplier.url}")
    private String supplierUrl;

    @Value("${services.store-cb.url}")
    private String storeCbUrl;

    // Cached results (volatile for thread-safe reads from controller)
    private volatile Map<String, Object> supplierStatus = Map.of("mode", "UNKNOWN");
    private volatile Map<String, Object> cbHealth = Map.of();

    public HealthMonitor(@Qualifier("shortTimeout") RestTemplate shortRt) {
        this.shortRt = shortRt;
    }

    @Scheduled(fixedRate = 1500)
    public void poll() {
        pollSupplier();
        pollCbHealth();
    }

    private void pollSupplier() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = shortRt.getForObject(supplierUrl + "/api/status", Map.class);
            if (body != null) {
                supplierStatus = body;
            }
        } catch (Exception e) {
            supplierStatus = Map.of("mode", "UNREACHABLE", "error", e.getClass().getSimpleName());
        }
    }

    private void pollCbHealth() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = shortRt.getForObject(storeCbUrl + "/store/health", Map.class);
            if (body != null) {
                cbHealth = body;
            }
            // On success, update cbHealth. On failure, keep the LAST KNOWN
            // cbHealth — this is critical because when the CB is OPEN and
            // all threads are briefly busy, we still want to show the CB state.
        } catch (Exception e) {
            // Keep last known cbHealth — don't overwrite with empty/error.
            // The CB state (OPEN/HALF_OPEN) is still valid even if the
            // health endpoint can't respond momentarily.
        }
    }

    public Map<String, Object> getSnapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supplier", supplierStatus);
        result.put("cbHealth", cbHealth);
        return result;
    }
}
