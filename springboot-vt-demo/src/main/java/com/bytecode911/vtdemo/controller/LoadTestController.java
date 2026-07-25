package com.bytecode911.vtdemo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

        Instant start = Instant.now();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < calls; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        restClient.get().uri(url).retrieve().toBodilessEntity();
                        success.incrementAndGet();
                    } catch (Exception e) {
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

        return new LoadTestResult(calls, success.get(), failure.get(), durationMs,
                calls * 1000.0 / Math.max(1, durationMs));
    }

    public record LoadTestResult(int calls, int success, int failure, long durationMs, double throughputPerSec) {}
}
