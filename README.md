# Distributed Real-Time Chat System

A distributed, fault-tolerant chat backend built with Java and Spring Boot. The system decouples message ingestion from delivery using RabbitMQ and Redis Pub/Sub, persists all messages to PostgreSQL, and exposes analytics queries via a REST API.

---

## Architecture

```
WebSocket Clients
      │
      ▼
  Producer (port 8080)
  - Accepts WebSocket connections per room
  - Publishes to RabbitMQ exchange (topic: room.<roomId>)
  - Subscribes to Redis room:<roomId> and broadcasts to connected clients
      │
      ▼ RabbitMQ (per-room queues)
      │
      ▼
  Consumer (port 8081)
  - Batches messages from per-room queues
  - Publishes batch to Redis room:<roomId>
  - Writes messages to PostgreSQL asynchronously
      │
      ├──▶ Redis Pub/Sub (fan-out to all Producer instances)
      └──▶ PostgreSQL (persistent storage + analytics)
```

**Key design decisions:**
- Producer and Consumer are separate services — ingestion and delivery scale independently
- Redis Pub/Sub fan-out ensures all Producer instances (behind a load balancer) deliver messages to their locally connected WebSocket clients
- Circuit breakers (Resilience4J) protect RabbitMQ and Redis publish paths; NACK returns messages to queue on Redis failure
- Batch processing (size=40, flush every 60ms) reduces PostgreSQL write overhead

### Why Redis Pub/Sub?

Redis Pub/Sub was chosen for low-latency fan-out of live chat messages across Producer instances. Message durability is guaranteed by RabbitMQ (durable queues, persistent delivery) and PostgreSQL persistence — not by Redis itself.

If a Producer instance disconnects, messages published during downtime are not replayed to that instance. This is an accepted trade-off for live chat: users receive real-time delivery or nothing, and the full message history is available via the Metrics API from PostgreSQL.

For production deployments requiring replay and consumer group semantics, Redis Streams or Kafka would be stronger alternatives.

### Delivery Semantics

The system provides **at-least-once delivery**. Messages are ACKed to RabbitMQ only after a successful Redis publish. If the Consumer crashes after publishing to Redis but before writing to PostgreSQL, the message will be redelivered and processed again on restart. Duplicate messages are possible during failure recovery and would require idempotency key checks for exactly-once processing.

---

## Modules

| Module | Port | Description |
|---|---|---|
| `producer` | 8080 | WebSocket server, RabbitMQ publisher, Redis subscriber |
| `consumer-v3` | 8081 | RabbitMQ consumer, Redis publisher, PostgreSQL writer, Metrics API |
| `test-client` | — | Load test runner and statistics generator |

---

## Prerequisites

- Java 17
- Maven
- RabbitMQ (default: `localhost:5672`, guest/guest)
- Redis (default: `localhost:6379`)
- PostgreSQL (default: `localhost:5432`)

---

## Setup

### 1. Initialize the database

```bash
psql -U postgres -f database/schema.sql
```

This creates the `chatdb` database, the `messages` table, and supporting indexes.

### 2. Build all modules

```bash
mvn clean package -DskipTests
```

### 3. Start the Consumer

```bash
cd consumer-v3
mvn spring-boot:run
```

### 4. Start the Producer

```bash
cd producer
mvn spring-boot:run
```

Start multiple Producer instances behind a load balancer for horizontal scaling.

---

## Configuration

### Producer (`producer/src/main/resources/application.properties`)

| Property | Default | Description |
|---|---|---|
| `server.port` | 8080 | HTTP/WebSocket port |
| `rabbitmq.rooms` | 20 | Number of chat rooms (fixed for benchmarking; dynamic room creation is supported in production) |
| `rabbitmq.channel.pool.size` | 20 | RabbitMQ channel pool for publishing |
| `spring.data.redis.host` | localhost | Redis host |

### Consumer (`consumer-v3/src/main/resources/application.properties`)

| Property | Default | Description |
|---|---|---|
| `rabbitmq.consumer.threads` | 20 | AMQP consumer channels (one per room recommended) |
| `rabbitmq.consumer.prefetch` | 200 | Max unacked messages per channel |
| `db.writer.threads` | 8 | PostgreSQL writer thread pool |
| `db.writer.batch-size` | 500 | Messages per DB batch insert |
| `db.writer.flush-interval-ms` | 1000 | Max wait before flushing a partial DB batch |
| `redis.publish.enabled` | true | Set `false` to skip Redis fan-out (DB-only mode) |

---

## Metrics API

The Consumer exposes a REST API on port 8081:

| Method | Endpoint | Description |
|---|---|---|
| GET | `/metrics` | Full analytics report (top users, top rooms, message stats) |
| GET | `/metrics/room/{roomId}?startTime=&endTime=` | Messages for a room in a time range |
| GET | `/metrics/user/{userId}/history` | Message history for a user |
| GET | `/metrics/active-users?startTime=&endTime=` | Unique active user count in a time window |
| GET | `/metrics/user/{userId}/rooms` | Rooms a user has participated in |
| DELETE | `/metrics/reset` | Truncate messages table and clear caches (use before each load test) |
| GET | `/health` | Health check |

---

## Load Testing

The `test-client` module includes three test scenarios:

| Test | File | Description |
|---|---|---|
| Baseline | `LoadTest.java` | Standard throughput test |
| Stress | `LoadTest.java` | High-concurrency stress test |
| Endurance | `EnduranceTest.java` | Sustained load over time |

Results are saved to the `load-tests/` directory. Reports include p50/p95/p99 latency breakdowns tracked with JMeter.

### Performance Results

**Endurance Test — 30 minutes at target 10,000 msg/sec**

