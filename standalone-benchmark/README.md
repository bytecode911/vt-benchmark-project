# vt-benchmark-standalone

Platform threads vs Virtual threads, same I/O-bound workload, same task count.
Improved version of the original single-file script — see the "What changed" section
in the root README for the full diff/reasoning.

## Prerequisites
- JDK 21+ (`java -version`)
- Maven 3.9+ (`mvn -version`)
- Internet access to Maven Central for the first build (only compiler/exec/jar plugins —
  the benchmark code itself has zero external dependencies)

## Run it
```bash
cd standalone-benchmark

# Default: 3,000 tasks, 200-thread platform pool, 1s delay, against a local mock server
mvn compile exec:java

# Custom load (use larger values on a beefier machine)
mvn compile exec:java -Dexec.args="--tasks=20000 --poolSize=200 --delayMs=500"

# Point at a real network endpoint instead of the local mock server
# (keep --tasks small here, e.g. 100-300, or httpbin.org will start rejecting/dropping requests)
mvn compile exec:java -Dexec.args="--tasks=200 --url=https://httpbin.org/delay/1"

# Or build a runnable jar
mvn package
java -jar target/vt-benchmark.jar --tasks=10000
```

## CLI options
| Flag | Default | Meaning |
|---|---|---|
| `--tasks` | 10000 | number of concurrent calls to fire per test |
| `--poolSize` | 200 | fixed platform-thread pool size for the platform-thread test |
| `--delayMs` | 1000 | simulated downstream latency (only used by the local mock server) |
| `--url` | *(local mock server)* | override target URL, e.g. a real internet endpoint |

## What it prints
- Live thread identity for the first few tasks + every 2000th (proves `pool-N-thread` vs `VirtualThread[#id]`)
- Per-test: duration, success/failure counts
- Final comparison table: throughput (req/s) and latency **min / avg / p50 / p95 / p99 / max** for both models,
  plus the wall-clock speedup factor

## Validate independently while it runs
```bash
# find the PID
jps -l

# thread dump — search for "VirtualThread" vs "pool-" to see the carrier-thread count
jcmd <PID> Thread.dump_to_file -overwrite dump.txt

# native memory: platform threads ~1MB/stack, virtual threads ~1KB
jcmd <PID> VM.native_memory summary

# flight recorder — see time spent "Parking" vs actually running
java -XX:StartFlightRecording=filename=run.jfr -cp target/classes com.bytecode911.vtbench.ThreadTest
# open run.jfr in JDK Mission Control
```

## Notes on the local mock server
By default the benchmark starts an in-process HTTP server (`LocalDelayServer`) instead of
hitting `httpbin.org`. Firing thousands of concurrent requests at a shared public endpoint
doesn't give reproducible numbers — you start seeing connection resets and throttling well
before 10k in-flight requests, and at that point you're benchmarking httpbin's rate limiter,
not your thread model. Use `--url=https://httpbin.org/delay/1` with a small `--tasks` if you
want one real-network sanity check on top of the local numbers.
