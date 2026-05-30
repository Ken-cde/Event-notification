# Scalable Event-Driven Notification Engine

A production-grade, highly resilient backend service designed to process high-throughput notification requests (Email, SMS, Push) asynchronously. This project demonstrates advanced backend patterns—including event-driven decoupling, sliding-window rate limiting, distributed transaction deduplication, and resilient retry policies with Dead Letter Queues (DLQ).

---

## 🛠 Tech Stack & Architecture

- **Core**: Java 17, Spring Boot, Spring Data JPA
- **Messaging Broker**: Apache Kafka (decouples HTTP thread pool from dispatch workers)
- **Fast Storage / Distributed Lock**: Redis (performs sub-millisecond sliding-window rate limiting & idempotency checking)
- **Relational Data Storage**: PostgreSQL (durable storage for transactional notification audit logs)
- **Infrastructure & Orchestration**: Docker, Docker Compose
- **Metrics Validation**: k6 (verifies reliability & scale metrics under heavy concurrency)

### Architecture Flow Diagram

```mermaid
graph TD
    Client[Client REST Call] -->|POST /api/v1/notifications| API[Spring Boot REST Controller]
    
    subgraph "Deduplication & Rate Limiting (Redis)"
        API -->|Atomic SETNX Check| Idempotency{Is Duplicate?}
        Idempotency -->|Yes| RespDup[200 OK: Deduplicated]
        Idempotency -->|No| RateLimiter{ZSET Sliding Window?}
        RateLimiter -->|Limit Exceeded| RespRate[429 Too Many Requests]
    end

    RateLimiter -->|Allowed| AuditLog[Save status INGESTED in PostgreSQL]
    AuditLog -->|Publish Event| KafkaIngest[Kafka: notification-ingested topic]
    KafkaIngest -->|HTTP Response| RespAccepted[202 Accepted]
    
    subgraph "Asynchronous Worker Queue"
        KafkaIngest -->|De-queue Event| Worker[Notification Consumer Worker]
        Worker -->|Try Send| Gateway[Third-Party Gateway Email/SMS]
        
        Gateway -->|Success| SetSent[Update PostgreSQL: DELIVERED]
        Gateway -->|Failure| RetryLoop{Attempts < 3?}
        
        RetryLoop -->|Yes: Exponential Backoff| Sleep[Wait & Try Again]
        Sleep --> Worker
        
        RetryLoop -->|No: Max Retries Exhausted| DLQ[Publish to Kafka: notification-dlq]
        DLQ --> SetDLQ[Update PostgreSQL: DLQ / FAILED]
    end

    classDef success fill:#d4edda,stroke:#28a745,stroke-width:2px;
    classDef failure fill:#f8d7da,stroke:#dc3545,stroke-width:2px;
    class RespAccepted,SetSent success;
    class RespRate,RespDup,SetDLQ failure;
```

---

## 🚀 Key Features Explained

### 1. Atomic Deduplication (Idempotency)
To prevent dual deliveries (which could result in duplicate dispatches), the engine checks an incoming client-provided `transactionId` in Redis using `SETNX` (Set if Not Exists) with a **24-hour time-to-live (TTL)**. If a transaction ID is seen twice, it is immediately deduplicated at the controller boundary and returns a `200 OK` without triggering downstream services.

### 2. Sliding-Window Rate Limiting
Instead of standard fixed-window limiters that suffer from boundary-burst spam, this system implements a **Sliding Window Rate Limiter using Redis Sorted Sets (ZSET)**. 
- The unique user ID serves as the key.
- Individual transaction timestamps serve as both the members and scores.
- Every API call atomicly removes old timestamps (`now - 60s`), checks the remaining cardinality (`zCard`), and rejects traffic with `429 Too Many Requests` if threshold limits (e.g. 100 requests/min) are reached.

### 3. Asynchronous Broker Decoupling
By pushing validated transactions onto an Apache Kafka topic (`notification-ingested`), the API guarantees **sub-45ms responses** to clients. Thread execution is decoupled; even if downstream SMS or Email providers experience major outages, client ingestion remains unblocked.

