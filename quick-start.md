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

## 5. Notes
- The standalone benchmark uses a local mock server by default.
- The Spring Boot app uses virtual threads when started with JDK 21+.
- If you want to compare with platform threads, change `spring.threads.virtual.enabled` to `false` in `springboot-vt-demo/src/main/resources/application.yml` and restart the app.
