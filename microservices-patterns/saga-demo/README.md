# Saga Pattern Demo – Service Collaboration

This demo illustrates the **Saga pattern** for implementing distributed transactions across microservices. It uses an e-commerce scenario where placing an order must verify the customer's credit limit — but Orders and Customers live in **separate databases owned by separate services**.

## Architecture

```
┌─────────────────┐         ┌──────────────┐         ┌─────────────────────┐
│  Order Service   │────────▶│   RabbitMQ   │────────▶│  Customer Service   │
│   (port 8081)    │◀────────│ (port 5672)  │◀────────│    (port 8082)      │
└────────┬────────┘         └──────────────┘         └──────────┬──────────┘
         │                                                      │
         ▼                                                      ▼
┌─────────────────┐                                  ┌─────────────────────┐
│    order-db      │                                  │    customer-db      │
│  PostgreSQL      │                                  │    PostgreSQL       │
│  (port 5432)     │                                  │    (port 5433)      │
└─────────────────┘                                  └─────────────────────┘
```

Each microservice has its own **private database** (Database per Service pattern). Communication happens exclusively through **asynchronous events** via RabbitMQ.

## Saga Flow

```
POST /orders
     │
     ▼
┌──────────────────────────────────────┐
│ STEP 1  OrderService creates Order   │
│         in PENDING state             │
└──────────────┬───────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│ STEP 2  OrderService publishes       │
│         OrderCreated event           │
│         ─── RabbitMQ ───▶            │
└──────────────────────────────────────┘
                                          ┌──────────────────────────────────────┐
                                          │ STEP 3  CustomerService receives     │
                                          │         event, attempts to reserve   │
                                          │         credit                       │
                                          └──────────────┬───────────────────────┘
                                                         │
                                                         ▼
                                          ┌──────────────────────────────────────┐
                                          │ STEP 4  CustomerService publishes    │
                                          │         CreditReservationResult      │
                                          │         ◀─── RabbitMQ ───            │
                                          └──────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│ STEP 5  OrderService receives result │
│         and updates Order to         │
│         APPROVED or REJECTED         │
└──────────────────────────────────────┘
```

## Infrastructure

| Service            | Port  | Description                        |
|--------------------|-------|------------------------------------|
| `order-db`         | 5432  | PostgreSQL – Orders database       |
| `customer-db`      | 5433  | PostgreSQL – Customers database    |
| `rabbitmq`         | 5672  | Message broker (AMQP)              |
| `rabbitmq` UI      | 15672 | RabbitMQ Management (guest/guest)  |
| `order-service`    | 8081  | Order microservice                 |
| `customer-service` | 8082  | Customer microservice              |

## Pre-seeded Customers

| ID | Name  | Credit Limit |
|----|-------|--------------|
| 1  | Alice | 1000.00      |
| 2  | Bob   | 500.00       |

## Getting Started

```bash
cd saga-demo
docker compose up --build
```

Wait until all services are healthy, then proceed with the demo commands below.

## Demo Commands

### View initial state

```bash
# Customers table
curl -s http://localhost:8082/customers | jq

# Orders table (empty)
curl -s http://localhost:8081/orders | jq
```

### Scenario A: Order APPROVED (within credit limit)

```bash
# Create order for Alice, total 200 (Alice has 1000 available)
curl -s -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": 1, "orderTotal": 200.00}' | jq
```

The full saga executes:

1. Order created in **PENDING** state
2. `OrderCreated` event published to RabbitMQ
3. CustomerService reserves 200 out of 1000 available credit
4. `CreditReservationResult(approved=true)` published back
5. Order updated to **APPROVED**

Verify:

```bash
# Order is now APPROVED
curl -s http://localhost:8081/orders/1 | jq

# Alice's creditReserved is now 200
curl -s http://localhost:8082/customers/1 | jq
```

### Scenario B: Order REJECTED (exceeds credit limit)

```bash
# Create order for Bob, total 600 (Bob only has 500 limit)
curl -s -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": 2, "orderTotal": 600.00}' | jq
```

The saga detects insufficient credit:

1. Order created in **PENDING** state
2. `OrderCreated` event published
3. CustomerService detects insufficient credit (needs 600, has 500)
4. `CreditReservationResult(approved=false)` published back
5. Order updated to **REJECTED**

Verify:

```bash
# Order is REJECTED
curl -s http://localhost:8081/orders/2 | jq

# Bob's creditReserved is still 0 (no credit was reserved)
curl -s http://localhost:8082/customers/2 | jq
```

### Scenario C: Exhausting credit over multiple orders

```bash
# Alice has 800 remaining (1000 - 200). Order 500 -> APPROVED
curl -s -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": 1, "orderTotal": 500.00}' | jq

# Alice has 300 remaining (1000 - 700). Order 400 -> REJECTED
curl -s -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": 1, "orderTotal": 400.00}' | jq

# Final state of both tables
curl -s http://localhost:8081/orders | jq
curl -s http://localhost:8082/customers | jq
```

Expected final state:

| Order ID | Customer | Total  | Status     |
|----------|----------|--------|------------|
| 1        | Alice    | 200.00 | APPROVED   |
| 2        | Bob      | 600.00 | REJECTED   |
| 3        | Alice    | 500.00 | APPROVED   |
| 4        | Alice    | 400.00 | REJECTED   |

### Watch the saga flow in real time

```bash
docker compose logs -f order-service customer-service
```

All saga steps are logged with `>>>` prefix and step numbers (`STEP 1` through `STEP 5`), making it easy to follow the distributed transaction in the console output.

### RabbitMQ Management UI

Open [http://localhost:15672](http://localhost:15672) (credentials: `guest` / `guest`) to inspect:

- **Exchanges**: `saga-exchange` (topic exchange)
- **Queues**: `order-created-queue` and `credit-result-queue`
- **Message rates** and delivery confirmations

## Visual Demo (Web Application)

A **web-based visual simulation** is included, built with Spring Boot + Thymeleaf. It shows an animated visualization of the Saga pattern executing across services in real time.

```bash
# Available at http://localhost:8090 after docker compose up --build
# Or run standalone:
cd saga-visual
mvn spring-boot:run
# Open http://localhost:8090
```

Features:
- **Saga steps visualization**: 5-step progress tracker with animated states (pending, active, done, failed)
- **Order Service panel**: live order list with PENDING/APPROVED/REJECTED status badges
- **Customer Service panel**: credit bar visualization showing limit, reserved, and available credit
- **RabbitMQ message flow**: animated message bubbles (OrderCreated, CreditOK/DENIED)
- **Auto Demo mode**: guided walkthrough of 4 scenarios (approved, rejected, second order, credit exhaustion)
- **Manual controls**: create orders for Alice or Bob with different amounts
- **Real services**: connects to the actual microservices via REST proxy (no simulation)

## Key Takeaways

1. **Database per Service**: Each microservice owns its data. There is no shared database.
2. **No distributed transactions (2PC)**: Instead, the Saga pattern coordinates through a sequence of local transactions + events.
3. **Eventual consistency**: The order is temporarily in `PENDING` state until the saga completes — the system is eventually consistent.
4. **Compensating actions**: When credit is insufficient, no rollback is needed because credit was never reserved. In more complex sagas, compensating transactions would undo previous steps.

## Cleanup

```bash
docker compose down -v
```

The `-v` flag removes the database volumes, so the next run starts fresh.
