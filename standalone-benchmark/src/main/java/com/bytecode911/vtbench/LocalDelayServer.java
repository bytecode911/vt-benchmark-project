package com.bytecode911.vtbench;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Minimal in-process HTTP server that simulates a slow downstream dependency
 * (equivalent to httpbin.org/delay/1 from the original script).
 * <p>
 * Why this exists: firing 10,000 concurrent requests at a public, shared,
 * rate-limited endpoint (httpbin.org) does not give reproducible numbers —
 * you will see connection resets / 429s well before 10k in-flight requests,
 * and you'll be measuring httpbin's throttling, not your thread model.
 * This server is used by default; pass --url=https://httpbin.org/delay/1
 * (with a much smaller --tasks, e.g. 200) if you want a real-network sanity check.
 */
public class LocalDelayServer {

    private final int delayMs;
    private HttpServer server;

    public LocalDelayServer(int delayMs) {
        this.delayMs = delayMs;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Virtual-thread executor on the server side too, so a slow handler never
        // exhausts a fixed platform-thread pool while you're benchmarking the client side.
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/delay", this::handle);
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            Thread.sleep(delayMs);
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
