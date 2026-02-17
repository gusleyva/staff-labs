# Exploring All Metrics in Prometheus

## 🔍 Key Concept: Metrics vs Logs

**Important**: Prometheus stores **metrics** (numbers), not **logs** (text messages).

- **Metrics** = Numerical data (counters, gauges, histograms) → **Prometheus**
- **Logs** = Text messages with timestamps → **Application logs** (check terminal or log files)

---

## 📊 How to Discover All Available Metrics

### Method 1: Browse All Metrics (Easiest)

In Prometheus UI at [http://localhost:9091](http://localhost:9091):

1. Click in the **Expression** box
2. Type just `{` and wait
3. A dropdown will show all available metrics
4. Or click the **🌐 Globe icon** next to the search box to browse metrics

### Method 2: Search by Pattern

Use regex to find metrics by category:

```promql
# All HTTP request metrics
{__name__=~"http.*"}

# All JVM metrics
{__name__=~"jvm.*"}

# All database metrics
{__name__=~"hikaricp.*"}

# All resilience metrics
{__name__=~"resilience4j.*"}

# All custom application metrics
{__name__=~"(cpu_burn|db_slow|external_service).*"}

# Everything (can be overwhelming!)
{__name__=~".*"}
```

---

## 📋 Available Metrics in Your Application

Based on your running app, here are the main categories:

### 1. **Application Metrics**
```promql
# Application startup time
application_started_time_seconds
application_ready_time_seconds
```

### 2. **HTTP Request Metrics**
```promql
# Request duration by endpoint
http_server_requests_seconds_count{uri="/api/external"}
http_server_requests_seconds_sum{uri="/api/external"}
http_server_requests_seconds_max{uri="/api/external"}

# All endpoints
http_server_requests_seconds_count

# Request rate (requests per second)
rate(http_server_requests_seconds_count[1m])

# Average latency
rate(http_server_requests_seconds_sum[1m]) / rate(http_server_requests_seconds_count[1m])
```

### 3. **Custom Service Metrics**
```promql
# CPU burn operations
cpu_burn_count_total
cpu_burn_duration_seconds_sum

# Database slow queries
db_slow_query_duration_seconds_count
db_slow_query_duration_seconds_sum

# External service calls
external_service_duration_seconds_count
external_service_success_total
external_service_failures_total
```

### 4. **Database Connection Pool (HikariCP)**
```promql
# Active connections
hikaricp_connections_active{pool="StaffLabsHikariCP"}

# Total connections
hikaricp_connections{pool="StaffLabsHikariCP"}

# Pending connections (waiting for a connection)
hikaricp_connections_pending{pool="StaffLabsHikariCP"}

# Connection acquisition time
hikaricp_connections_acquire_seconds_sum
```

### 5. **JVM Metrics**
```promql
# Memory usage
jvm_memory_used_bytes{area="heap"}
jvm_memory_max_bytes{area="heap"}

# Garbage collection
jvm_gc_pause_seconds_count
jvm_gc_pause_seconds_sum

# Thread count
jvm_threads_live
jvm_threads_daemon
```

### 6. **Thread Pool (Executor)**
```promql
# Active threads
executor_active_threads

# Completed tasks
executor_completed_tasks_total

# Queue size
executor_queued_tasks
```

### 7. **System Metrics**
```promql
# CPU usage
system_cpu_usage
process_cpu_usage

# Disk space
disk_free_bytes
disk_total_bytes
```

### 8. **Resilience4j Metrics**
```promql
# Circuit breaker
resilience4j_circuitbreaker_state
resilience4j_circuitbreaker_failure_rate
resilience4j_circuitbreaker_slow_call_rate

# Retries
resilience4j_retry_calls_total

# Bulkhead
resilience4j_bulkhead_available_concurrent_calls

# Rate limiter
resilience4j_ratelimiter_available_permissions
```

---

## 🎯 Useful Queries for Monitoring

### Monitor Request Rate by Endpoint
```promql
rate(http_server_requests_seconds_count[1m])
```

### Check Database Connection Pool Saturation
```promql
hikaricp_connections_active{pool="StaffLabsHikariCP"} / hikaricp_connections{pool="StaffLabsHikariCP"}
```
Returns value between 0-1 (1 = fully saturated)

### External Service Success Rate
```promql
external_service_success_total / (external_service_success_total + external_service_failures_total)
```

### P95 Latency for External Endpoint
```promql
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/api/external"}[1m]))
```

### Memory Usage Percentage
```promql
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100
```

---

## 🔎 How to Explore Interactively

### Step 1: Start Typing
In the Prometheus expression box, start typing a metric name:
```
http_
```
Autocomplete will show all metrics starting with `http_`

### Step 2: Add Labels
Narrow down by labels:
```promql
http_server_requests_seconds_count{uri="/api/external"}
```

### Step 3: Use Functions
Apply PromQL functions:
```promql
rate(http_server_requests_seconds_count{uri="/api/external"}[1m])
```

---

## 📝 Where to Find Logs (Not Metrics)

If you want to see **actual log messages**, check:

### 1. Application Terminal
The terminal where you ran `./gradlew bootRun` shows logs like:
```
External service call failed (simulated)
CircuitBreaker 'externalService' changed state from CLOSED to OPEN
External service fallback triggered. Reason: ...
```

### 2. Application Log Files
Check if logs are written to files in your project directory.

### 3. Grafana Loki (Not Currently Set Up)
For centralized log aggregation, you'd need to set up Loki, but that's not part of your current stack.

---

## 🎨 Pro Tips

### See All Metric Names
```promql
{__name__!=""}
```
Then switch to **Table** view to see a list.

### Filter by Label
```promql
{job="staff-labs-api"}
```
Shows all metrics from your application.

### Combine Metrics
```promql
# Success rate
rate(external_service_success_total[1m]) / (rate(external_service_success_total[1m]) + rate(external_service_failures_total[1m]))
```

---

## 📊 Quick Reference Table

| What You Want | Query |
|---------------|-------|
| All metrics | `{__name__!=""}` |
| HTTP metrics | `{__name__=~"http.*"}` |
| JVM metrics | `{__name__=~"jvm.*"}` |
| DB metrics | `{__name__=~"hikaricp.*"}` |
| Custom metrics | `{__name__=~"(cpu_burn\|external_service\|db_slow).*"}` |
| Resilience metrics | `{__name__=~"resilience4j.*"}` |
| Request rate | `rate(http_server_requests_seconds_count[1m])` |
| Active DB connections | `hikaricp_connections_active` |
| Circuit breaker state | `resilience4j_circuitbreaker_state` |

---

## ✅ Summary

- **Prometheus** = Metrics (numbers)
- **Logs** = Check your terminal or application logs
- Use `{__name__=~"pattern.*"}` to explore metrics by category
- Click the 🌐 globe icon in Prometheus UI to browse all metrics
- For detailed log messages, check the terminal where `./gradlew bootRun` is running
