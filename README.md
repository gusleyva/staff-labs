# Staff Engineering Lab - Latency, Saturation & Observability

A production-grade demonstration system built with **Java 21** and **Spring Boot 3.2+** to explore tail latency, resource saturation, and observability patterns.

## 🎯 Purpose

This lab is designed to:
- Demonstrate **tail latency** and how it impacts system performance
- Illustrate **resource saturation** (CPU, DB connections, I/O)
- Showcase **production-grade observability** with Prometheus, Tempo, and Grafana
- Provide a hands-on environment for SRE/performance engineering experiments

## 📋 Tech Stack

- **Language**: Java 21 with Virtual Threads
- **Framework**: Spring Boot 3.2.2
- **Build**: Gradle 8.5 (Kotlin DSL)
- **Database**: PostgreSQL 16
- **Metrics**: Micrometer + Prometheus
- **Tracing**: OpenTelemetry + Grafana Tempo
- **Logging**: Logback with JSON encoding (trace_id/span_id propagation)
- **Visualization**: Grafana with provisioned dashboards
- **Load Testing**: k6

## 🚀 Quick Start

### Prerequisites

- Java 21 (OpenJDK or Oracle)
- Docker & Docker Compose
- k6 (optional, for load testing)

### 1. Start Infrastructure

```bash
# Start PostgreSQL, Prometheus, Tempo, and Grafana
docker-compose up -d

# Verify all services are healthy
docker-compose ps
```

**Services:**
- PostgreSQL: `localhost:5432`
- Prometheus: `http://localhost:9091`
- Tempo: `http://localhost:3200`
- Grafana: `http://localhost:3000` (admin/admin)

### 2. Build & Run Application

```bash
# Build the application
./gradlew clean build

# Run the application
./gradlew bootRun
```

The API will be available at `http://localhost:8080`

### 3. Seed Database (Optional but Recommended)

```bash
# Seed 100,000 orders (takes ~10-15 seconds)
curl -X POST "http://localhost:8080/admin/seed?count=100000"

# For extreme latency experiments, seed 1 million orders
curl -X POST "http://localhost:8080/admin/seed?count=1000000"
```

### 4. Access Grafana Dashboard

1. Open `http://localhost:3000`
2. Login: `admin` / `admin`
3. Navigate to **Dashboards** → **Staff Labs - Latency & Saturation**

## 🔬 API Endpoints

### Database Performance Lab

**Slow Query** (GROUP BY on unindexed columns):
```bash
curl http://localhost:8080/api/db/search
```

This query performs a `GROUP BY` and aggregation on **unindexed** columns, simulating poor query design.

### CPU Saturation Lab

**CPU Burn** (blocks a CPU core):
```bash
# Burn CPU for 100ms
curl "http://localhost:8080/api/cpu?ms=100"

# Burn CPU for 500ms
curl "http://localhost:8080/api/cpu?ms=500"
```

Uses recursive Fibonacci to ensure real CPU work (not just busy-waiting).

### Downstream Simulation

**External Service Call** (with jitter and 10% failure rate):
```bash
curl http://localhost:8080/api/external
```

Simulates calling an unreliable external service with network jitter.

### Admin Operations

**Seed Data** (batch insert):
```bash
curl -X POST "http://localhost:8080/admin/seed?count=50000"
```

Uses `JdbcTemplate` batch updates for high-speed insertion.

## 📊 Load Testing

Run the k6 load test suite with 3 scenarios:

```bash
# Install k6 (macOS)
brew install k6

# Run the load test
k6 run load/k6-script.js
```

**Scenarios:**
1. **CPU Saturation** (30s warmup → 1m sustained → 30s cooldown)
2. **DB Saturation** (same pattern, starts after CPU test)
3. **Mixed Load** (realistic traffic, 10 → 100 RPS)

## 🔍 What to Observe

### 1. Tail Latency

- **Dashboard Panel**: "Latency Percentiles"
- **What to Look For**: 
  - Watch the **p95** and **p99** diverge from **p50**
  - During load tests, p99 can be 10-50x higher than p50
  - This is the "tail latency" problem

### 2. Resource Saturation

#### HikariCP Connection Pool
- **Dashboard Panel**: "HikariCP Connection Pool"
- **Resource Constraint**: Pool size = 5 connections
- **What to Look For**:
  - When `Active Connections` = 5, new requests queue
  - `Pending Connections` > 0 indicates saturation
  - Latency spikes when pool is exhausted

#### Thread Pool Saturation
- **Metrics**: `http_server_requests_seconds` latency increases
- **Resource Constraint**: Max threads = 20
- **What to Look For**:
  - When all 20 threads are busy, requests queue
  - CPU endpoint with high concurrency demonstrates this

### 3. Database I/O Bottleneck

- **Endpoint**: `GET /api/db/search`
- **Query Design**: GROUP BY on unindexed `status` and `customer_email`
- **What to Look For**:
  - Full table scans on large datasets
  - Latency grows linearly with data size
  - Watch logs: `org.hibernate.SQL` shows actual queries

