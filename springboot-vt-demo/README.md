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

## Compatibility note
Virtual threads require a **JDK 21+ runtime**. `spring.threads.virtual.enabled=true` is safe to
leave in a Boot 3.2+ app running on an older JDK — Spring detects the JDK can't support it and
silently falls back to platform threads, it does not fail to start. Since the RAC-591 migration
target is Java 17, this module is a sandbox for validating the pattern now, ready to switch on
for real once/if that service (or a new one) moves to JDK 21.