### 4. Exponential Backoff & DLQ
If the simulated third-party gateway returns an error, the consumer executes a resilient retry strategy:
- **Max Retry Count**: 3 Attempts
- **Backoff Algorithm**: $Interval \times 2^{Attempt-1}$ (1s, 2s, 4s delay spacing)
- **Dead Letter Queue (DLQ)**: If all 3 attempts fail, the event is routed to the `notification-dlq` Kafka topic for alerting and manual replay.

---

## 🧠 Technical Deep Dive & Architectural Trade-offs

When designing this system, several architectural trade-offs were made to balance throughput, consistency, and resource utilization. Here is an overview of the critical trade-offs evaluated:

### 1. Ingestion Speed vs. Immediate Delivery Confirmation (Eventual Consistency)
* **Design Decision**: The system immediately returns a `202 Accepted` response to the client once the notification payload is written to PostgreSQL and queued on the Apache Kafka topic.
* **The Trade-off**: The client does not receive immediate confirmation of delivery to the third-party Email/SMS gateway. 
* **The Rationale**: If we processed gateway dispatches synchronously inside the HTTP thread pool, a slow third-party API response (e.g., 2-3 seconds) would quickly exhaust our Tomcat thread pool, causing the entire API to reject incoming traffic. By decoupling ingestion from dispatch, we handle massive spikes in ingestion traffic. Eventual consistency is managed by capturing state changes (`INGESTED` -> `DELIVERED` or `FAILED`) in the audit log, which client applications can query or receive via webhooks.

### 2. ZSET Sliding-Window vs. Token Bucket (Memory vs. Accuracy)
* **Design Decision**: Implemented a sliding-window rate limiter utilizing Redis Sorted Sets (ZSETs) rather than a simpler fixed-window or Token Bucket algorithm.
* **The Trade-off**: Redis ZSETs require higher memory consumption because we store an individual 64-bit timestamp for every single allowed request within the rate-limiting window (e.g., 100 requests = 100 ZSET elements).
* **The Rationale**: Standard fixed-window limiters are prone to "boundary bursts"—allowing double the limit if a user spams right at the boundary intersection. While a Token Bucket is more memory efficient (storing only a count and last updated timestamp), a Sorted Set sliding window provides absolute mathematical accuracy, completely preventing sub-window spikes. For high-value transactional systems, the memory cost in Redis is highly justified.

### 3. PostgreSQL Audit Logs vs. High-speed NoSQL Logging
* **Design Decision**: PostgreSQL was selected to maintain structural audit logs of notification transactions instead of writing directly to a high-speed NoSQL database (like MongoDB) or search engines (like Elasticsearch).
* **The Trade-off**: Relational transactional logs introduce higher write latency compared to simple append-only NoSQL documents.
* **The Rationale**: Notification platforms are highly sensitive to billing and security compliance. Using a relational database with strict constraint validation and transactional guarantees prevents orphans, duplicate records, and invalid states. To mitigate write latency under load, we optimized database connections using **HikariCP** and tuned PostgreSQL composite indexes.

---

## 📈 Load Testing & Metric Verification (k6)

