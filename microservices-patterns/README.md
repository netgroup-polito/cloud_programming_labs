# Cloud-Native Programming – Microservices Design Patterns

Hands-on demos for a cloud-native programming course. Each demo is a self-contained Docker Compose project that illustrates a key microservices design pattern using **Spring Boot 3**, **PostgreSQL**, **MongoDB**, **RabbitMQ**, and **Resilience4j**.

## Prerequisites

- Docker & Docker Compose
- `curl` and `jq` (for running demo commands)
- Bash (for automated demo scripts)

No local Java or Maven installation is needed — everything builds inside Docker containers.

## Demos

### 1. Saga Pattern — Service Collaboration

> **Problem**: Business transactions that span multiple services cannot use local ACID transactions. Two-phase commit (2PC) is not a viable option in microservices.
>
> **Solution**: Implement each cross-service transaction as a **saga** — a sequence of local transactions coordinated through asynchronous events. If a step fails, compensating transactions undo the previous steps.

```
saga-demo/
├── order-service        (port 8081)  — Creates orders in PENDING state
├── customer-service     (port 8082)  — Manages customer credit limits
├── RabbitMQ             (port 5672)  — Event bus
└── 2x PostgreSQL                     — One database per service
```

**Demo flow**: POST an order → OrderService publishes `OrderCreated` → CustomerService attempts to reserve credit → publishes result → OrderService approves or rejects the order.

```bash
cd saga-demo
docker compose up --build
curl -s -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": 1, "orderTotal": 200.00}' | jq
```

[Full documentation →](saga-demo/README.md)

---

### 2. CQRS — Command Query Responsibility Segregation

> **Problem**: With Database per Service, queries that need data from multiple services cannot simply join across databases.
>
> **Solution**: Maintain a **read-only view database** (the Query side) that subscribes to domain events from all services (the Command side). The view is denormalized and optimized for the specific query needs.

```
cqrs-demo/
├── order-service        (port 8081)  — Publishes order events
├── ticket-service       (port 8082)  — Publishes ticket events
├── delivery-service     (port 8083)  — Publishes delivery events
├── accounting-service   (port 8084)  — Publishes invoice events
├── order-history-service(port 8085)  — CQRS read side (MongoDB view)
├── RabbitMQ             (port 5672)  — Event bus
├── PostgreSQL           (port 5432)  — 4 databases (one per command service)
└── MongoDB              (port 27017) — Denormalized view database
```

**Demo flow**: Create an order → create a ticket → accept it → schedule delivery → deliver → create invoice. At each step, query the Order History Service to see a single denormalized document aggregating data from all 4 services.

```bash
cd cqrs-demo
docker compose up --build
# Create order, then query the view
curl -s -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": 1, "customerName": "Alice", "items": "2x Margherita Pizza", "totalAmount": 35.00}' | jq
curl -s http://localhost:8085/order-history | jq
```

[Full documentation →](cqrs-demo/README.md)

---

### 3. Circuit Breaker

> **Problem**: When a downstream service is slow or unavailable, the calling service's threads hang waiting for a response. This leads to thread pool exhaustion and cascading failure — the caller becomes unresponsive too.
>
> **Solution**: A **circuit breaker** proxy monitors calls to the downstream service. When failures exceed a threshold, the circuit **opens** and returns a fallback response immediately, protecting the caller's resources. After a timeout, it allows test requests through (**half-open**) to check if the downstream service has recovered.

```
circuit-breaker-demo/
├── supplier-service     (port 8080)  — Downstream service (can simulate slowness)
├── store-service-no-cb  (port 8081)  — Caller WITHOUT circuit breaker
└── store-service-cb     (port 8082)  — Caller WITH Resilience4j circuit breaker
```

**Demo flow**: Both store services have a 5-thread pool. When the supplier becomes slow, the unprotected store dies (cascading failure) while the circuit-breaker-protected store survives with instant fallback responses.

```bash
cd circuit-breaker-demo
docker compose up --build

# Automated interactive demos (recommended for live presentation)
./demo-no-cb.sh    # Shows the cascading failure problem
./demo-cb.sh       # Shows the circuit breaker solution
```

[Full documentation →](circuit-breaker-demo/README.md)

---

## Quick Reference

| Pattern | Problem | Solution | Key Technology |
|---------|---------|----------|----------------|
| **Saga** | Cross-service transactions | Sequence of local transactions + compensating actions | RabbitMQ events |
| **CQRS** | Cross-service queries | Denormalized read-only view database | MongoDB + RabbitMQ |
| **Circuit Breaker** | Cascading failures | Proxy that stops calling a failing service | Resilience4j |

## Common Commands

```bash
# Start a demo
cd <demo-folder>
docker compose up --build

# Start in background
docker compose up --build -d

# View logs
docker compose logs -f

# View logs for specific service
docker compose logs -f <service-name>

# Stop and cleanup
docker compose down

# Stop and remove all data volumes
docker compose down -v

# ── Connect to a database inside a container ──

# PostgreSQL (saga-demo: order-db / customer-db)
docker compose exec order-db psql -U order_user -d orderdb
#   \dt              — list tables
#   SELECT * FROM orders;
#   \q               — quit

# PostgreSQL (cqrs-demo: single postgres instance, 4 databases)
docker compose exec postgres psql -U admin -d orderdb
docker compose exec postgres psql -U admin -d ticketdb

# MongoDB (cqrs-demo: order history view)
docker compose exec mongodb mongosh orderhistorydb
#   show collections
#   db.order_history.find().pretty()
#   exit

# MySQL (generic pattern — not used in these demos, shown for reference)
docker compose exec <mysql-container> mysql -u <user> -p<password> <database>
```

## Architecture Principles

All demos follow the same core principles:

1. **Database per Service** — Each microservice owns its private database, accessible only through its API
2. **Loose Coupling** — Services communicate through asynchronous events (Saga, CQRS) or resilient synchronous calls (Circuit Breaker)
3. **Independent Deployment** — Each service is a separate Spring Boot application with its own Dockerfile
4. **Polyglot Persistence** — Different services use different databases when appropriate (PostgreSQL for transactional data, MongoDB for denormalized views)
5. **Infrastructure as Code** — The entire infrastructure is defined in `docker-compose.yml` — one command to start everything
