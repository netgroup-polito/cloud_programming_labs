# CQRS Pattern Demo – Order History View

This demo illustrates the **Command Query Responsibility Segregation (CQRS)** pattern. In a microservice architecture with **Database per Service**, it is no longer straightforward to implement queries that join data from multiple services. CQRS solves this by maintaining a **read-only view database** that subscribes to domain events published by all services.

## Architecture

```
 COMMAND SIDE (each service has its own database)          READ SIDE (denormalized view)
 ───────────────────────────────────────────────          ──────────────────────────────

 ┌─────────────────┐
 │  Order Service   │──── ORDER_CREATED ──────┐
 │  (port 8081)     │──── ORDER_CONFIRMED ────┐│
 │  [PostgreSQL]    │                         ││
 └─────────────────┘                         ││
                                              ││     ┌───────────────────────────┐
 ┌─────────────────┐                         ││     │                           │
 │  Ticket Service  │──── TICKET_CREATED ────┐││     │  Order History Service    │
 │  (port 8082)     │──── TICKET_ACCEPTED ──┐│││     │  (port 8085)             │
 │  [PostgreSQL]    │                       ││││     │                           │
 └─────────────────┘                       ││││     │  ┌─────────────────────┐  │
                                    ┌──────┤│││     │  │ Order History View  │  │
 ┌─────────────────┐                │      ││││     │  │    Database         │  │
 │ Delivery Service │── DELIVERY_*──┤ Rabbit├┘│││     │  │  [MongoDB]         │  │
 │  (port 8083)     │              │  MQ   ├─┘││     │  └─────────────────────┘  │
 │  [PostgreSQL]    │              │       ├──┘│     │                           │
 └─────────────────┘                │       ├───┘     │  GET /order-history      │
                                    │       │────────▶│  GET /order-history/{id} │
 ┌─────────────────┐                │       │         │                           │
 │Accounting Service│── INVOICE_*───┘       │         └───────────────────────────┘
 │  (port 8084)     │                       │
 │  [PostgreSQL]    │                       │
 └─────────────────┘                       │
                                            │
                              All events flow through
                              RabbitMQ topic exchange
                              "cqrs-events"
```

### How it works

1. Each **command service** owns its data in a private PostgreSQL database and exposes write APIs (POST, PUT)
2. When data changes, the command service publishes a **domain event** to RabbitMQ
3. The **Order History Service** (read side) subscribes to **all** events via a wildcard binding (`#`)
4. It projects each event into a **denormalized MongoDB document** that aggregates data from all services
5. Clients query the Order History Service to get a **complete, cross-service view** of any order

## Infrastructure

| Service                | Port  | Database   | Description                         |
|------------------------|-------|------------|-------------------------------------|
| `postgres`             | 5432  | PostgreSQL | Shared instance, 4 separate DBs     |
| `mongodb`              | 27017 | MongoDB    | View database for order history     |
| `rabbitmq`             | 5672  | —          | Message broker (AMQP)               |
| `rabbitmq` UI          | 15672 | —          | Management console (guest/guest)    |
| `order-service`        | 8081  | orderdb    | Manages orders                      |
| `ticket-service`       | 8082  | ticketdb   | Manages kitchen tickets             |
| `delivery-service`     | 8083  | deliverydb | Manages deliveries                  |
| `accounting-service`   | 8084  | accountingdb | Manages invoices                  |
| `order-history-service`| 8085  | MongoDB    | CQRS read-side view                 |

## Domain Events

| Service            | Event Type          | Routing Key              |
|--------------------|---------------------|--------------------------|
| Order Service      | `ORDER_CREATED`     | `order.created`          |
| Order Service      | `ORDER_CONFIRMED`   | `order.confirmed`        |
| Ticket Service     | `TICKET_CREATED`    | `ticket.created`         |
| Ticket Service     | `TICKET_ACCEPTED`   | `ticket.accepted`        |
| Delivery Service   | `DELIVERY_SCHEDULED`| `delivery.scheduled`     |
| Delivery Service   | `DELIVERY_PICKED_UP`| `delivery.pickedUp`      |
| Delivery Service   | `DELIVERY_DELIVERED`| `delivery.delivered`     |
| Accounting Service | `INVOICE_CREATED`   | `accounting.invoiceCreated` |

## Getting Started

```bash
cd cqrs-demo
docker compose up --build
```

Wait until all services are healthy (first build takes a few minutes for Maven dependency downloads).

## Demo Script

The demo simulates a food delivery order lifecycle. At each step, query the **Order History View** to see the denormalized, cross-service view being built incrementally.

### Step 0: Check initial state

```bash
# Order History View is empty
curl -s http://localhost:8085/order-history | jq
```

### Step 1: Create an Order (OrderService)

```bash
curl -s -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": 1, "customerName": "Alice", "items": "2x Margherita Pizza", "totalAmount": 35.00}' | jq
```

Check the view — it now has the order with `orderStatus: CREATED`:

```bash
curl -s http://localhost:8085/order-history | jq
```

### Step 2: Create a Kitchen Ticket (TicketService)

```bash
curl -s -X POST http://localhost:8082/tickets \
  -H "Content-Type: application/json" \
  -d '{"orderId": 1, "items": "2x Margherita Pizza"}' | jq
```

Check the view — `ticketStatus` is now `CREATED`:

```bash
curl -s http://localhost:8085/order-history/1 | jq
```

### Step 3: Kitchen Accepts the Ticket (TicketService)

```bash
curl -s -X PUT http://localhost:8082/tickets/1/accept | jq
```

Check the view — `ticketStatus` is now `ACCEPTED`:

```bash
curl -s http://localhost:8085/order-history/1 | jq
```

### Step 4: Confirm the Order (OrderService)

