# Circuit Breaker Testing Guide

## 🔍 Current State Analysis

**Problem Identified**: The resilience annotations in [ExternalService.java](file:///Users/gustavolc/.gemini/antigravity/scratch/staff-labs/src/main/java/com/stafflabs/service/ExternalService.java#L26-L33) are **commented out**, which means:
- ❌ Circuit Breaker is **NOT active**
- ❌ Retry logic is **NOT active**
- ❌ Bulkhead is **NOT active**
- ❌ Rate Limiter is **NOT active**

**Important**: [MockConfig.java](file:///Users/gustavolc/.gemini/antigravity/scratch/staff-labs/src/main/java/com/stafflabs/config/MockConfig.java) should **NOT** be commented. This file controls failure injection for testing.

---

## 📊 Scenario Comparison

### Scenario 1: WITHOUT Protection (Current State - Annotations Commented)

#### What Happens:
1. **No Circuit Breaker** - Every request hits the external service, even when it's failing
2. **No Retries** - Failed requests fail immediately without retry attempts
3. **No Bulkhead** - All threads can be consumed by slow external calls
4. **No Rate Limiting** - Unlimited requests allowed

#### Expected Behavior Under Load:
```bash
# Configure high failure rate
curl -X POST http://localhost:8080/admin/mock/configure \
  -H "Content-Type: application/json" \
  -d '{"failureRate": 0.6, "delayMs": 500}'

# Run load test
k6 run load/k6-week2-stress.js
```

**What You'll See:**
- ⚠️ **High latency** - Every request waits for timeout (500ms delay)
- ⚠️ **60% failure rate** - Requests fail without recovery
- ⚠️ **Thread exhaustion** - Application becomes unresponsive
- ⚠️ **Cascading failures** - Other endpoints (`/api/cpu`, `/api/db`) slow down
- ⚠️ **No fallback** - Users see error messages

**Grafana Metrics:**
- Circuit Breaker State: **No data** (metric doesn't exist without annotation)
- Retry Calls: **No data**
- Bulkhead: **No data**
- Request latency: **Consistently high** (p95 > 500ms)

**Logs:**
```
External service call failed (simulated)
External service call failed (simulated)
External service call failed (simulated)
```

---

### Scenario 2: WITH Protection (Annotations Active)

#### Step 1: Uncomment the Annotations

Edit [ExternalService.java](file:///Users/gustavolc/.gemini/antigravity/scratch/staff-labs/src/main/java/com/stafflabs/service/ExternalService.java#L26-L33):

```java
@io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "externalService", fallbackMethod = "fallback")
@io.github.resilience4j.retry.annotation.Retry(name = "externalService")
@io.github.resilience4j.bulkhead.annotation.Bulkhead(name = "externalService", type = io.github.resilience4j.bulkhead.annotation.Bulkhead.Type.SEMAPHORE)
@io.github.resilience4j.ratelimiter.annotation.RateLimiter(name = "externalService")
public String callExternalService() {
```

#### Step 2: Restart the Application
```bash
# Stop current application (Ctrl+C)
./gradlew bootRun
```

#### Step 3: Run the Same Test
```bash
# Configure high failure rate
curl -X POST http://localhost:8080/admin/mock/configure \
  -H "Content-Type: application/json" \
  -d '{"failureRate": 0.6, "delayMs": 500}'

# Run load test
k6 run load/k6-week2-stress.js
```

**What You'll See:**
- ✅ **Initial Phase** (First ~10 requests):
  - Retries kick in (3 attempts per request)
  - Some requests succeed, some fail
  - Latency is high but retries help

- ✅ **Circuit Opens** (After 50% failure rate):
  - Circuit Breaker transitions: `CLOSED → OPEN`
  - **Fallback activates immediately**
  - Latency drops to **~0-2ms** (no external call)
  - Users get: `"Graceful Degradation: Cached Response (Service Unavailable)"`

- ✅ **Self-Healing** (After 5 seconds):
  - Circuit Breaker: `OPEN → HALF_OPEN`
  - Allows 3 test requests
  - If still failing: `HALF_OPEN → OPEN`
  - If recovered: `HALF_OPEN → CLOSED`

**Grafana Metrics:**
- **Circuit Breaker State**: 
  - `0` (Closed) → `1` (Open) → `2` (Half-Open) → back to `0` or `1`
- **Retry Calls Rate**: Shows spike in retry attempts
- **Bulkhead Saturation**: Max 3 concurrent calls, others fast-fail
- **Request Latency**: 
  - Initial spike (500ms+)
  - **Drops dramatically** when circuit opens (~2ms)

**Logs:**
```
External service call failed (simulated)
External service call failed (simulated)
CircuitBreaker 'externalService' changed state from CLOSED to OPEN
External service fallback triggered. Reason: CircuitBreaker 'externalService' is OPEN
External service fallback triggered. Reason: CircuitBreaker 'externalService' is OPEN
```

---

## 🧪 Step-by-Step Testing Instructions

### Test 1: Verify Current State (Without Protection)

1. **Check Prometheus** - [http://localhost:9091](http://localhost:9091)
   ```promql
   resilience4j_circuitbreaker_state{name="externalService"}
   ```
   **Expected**: No data (metric doesn't exist)

2. **Hit the endpoint**:
   ```bash
   curl http://localhost:8080/api/external
   ```
   **Expected**: Either success or error (no fallback message)

3. **Configure failures**:
   ```bash
   curl -X POST http://localhost:8080/admin/mock/configure \
     -H "Content-Type: application/json" \
     -d '{"failureRate": 0.8, "delayMs": 1000}'
   ```

4. **Hit endpoint multiple times**:
   ```bash
   for i in {1..20}; do curl http://localhost:8080/api/external; echo ""; done
   ```
   **Expected**: ~80% failures, each takes 1+ second, no fallback

---

### Test 2: Enable Protection and Test Circuit Breaker

1. **Uncomment annotations** in [ExternalService.java](file:///Users/gustavolc/.gemini/antigravity/scratch/staff-labs/src/main/java/com/stafflabs/service/ExternalService.java#L26-L33)

2. **Restart application**:
   ```bash
   ./gradlew bootRun
   ```

3. **Verify metrics exist** - [http://localhost:9091](http://localhost:9091)
   ```promql
   resilience4j_circuitbreaker_state{name="externalService"}
   ```
   **Expected**: Returns `0` (CLOSED state)

4. **Configure high failure rate**:
   ```bash
   curl -X POST http://localhost:8080/admin/mock/configure \
     -H "Content-Type: application/json" \
     -d '{"failureRate": 0.7, "delayMs": 100}'
   ```

5. **Trigger circuit breaker** (need at least 5 calls per config):
   ```bash
   for i in {1..15}; do 
     echo "Request $i:"
     curl http://localhost:8080/api/external
     echo ""
     sleep 0.5
   done
   ```

6. **Watch Grafana Dashboard** - [http://localhost:3000](http://localhost:3000)
   - Open **Resilience Dashboard**
   - Watch **Circuit Breaker State** panel
   - You should see the line jump from `0` to `1`

7. **Check Prometheus**:
   ```promql
   resilience4j_circuitbreaker_state{name="externalService"}
   ```
   **Expected**: Returns `1` (OPEN state)

8. **Verify fast failures**:
   ```bash
   time curl http://localhost:8080/api/external
   ```
   **Expected**: 
   - Response: `"Graceful Degradation: Cached Response (Service Unavailable)"`
   - Time: < 10ms (instant fallback)

9. **Wait 5 seconds and check again**:
   ```bash
   sleep 5
   # Check Prometheus - should show state = 2 (HALF_OPEN)
   curl http://localhost:8080/api/external
   ```

10. **Reset to normal**:
    ```bash
    curl -X POST http://localhost:8080/admin/mock/configure \
      -H "Content-Type: application/json" \
      -d '{"failureRate": 0.1, "delayMs": 50}'
    ```

---

## 📈 Key Metrics to Monitor

### Prometheus Queries

| Metric | Query | What to Look For |
|--------|-------|------------------|
| Circuit State | `resilience4j_circuitbreaker_state{name="externalService"}` | 0=Closed, 1=Open, 2=Half-Open |
| Retry Success | `rate(resilience4j_retry_calls_total{name="externalService",kind="successful_with_retry"}[1m])` | Spike during failures |
| Retry Failed | `rate(resilience4j_retry_calls_total{name="externalService",kind="failed_with_retry"}[1m])` | High when circuit opens |
| Bulkhead Available | `resilience4j_bulkhead_available_concurrent_calls{name="externalService"}` | Should stay >= 0 |
| Fallback Counter | `external_service_fallback_total` | Increments when circuit is open |

---

## ✅ Success Criteria

### Without Protection (Annotations Commented):
- [ ] No resilience metrics in Prometheus
- [ ] High latency under failure (matches configured delay)
- [ ] No fallback responses
- [ ] Application becomes unresponsive under load

### With Protection (Annotations Active):
- [x] Circuit breaker state visible in Prometheus
- [x] State transitions: CLOSED → OPEN → HALF_OPEN
- [x] Fallback responses when circuit is OPEN
- [x] Latency drops dramatically when circuit opens
- [x] Application remains responsive for other endpoints
- [x] Self-healing behavior after wait duration

---

## 🎯 Expected Timeline

```
Time    | Circuit State | Behavior
--------|---------------|------------------------------------------
0-10s   | CLOSED        | Normal operation, retries on failures
10-15s  | CLOSED        | 50% failure threshold reached
15s     | OPEN          | Circuit opens, fallback activates
15-20s  | OPEN          | All requests return fallback instantly
20s     | HALF_OPEN     | Allows 3 test requests
20-25s  | OPEN/CLOSED   | Re-opens if still failing, closes if healthy
```

---

## 🐛 Troubleshooting

**Circuit breaker never opens:**
- Check you have at least 5 requests (minimumNumberOfCalls in config)
- Verify failure rate > 50%
- Check annotations are uncommented and app restarted

**No metrics in Prometheus:**
- Verify annotations are active
- Check [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus)
- Search for "resilience4j" in the output

**Fallback not triggering:**
- Verify `fallbackMethod = "fallback"` in annotation
- Check `fallback()` method exists in same class
- Review application logs for errors
