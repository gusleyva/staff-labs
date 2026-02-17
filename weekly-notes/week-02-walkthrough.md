# Resilience and Observability Guide

This guide answers your questions regarding the "Defense in Depth" strategy implemented in the `staff-labs-api`.

## 1. Toggling Resilience Protection

The resilience patterns (Circuit Breaker, Retry, Bulkhead, Rate Limiter) are implemented using **Resilience4j annotations** in the `ExternalService` class.

### How to Toggle OFF
```bash
POST /admin/resilience/disable
```

### How to Toggle ON
```bash
POST /admin/resilience/enable
```

---

## 2. Proper Execution Steps

Follow these steps to run the full environment:

### Step 1: Start Infrastructure
Ensure Docker is running, then execute:
```bash
docker-compose up -d
```
This starts **PostgreSQL**, **Prometheus**, **Tempo**, and **Grafana**.

### Step 2: Build and Run the API
```bash
./gradlew clean build  
./gradlew bootRun
```
The API will be available at `http://localhost:8080`.

---

## 3. Endpoints & Observability

### Key Endpoints
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/admin/resilience/toggle?enabled=true|false` | `POST` | Toggle resilience protection. |
| `/admin/resilience/status` | `GET` | Resilience current state. |
| `/admin/resilience/enable` | `POST` | Enable resilience protection. |
| `/admin/resilience/disable` | `POST` | Disable resilience protection. |
| `/api/external` | `GET` | Main endpoint protected by resilience patterns. |
| `/admin/mock/configure` | `POST` | Configure failure rate and latency (Chaos Injection). |
| `/admin/seed` | `POST` | Seed the database with sample data. |

### How to Monitor Patterns

#### Logs from Grafana
Access Grafana at [http://localhost:3000](http://localhost:3000) (User: `admin` / Pass: `admin`).
* Go to Dashboards → Application Logs
* Use the log_level dropdown to filter by level
* Click on any trace ID in the logs to jump to the trace in Tempo

#### Grafana
Access Grafana at [http://localhost:3000](http://localhost:3000) (User: `admin` / Pass: `admin`).
1. Navigate to **Dashboards**.
2. Open the **Resilience Dashboard** (Imported from `grafana/resilience-dashboard.json`).
3. You will see real-time panels for:
   - **Circuit Breaker State Transitions**
   - **Retry Rates**
   - **Bulkhead Saturation**

#### Prometheus
Access the Prometheus UI at [http://localhost:9091](http://localhost:9091).
Use these queries to check transitions:
- **Circuit Breaker State**: `resilience4j_circuitbreaker_state` (0=Closed, 1=Open, 2=Half-Open)
- **Retry Success/Failure**: `resilience4j_retry_calls_total`

---
 
 ## Testing Resilience toggle
 
 ### How to verify the Toggle is working
 
 1. **Check the Response Body**:
    - **Enabled**: When a call fails, you see `"Graceful Degradation: Cached Response (Service Unavailable)"` (string response).
    - **Disabled**: When a call fails, you see a JSON error response like `{"status":"error","message":"..."}` (exception bubbles up to controller).
 
 2. **Check Prometheus Metrics**:
    - **Enabled**: `resilience4j_circuitbreaker_calls_seconds_count` WILL increment on every call.
    - **Disabled**: `resilience4j_circuitbreaker_calls_seconds_count` WILL NOT increment (aspects are bypassed).
 
 3. **Check Application Logs**:
    - You should see `Resilience is Disabled - BYPASSING aspects...` in the console/Loki logs when the toggle is off.
 
 ### Scenario 1: Compare Behavior With/Without Resilience
 1. Set high failure rate
 ```bash
 curl -X POST "http://localhost:8080/admin/mock/config?failureRate=0.8&delayMs=100"
 ```
 
 2. Test with resilience ENABLED
 ```bash
 # Enable resilience
 curl -X POST "http://localhost:8080/admin/resilience/enable"

 # Make requests
 for i in {1..20}; do
   curl -s http://localhost:8080/api/external | jq -r '.response // .message'
 done

 # Search for logs 
 You should see event state transition and circuit breaker log messages in your application logs
 
 # Check metrics - should see circuit breaker, retry activity
 curl http://localhost:8080/actuator/prometheus | grep resilience4j
 ```
 
 3. Test with resilience DISABLED
 ```bash
 # Disable resilience
 curl -X POST "http://localhost:8080/admin/resilience/disable"
 
 # Make requests - expect JSON error responses for failures
 for i in {1..20}; do
   curl -s http://localhost:8080/api/external | jq -r '.response // .message'
 done
 
 # Search for logs 
 You should see the BYPASSING log message in your application logs
 
 # Check metrics - resilience4j metrics should NOT have increased from previous values
 curl http://localhost:8080/actuator/prometheus | grep resilience4j
 ```
 
 ### Scenario 2: Observe in Grafana
 1. Enable resilience and generate load
 2. Open Grafana → Application Logs dashboard
 3. Filter for resilience events
 4. Disable resilience via API
 5. Observe the change in log patterns (no more retry/fallback logs)
 
 ### Scenario 3: Load testing with toggle
 ```bash
 # Install k6 if not already installed
 brew install k6
 
 # Run load test with resilience enabled
 curl -X POST "http://localhost:8080/admin/resilience/enable"
 k6 run load/k6-week2-stress.js
 
 # Run load test with resilience disabled
 curl -X POST "http://localhost:8080/admin/resilience/disable"
 k6 run load/k6-week2-stress.js
 
 # Compare metrics in Grafana
 ```

## Troubleshooting

### Logs Not Appearing in Loki
Check Loki is running:
```bash
docker ps | grep loki
curl http://localhost:3100/ready
```

Check application can reach Loki:
```bash
# From inside the app container (if dockerized)
curl http://loki:3100/ready

# From host (if app runs locally)
curl http://localhost:3100/ready
```

Check Logback configuration:
* Verify logback-spring.xml has the LOKI appender
* Check application logs for Loki connection errors

### Resilience Toggle Not Working
Verify aspect is loaded:
```bash
# Check application logs for aspect registration
grep -i "ResilienceToggleAspect" logs/application.log

```

Check aspect order:
* Aspect must have @Order(Ordered.HIGHEST_PRECEDENCE)
* Verify it's executing before Resilience4j aspects

Test with metrics:
```bash
# Should see resilience.toggle.* metrics
curl http://localhost:8080/actuator/prometheus | grep resilience.toggle

```