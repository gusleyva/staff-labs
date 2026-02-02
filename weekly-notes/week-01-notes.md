# Week 01 - Notes

## Overview
General notes for Week 01.
- Configure the project.
- Install gradle and Java 21.
- Infrastructure is created with docker, but app service is initialized manually with gradle to simplify changes, debugging and restarting.
- Once started docker you can try access to Prometheus and Grafana, Tempo lives inside Grafana.

## Grafana Dashboard
To access to dashboard
`![Image Description](../images/01-grafana-dashboard-path.png)`

Play with endpoints to create load and observe the metrics in Grafana.
`![Image Description](../images/01-grafana-dashboard.png)`

## APIs
- /seed is a high speed batch insertion, nonetheless it can be slow if you request a large number of orders.
```
# Seed 100,000 orders (takes ~10-15 seconds)
curl -X POST "http://localhost:8080/admin/seed?count=100000"
```

- /db/search - Slow database query
```bash
curl http://localhost:8080/api/db/search
```

- /cpu - Blocks a CPU core using recursion.
```bash
# Burn CPU for 500ms
curl "http://localhost:8080/api/cpu?ms=500"
```

- /external - External service simulation - introduces jitter and random failures. Jitter is the variation in the delay of a signal/package.

The Problem: If you have 1,000 clients and the server goes down, all clients will fail simultaneously. If everyone retries exactly at the 1s, 2s, and 4s marks, you create massive traffic spikes (Internal DDoS) that will crush the server every time it tries to recover. This is known as the "Thundering Herd" problem.

The Solution: Add Jitter. Instead of retrying at exactly 2000ms, you retry at 2000ms + random(0, 500).

The Result: You spread the load over time (smoothing the traffic), allowing the server to "breathe" and recover successfully.

## k6 test
In Week 1 of your plan, you will observe this phenomenon clearly:
1. Low Load: You run k6 with a few users: p50 = 50ms, p99 = 80ms (The system is healthy).
2. High Load: You increase to 100 users (k6 script): p50 = 60ms (It looks like you're doing fine), but the p99 = 3000ms.

The Conclusion: Your system is saturated. The average (p50) is lying to you; the system has already failed for your most critical users.

**Scenarios:**
1. **CPU Saturation** (30s warmup → 1m sustained → 30s cooldown)
2. **DB Saturation** (same pattern, starts after CPU test)
3. **Mixed Load** (realistic traffic, 10 → 100 RPS)

```
k6 run load/k6-script.js                                                                                         ✔  1297  00:26:37

         /\      Grafana   /‾‾/  
    /\  /  \     |\  __   /  /   
   /  \/    \    | |/ /  /   ‾‾\ 
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/ 

     execution: local
        script: load/k6-script.js
        output: -

     scenarios: (100.00%) 3 scenarios, 100 max VUs, 10m0s max duration (incl. graceful stop):
              * cpu_saturation: Up to 10 looping VUs for 2m0s over 3 stages (gracefulRampDown: 10s, exec: cpuHeavy, gracefulStop: 30s)
              * db_saturation: Up to 5 looping VUs for 2m0s over 3 stages (gracefulRampDown: 10s, exec: dbHeavy, startTime: 2m30s, gracefulStop: 30s)
              * mixed_load: Up to 100.00 iterations/s for 4m30s over 4 stages (maxVUs: 50-100, exec: mixedLoad, startTime: 5m0s, gracefulStop: 30s)

WARN[0324] Insufficient VUs, reached 100 active VUs and cannot initialize more  executor=ramping-arrival-rate scenario=mixed_load

 ================== LOAD TEST SUMMARY ==================
 Total requests: 3768
 Request rate: 6.47 req/s
 Failed requests: 0.0263
 
Latency:
   p50: 0 ms
   p95: 13661.02 ms
   p99: 0 ms
 =======================================================


running (09m42.0s), 000/100 VUs, 3768 complete and 0 interrupted iterations
cpu_saturation ✓ [======================================] 00/10 VUs    2m0s  
db_saturation  ✓ [======================================] 0/5 VUs      2m0s  
mixed_load     ✓ [======================================] 000/100 VUs  4m30s  001.29 iters/s
ERRO[0582] thresholds on metrics 'http_req_duration' have been crossed 
```
