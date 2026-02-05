@echo off
REM ===============================================
REM Hush.Chat - Simple Run Script
REM Starts Docker (Redis) and Spring Boot app
REM ===============================================

echo.
echo ========================================
echo   Hush.Chat - Starting Environment
echo ========================================
echo.

REM -------------------------------
REM Check Docker
REM -------------------------------
echo [INFO] Checking Docker...
docker info >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Docker is not running
    echo [FIX]   Start Docker Desktop and try again
    pause
    exit /b 1
)
echo [OK] Docker is running

REM -------------------------------
REM Start Redis via Docker Compose
REM -------------------------------
echo.
echo [INFO] Starting Redis (docker-compose)...
docker compose up -d
IF %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to start Docker containers
    pause
    exit /b 1
)
echo [OK] Redis container started

REM -------------------------------
REM Optional: wait a bit for Redis
REM -------------------------------
timeout /t 3 /nobreak >nul

REM -------------------------------
REM Start Spring Boot Application
REM -------------------------------
echo.
echo ========================================
echo   Starting Hush.Chat Backend
echo ========================================
echo.
echo [INFO] Access: http://localhost:8080
echo [INFO] Press Ctrl+C to stop
echo.

.\mvnw.cmd spring-boot:run

REM -------------------------------
REM If app fails
REM -------------------------------
IF %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Application failed to start
    echo [INFO] Check logs above
    pause
    exit /b 1
)

exit /b 0
