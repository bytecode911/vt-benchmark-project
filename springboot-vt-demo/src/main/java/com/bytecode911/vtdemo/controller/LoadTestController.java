package com.bytecode911.vtdemo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fans a burst of concurrent calls out to /api/delay/{ms} on a fresh virtual-thread-per-task
 * executor and reports timing -- mirrors what an operations-review-style service does when it
 * fans out to several downstream calls per request (the pattern this repo's Spring Boot
 * migration work targets), just driven by a single HTTP trigger for easy load testing.
 *
 * Example: GET /api/load-test?calls=5000&delayMs=1000
 */
@RestController
@RequestMapping("/api/load-test")
public class LoadTestController {

    private final RestClient restClient = RestClient.create();

    @GetMapping
    public LoadTestResult run(
            @RequestParam(defaultValue = "2000") int calls,
            @RequestParam(defaultValue = "1000") long delayMs,
            @RequestParam(defaultValue = "8080") int port) {

        String url = "http://localhost:" + port + "/api/delay/" + delayMs;
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        Instant start = Instant.now();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < calls; i++) {
                futures.add(executor.submit(() -> {
                    long reqStart = System.currentTimeMillis();
                    try {
                        restClient.get().uri(url).retrieve().toBodilessEntity();
                        long latency = System.currentTimeMillis() - reqStart;
                        latencies.add(latency);
                        success.incrementAndGet();
                    } catch (Exception e) {
                        long latency = System.currentTimeMillis() - reqStart;
                        latencies.add(latency);
                        failure.incrementAndGet();
                    }
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException | InterruptedException ignored) {
                    // already counted via the failure AtomicInteger inside the task
                }
            }
        }
        long durationMs = Duration.between(start, Instant.now()).toMillis();
        double durationSec = durationMs / 1000.0;
        double throughput = calls * 1000.0 / Math.max(1, durationMs);

        // Calculate latency percentiles
        Collections.sort(latencies);
        double minLatency = latencies.isEmpty() ? 0 : latencies.get(0);
        double maxLatency = latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1);
        double avgLatency = latencies.isEmpty() ? 0 : latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        double p50 = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.50));
        double p95 = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.95));
        double p99 = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.99));

        return new LoadTestResult(
                calls, success.get(), failure.get(), durationSec, throughput,
                minLatency, avgLatency, p50, p95, p99, maxLatency);
    }

    public record LoadTestResult(
            int calls, int success, int failures, double durationSeconds, double throughputReqSec,
            double latencyMinMs, double latencyAvgMs, double latencyP50Ms, 
            double latencyP95Ms, double latencyP99Ms, double latencyMaxMs) {}
}
