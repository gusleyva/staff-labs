# Week 02 - Notes

## Overview
General notes for Week 02.
Defense in depth; Implement multiple protection layers for external services that are
- Slow
- Intermittent
- Down
- Unexpected peak traffic

Does NOT crash your system.

## APIs
- /api/external simulates calls to external services.
- /admin/mock/configure failure injection tool (mini) for Chaos engineering.

## 1) Circuit breaker - Dead service
If downstream calls fails constantly, your API will retry and consume resources.
Based on the configuration, circuit breaker will help you to
- Evaluate failures, if 50% of calls fails, it will set to OPEN.
- If 20% of calls are slow (>2s), it will set to OPEN.
- Sliding window of 10

When is OPEN
- Doesn't call external services.
- Quick internal fallback.

Mental model, if is broken, stop trying.

This protects:
- Threads
- HTTP connections
- CPU
- Connection pool.

## 2) Retry with exponential backoff + Jitter - Extra protection against intermittence
Thundering herd happens when all clients retries at the same time.
A lot of 5xx errors are transitory.

Without jitter:
```
1000 clients fails.
All of them retries in 1 second.
The system explodes, it could be considered as an internal DDoS.
```

With Jitter
```
1000 clients fails.
Clients retries between 800ms - 1200ms.
Loads are distributed.
```

This is real distributed systems.

**Important:**
- Retry only for 5xx or timeout.
- Never for 4xx (this is logical error).

## 3) Bulkhead - Thread starvation protection - Slow service blocks threads
If external service has a 5s delay and you have 200 concurrent request...
All 200 request are blocked in Tomcat, your API dies...

With SemaphoreBulkd (3 permits):
Only 3 concurrent calls to external service, all others
- Waits
- Fast failure (it is configurable)

But, what it matters, all other endpoinst (/api/orders, /api/carts) are working.

This is real resiliency.

** Mental model ** 
Slow component should not block the full system.

## 4) Rate limiter 
Protects APIs againts external abuse.
Set a shield for bots, DDos attacks:
- Sends 5000 requests/min.
- Artificial peaks.

Set 50 req/min:
- Control consume.
- Allow predictable results. 

## 5) Failure injection - Chaos engineering
Inject and simulate delays and failure rate:
```
POST /admin/mock/configure
{
  "delayMs": 5000,
  "failRate": 0.8
}
```
Allowing validate architecture:
- Simulate extreme latency.
- Simulate 80% of failure.
- Open circuit breaker.
- Activate fallback.
- Saturate bulkhead.

## Observability
Everyone implements a circuit breaker, the difference is to
- Export metrics to prometheus.
- Visualize Grafana state

### Circuit breaker
Observe transition with dashboard
Closed -> Open -> Half-open -> Closed

### Retry metrics
- retries sucessful
- retries failed

### Bulkhead saturation
- Permits available
- Permits waiting

During k6 stress test
- 3 busy
- Queue

### Patter interaction
```
RateLimiter
   ↓
Bulkhead
   ↓
Retry
   ↓
CircuitBreaker
   ↓
External Call
```

### k6 test
When increase failRate or delay
1. Retries starts.
2. Increase times.
3. Bulkhead is full.
4. Failure rate > 50%.
5. Circuit breaker is open.
6. Fallback logic starts.
7. Latency decrease -> Graceful degradation

### Bulkhead vs circuit breaker
Circuite breaker stops request to a failing service, prevent cascading failures, fail fast..
Bulkhead partitions system resources (e.g. thread pools) to contain damage, ensuring one slow component (slow external service) doesn't overwhelm the entire application, isolate resources and prevent saturation.

## Load test
When running the `k6` stress test, execute **two scenarios** to validate your resilience strategy:

---

### 1️⃣ Without Protection (Toggle OFF)

Expected behavior:

- Latency spikes dramatically  
- Application threads become exhausted  
- Requests start failing across the system  
- Other endpoints become unresponsive  

This demonstrates how an unprotected system behaves under downstream instability.

---

### 2️⃣ With Protection Enabled (Toggle ON)

Expected behavior:

- The Circuit Breaker transitions to **OPEN**
- Fallback responses return quickly
- Latency stabilizes
- Other endpoints (`/api/cpu`, `/api/db`, etc.) remain responsive  

If this happens, your defensive layers are working correctly.


## Summary
This lab is not about simply using Resilience4j.

It is about proving that you understand how to:

- Design resilient distributed systems  
- Prevent cascading failures  
- Contain and isolate impact  
- Monitor internal system state  
- Intentionally inject and simulate failure (controlled chaos)  

If your system degrades gracefully under stress while remaining partially operational, your architecture is behaving as intended.