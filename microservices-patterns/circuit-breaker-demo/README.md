# Circuit Breaker Pattern Demo

This demo shows the **cascading failure problem** in microservices and how the **Circuit Breaker pattern** solves it. Two identical store services call the same supplier service — one without protection, one with a Resilience4j circuit breaker. When the supplier becomes slow, the difference is dramatic.

## Architecture

```
                           ┌─────────────────────┐
                     ┌────▶│  Supplier Service    │
                     │     │  (port 8080)         │
                     │     │                      │
                     │     │  GET /api/products    │
                     │     │  POST /api/simulate/* │
                     │     └─────────────────────┘
                     │                ▲
                     │                │
        ┌────────────┴──┐    ┌───────┴────────────┐
        │ Store Service │    │ Store Service       │
        │ NO Circuit    │    │ WITH Circuit        │
        │ Breaker       │    │ Breaker             │
        │ (port 8081)   │    │ (port 8082)         │
        │               │    │                     │
        │ maxThreads: 5 │    │ maxThreads: 5       │
        │ readTimeout:  │    │ readTimeout: 3s     │
        │   60s         │    │ Resilience4j CB     │
        └───────────────┘    └─────────────────────┘
```

Both store services have an intentionally **small thread pool (5 threads)** to make the cascading failure visible quickly.

## Circuit Breaker States

```
                    success
              ┌───────────────┐
              │               │
              ▼               │
         ┌─────────┐   failure threshold    ┌──────────┐
         │ CLOSED  │ ─────────────────────▶ │   OPEN   │
         └─────────┘     reached            └──────────┘
              ▲                                   │
              │                              timeout expires
              │                                   │
              │         success              ┌────▼─────┐
              └──────────────────────────────│HALF-OPEN │
                                             └──────────┘
                                     failure │     ▲
                                             │     │
                                             └─────┘
                                           back to OPEN
```

- **CLOSED**: Requests flow normally. Failures are counted.
- **OPEN**: All requests are **blocked immediately** — fallback is returned without even calling the supplier. Threads are not consumed.
- **HALF-OPEN**: A limited number of test requests are allowed through. If they succeed → CLOSED. If they fail → back to OPEN.

## Configuration

| Parameter | Value | Description |
|-----------|-------|-------------|
| `slidingWindowSize` | 5 | Evaluate last 5 calls |
| `minimumNumberOfCalls` | 3 | Start evaluating after 3 calls |
| `failureRateThreshold` | 50% | Open circuit if >50% of calls fail |
| `waitDurationInOpenState` | 15s | Stay OPEN for 15s before HALF-OPEN |
| `permittedNumberOfCallsInHalfOpenState` | 2 | Allow 2 test calls in HALF-OPEN |

## Getting Started

```bash
cd circuit-breaker-demo
docker compose up --build
```

## Visual Demo (Web Application)

A **web-based visual simulation** is included, built with Spring Boot + Thymeleaf. It shows an animated, side-by-side comparison of both store services reacting to a supplier failure in real time.

```bash
# Available at http://localhost:8090 after docker compose up --build
# Or run standalone:
cd circuit-breaker-visual
mvn spring-boot:run
# Open http://localhost:8090
```

Features:
- **Side-by-side panels**: Store Service without CB vs with CB
- **Thread pool visualization**: see threads get stuck (no CB) vs stay free (with CB)
- **Circuit Breaker state machine**: live CLOSED → OPEN → HALF-OPEN → CLOSED transitions
- **Sliding window display**: see each call result in the window
- **Auto Demo mode**: guided walkthrough of the full failure + recovery scenario
- **Event log**: real-time log of all events
- **Real services**: connects to the actual microservices via REST proxy (no simulation)

## Automated Demo Scripts

Two interactive scripts automate the full demo with colored output, timing, and pause-between-steps for live presentation.

```bash
# FIRST: show the problem (cascading failure without circuit breaker)
./demo-no-cb.sh

# THEN: show the solution (circuit breaker protects the service)
./demo-cb.sh
```

Each script resets the supplier to HEALTHY at the start, so they can be run independently or in sequence.

---

## Manual Demo Commands

### Phase 1: Everything is healthy

```bash
# Check supplier status
curl -s http://localhost:8080/api/status | jq

# Both store services work perfectly
curl -s http://localhost:8081/store/products | jq
curl -s http://localhost:8082/store/products | jq

# Both health endpoints respond instantly
curl -s http://localhost:8081/store/health | jq
curl -s http://localhost:8082/store/health | jq
```

### Phase 2: Make the supplier SLOW (30s delay)

```bash
curl -s -X POST http://localhost:8080/api/simulate/slow | jq
```

### Phase 3: WITHOUT Circuit Breaker — Cascading Failure

Open a terminal and send 6 concurrent requests to the store service **without** circuit breaker:

```bash
echo "--- Sending 6 concurrent requests to store WITHOUT circuit breaker ---"
for i in $(seq 1 6); do
  curl -s -o /dev/null -w "Request $i: %{time_total}s (HTTP %{http_code})\n" \
    http://localhost:8081/store/products &
done
wait
```

**What happens:**
- Requests 1-5 each grab a thread and **hang for 30 seconds** waiting for the slow supplier
- Request 6+ must wait because **all 5 threads are exhausted**
- The service is effectively **DEAD**

Now try to reach the health endpoint:

