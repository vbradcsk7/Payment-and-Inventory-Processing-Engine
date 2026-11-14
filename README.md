# Production-Grade Payment and Inventory Processing Engine

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-red.svg)](https://flywaydb.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A high-performance, production-ready RESTful engine built with Java 21 and Spring Boot 3.3.x designed for high-concurrency e-commerce and fintech operations. It handles order lifecycle management, strict request idempotency, row-level pessimistic concurrency control on inventory, cryptographic webhook signature verification (HMAC-SHA256), and scheduled order expiration.

---

## Architecture Overview

The system implements a Layered Clean Architecture that enforces separation of concerns across Domain Entities, Data Access, Business Services, Security Filters, and REST Controllers.

mermaid
flowchart TD
    Client["Client / Frontend"]
    Gateway["Payment Gateway (Stripe / PayPal)"]
    
    subgraph Engine["Payment and Inventory Engine (Spring Boot 3)"]
        Filter["WebhookSignatureFilter (HMAC-SHA256)"]
        OrderCtrl["OrderController"]
        WebhookCtrl["WebhookController"]
        
        OrderSvc["OrderService (Idempotency Manager)"]
        InvSvc["InventoryService (Pessimistic Locking)"]
        WebhookSvc["WebhookProcessorService"]
        Job["OrderExpirationJob (@Scheduled)"]
    end
    
    subgraph Database["PostgreSQL 16"]
        OrdersTbl[("orders (UNIQUE idempotency_key)")]
        ProductsTbl[("products (PESSIMISTIC_WRITE locks)")]
        ItemsTbl[("order_items")]
        WebhooksTbl[("webhook_events (JSONB payloads)")]
    end

    Client -->|"POST /api/v1/orders (Idempotency-Key)"| OrderCtrl
    OrderCtrl --> OrderSvc
    OrderSvc -->|"1. Check Idempotency"| OrdersTbl
    OrderSvc -->|"2. Reserve Stock (FOR UPDATE)"| InvSvc
    InvSvc --> ProductsTbl

    Gateway -->|"POST /api/v1/webhooks/payments (X-Webhook-Signature)"| Filter
    Filter -->|"HMAC Validated"| WebhookCtrl
    WebhookCtrl --> WebhookSvc
    WebhookSvc -->|"Auditing and Idempotency"| WebhooksTbl
    WebhookSvc -->|"Commit/Release Stock and Update State"| OrdersTbl
    WebhookSvc --> InvSvc

    Job -->|"Auto-expire unpaid orders > 15m"| OrdersTbl
    Job -->|"Restore reserved stock"| ProductsTbl


---

## The 4 Core Technical Pillars

### 1. Request Idempotency (Idempotency-Key)
Network retries and double-clicks must never create duplicate orders or deduct duplicate inventory.
* Header: Idempotency-Key: UUID is required on order creation.
* Fast-Path Check: Before beginning a transaction, the engine queries orders by idempotency_key. If present, it immediately returns the original order with HTTP 200 OK.
* Zero Race Conditions: The table enforces a database-level UNIQUE constraint on idempotency_key. In high-speed parallel race conditions where two threads pass the initial check simultaneously, the database catches the collision (DataIntegrityViolationException), and the engine recovers seamlessly by returning the saved order.

### 2. Pessimistic Concurrency Control on Inventory
When 20 users attempt to purchase the last remaining item in the same millisecond:
* Pessimistic Lock (SELECT ... FOR UPDATE): The repository acquires an exclusive row lock (@Lock(LockModeType.PESSIMISTIC_WRITE)) with a strict lock timeout.
* Two-Phase Inventory State:
  * When an order is created (status = PENDING): stock_available decrements and stock_reserved increments.
  * When payment succeeds: stock_reserved is committed permanently.
  * If stock is insufficient, an InsufficientStockException is thrown, triggering a complete transaction rollback.

### 3. Cryptographic Webhook Security (HMAC-SHA256)
Webhooks delivered from payment gateways (e.g., Stripe, PayPal, Adyen) must be verified before processing:
* Raw Body Caching: Incoming requests pass through CachedBodyHttpServletRequest so the raw request payload can be read both by the cryptographic filter and Jackson deserializer.
* Constant-Time Comparison: The HMAC-SHA256 hash of the payload is computed using a shared WEBHOOK_SECRET and compared against X-Webhook-Signature using MessageDigest.isEqual() to prevent timing attacks.
* Unauthenticated or tampered requests are rejected with 401 Unauthorized before reaching business logic.

### 4. Idempotent and Transactional Webhook Processing
Payment gateways re-send webhooks if network latency occurs:
* Each webhook has an immutable event_id stored in webhook_events.
* If a duplicate event_id is received, the engine returns 200 OK immediately without re-executing transitions.
* If valid:
  * payment.succeeded: Transitions order to PAID and commits reserved stock.
  * payment.failed: Transitions order to CANCELLED and restores reserved stock to stock_available.

---

## Database Design (PostgreSQL 16)

The schema is automatically versioned and migrated via Flyway:

| Table | Primary Key | Key Columns and Constraints | Purpose |
|---|---|---|---|
| products | id (BIGSERIAL) | sku (UNIQUE), stock_available, stock_reserved, price, version | Product catalog and real-time inventory management. |
| orders | id (UUID) | idempotency_key (UNIQUE), customer_email, total_amount, status | Order lifecycle (PENDING, PAID, CANCELLED, EXPIRED). |
| order_items | id (BIGSERIAL) | order_id (FK), product_id (FK), quantity, unit_price | Line items per order. |
| webhook_events | event_id (VARCHAR) | provider, event_type, payload (JSONB), status, received_at | Full audit trail and deduplication of external webhooks. |

---

## Quick Start with Docker Compose

Ensure Docker and Docker Compose are installed, then run:

bash
docker compose up --build


The stack will:
1. Boot PostgreSQL 16 on port 5432 and verify health.
2. Run Flyway migrations (V1__init_schema.sql and V2__seed_products.sql).
3. Start the Spring Boot application on port 8080.

To stop the containers:
bash
docker compose down -v


---

## Local Development

### Prerequisites
* Java 21 LTS
* PostgreSQL 16 running locally (or via Docker)

### Run with Maven Wrapper
bash
./mvnw spring-boot:run


For zero-dependency in-memory development (H2 in PostgreSQL mode):
bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local


---

## Interactive Documentation (Swagger UI)

Once running, navigate to:
* Dashboard UI: http://localhost:8080/
* Swagger UI: http://localhost:8080/swagger-ui.html
* OpenAPI Specs (JSON): http://localhost:8080/v3/api-docs
* Healthcheck: http://localhost:8080/actuator/health

---

## Payment Gateway and Concurrency Simulator

The project includes an end-to-end test suite in /simulator that validates:
1. Catalog validation and seed inspection.
2. Idempotent order creation and retry replay verification.
3. Cryptographically signed payment.succeeded webhook dispatch.
4. Duplicate webhook delivery handling.
5. Malicious webhook attack simulation with invalid signatures.
6. 20-Thread Concurrency Race Test: 20 workers simultaneously attempting to purchase the last 1 remaining unit of stock.

To execute the simulator:

bash
python3 simulator/simulate_payment_gateway.py


### Or using the Bash simulator:
bash
./simulator/simulate.sh


---

## Automated Unit and Integration Tests

Run the test suite:
bash
./mvnw clean test


### Included Tests:
* HmacSignatureValidatorTest: Validates HMAC-SHA256 calculations, constant-time comparison, header formatting, and tamper detection.
* OrderServiceTest: Mocks order creation, tests idempotent replay without stock deductions, and validates insufficient inventory handling.
* WebhookProcessorServiceTest: Tests event deduplication, payment.succeeded stock commit, and payment.failed stock release.
* WebhookControllerIntegrationTest: Verifies HTTP 200 on valid signature, HTTP 401 on missing signature, and HTTP 401 on forged signatures.

---

## Background Scheduled Job: Order Expiration

A scheduled background worker (OrderExpirationJob) runs every minute:
* Finds orders in PENDING state older than 15 minutes.
* Transitions them to EXPIRED.
* Releases their reserved inventory back to stock_available.

---

## Project Structure

text
payment-inventory-engine/
├── .mvn/wrapper/                  # Maven Wrapper configuration
├── docker-compose.yml             # PostgreSQL 16 + Application stack
├── Dockerfile                     # Multi-stage production Docker build
├── pom.xml                        # Maven dependencies and plugins
├── README.md                      # Technical documentation
├── simulator/
│   ├── simulate_payment_gateway.py# 20-thread concurrency and webhook simulator
│   └── simulate.sh                # Bash curl and openssl simulator
└── src/
    ├── main/
    │   ├── java/com/ilich/paymentengine/
    │   │   ├── config/            # OpenAPI, Filter, Scheduling config
    │   │   ├── controller/        # Order, Product, Webhook controllers
    │   │   ├── dto/               # Java 21 Records (Request/Response)
    │   │   ├── exception/         # GlobalExceptionHandler and custom exceptions
    │   │   ├── model/             # JPA Entities (Order, Product, WebhookEvent)
    │   │   ├── repository/        # Spring Data JPA with @Lock queries
    │   │   ├── security/          # HMAC validator and cached body filter
    │   │   └── service/           # Order, Inventory, Webhook and Expiration services
    │   └── resources/
    │       ├── application.yml    # Application configuration
    │       ├── application-local.yml # In-memory H2 profile configuration
    │       ├── static/index.html  # Interactive dashboard UI
    │       └── db/migration/      # Flyway SQL migrations (V1 and V2)
    └── test/                      # Unit, WebMvc, and Mockito test suites


---

## License
Distributed under the Apache 2.0 License. See LICENSE for more information.
