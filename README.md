# Hush.Chat

Hush.Chat is a real-time chat application with a React frontend and a Spring Boot backend. It supports authenticated chat sessions, temporary rooms, real-time messaging over WebSocket, presence, reactions, OTP-based authentication, and file uploads.

## Project Structure

- `hush-chat-react/` - React 19 frontend
- `Hush.Chat/` - Spring Boot 3.3 backend
- `Hush.Chat/docker-compose.yml` - Redis development service
- `HOW-TO-RUN.md` - legacy quick-start notes

## Technology Stack

- Frontend: React, React Router, STOMP, SockJS
- Backend: Java 17, Spring Boot, Spring Security, Spring Data JPA
- Data: MySQL and Redis
- Build tools: npm and Maven Wrapper

## Prerequisites

Install or start the following before running the application:

- Java 17 or later
- Node.js and npm
- Docker Desktop with Docker Compose
- MySQL 8 running locally on port `3306`

The development backend connects to a MySQL database named `chatdb`. The configured development datasource creates the database when it does not exist, but the MySQL server itself must already be running.

## Run Locally

Run the backend and frontend in separate terminals.

### 1. Start the backend and Redis

From the repository root in PowerShell:

```powershell
cd Hush.Chat
cmd /c run-script.bat
```

This starts Redis in Docker and launches Spring Boot on `http://localhost:8080`.

To start the backend manually instead:

```powershell
cd Hush.Chat
docker compose up -d
.\mvnw.cmd spring-boot:run
```

### 2. Start the frontend

```powershell
cd hush-chat-react
npm install
npm start
```

The frontend is available at `http://localhost:3000`.

### Stop services

Stop the frontend and backend with `Ctrl+C`. Stop Redis with:

```powershell
cd Hush.Chat
docker compose down
```

## Testing

### Frontend

```powershell
cd hush-chat-react
npm test
```

Create a production frontend build with:

```powershell
npm run build
```

### Backend

```powershell
cd Hush.Chat
.\mvnw.cmd test
```

## Configuration

Development defaults are stored in `Hush.Chat/src/main/resources/application.properties`. Important settings include:

- Backend port: `8080`
- MySQL: `localhost:3306/chatdb`
- Redis: `localhost:6379`
- Upload directory: `Hush.Chat/uploads/`
- Maximum upload size: `200 MB`

For local development, the application no longer ships default database credentials.
Before starting the backend, supply the database connection through environment
variables. `DB_URL` is optional (defaults to `localhost:3306/chatdb`), but
`DB_USERNAME` and `DB_PASSWORD` are required to connect to MySQL:

```powershell
cd Hush.Chat
$env:DB_URL = "jdbc:mysql://localhost:3306/chatdb?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:DB_USERNAME = "your-local-db-user"
$env:DB_PASSWORD = "your-local-db-password"
.\mvnw.cmd spring-boot:run
```

For production, activate the `prod` profile and provide environment variables for secrets and infrastructure credentials:

```powershell
cd Hush.Chat
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:APP_JWT_SECRET = "replace-with-a-secure-secret"
$env:DB_URL = "jdbc:mysql://host:3306/chatdb?useSSL=true&requireSSL=true&serverTimezone=UTC"
$env:DB_USERNAME = "database-user"
$env:DB_PASSWORD = "database-password"
.\mvnw.cmd spring-boot:run
```

Do not commit real passwords, JWT secrets, or other credentials to source control. Replace the development values before deploying outside a local environment.

## Health Checks

When the backend is running, actuator endpoints include:

- `http://localhost:8080/actuator/health`
- `http://localhost:8080/actuator/info`
- `http://localhost:8080/actuator/metrics`
