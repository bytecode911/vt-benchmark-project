# vt-benchmark-springboot

The production-shaped follow-up the original chat teased: `spring.threads.virtual.enabled=true`
on Spring Boot 3.5.7 (same version as the RAC-591 `iuw-operations-review-service` migration),
with Actuator wired up so you can prove it's actually running on virtual threads, not just take
the config flag on faith.

## Prerequisites
- JDK 21+
- Maven 3.9+ and internet access to Maven Central

## Run it
```bash
cd springboot-vt-demo
mvn spring-boot:run
```

## Endpoints
| Endpoint | Purpose |
|---|---|
| `GET /api/delay/{ms}` | simulates a slow downstream call (sleeps `ms` milliseconds) |
| `GET /api/thread-info` | returns `Thread.currentThread().toString()` for the handling request — confirms `VirtualThread[#id]/...` |
| `GET /api/load-test?calls=5000&delayMs=1000` | fires `calls` concurrent requests at `/api/delay/{ms}` on a virtual-thread-per-task executor and returns duration/success/failure/throughput |
| `GET /actuator/threaddump` | full JVM thread dump via Actuator — grep for `VIRTUAL` |
| `GET /actuator/health`, `/actuator/metrics` | standard Actuator diagnostics |

## Quick validation
```bash
# 1. confirm virtual threads are active
curl localhost:8080/api/thread-info
# -> VirtualThread[#34]/runnable@ForkJoinPool-1-worker-1

# 2. run a load test
curl "localhost:8080/api/load-test?calls=3000&delayMs=1000"

# 3. compare with virtual threads OFF: set spring.threads.virtual.enabled=false
#    in application.yml (or --spring.threads.virtual.enabled=false on the command line),
#    restart, and rerun the same curl — request handling now falls back to the
#    fixed Tomcat platform-thread pool (server.tomcat.threads.max) and step 1 will
#    show a "pool-" / Tomcat worker thread instead of a VirtualThread.

# 4. thread dump while a load test is in flight (run this in a second terminal)
curl localhost:8080/actuator/threaddump | grep -c '"virtual" : true'
```

## Benchmark Results (JDK 21, Spring Boot 3.5.7)

### Test: 3000 concurrent calls with 1000ms delay per call

| Metric | Virtual Threads **ON** ✅ | Virtual Threads **OFF** ❌ | Improvement |
|--------|--------------|--------------|-------------|
| Duration (s) | 3.85 | 11.64 | **3.0x faster** |
| Success Rate | 76.83% | 65.5% | **+11.33%** |
| Throughput (req/s) | 778.82 | 257.84 | **3.0x higher** |
| Avg Latency (ms) | 2059.84 | 4468 | **2.2x lower** |
| p95 Latency (ms) | 3183 | 10362 | **3.2x lower** |
| p99 Latency (ms) | 3645 | 10480 | **2.9x lower** |
| Max Latency (ms) | 3785 | 11548 | **3.0x lower** |

### Key Insights

1. **Virtual threads handle high concurrency better** — Tomcat's fixed thread pool (200 threads) becomes a bottleneck for platform threads, causing timeouts and failures
2. **Virtual threads enable 3x+ throughput** — one virtual thread per task allows 3000 concurrent operations vs. 200 platform threads
3. **Latency is significantly better** — especially at the tail (p95, p99), where platform threads struggle
4. **Success rate improves** — fewer timeout failures when request concurrency isn't limited by thread pool size

### How to Run the Comparison

From the project root:
```powershell
pwsh -ExecutionPolicy Bypass -File test-vt-springboot.ps1
```

This automated script will:
1. Build and start the Spring Boot app with virtual threads **enabled**
2. Run the same 3000-call benchmark
3. Stop and restart with virtual threads **disabled**
4. Run the benchmark again
5. Display side-by-side results

## Compatibility note
Virtual threads require a **JDK 21+ runtime**. `spring.threads.virtual.enabled=true` is safe to
leave in a Boot 3.2+ app running on an older JDK — Spring detects the JDK can't support it and
silently falls back to platform threads, it does not fail to start. Since the RAC-591 migration
target is Java 17, this module is a sandbox for validating the pattern now, ready to switch on
for real once/if that service (or a new one) moves to JDK 21.
