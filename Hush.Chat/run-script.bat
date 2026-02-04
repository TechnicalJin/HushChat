@echo off
REM ========================================
REM Hush Chat - All-in-One Run Script
REM ========================================
REM This script will:
REM 1. Start Docker services (Redis & MySQL)
REM 2. Wait for services to be ready
REM 3. Compile the project
REM 4. Run the Spring Boot application
REM ========================================

echo.
echo ========================================
echo   Hush Chat - Starting Application
echo ========================================
echo.

REM Check if Docker is running
echo [1/5] Checking Docker status...
docker info >nul 2>&1
if errorlevel 1 (
    echo.
    echo [ERROR] Docker is not running!
    echo Please start Docker Desktop and try again.
    echo.
    pause
    exit /b 1
)
echo [OK] Docker is running
echo.

REM Start Docker Compose services
echo [2/5] Starting Docker services (Redis)...
docker-compose up -d
if errorlevel 1 (
    echo.
    echo [ERROR] Failed to start Docker services!
    echo Check docker-compose.yml configuration.
    echo.
    pause
    exit /b 1
)
echo [OK] Docker services started
echo.

REM Wait for Redis to be healthy
echo [3/5] Waiting for Redis to be ready...
echo.

REM Wait for Redis
echo Checking Redis...
:wait_redis
timeout /t 2 /nobreak >nul
docker exec chat-redis redis-cli ping >nul 2>&1
if errorlevel 1 (
    echo Still waiting for Redis...
    goto wait_redis
)
echo [OK] Redis is ready
echo.

REM Compile the project
echo [4/5] Compiling project with Maven...
echo This may take a few minutes...
echo.
call mvn clean package -DskipTests
if errorlevel 1 (
    echo.
    echo [ERROR] Maven build failed!
    echo Check the error messages above.
    echo.
    pause
    exit /b 1
)
echo.
echo [OK] Project compiled successfully
echo.

REM Run the application
echo [5/5] Starting Spring Boot application...
echo.
echo ========================================
echo   Application is starting...
echo   Press Ctrl+C to stop
echo ========================================
echo.
echo Application will be available at:
echo   - Main: http://localhost:8080
echo   - Health: http://localhost:8080/actuator/health
echo   - Dev Tools: http://localhost:8080/api/dev/redis/status
echo.

call mvn spring-boot:run

REM This part runs after you stop the application with Ctrl+C
echo.
echo ========================================
echo   Application stopped
echo ========================================
echo.
echo To stop Docker services, run:
echo   docker-compose stop
echo.
echo Or run: stop-redis.bat
echo.
pause
