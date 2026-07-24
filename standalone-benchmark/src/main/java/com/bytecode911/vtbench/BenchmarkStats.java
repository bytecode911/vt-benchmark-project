package com.bytecode911.vtbench;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free-ish metrics collector: pre-sized array + AtomicInteger cursor for latency
 * samples (avoids the synchronization / boxing cost of a List<Long> under 10k+ concurrent
 * writers), plus LongAdder counters for success/failure. The original script only measured
 * total wall-clock time — this adds throughput and p50/p95/p99 latency, which is what you
 * actually need to defend a "virtual threads are faster" claim.
 */
public class BenchmarkStats {

    private final long[] latenciesMs;
    private final AtomicInteger writeIndex = new AtomicInteger(0);
    private final LongAdder successCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private volatile long testDurationMs;

    public BenchmarkStats(int expectedTasks) {
        this.latenciesMs = new long[Math.max(1, expectedTasks)];
    }

    public void recordSuccess(long latencyMs) {
        successCount.increment();
        int i = writeIndex.getAndIncrement();
        if (i < latenciesMs.length) {
            latenciesMs[i] = latencyMs;
        }
    }

    public void recordFailure() {
        failureCount.increment();
    }

    public void setTestDurationMs(long durationMs) {
        this.testDurationMs = durationMs;
    }

    public long getSuccessCount() {
        return successCount.sum();
    }

    public long getFailureCount() {
        return failureCount.sum();
    }

    public long getTestDurationMs() {
        return testDurationMs;
    }

    public double getThroughputPerSec() {
        long total = getSuccessCount() + getFailureCount();
        return testDurationMs == 0 ? 0 : (total * 1000.0) / testDurationMs;
    }

    public Percentiles computePercentiles() {
        int count = Math.min(writeIndex.get(), latenciesMs.length);
        long[] copy = Arrays.copyOf(latenciesMs, count);
        Arrays.sort(copy);
        if (count == 0) {
            return new Percentiles(0, 0, 0, 0, 0, 0);
        }
        return new Percentiles(
                copy[0],
                copy[count - 1],
                average(copy),
                percentile(copy, 50),
                percentile(copy, 95),
                percentile(copy, 99)
        );
    }

    private static double average(long[] values) {
        if (values.length == 0) return 0;
        long sum = 0;
        for (long v : values) sum += v;
        return (double) sum / values.length;
    }

    private static long percentile(long[] sorted, int p) {
        if (sorted.length == 0) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        idx = Math.max(0, Math.min(idx, sorted.length - 1));
        return sorted[idx];
    }

    public record Percentiles(long min, long max, double avg, long p50, long p95, long p99) {}
}