| Metric | Result |
|---|---|
| Target throughput | 10,000 msg/sec |
| Sustained send rate | ~9,400–9,510 msg/sec |
| Total messages sent | 16,815,950 |
| DB write throughput (steady state) | ~9,400–9,650 msg/sec |
| DB write p50 latency | 7–12 ms |
| DB write p95 latency | 32–74 ms |
| DB write p99 latency | 42–171 ms |
| Queue depth (steady state) | 0 (consumer keeps up) |

The Consumer pipeline sustains ~9,500 msg/sec DB writes with zero queue backlog under steady-state load. Transient p99 spikes to ~735 ms occurred during burst startup as the DB writer warmed up; steady-state p99 dropped to under 50 ms.

**Reset before each run:**

```bash
# 1. Truncate PostgreSQL messages table and clear Caffeine caches
curl -X DELETE http://localhost:8081/metrics/reset

# 2. Purge all RabbitMQ queues (room.1 through room.20)
for i in $(seq 1 20); do
  rabbitmqadmin purge queue name=room.$i
done
```

`rabbitmqadmin` is bundled with RabbitMQ. When running via Docker Compose, exec into the container:

```bash
docker exec -it <rabbitmq-container> sh -c \
  'for i in $(seq 1 20); do rabbitmqadmin purge queue name=room.$i; done'
```

This ensures no leftover messages from a previous run skew throughput or latency measurements.

---

## Circuit Breakers

Resilience4J circuit breakers protect both publish paths:

| Breaker | Protects | Fallback behavior |
|---|---|---|
| `rabbitmq-publish` | Producer → RabbitMQ | Message dropped, error logged; prioritizes service availability over durability |
| `redis-publish` | Consumer → Redis | RuntimeException thrown → RabbitMQ NACK (message requeued until Redis recovers) |
| `db-write` | Consumer → PostgreSQL | Write failure logged; message may be lost if buffer capacity is exceeded |

All breakers: 10-call sliding window, 50% failure threshold, 30s open duration, 3 half-open probe calls.

The current design prioritizes service availability during downstream failures. The DB writer implements exponential backoff retries (up to `db.writer.max-retries`) and routes permanently failed batches to a dead letter log (`[DEAD LETTER]` entries with message IDs, room, and user). Production hardening would extend this with:
- RabbitMQ Dead Letter Exchange (DLX) to requeue failed messages for later replay
- Outbox pattern for transactional DB + RabbitMQ publish

---

## Local Development (Docker Compose)

Spin up all services locally with a single command:

```bash
docker-compose up --build
```

This starts RabbitMQ, Redis, PostgreSQL, Producer, and Consumer with health-check ordering. PostgreSQL is initialized from `database/schema.sql` automatically.

| Service | Local URL |
|---|---|
| Producer (WebSocket) | `ws://localhost:8080/ws` |
| Consumer (Metrics API) | `http://localhost:8081/metrics` |
| RabbitMQ Management UI | `http://localhost:15672` (guest/guest) |

---

## AWS Deployment (EKS + ALB)

### AWS Infrastructure

| Component | AWS Service |
|---|---|
| Producer / Consumer | Kubernetes Deployments on Amazon EKS |
| Load Balancer | Application Load Balancer (AWS ALB Ingress Controller) |
| Container Registry | Amazon ECR |
| Database | Amazon RDS (PostgreSQL) |
| Cache | Amazon ElastiCache (Redis) |
| Message Broker | Self-managed RabbitMQ on EC2 |

### 1. Build and push images to ECR

```bash
# Authenticate
aws ecr get-login-password --region us-west-2 | \
docker login --username AWS --password-stdin <YOUR_ECR_URI>

# Build and push producer
docker build -t producer -f producer/Dockerfile .
docker tag producer:latest <YOUR_ECR_URI>/producer:latest
docker push <YOUR_ECR_URI>/producer:latest

# Build and push consumer
docker build -t consumer -f consumer-v3/Dockerfile .
docker tag consumer:latest <YOUR_ECR_URI>/consumer:latest
docker push <YOUR_ECR_URI>/consumer:latest
```

### 2. Configure secrets

Edit `k8s/secrets.yml` with your RDS, ElastiCache, and RabbitMQ endpoints, then apply:

```bash
kubectl apply -f k8s/secrets.yml
```

### 3. Deploy to EKS

```bash
# Update image URIs in k8s/producer-deployment.yml and k8s/consumer-deployment.yml first
kubectl apply -f k8s/producer-deployment.yml
kubectl apply -f k8s/consumer-deployment.yml
kubectl apply -f k8s/alb-ingress.yml
```

### 4. Verify

```bash
kubectl get pods
kubectl get ingress chat-ingress   # shows ALB DNS name
```

### Scaling

Scale Producer horizontally — each instance subscribes to Redis Pub/Sub independently, so all instances receive and broadcast messages to their local WebSocket clients:

```bash
kubectl scale deployment producer --replicas=4
```

Consumer scaling requires partitioning rooms across instances (update `rabbitmq.rooms.start` and `rabbitmq.rooms.end` per Consumer deployment to avoid duplicate processing).

### Key ALB Configuration

The ALB ingress (`k8s/alb-ingress.yml`) sets `idle_timeout=3600s` to keep WebSocket connections alive. Without this, the default 60s ALB timeout drops long-lived WebSocket sessions.

---

## Tech Stack

- **Java 17**, Spring Boot 3.4
- **RabbitMQ** (AMQP 5.21) — message queue with per-room topic routing
- **Redis** — Pub/Sub fan-out across Producer instances
- **PostgreSQL** — persistent message storage with analytics indexes
- **Resilience4J** — circuit breakers on RabbitMQ and Redis publish paths
- **Caffeine** — in-process cache for analytics queries
- **JMeter** — latency profiling (p50/p95/p99)
