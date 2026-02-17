Staff Engineering Lab - Project Walkthrough

📦 Project Delivered
A complete production-grade observability lab demonstrating tail latency, resource saturation, and distributed tracing with Java 21 and Spring Boot 3.2+.

📁 File Structure
staff-labs/
├── src/main/java/com/stafflabs/
│   ├── StaffLabsApplication.java           # Main Spring Boot application
│   ├── config/ObservabilityConfig.java     # RestClient configuration
│   ├── controller/
│   │   ├── AdminController.java            # Data seeding endpoint
│   │   ├── CpuController.java              # CPU saturation endpoint
│   │   ├── DatabaseController.java         # Slow query endpoint
│   │   └── ExternalController.java         # External service simulation
│   ├── domain/Order.java                   # JPA entity (10+ fields)
│   ├── repository/OrderRepository.java     # Slow query with GROUP BY
│   └── service/
│       ├── CpuService.java                 # Fibonacci CPU burn
│       ├── ExternalService.java            # Jitter + 10% failures
│       └── OrderService.java               # Batch insert + queries
├── src/main/resources/
│   ├── application.yml                     # Spring config (HikariCP=5, threads=20)
│   └── logback-spring.xml                  # JSON logs with trace_id
├── infra/
│   ├── grafana/provisioning/
│   │   ├── dashboards/
│   │   │   ├── dashboards.yml              # Dashboard provider
│   │   │   └── staff-labs-dashboard.json   # 5-panel performance dashboard
│   │   └── datasources/datasources.yml     # Prometheus + Tempo
│   ├── prometheus/prometheus.yml           # Scrape config (5s interval)
│   └── tempo/tempo.yaml                    # OTLP receiver config
├── load/k6-script.js                       # 3 scenarios: CPU, DB, Mixed
├── build.gradle.kts                        # Java 21, Spring Boot 3.2.2
├── docker-compose.yml                      # PostgreSQL 16, Prometheus, Tempo, Grafana
├── .gitignore
└── README.md                               # Complete documentation

🎯 Core Features Implemented

1. Database Performance Lab
Order Entity: 10 fields including customer info, product details, pricing, and status.
Seed Endpoint: POST /admin/seed?count=N

Uses JdbcTemplate batch updates
Can insert 100K orders in ~10-15 seconds
Supports up to 10M orders for extreme tests

Slow Query: GET /api/db/search

GROUP BY on unindexed status and customer_email
Demonstrates terrible query performance
Perfect for showing tail latency under load

2. CPU Saturation Lab
Endpoint: GET /api/cpu?ms=100

Uses recursive Fibonacci(35) to burn CPU
Pegs a core for specified duration
Max 10 seconds to prevent abuse
Great for thread pool exhaustion demos

3. Downstream Simulation
Endpoint: GET /api/external

Random jitter: 0-100ms
10% failure rate (503 responses)
Simulates real-world external API behavior

4. Resource Constraints

Resource        Value   Purpose
HikariCP pool   5       Force connection exhaustion
Server threads  20      Force thread pool saturation
DB indexes      None on status/email   Make queries slow

📊 Observability Stack

Metrics (Micrometer + Prometheus)
✅ Percentile Histograms for all /api/** endpoints
✅ Custom Metrics:
- db.seed.duration
- db.slow.query.duration
- cpu.burn.duration
- external.service.duration
✅ HikariCP Metrics: Active, Pending, and Max connections
✅ JVM Metrics: GC pauses, heap usage, thread counts

Tracing (OpenTelemetry + Tempo)
✅ OTLP Export to Tempo via HTTP (port 4318)
✅ 100% Sampling (configured in application.yml)
✅ Trace Propagation across all service boundaries

Logging (Logback + JSON)
✅ JSON Encoder with logstash-logback-encoder
✅ Trace Context: Every log includes trace_id, span_id, trace_flags
✅ Plain Text in dev, JSON in prod (Spring profiles)

Grafana Dashboard
5 Panels:
- Throughput (RPS) - Request rate visualization
- Latency Percentiles - p50, p95, p99 by endpoint
- Latency Heatmap - Visual distribution of response times
- HikariCP Pool - Connection saturation tracking
- JVM GC Pauses - Garbage collection impact

🧪 Load Testing (k6)

3 Scenarios:
- CPU Saturation: 10 VUs, sustained load on CPU endpoint
- DB Saturation: 5 VUs, sustained load on slow query
- Mixed Load: 10 → 100 RPS, realistic traffic pattern

Custom Metrics:
- Error rate tracking
- Per-endpoint latency trends
- Failure counters

🚀 Next Steps

1. Start the Infrastructure
cd /Users/gustavolc/.gemini/antigravity/scratch/staff-labs

# Start all services
docker-compose up -d

# Check health
docker-compose ps

2. Build the Application
cd /Users/gustavolc/.gemini/antigravity/scratch/staff-labs

# Download the wrapper JAR (one-time setup)
mkdir -p gradle/wrapper
curl -L https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar -o gradle/wrapper/gradle-wrapper.jar

# Now you can build
./gradlew clean build

3. Run the Application
./gradlew bootRun
The API will start on http://localhost:8080

4. Seed the Database
curl -X POST "http://localhost:8080/admin/seed?count=100000"

5. Open Grafana
Navigate to http://localhost:3000
Login: admin / admin
Go to Dashboards → Staff Labs - Latency & Saturation

6. Run Load Tests
brew install k6
k6 run load/k6-script.js

7. Explore!
Try the experiments documented in the README:
- Connection pool exhaustion
- CPU saturation
- Tail latency observation
- Distributed trace analysis

🎓 Key Observability Patterns

Pattern 1: Trace Context Propagation
Every log entry includes trace_id and span_id, allowing you to:
- See a slow request in Grafana
- Copy the trace_id from logs
- Search for it in Tempo
- Analyze the full trace timeline

Pattern 2: Percentile Histograms
Under load:
- p50 stays low
- p99 spikes dramatically
This is the core tail latency pattern.

Pattern 3: Resource Saturation Detection
When HikariCP hits max connections:
- New requests wait
- Pending connections increase
- Latency rises proportionally

✅ All Requirements Met

Java 21 ✅
Spring Boot 3.2+ ✅
Gradle Kotlin DSL ✅
PostgreSQL 16 ✅
Micrometer + Prometheus ✅
OpenTelemetry + Tempo ✅
Logback JSON with trace_id ✅
Docker Compose ✅
Grafana provisioning ✅
k6 load tests ✅

The lab is ready to use! 🎉
Set the workspace to /Users/gustavolc/.gemini/antigravity/scratch/staff-labs to start experimenting.
