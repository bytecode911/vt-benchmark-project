# Quick Start

## 1. Prerequisites
- JDK 21+
- Maven 3.9+

## 2. Run the standalone benchmark
```powershell
cd D:\complete-lab\dev\vt-benchmark-project\standalone-benchmark
mvn compile exec:java
```

## 3. Run the Spring Boot demo
```powershell
cd D:\complete-lab\dev\vt-benchmark-project\springboot-vt-demo
mvn spring-boot:run
```

## 4. Test URLs
### Spring Boot endpoints
```powershell
curl http://localhost:8080/api/thread-info
curl "http://localhost:8080/api/load-test?calls=3000&delayMs=1000"
curl http://localhost:8080/actuator/health
```

### Optional
```powershell
curl http://localhost:8080/api/delay/1000
```

## 5. Benchmark Results

Run the included test script to compare virtual threads enabled vs disabled:

```powershell
pwsh -ExecutionPolicy Bypass -File test-vt-springboot.ps1
```

This will run the same load test (3000 calls, 1000ms delay) twice — once with virtual threads enabled and once disabled — and show you the performance difference.

### Results Summary
- **Virtual threads enabled:** 3.85s, 778.82 req/s, 76.83% success
- **Virtual threads disabled:** 11.64s, 257.84 req/s, 65.5% success
- **Winner:** Virtual threads are **3x faster** ✅

## 6. Notes
- The standalone benchmark uses a local mock server by default.
- The Spring Boot app uses virtual threads when started with JDK 21+.
- If you want to compare with platform threads, change `spring.threads.virtual.enabled` to `false` in `springboot-vt-demo/src/main/resources/application.yml` and restart the app.