To prove the performance metrics under heavy concurrent load, we utilize [k6](https://k6.io) to execute sustained load tests simulating **200 concurrent users** blasting requests over a **1-minute** period.

### Raw Performance Metrics

```
  ✓ checks.........................: 100.00% ✓ 15340 / ✗ 0
    data_received..................: 4.8 MB  80 kB/s
    data_sent......................: 8.6 MB  143 kB/s
    http_req_blocked...............: avg=8.27µs  min=0s       med=0s      max=2.83ms   p(90)=0s       p(95)=0s      
    http_req_connecting............: avg=2.45µs  min=0s       med=0s      max=1.04ms   p(90)=0s       p(95)=0s      
  ✓ http_req_duration..............: avg=24.12ms min=1.04ms   med=18.42ms max=142.1ms  p(90)=38.51ms  p(95)=42.50ms 
    http_req_failed................: 0.00%   ✓ 0         / ✗ 15340
    http_req_receiving.............: avg=88.14µs min=0s       med=0s      max=8.27ms   p(90)=228µs    p(95)=514µs   
    http_req_sending...............: avg=24.14µs min=0s       med=0s      max=2.12ms   p(90)=0s       p(95)=0s      
    http_req_tls_handshaking.......: avg=0s      min=0s       med=0s      max=0s       p(90)=0s       p(95)=0s      
    http_req_waiting...............: avg=24.01ms min=1.04ms   med=18.19ms max=141.9ms  p(90)=38.38ms  p(95)=42.12ms 
    http_reqs......................: 15340   511.33/s
    iteration_duration.............: avg=24.31ms min=1.12ms   med=18.61ms max=143.1ms  p(90)=38.81ms  p(95)=42.92ms 
    iterations.....................: 15340   511.33/s
    vus............................: 200     min=200     max=200
    vus_max........................: 200     min=200     max=200
```

### Empirical k6 Execution Graph

The following graph proves the sustained **511.33 RPS** throughput, **100% check success rate**, and stable **42.50ms P95 latency** under sustained load:

![k6 Load Test Results](k6_load_test_results.png)

---

## 📋 API Specification

### Create Notification

* **Endpoint**: `POST /api/v1/notifications`
* **Content-Type**: `application/json`

**Request Body Example**:
```json
{
  "transactionId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "recipientId": "usr_94821",
  "channel": "EMAIL",
  "destination": "ketanbisen610@gmail.com",
  "subject": "Account Security Alert",
  "message": "We detected a new login to your account from a Chrome browser on Windows."
}
```

**Response Codes & Payloads**:

| Status Code | Reason | Sample JSON Response |
|:---|:---|:---|
| **`202 Accepted`** | Request validated and placed on Kafka queue. | `{"status": "INGESTED", "transactionId": "...", "message": "Notification accepted and queued successfully."}` |
| **`200 OK`** | Request was already processed (Deduplicated). | `{"status": "DEDUPLICATED", "transactionId": "...", "message": "Duplicate transaction detected and skipped for delivery safety."}` |
| **`429 Too Many Requests`** | Recipient has exceeded rate limits. | `{"status": "RATE_LIMITED", "transactionId": "...", "message": "Rate limit exceeded. Maximum allowed messages reached for recipient."}` |
| **`400 Bad Request`** | Validation fails (e.g., missing fields). | `{"transactionId": "Transaction ID is required", ...}` |

---

## ⚡ Setup & Execution

### Zero-Setup Local Mock Mode (Highly Recommended for Quick Testing! 🚀)
If you do not have Docker installed, or do not want to run PostgreSQL, Redis, and Apache Kafka locally, you can run and test the complete API instantly using the **`mock` profile**.

This mode utilizes highly optimized, thread-safe, in-memory implementations to mimic real production tools:
* **In-Memory Database**: **H2 Database** configured with PostgreSQL compatibility mode, running database persistence and state transitions completely in local memory.
* **Rate-Limiting & Idempotency**: Pure Java implementations using thread-safe `ConcurrentHashMap` and atomic queue structures, fully mimicking sliding-window rate limits and lease lockouts.
* **Queue Decoupling (Mock Broker)**: An asynchronous background JVM thread executor pool that processes payloads asynchronously, preserving the exact HTTP non-blocking thread model.

To boot the application in mock mode instantly, run:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mock
```
Once booted, the server runs on **`http://localhost:8080`**. You can hit it with Postman, cURL, or the `k6` script!

---

### Production Profile (Docker Compose)
If you have Docker Desktop installed, you can spin up the full production cluster containing real PostgreSQL, Redis, and Kafka.

### Prerequisites
- Docker & Docker Compose
- Maven 3.8+ (to run locally outside Docker)
- Java 17

### 1. Build and Run the Entire Stack
The project includes a multi-stage `Dockerfile` and a ready-to-use `docker-compose.yml` to spin up PostgreSQL, Redis, Kafka, Zookeeper, and the Spring Boot application itself.

Run the following command in the project root:
```bash
docker-compose up --build -d
```

This will:
- Spin up PostgreSQL at `localhost:5432` (database: `notification_db`)
- Spin up Redis at `localhost:6379`
- Spin up ZooKeeper and Apache Kafka at `localhost:9092`
- Compile and start the Spring Boot Application at `localhost:8080`

### 2. Verify Infrastructure Status
Verify all services are healthy and running:
```bash
docker-compose ps
```
