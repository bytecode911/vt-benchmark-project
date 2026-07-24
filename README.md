# Virtual Threads vs Platform Threads — Benchmark Project

Two importable Maven projects that prove out Java 21 virtual threads against an I/O-bound
workload, built from the earlier chat's single-file `ThreadTest.java` script.

- **`standalone-benchmark/`** — the original script, fixed up and turned into a reusable,
  configurable CLI tool. No Spring, no external dependencies, pure JDK.
- **`springboot-vt-demo/`** — the "next step" the earlier chat offered: a real Spring Boot
  3.5.7 service (same version as the RAC-591 `iuw-operations-review-service` migration) with
  `spring.threads.virtual.enabled=true` and Actuator wired up to prove it.

Each has its own `pom.xml` and README — import either folder into IntelliJ/Eclipse as a Maven
project independently, or open this root folder (both will be picked up as separate modules).

## What changed vs. the original script (and why)

| Issue in the original | Fix |
|---|---|
| 10,000 concurrent requests hardcoded against `httpbin.org` | You'll hit connection resets / throttling well before 10k in-flight requests — at that point you're benchmarking httpbin's rate limiter, not your thread model. `standalone-benchmark` now defaults to an **embedded local mock server** (`LocalDelayServer`), with `--url=` to opt into a real endpoint for a small sanity check. |
| Only wall-clock duration was measured | Added a thread-safe `BenchmarkStats` collector: success/failure counts, throughput (req/s), and **latency min/avg/p50/p95/p99/max** for both runs — the numbers you'd actually need to defend a "virtual threads are N× faster" claim. |
| `try (executor)` auto-close **and** a manual `shutdown()`/`awaitTermination()` in the same block | Redundant, and if `awaitTermination` genuinely timed out, the implicit `close()` on block-exit would loop waiting again with no timeout of its own. Replaced with one explicit path: `shutdown()` → `awaitTermination(5m)` → `shutdownNow()` fallback if it doesn't finish. |
| Task count, pool size, delay, and target URL all hardcoded | Pulled into `BenchmarkConfig` — configurable via `--tasks=`, `--poolSize=`, `--delayMs=`, `--url=` so it's a reusable tool, not a one-off script. |
| Single `.java` file, no build | Proper Maven project (`pom.xml` with compiler/exec/jar plugins) — import it, don't copy-paste it. |
| Chat ended by offering a Spring Boot + Actuator version | Built it — `springboot-vt-demo/`, on the same Boot version your RAC-591 migration already runs, so the pattern (`spring.threads.virtual.enabled=true`) is directly reusable, not just a toy demo. |

One thing worth flagging: virtual threads need a **JDK 21+ runtime**. The RAC-591 migration
target is Java 17, so treat `springboot-vt-demo` as a sandbox to validate the pattern now —
the config flag is harmless on 17 (Spring detects it's unsupported and silently falls back to
platform threads), but you won't get the actual virtual-thread benefit until a service is on 21.

## Quick start
```bash
# Standalone benchmark (pure JDK, no Spring)
cd standalone-benchmark
mvn compile exec:java -Dexec.args="--tasks=10000 --poolSize=200 --delayMs=1000"

# Spring Boot demo
cd ../springboot-vt-demo
mvn spring-boot:run
curl "localhost:8080/api/load-test?calls=3000&delayMs=1000"
```

## Validating the difference for real (not just trusting the printed numbers)
```bash
jps -l                                              # find the PID
jcmd <PID> Thread.dump_to_file -overwrite dump.txt  # count VirtualThread vs pool-/Tomcat threads
jcmd <PID> VM.native_memory summary                 # platform ~1MB/stack vs virtual ~1KB
java -XX:StartFlightRecording=filename=run.jfr ...  # open in JDK Mission Control — see time
                                                     # spent "Parking" vs actually running
```

Sample run of `standalone-benchmark` in this sandbox (300 tasks, 50-thread pool, 200ms delay,
local mock server — small numbers so it completes fast, same pattern holds at 10k):

```
PLATFORM THREADS (pool of 50) FINISHED in 2.21s | success=300 fail=0
VIRTUAL THREADS (1 per task)  FINISHED in 0.69s | success=300 fail=0
Virtual threads were 3.2x faster wall-clock for this I/O-bound workload.
```
