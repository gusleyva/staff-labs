# Resilience in Depth: Staff Labs Strategy

## Overview
This document outlines the "Defense in Depth" strategy implemented for the `staff-labs-api` to handle downstream instability in the `external-service`. We utilize **Resilience4j** to implement a combination of resilience patterns.

## Patterns Implemented

### 1. Circuit Breaker
**Purpose**: Fail fast when the downstream service is unhealthy to prevent resource exhaustion and waiting for timeouts.
**Configuration**:
- **Sliding Window**: 10 calls.
- **Failure Threshold**: 50% failure rate triggers OPEN state.
- **Slow Call Threshold**: 20% of calls taking > 2s triggers OPEN state.
- **Wait Duration**: 5s in OPEN state before attempting HALF-OPEN.

### 2. Retries with Intelligence
**Purpose**: Recover from transient failures (e.g., network blips).
**Key Mechanism**: **Exponential Backoff with Jitter**.
- **Backoff**: Waiting longer between attempts (100ms * 2^n) reduces load on a struggling service.
- **Jitter**: Adding random noise to the wait time prevents the **"Thundering Herd"** problem, where many clients retry exactly at the same time, overwhelming the recovering service again.

### 3. Bulkhead (Isolation)
**Purpose**: Limit the number of concurrent calls to the external API.
**Mechanism**: `SemaphoreBulkhead` with max 3 concurrent calls.
**Why needed**: Even with a Circuit Breaker, a sudden spike in traffic during the "closed" state could saturate all Tomcat threads if the external service slows down. The Bulkhead ensures that `externalService` can never consume more than 3 threads, leaving the rest of the application responsive.

**Bulkhead vs. Circuit Breaker**:
- **Circuit Breaker**: Stops calls based on *recent failure history* (Health based). Global protection.
- **Bulkhead**: Stops calls based on *current concurrency* (Capacity based). Isolation protection.

### 4. Rate Limiter
**Purpose**: Enforce a contract or quota.
**Configuration**: 50 requests per minute.
**Use Case**: B2B limits or protecting a very expensive resource.

## Monitoring
We monitor the state of these components via Prometheus and Grafana:
- `resilience4j_circuitbreaker_state`: Tracks Closed/Open/Half-Open state.
- `resilience4j_retry_calls_total`: Success vs. Failed retries.
- `resilience4j_bulkhead_available_concurrent_calls`: Thread saturation.

## Verification & Testing

### 1. Start the Application
```bash
./gradlew bootRun
```

### 2. Run the Load Test
We have prepared a k6 script that:
- Configures the Mock to have a **60% failure rate**.
- Stress tests the endpoint.
- Resets the Mock after the test.

```bash
k6 run load/k6-week2-stress.js
```

**Note**: The k6 script automatically calls the admin endpoint to set the failure rate. You can also do this manually:
```bash
curl -X POST http://localhost:8080/admin/mock/configure \
  -H "Content-Type: application/json" \
  -d '{"failureRate": 0.6, "delayMs": 100}'
```

### 3. What to Look For

#### A. The "Fail Fast" Effect (k6 Output)
- **Initial Phase**: `http_req_duration` may spike (2000ms+) as requests timeout or retry.
- **Circuit Breaker Open**: After ~10 failures, `http_req_duration` drops to **~0ms**.
    - *Why?* The system stops calling the failing service and returns the fallback immediately.

#### B. Graceful Degradation (Logs)
- Check logs for state transition:
  ```text
  CircuitBreaker 'externalService' changed state from CLOSED to OPEN
  ```
- Fallback activation:
  ```text
  External service fallback triggered. Reason: ...
  ```

#### C. Half-Open / Self-Healing
- After **5 seconds** (waitDurationInOpenState), the Circuit Breaker moves to **HALF_OPEN**.
- It allows a few requests to check if the downstream service has recovered.
- If successful, it closes. If failing, it re-opens.

```
k6 run load/k6-week2-stress.js                 ✔  1316  20:26:57

         /\      Grafana   /‾‾/  
    /\  /  \     |\  __   /  /   
   /  \/    \    | |/ /  /   ‾‾\ 
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/ 

     execution: local
        script: load/k6-week2-stress.js
        output: -

     scenarios: (100.00%) 1 scenario, 10 max VUs, 2m30s max duration (incl. graceful stop):
              * default: Up to 10 looping VUs for 2m0s over 3 stages (gracefulRampDown: 30s, gracefulStop: 30s)

INFO[0000] Mock configured with high failure rate        source=console
INFO[0120] Mock reset to default configuration           source=console


  █ THRESHOLDS 

    http_req_duration
    ✓ 'p(95)<3000' p(95)=128.98ms


  █ TOTAL RESULTS 

    checks_total.......: 1306   10.862568/s
    checks_succeeded...: 97.70% 1276 out of 1306
    checks_failed......: 2.29%  30 out of 1306

    ✓ status is 200
    ✗ is fallback
      ↳  95% — ✓ 623 / ✗ 30

    HTTP
    http_req_duration..............: avg=15.31ms min=980µs med=2.08ms max=205.05ms p(90)=44.83ms p(95)=128.98ms
      { expected_response:true }...: avg=15.31ms min=980µs med=2.08ms max=205.05ms p(90)=44.83ms p(95)=128.98ms
    http_req_failed................: 0.00%  0 out of 655
    http_reqs......................: 655    5.447919/s

    EXECUTION
    iteration_duration.............: avg=1.01s   min=1s    med=1s     max=1.2s     p(90)=1.04s   p(95)=1.13s   
    iterations.....................: 653    5.431284/s
    vus............................: 1      min=1        max=10
    vus_max........................: 10     min=10       max=10

    NETWORK
    data_received..................: 142 kB 1.2 kB/s
    data_sent......................: 54 kB  448 B/s
```


### 4. Observability & Metrics (Grafana)

1.  **Start Infrastructure** (if not running):
    ```bash
    docker compose up -d
    ```

2.  **Access Grafana**:
    - URL: http://localhost:3000
    - Credentials: `admin` / `admin`

3.  **Import Dashboard**:
    - Go to **Dashboards -> New -> Import**.
    - Upload (or copy content of): `grafana/resilience-dashboard.json`.

4.  **Visualize**:
    - **Circuit Breaker State**: Watch the line jump from `0` (Closed) to `1` (Open) during the test.
    - **Retries**: See the spike in retry attempts.
    - **Bulkhead**: If you increase load (more VUs), watch saturation.

5.  **Prometheus Direct Access**:
    - URL: http://localhost:9091
    - Query: `resilience4j_circuitbreaker_state`