```bash
curl -s -X PUT http://localhost:8081/orders/1/confirm | jq
```

Check the view — `orderStatus` is now `CONFIRMED`:

```bash
curl -s http://localhost:8085/order-history/1 | jq
```

### Step 5: Schedule Delivery (DeliveryService)

```bash
curl -s -X POST http://localhost:8083/deliveries \
  -H "Content-Type: application/json" \
  -d '{"orderId": 1, "address": "Via Roma 1, Milano"}' | jq
```

Check the view — `deliveryStatus: SCHEDULED`, `deliveryAddress` populated:

```bash
curl -s http://localhost:8085/order-history/1 | jq
```

### Step 6: Rider Picks Up the Order (DeliveryService)

```bash
curl -s -X PUT http://localhost:8083/deliveries/1/pickup | jq
```

Check the view — `deliveryStatus: PICKED_UP`:

```bash
curl -s http://localhost:8085/order-history/1 | jq
```

### Step 7: Order Delivered (DeliveryService)

```bash
curl -s -X PUT http://localhost:8083/deliveries/1/deliver | jq
```

Check the view — `deliveryStatus: DELIVERED`:

```bash
curl -s http://localhost:8085/order-history/1 | jq
```

### Step 8: Create Invoice (AccountingService)

```bash
curl -s -X POST http://localhost:8084/invoices \
  -H "Content-Type: application/json" \
  -d '{"orderId": 1, "amount": 35.00}' | jq
```

### Step 9: Final View — Complete Order History

```bash
curl -s http://localhost:8085/order-history/1 | jq
```

Expected output — a **single denormalized document** aggregating data from all 4 services:

```json
{
  "id": "1",
  "orderId": 1,
  "customerName": "Alice",
  "items": "2x Margherita Pizza",
  "totalAmount": 35.0,
  "orderStatus": "CONFIRMED",
  "ticketStatus": "ACCEPTED",
  "deliveryStatus": "DELIVERED",
  "deliveryAddress": "Via Roma 1, Milano",
  "invoiceStatus": "CREATED",
  "invoiceAmount": 35.0,
  "lastUpdated": "2026-...",
  "events": [
    { "eventType": "ORDER_CREATED",      "serviceName": "order-service",      "details": "..." },
    { "eventType": "TICKET_CREATED",     "serviceName": "ticket-service",     "details": "..." },
    { "eventType": "TICKET_ACCEPTED",    "serviceName": "ticket-service",     "details": "..." },
    { "eventType": "ORDER_CONFIRMED",    "serviceName": "order-service",      "details": "..." },
    { "eventType": "DELIVERY_SCHEDULED", "serviceName": "delivery-service",   "details": "..." },
    { "eventType": "DELIVERY_PICKED_UP", "serviceName": "delivery-service",   "details": "..." },
    { "eventType": "DELIVERY_DELIVERED", "serviceName": "delivery-service",   "details": "..." },
    { "eventType": "INVOICE_CREATED",    "serviceName": "accounting-service", "details": "..." }
  ]
}
```

### Verify individual service databases

Each service's data stays **private** — you can verify independently:

```bash
# Orders table (command side)
curl -s http://localhost:8081/orders | jq

# Tickets table (command side)
curl -s http://localhost:8082/tickets | jq

# Deliveries table (command side)
curl -s http://localhost:8083/deliveries | jq

# Invoices table (command side)
curl -s http://localhost:8084/invoices | jq
```

### Watch events flowing in real time

```bash
docker compose logs -f order-history-service
```

All projection events are logged with `>>>` prefix, showing each event being received and projected into the view.

### RabbitMQ Management UI

Open [http://localhost:15672](http://localhost:15672) (credentials: `guest` / `guest`) to inspect:

- **Exchange**: `cqrs-events` (topic exchange)
- **Queue**: `order-history-queue` (bound with `#` wildcard)
- **Message flow** from all 4 command services into the single view queue

## Visual Demo (Web Application)

A **web-based visual simulation** is included, built with Spring Boot + Thymeleaf. It shows an animated, step-by-step walkthrough of the complete CQRS flow in real time.

```bash
# Available at http://localhost:8090 after docker compose up --build
# Or run standalone:
cd cqrs-visual
mvn spring-boot:run
# Open http://localhost:8090
```

Features:
- **Command Side panel**: shows all 4 command services with live status updates
- **RabbitMQ event flow**: animated event bubbles flowing from command side to read side
- **Read Side panel**: denormalized Order History View updating in real time after each event
- **Event trail**: full audit log from MongoDB showing every event received
- **Auto Demo mode**: guided walkthrough of the complete order lifecycle (8 steps)
- **Step-by-step mode**: execute one command at a time with explanations
- **Real services**: connects to the actual microservices via REST proxy (no simulation)

## Key Takeaways

1. **CQRS separates reads from writes**: Command services handle writes to their own databases. The Order History Service handles reads from a denormalized view.

2. **The view database is optimized for queries**: Instead of joining 4 relational databases at query time, the view is pre-built as a single MongoDB document per order — no joins needed.

3. **Event-driven updates**: The view is kept up to date by subscribing to domain events. No direct coupling between services.

4. **Eventual consistency**: There is a small delay (replication lag) between a command and the view being updated. The view is **eventually consistent**, not immediately consistent.

5. **Multiple views possible**: You could add more view services (e.g., a `CustomerHistoryService`, a `DeliveryDashboardService`) subscribing to the same events, each with its own optimized database schema.

6. **Drawbacks visible in the demo**: Code duplication (the `DomainEvent` class exists in every service), increased system complexity (8 containers), and eventual consistency.

## Cleanup

```bash
docker compose down -v
```

The `-v` flag removes all database volumes, so the next run starts fresh.
