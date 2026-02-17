# Prometheus Search Guide

## 🔍 How to Search in Prometheus

### Access Prometheus UI
Open your browser and go to: **[http://localhost:9091](http://localhost:9091)**

---

## 📊 Available Resilience Metrics

Based on your running application, these metrics are available:

### Circuit Breaker Metrics
```promql
# Current state (0=Closed, 1=Open, 2=Half-Open)
resilience4j_circuitbreaker_state{name="externalService"}

# Failure rate (percentage)
resilience4j_circuitbreaker_failure_rate{name="externalService"}

# Slow call rate (percentage)
resilience4j_circuitbreaker_slow_call_rate{name="externalService"}

# Number of buffered calls
resilience4j_circuitbreaker_buffered_calls{name="externalService"}

# Calls not permitted (when circuit is open)
resilience4j_circuitbreaker_not_permitted_calls_total{name="externalService"}
```

### Retry Metrics
```promql
# Total retry calls by kind (successful_with_retry, failed_with_retry, etc.)
resilience4j_retry_calls_total{name="externalService"}

# Rate of successful retries
rate(resilience4j_retry_calls_total{name="externalService",kind="successful_with_retry"}[1m])

# Rate of failed retries
rate(resilience4j_retry_calls_total{name="externalService",kind="failed_with_retry"}[1m])
```

### Bulkhead Metrics
```promql
# Available concurrent calls
resilience4j_bulkhead_available_concurrent_calls{name="externalService"}

# Max allowed concurrent calls
resilience4j_bulkhead_max_allowed_concurrent_calls{name="externalService"}
```

### Rate Limiter Metrics
```promql
# Available permissions
resilience4j_ratelimiter_available_permissions{name="externalService"}

# Waiting threads
resilience4j_ratelimiter_waiting_threads{name="externalService"}
```

---

## 🎯 Step-by-Step: Search in Prometheus

### 1. Open Prometheus
Navigate to [http://localhost:9091](http://localhost:9091)

### 2. Use the Expression Browser
You'll see a search box at the top. This is where you enter PromQL queries.

### 3. Try These Queries

**Check Circuit Breaker State:**
```promql
resilience4j_circuitbreaker_state
```
- Click **Execute** button
- Switch to **Graph** tab to see state over time
- Value: `0` = Closed, `1` = Open, `2` = Half-Open

**Check Failure Rate:**
```promql
resilience4j_circuitbreaker_failure_rate
```
- Shows percentage of failed calls
- When this exceeds 50%, circuit should open

**See All Resilience Metrics:**
```promql
{__name__=~"resilience4j.*"}
```
- Shows all resilience4j metrics at once

---

## 🧪 Quick Test Workflow

### Step 1: Check Current State
```promql
resilience4j_circuitbreaker_state{name="externalService"}
```
**Expected**: Should show `0` (Closed) initially

### Step 2: Inject Failures
In terminal:
```bash
curl -X POST http://localhost:8080/admin/mock/configure \
  -H "Content-Type: application/json" \
  -d '{"failureRate": 0.7, "delayMs": 500}'
```

### Step 3: Generate Traffic
```bash
for i in {1..20}; do curl http://localhost:8080/api/external; sleep 0.5; done
```

### Step 4: Watch State Change in Prometheus
Refresh the query or set auto-refresh:
```promql
resilience4j_circuitbreaker_state{name="externalService"}
```
**Expected**: Should change from `0` to `1` (Open)

### Step 5: Check Not Permitted Calls
```promql
resilience4j_circuitbreaker_not_permitted_calls_total{name="externalService"}
```
**Expected**: Counter increases when circuit is open

---

## 🎨 Prometheus UI Tips

### Enable Auto-Refresh
1. Click the dropdown next to **Execute** button
2. Select refresh interval (e.g., `5s`)
3. Queries will auto-update

### Switch Between Table and Graph
- **Table** tab: Shows current values
- **Graph** tab: Shows values over time (better for watching state transitions)

### Time Range
- Use the time picker at top to adjust range
- Default is last 1 hour
- For testing, use last 5-15 minutes

### Multiple Metrics
You can add multiple queries:
1. Click **+ Add Query**
2. Enter another metric
3. Both will show on the same graph

---

## 🐛 Troubleshooting

### "No data" in Prometheus

**Check 1: Is the app running?**
```bash
curl http://localhost:8080/actuator/health
```

**Check 2: Are metrics exposed?**
```bash
curl http://localhost:8080/actuator/prometheus | grep resilience
```
If you see metrics here but not in Prometheus, wait 15-30 seconds for scraping.

**Check 3: Is Prometheus scraping the app?**
1. Go to [http://localhost:9091/targets](http://localhost:9091/targets)
2. Look for `staff-labs-api` target
3. Should show **UP** status

### Metrics exist but show "0" or no activity

**Cause**: No traffic to the endpoint yet

**Solution**: Generate some requests:
```bash
curl http://localhost:8080/api/external
```

### Circuit breaker state not changing

**Cause**: Not enough failures or requests

**Solution**: 
1. Configure high failure rate (70%+)
2. Send at least 10-15 requests
3. Wait a few seconds for evaluation

---

## 📈 Example Queries for Analysis

### See retry success rate
```promql
rate(resilience4j_retry_calls_total{kind="successful_with_retry"}[1m]) 
/ 
rate(resilience4j_retry_calls_total[1m])
```

### Check if bulkhead is saturated
```promql
resilience4j_bulkhead_available_concurrent_calls{name="externalService"} == 0
```

### Monitor circuit breaker transitions
```promql
changes(resilience4j_circuitbreaker_state{name="externalService"}[5m])
```
Shows how many times state changed in last 5 minutes

---

## ✅ Quick Verification Checklist

- [ ] Prometheus UI accessible at http://localhost:9091
- [ ] Query `resilience4j_circuitbreaker_state` returns data
- [ ] Application is running (check http://localhost:8080/actuator/health)
- [ ] Metrics endpoint works (http://localhost:8080/actuator/prometheus)
- [ ] Prometheus targets show UP status (http://localhost:9091/targets)

---

## 🎯 Most Important Metric

**For circuit breaker testing, watch this one:**
```promql
resilience4j_circuitbreaker_state{name="externalService"}
```

This single metric tells you everything:
- `0` = System is healthy (Closed)
- `1` = System detected failures (Open) - **This is what you want to see during testing**
- `2` = System is testing recovery (Half-Open)