### 4. Distributed Traces

1. Open Grafana → **Explore** → Select **Tempo** datasource
2. Search for traces from `staff-labs-api`
3. **What to Look For**:
   - Trace spans show time breakdown (DB vs. CPU vs. External)
   - `trace_id` in logs correlates to traces
   - Identify bottlenecks visually

### 5. Metrics in Prometheus

Open `http://localhost:9091` and query:

```promql
# Request rate
rate(http_server_requests_seconds_count{uri="/api/db/search"}[1m])

# p99 latency for DB endpoint
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{uri="/api/db/search"}[1m])) by (le))

# HikariCP active connections
hikaricp_connections_active{pool="StaffLabsHikariCP"}

# GC pause time
rate(jvm_gc_pause_seconds_sum[1m])
```

## 📈 Grafana Dashboard Panels

1. **Throughput (RPS)** - Requests per second
2. **Latency Percentiles** - p50, p95, p99 by endpoint
3. **Latency Heatmap** - Visual distribution of latencies
4. **HikariCP Connection Pool** - Active vs. Pending vs. Max
5. **JVM Garbage Collection** - GC pause times

## 🧪 Experiments to Try

### Experiment 1: Connection Pool Exhaustion
1. Seed 1M orders
2. Run: `for i in {1..10}; do curl http://localhost:8080/api/db/search & done`
3. Observe in Grafana: `Pending Connections` spike, latency increases

### Experiment 2: CPU Saturation
1. Run: `for i in {1..30}; do curl "http://localhost:8080/api/cpu?ms=500" & done`
2. Observe: Thread pool exhaustion, request queueing

### Experiment 3: Tail Latency Under Load
1. Run k6 mixed scenario: `k6 run load/k6-script.js`
2. Observe in Grafana: p99 latency diverges from p50

### Experiment 4: Trace a Slow Request
1. Make a slow request: `curl http://localhost:8080/api/db/search`
2. Find the `trace_id` in the response headers or logs
3. Search for it in Grafana Tempo
4. Analyze the span timeline

## 🏗️ Project Structure

```
staff-labs/
├── src/main/java/com/stafflabs/
│   ├── StaffLabsApplication.java
│   ├── config/
│   │   └── ObservabilityConfig.java
│   ├── controller/
│   │   ├── AdminController.java
│   │   ├── CpuController.java
│   │   ├── DatabaseController.java
│   │   └── ExternalController.java
│   ├── domain/
│   │   └── Order.java
│   ├── repository/
│   │   └── OrderRepository.java
│   └── service/
│       ├── CpuService.java
│       ├── ExternalService.java
│       └── OrderService.java
├── src/main/resources/
│   ├── application.yml
│   └── logback-spring.xml
├── infra/
│   ├── grafana/provisioning/
│   │   ├── dashboards/
│   │   │   ├── dashboards.yml
│   │   │   └── staff-labs-dashboard.json
│   │   └── datasources/
│   │       └── datasources.yml
│   ├── prometheus/
│   │   └── prometheus.yml
│   └── tempo/
│       └── tempo.yaml
├── load/
│   └── k6-script.js
├── build.gradle.kts
├── docker-compose.yml
└── README.md
```

## 🔧 Configuration

### Resource Constraints (Intentional)

| Resource | Limit | Purpose |
|----------|-------|---------|
| HikariCP Pool | 5 connections | Force connection pool exhaustion |
| Tomcat Threads | 20 threads | Demonstrate thread pool saturation |
| DB Query | No indexes on `status`, `customer_email` | Simulate slow queries |

### Observability Configuration

- **Metrics**: All `/api/**` endpoints have percentile histograms (p50, p95, p99)
- **Tracing**: 100% sampling rate (adjust in production)
- **Logging**: JSON format with `trace_id` and `span_id` propagation

## 🐛 Troubleshooting

### Application won't start
- Ensure PostgreSQL is running: `docker-compose ps`
- Check port 8080 is available: `lsof -i :8080`

### No metrics in Prometheus
- Verify actuator endpoint: `curl http://localhost:8080/actuator/prometheus`
- Check Prometheus targets: `http://localhost:9090/targets`

### No traces in Tempo
- Verify Tempo is running: `curl http://localhost:4318`
- Check application logs for OTLP export errors

### Dashboard not loading
- Ensure Grafana provisioning directory is mounted correctly
- Check Grafana logs: `docker-compose logs grafana`

## 📚 Learning Resources

- [Micrometer Documentation](https://micrometer.io/docs)
- [OpenTelemetry Java](https://opentelemetry.io/docs/instrumentation/java/)
- [HikariCP Configuration](https://github.com/brettwooldridge/HikariCP)
- [Tail Latency: The Silent Killer](https://www.youtube.com/watch?v=6_9YdM1W9lw)

## 📝 License

MIT License - Feel free to use this for learning and experimentation.

---

**Happy experimenting! 🚀**

For questions or improvements, open an issue or PR.
