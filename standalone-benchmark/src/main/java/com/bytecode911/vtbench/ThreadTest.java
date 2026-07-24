package com.bytecode911.vtbench;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Platform threads vs Virtual threads, same I/O-bound workload, same task count.
 *
 * Usage:
 *   mvn compile exec:java
 *   mvn compile exec:java -Dexec.args="--tasks=20000 --poolSize=200 --delayMs=500"
 *   mvn compile exec:java -Dexec.args="--tasks=200 --url=https://httpbin.org/delay/1"
 *
 * or after `mvn package`:
 *   java -jar target/vt-benchmark.jar --tasks=10000
 */
public class ThreadTest {

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public static void main(String[] args) throws Exception {
        BenchmarkConfig config = BenchmarkConfig.fromArgs(args);

        LocalDelayServer localServer = null;
        String targetUrl = config.url();
        if (targetUrl == null) {
            localServer = new LocalDelayServer(config.delayMs());
            localServer.start();
            targetUrl = localServer.baseUrl() + "/delay";
            System.out.println("No --url supplied -> started local mock delay server at " + targetUrl);
            System.out.println("(pass --url=https://httpbin.org/delay/1 with a small --tasks for a real-network check)\n");
        }

        System.out.printf("Config: tasks=%d, platformPoolSize=%d, target=%s%n%n",
                config.tasks(), config.poolSize(), targetUrl);

        try {
            BenchmarkStats platformStats = new BenchmarkStats(config.tasks());
            ExecutorService platformPool = Executors.newFixedThreadPool(config.poolSize());
            runTest(platformPool, "PLATFORM THREADS (pool of " + config.poolSize() + ")",
                    config.tasks(), targetUrl, platformStats);

            System.out.println("--- cooling down 2s between tests ---\n");
            Thread.sleep(2000);

            BenchmarkStats virtualStats = new BenchmarkStats(config.tasks());
            ExecutorService virtualPool = Executors.newVirtualThreadPerTaskExecutor();
            runTest(virtualPool, "VIRTUAL THREADS (1 per task)",
                    config.tasks(), targetUrl, virtualStats);

            printComparison(platformStats, virtualStats);
        } finally {
            if (localServer != null) {
                localServer.stop();
            }
        }
    }

    private static void runTest(ExecutorService executor, String testName, int totalTasks,
                                 String targetUrl, BenchmarkStats stats) throws InterruptedException {
        System.out.println("=== Starting " + testName + " ===");
        Instant start = Instant.now();

        for (int i = 0; i < totalTasks; i++) {
            int taskId = i;
            executor.submit(() -> callSlowApi(taskId, targetUrl, stats));
        }

        // Single, unambiguous shutdown path: request shutdown, wait, and force-cancel
        // on timeout. (The original script mixed try-with-resources auto-close with a
        // manual shutdown()/awaitTermination() call, which is redundant and, on a real
        // timeout, could still block indefinitely inside the implicit close().)
        executor.shutdown();
        boolean finished = executor.awaitTermination(5, TimeUnit.MINUTES);
        if (!finished) {
            System.out.println("WARNING: timed out after 5 minutes, forcing shutdownNow()");
            executor.shutdownNow();
        }

        long durationMs = Duration.between(start, Instant.now()).toMillis();
        stats.setTestDurationMs(durationMs);

        System.out.printf("%s FINISHED in %.2fs | success=%d fail=%d%n%n",
                testName, durationMs / 1000.0, stats.getSuccessCount(), stats.getFailureCount());
    }

    private static void callSlowApi(int taskId, String targetUrl, BenchmarkStats stats) {
        if (taskId < 5 || taskId % 2000 == 0) {
            System.out.println("Task " + taskId + " running on: " + Thread.currentThread());
        }
        Instant callStart = Instant.now();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(15))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latencyMs = Duration.between(callStart, Instant.now()).toMillis();
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                stats.recordSuccess(latencyMs);
            } else {
                stats.recordFailure();
            }
        } catch (Exception e) {
            stats.recordFailure();
            if (taskId < 5) {
                System.err.println("Task " + taskId + " failed: " + e);
            }
        }
    }

    private static void printComparison(BenchmarkStats platform, BenchmarkStats virtual) {
        var p = platform.computePercentiles();
        var v = virtual.computePercentiles();

        System.out.println("=================== RESULTS ===================");
        System.out.printf("%-24s %14s %14s%n", "Metric", "Platform", "Virtual");
        System.out.printf("%-24s %14.2f %14.2f%n", "Duration (s)", platform.getTestDurationMs() / 1000.0, virtual.getTestDurationMs() / 1000.0);
        System.out.printf("%-24s %14d %14d%n", "Success", platform.getSuccessCount(), virtual.getSuccessCount());
        System.out.printf("%-24s %14d %14d%n", "Failures", platform.getFailureCount(), virtual.getFailureCount());
        System.out.printf("%-24s %14.1f %14.1f%n", "Throughput (req/s)", platform.getThroughputPerSec(), virtual.getThroughputPerSec());
        System.out.printf("%-24s %14d %14d%n", "Latency min (ms)", p.min(), v.min());
        System.out.printf("%-24s %14.1f %14.1f%n", "Latency avg (ms)", p.avg(), v.avg());
        System.out.printf("%-24s %14d %14d%n", "Latency p50 (ms)", p.p50(), v.p50());
        System.out.printf("%-24s %14d %14d%n", "Latency p95 (ms)", p.p95(), v.p95());
        System.out.printf("%-24s %14d %14d%n", "Latency p99 (ms)", p.p99(), v.p99());
        System.out.printf("%-24s %14d %14d%n", "Latency max (ms)", p.max(), v.max());

        double speedup = virtual.getTestDurationMs() == 0 ? 0
                : (double) platform.getTestDurationMs() / Math.max(1, virtual.getTestDurationMs());
        System.out.printf("%nVirtual threads were %.1fx faster wall-clock for this I/O-bound workload.%n", speedup);
        System.out.println("=================================================");
    }
}
