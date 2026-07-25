package com.bytecode911.vtbench;

/**
 * CLI configuration for the benchmark. Supports --key=value args, e.g.:
 *   java -jar vt-benchmark.jar --tasks=10000 --poolSize=200 --delayMs=1000
 *   java -jar vt-benchmark.jar --url=https://httpbin.org/delay/1 --tasks=200
 */
public record BenchmarkConfig(int tasks, int poolSize, int delayMs, String url) {

    public static BenchmarkConfig fromArgs(String[] args) {
        int tasks = 3_000;
        int poolSize = 200;
        int delayMs = 1000;
        String url = null;

        for (String arg : args) {
            String[] parts = arg.replaceFirst("^--", "").split("=", 2);
            if (parts.length != 2) {
                System.err.println("Ignoring malformed argument: " + arg);
                continue;
            }
            switch (parts[0]) {
                case "tasks" -> tasks = Integer.parseInt(parts[1]);
                case "poolSize" -> poolSize = Integer.parseInt(parts[1]);
                case "delayMs" -> delayMs = Integer.parseInt(parts[1]);
                case "url" -> url = parts[1];
                default -> System.err.println("Unknown option: " + parts[0]);
            }
        }
        return new BenchmarkConfig(tasks, poolSize, delayMs, url);
    }
}
