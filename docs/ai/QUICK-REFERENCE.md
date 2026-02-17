# Quick Reference: Circuit Breaker Testing

## 🎯 The Key Issue

**You commented the wrong file!** 

- ❌ **DON'T comment**: [MockConfig.java](file:///Users/gustavolc/.gemini/antigravity/scratch/staff-labs/src/main/java/com/stafflabs/config/MockConfig.java) - This controls failure injection
- ✅ **DO comment/uncomment**: [ExternalService.java](file:///Users/gustavolc/.gemini/antigravity/scratch/staff-labs/src/main/java/com/stafflabs/service/ExternalService.java#L26-L33) annotations - This toggles protection

---

## 📊 Side-by-Side Comparison

| Aspect | WITHOUT Protection (Commented) | WITH Protection (Active) |
|--------|-------------------------------|-------------------------|
| **Annotations** | Lines 26-33 commented | Lines 26-33 active |
| **Circuit Breaker** | ❌ Doesn't exist | ✅ Transitions: CLOSED→OPEN→HALF_OPEN |
| **Failure Handling** | ❌ Every request fails | ✅ Fallback after threshold |
| **Latency (failing)** | ⚠️ 500ms+ per request | ✅ ~2ms (instant fallback) |
| **Retries** | ❌ No retries | ✅ 3 attempts with backoff |
| **Thread Safety** | ❌ All threads can block | ✅ Max 3 concurrent (bulkhead) |
| **Prometheus Metrics** | ❌ No resilience metrics | ✅ Full metrics available |
| **Grafana Dashboard** | ❌ No data | ✅ Real-time state transitions |
| **User Experience** | ❌ Errors and timeouts | ✅ Graceful degradation |

---

## 🚀 Quick Test (5 minutes)

### Current State (No Protection)
```bash
# 1. Inject failures
curl -X POST http://localhost:8080/admin/mock/configure \
  -H "Content-Type: application/json" \
  -d '{"failureRate": 0.7, "delayMs": 500}'

# 2. Test 10 requests
for i in {1..10}; do curl http://localhost:8080/api/external; echo ""; done

# Expected: ~7 failures, each takes 500ms+, no fallback
```

### Enable Protection
```bash
# 1. Uncomment lines 26-33 in ExternalService.java
# 2. Restart: ./gradlew bootRun
# 3. Same test
curl -X POST http://localhost:8080/admin/mock/configure \
  -H "Content-Type: application/json" \
  -d '{"failureRate": 0.7, "delayMs": 500}'

for i in {1..10}; do curl http://localhost:8080/api/external; echo ""; done

# Expected: First few fail, then fallback kicks in (~2ms response)
```

---

## 📈 What to Watch in Grafana

Open [Resilience Dashboard](http://localhost:3000):

**Without Protection:**
- All panels show "No data"

**With Protection:**
1. **Circuit Breaker State** panel:
   - Starts at `0` (Closed)
   - Jumps to `1` (Open) after ~5-10 failed requests
   - Returns to `2` (Half-Open) after 5 seconds
   
2. **Retry Calls Rate** panel:
   - Shows spike during initial failures
   
3. **Bulkhead Saturation** panel:
   - Shows max 3 concurrent calls

---

## 🎬 Expected Sequence (With Protection)

```
Request #1-5:   ✅ Some succeed, some fail, retries happen
Request #6-10:  ⚠️  Failure rate > 50%, circuit evaluating
Request #11:    🔴 Circuit OPENS
Request #12+:   ⚡ Instant fallback (~2ms)
                "Graceful Degradation: Cached Response"
After 5s:       🟡 Circuit goes HALF_OPEN (allows 3 test requests)
If still bad:   🔴 Back to OPEN
If recovered:   ✅ Back to CLOSED
```

---

## ✅ Action Items

1. **Uncomment** [ExternalService.java lines 26-33](file:///Users/gustavolc/.gemini/antigravity/scratch/staff-labs/src/main/java/com/stafflabs/service/ExternalService.java#L26-L33)
2. **Restart** application: `./gradlew bootRun`
3. **Configure** failures: Use `/admin/mock/configure` endpoint
4. **Test** with multiple requests
5. **Watch** Grafana dashboard for state transitions
6. **Check** Prometheus metrics

For detailed instructions, see [CIRCUIT-BREAKER-TESTING.md](file:///Users/gustavolc/.gemini/antigravity/scratch/staff-labs/weekly-notes/CIRCUIT-BREAKER-TESTING.md)