```bash
# This will HANG or timeout — the service is completely unresponsive!
curl -s --max-time 5 http://localhost:8081/store/health
echo "(timed out — service is UNRESPONSIVE)"
```

**The cascading failure is complete**: the supplier's slowness has propagated to the store service, making it unable to serve ANY request — even ones that don't need the supplier.

Wait 30 seconds for the threads to free up, then verify it recovers:

```bash
# After 30s, threads are released and service responds again
curl -s http://localhost:8081/store/health | jq
```

### Phase 4: WITH Circuit Breaker — Service Survives

Now test the store service **with** circuit breaker. Send sequential requests and watch the circuit breaker in action:

```bash
echo "--- Request 1: Circuit is CLOSED, call goes through (will timeout in 3s) ---"
curl -s -w "\n(took %{time_total}s)\n" http://localhost:8082/store/products; echo

echo "--- Request 2: Circuit is CLOSED, recording another failure ---"
curl -s -w "\n(took %{time_total}s)\n" http://localhost:8082/store/products; echo

echo "--- Request 3: Circuit is CLOSED, recording another failure ---"
curl -s -w "\n(took %{time_total}s)\n" http://localhost:8082/store/products; echo

echo "--- Request 4: Circuit is now OPEN — instant fallback! ---"
curl -s -w "\n(took %{time_total}s)\n" http://localhost:8082/store/products; echo

echo "--- Request 5: Circuit is OPEN — instant fallback! ---"
curl -s -w "\n(took %{time_total}s)\n" http://localhost:8082/store/products; echo
```

**What happens:**
- Requests 1-3: Each takes ~3s (RestTemplate timeout), circuit breaker records the failures
- After 3 failures (>50% failure rate): Circuit **OPENS**
- Requests 4+: Return **instantly** with fallback data — the supplier is **not even called**
- Threads are freed in ~3s instead of hanging for 30s

Check the health endpoint — it responds **instantly**:

```bash
# Service is ALIVE and responsive!
curl -s http://localhost:8082/store/health | jq
```

Expected output:

```json
{
  "status": "UP",
  "circuitBreaker": {
    "state": "OPEN",
    "failureRate": "100.0%",
    "bufferedCalls": 3,
    "failedCalls": 3,
    "successfulCalls": 0,
    "notPermittedCalls": 2
  }
}
```

### Phase 5: Supplier Recovers — Circuit Closes

```bash
# Make supplier healthy again
curl -s -X POST http://localhost:8080/api/simulate/healthy | jq
```

Wait 15 seconds (the `waitDurationInOpenState`), then the circuit breaker transitions to **HALF-OPEN**:

```bash
# Check state — should be HALF_OPEN after 15s
curl -s http://localhost:8082/store/health | jq

# Test request — circuit breaker allows it through as a test
curl -s -w "\n(took %{time_total}s)\n" http://localhost:8082/store/products; echo

# Second test request succeeds — circuit CLOSES!
curl -s -w "\n(took %{time_total}s)\n" http://localhost:8082/store/products; echo

# Verify: circuit is back to CLOSED
curl -s http://localhost:8082/store/health | jq
```

Expected output:

```json
{
  "status": "UP",
  "circuitBreaker": {
    "state": "CLOSED",
    "failureRate": "0.0%",
    "bufferedCalls": 2,
    "failedCalls": 0,
    "successfulCalls": 2,
    "notPermittedCalls": 0
  }
}
```

### Phase 6: Watch the logs

```bash
# See the contrast between the two store services
docker compose logs -f store-service-no-cb store-service-cb
```

Key log patterns:
- **No CB**: `Calling supplier...` then silence for 30 seconds
- **With CB**: `Circuit CLOSED — calling supplier...` → `FALLBACK triggered` → `CIRCUIT BREAKER IS OPEN — returning fallback immediately`

## Side-by-side comparison

| | Without Circuit Breaker | With Circuit Breaker |
|---|---|---|
| **Supplier slow** | Threads hang for 30s each | Threads timeout in 3s |
| **Thread pool** | Exhausted after 5 requests | Protected — threads freed quickly |
| **Other endpoints** | `/health` UNRESPONSIVE | `/health` responds instantly |
| **Response time** | 30s per request (or timeout) | 3s for first 3, then **instant** fallback |
| **Recovery** | Manual (wait for threads to free) | Automatic (HALF-OPEN → test → CLOSED) |
| **Cascading failure** | YES — caller becomes unresponsive | NO — caller survives |

## Key Takeaways

1. **The problem**: When a downstream service is slow, the calling service's threads are consumed waiting. With a limited thread pool, the caller becomes unresponsive too — a **cascading failure**.

2. **Circuit Breaker solution**: A proxy that monitors call failures. When failures exceed a threshold, it **stops making calls** and returns a fallback immediately, protecting the caller's resources.

3. **Three states**:
   - **CLOSED** (normal): Requests go through, failures are monitored
   - **OPEN** (protection): Requests are blocked, fallback returned instantly
   - **HALF-OPEN** (recovery): Limited test requests check if the downstream service recovered

4. **Fail fast**: The combination of short timeouts (3s) + circuit breaker means the service fails fast instead of hanging, preserving threads for other work.

5. **Resilience4j**: The standard circuit breaker library for Spring Boot 3.x, configured via `application.yml` and applied with a simple `@CircuitBreaker` annotation.

## Cleanup

```bash
docker compose down
```
