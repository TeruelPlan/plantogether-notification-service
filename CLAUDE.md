# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
mvn -B package -DskipTests

# Build with tests
mvn -B package

# Run locally
mvn spring-boot:run

# Run a single test
mvn test -Dtest=ClassName#methodName

# Run all tests
mvn test

# Docker build
docker build -t plantogether-notification-service .
```

## Architecture

This is a **Spring Boot 3.3.6 / Java 21** microservice within the PlanTogether platform. It runs on **port 8087** and registers with **Eureka** for service discovery.

### Responsibility

Orchestrates all user notifications. It:
1. Consumes events from **RabbitMQ** (published by trip, poll, expense, task, chat services)
2. Resolves user profiles (UUID → name/email) from **Redis cache** (TTL 24h) or **Keycloak Admin API** on cache miss
3. Sends **FCM push notifications** (Firebase Admin SDK) and **emails** (SMTP via Spring Mail + Thymeleaf templates)
4. Persists notification preferences, FCM tokens, DND settings, and delivery history in **PostgreSQL**

### Zero PII principle
Only Keycloak UUIDs are stored in the database — no names, emails, or phone numbers. User profile data is fetched at send time from cache/Keycloak and never persisted.

### Key integrations
- **Keycloak** (`KEYCLOAK_URL`): JWT validation (JWK set URI) + Admin API for user profile resolution
- **Firebase**: credentials via `FIREBASE_CREDENTIALS_PATH` (default: `classpath:firebase-service-account.json`)
- **RabbitMQ queues**: `q.expense.created`, `q.trip.event`, `q.notification.push`
- **`plantogether-common`** (internal, version `1.0.0-SNAPSHOT`): shared DTOs/events — must be installed in local Maven repo

### REST API (port 8087, JWT-protected)
- `PUT/GET /api/notifications/preferences` — global and per-trip preferences
- `POST/DELETE /api/notifications/dnd` — Do Not Disturb scheduling
- `GET/POST /api/notifications/fcm-token` — device token registration
- `GET /api/notifications/history` — delivery history

### Email templates
Thymeleaf HTML templates in `src/main/resources/email-templates/`. Multilingual variants follow the pattern `{event-type}_{locale}.html` (e.g. `poll-created_fr.html`).

### Retry strategy
FCM/email failures are retried 3 times (5s → 60s → 300s). After exhaustion, a `NotificationFailed` event is published to RabbitMQ.

### Required environment variables
| Variable | Description |
|---|---|
| `DB_USER` / `DB_PASSWORD` | PostgreSQL credentials |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | RabbitMQ credentials |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP credentials (use app-passwords) |
| `FIREBASE_CREDENTIALS_PATH` | Path to Firebase Admin JSON key |
| `KEYCLOAK_URL` | Keycloak base URL (default: `http://localhost:8180`) |
| `EUREKA_URL` | Eureka server URL (default: `http://localhost:8761/eureka/`) |
