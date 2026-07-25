# Test script for Spring Boot Virtual Threads vs Platform Threads
# Tests both configurations

$projectPath = "D:\complete-lab\dev\vt-benchmark-project\springboot-vt-demo"
$configFile = "$projectPath\src\main\resources\application.yml"

function Run-Test($vtEnabled) {
    Write-Host "`n" -ForegroundColor Cyan
    Write-Host "====================================================" -ForegroundColor Cyan
    Write-Host "Testing with Virtual Threads: $vtEnabled" -ForegroundColor Yellow
    Write-Host "====================================================" -ForegroundColor Cyan
    
    # Update config
    $content = Get-Content $configFile
    $content = $content -replace 'enabled: (true|false)', "enabled: $vtEnabled"
    Set-Content -Path $configFile -Value $content
    
    Write-Host "Updated application.yml - Virtual Threads: $vtEnabled" -ForegroundColor Green
    
    # Build and run
    Write-Host "Building Spring Boot app..." -ForegroundColor Cyan
    Set-Location $projectPath
    
    # Clean and build
    mvn clean compile -q
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Build failed!" -ForegroundColor Red
        return $false
    }
    
    Write-Host "Starting Spring Boot server..." -ForegroundColor Green
    $job = Start-Job -ScriptBlock {
        cd "$using:projectPath"
        mvn spring-boot:run -DskipTests 2>&1
    }
    
    # Wait for server to start
    Write-Host "Waiting for server to start..." -ForegroundColor Yellow
    Start-Sleep -Seconds 5
    
    # Check if server is running
    try {
        $health = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -ErrorAction Stop
        Write-Host "Server is running ✓" -ForegroundColor Green
    } catch {
        Write-Host "Server failed to start!" -ForegroundColor Red
        Stop-Job -Job $job
        Remove-Job -Job $job
        return $false
    }
    
    # Run benchmark
    Write-Host "`nRunning benchmark: 3000 calls with 1000ms delay..." -ForegroundColor Cyan
    Write-Host "Endpoint: http://localhost:8080/api/load-test?calls=3000&delayMs=1000" -ForegroundColor Gray
    
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/api/load-test?calls=3000&delayMs=1000" -ErrorAction Stop
        $result = $response.Content | ConvertFrom-Json
        
        Write-Host "`n`n" -ForegroundColor White
        Write-Host "RESULTS (Virtual Threads = $vtEnabled)" -ForegroundColor Yellow
        Write-Host "-------------------------------------------" -ForegroundColor Yellow
        Write-Host "Duration (s):           $([math]::Round($result.durationSeconds, 2))" -ForegroundColor Cyan
        Write-Host "Success:                $($result.success)" -ForegroundColor Green
        Write-Host "Failures:               $($result.failures)" -ForegroundColor $($result.failures -gt 0 ? 'Red' : 'Green')
        $successRate = [math]::Round(($result.success / ($result.success + $result.failures) * 100), 2)
        Write-Host "Success Rate:           $successRate%" -ForegroundColor $($successRate -eq 100 ? 'Green' : 'Yellow')
        Write-Host "Throughput (req/s):     $([math]::Round($result.throughputReqSec, 2))" -ForegroundColor Cyan
        Write-Host "" -ForegroundColor White
        Write-Host "LATENCY (ms)" -ForegroundColor Yellow
        Write-Host "  Min:                  $([math]::Round($result.latencyMinMs, 2))" -ForegroundColor White
        Write-Host "  Avg:                  $([math]::Round($result.latencyAvgMs, 2))" -ForegroundColor White
        Write-Host "  p50:                  $([math]::Round($result.latencyP50Ms, 2))" -ForegroundColor White
        Write-Host "  p95:                  $([math]::Round($result.latencyP95Ms, 2))" -ForegroundColor White
        Write-Host "  p99:                  $([math]::Round($result.latencyP99Ms, 2))" -ForegroundColor White
        Write-Host "  Max:                  $([math]::Round($result.latencyMaxMs, 2))" -ForegroundColor White
        Write-Host "-------------------------------------------" -ForegroundColor Yellow
        Write-Host "`n"
        
    } catch {
        Write-Host "Error running benchmark: $_" -ForegroundColor Red
    }
    
    # Stop server
    Write-Host "Stopping server..." -ForegroundColor Cyan
    Stop-Job -Job $job
    Remove-Job -Job $job
    Start-Sleep -Seconds 2
    
    return $true
}

# Run tests
Write-Host "`nSpring Boot Virtual Threads Benchmark Test" -ForegroundColor Magenta
Write-Host "========================================" -ForegroundColor Magenta

# Test 1: Virtual Threads ENABLED
Run-Test "true"

# Test 2: Virtual Threads DISABLED
Run-Test "false"

# Final comparison
Write-Host "`nTest Complete! Compare results above." -ForegroundColor Green
Write-Host "Note: Virtual Threads should show lower avg latency and higher throughput." -ForegroundColor Gray
