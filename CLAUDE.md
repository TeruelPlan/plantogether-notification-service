# CLAUDE.md

This file provides guidance to Claude when working with code in this repository.

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

**Prerequisites:** install shared libs first:
```bash
cd ../plantogether-proto && mvn clean install
cd ../plantogether-common && mvn clean install
```

## Architecture

Spring Boot 3.3.6 microservice (Java 21). Orchestrates all user notifications: consumes domain events from
RabbitMQ, resolves user profiles via gRPC, and sends FCM push notifications + SMTP emails.

**Port:** REST `8087` (no gRPC server)

**Package:** `com.plantogether.notification`

### Package structure

```
com.plantogether.notification/
├── config/          # SecurityConfig, RabbitConfig
├── controller/      # REST controllers (preferences, FCM tokens)
├── domain/          # JPA entities (NotificationPreference, NotificationLog)
├── repository/      # Spring Data JPA
├── service/         # NotificationService, FcmService, EmailService
├── dto/             # Request/Response DTOs (Lombok @Data @Builder)
├── grpc/
│   └── client/      # TripGrpcClient (GetTripMembers → trip-service:9081)
└── event/
    └── listener/    # RabbitMQ consumers for all *.events routing keys
```

### Infrastructure dependencies

| Dependency | Default (local) | Purpose |
|---|---|---|
| PostgreSQL 16 | `localhost:5432/plantogether_notification` | Preferences + delivery log (db_notification) |
| RabbitMQ | `localhost:5672` | Consuming all domain events |
| Keycloak 24+ | `localhost:8180` | JWT validation (REST API) |
| trip-service gRPC | `localhost:9081` | GetTripMembers (email + display_name resolution) |
| Firebase Admin SDK | — | FCM push notifications |
| SMTP | — | Email sending via Spring Mail + Thymeleaf templates |


### Zero PII principle

No names or emails stored in this service's database — only Keycloak UUIDs. User profiles (email, display_name)
are fetched at send time via `TripGrpcService.GetTripMembers(tripId)` and never persisted.

### Domain model (db_notification)

**`notification_preference`** — id (UUID), keycloak_id, trip_id (nullable = global preference), channel
(`PUSH`/`EMAIL`), event_type, enabled (BOOLEAN).

**`notification_log`** — id (UUID), keycloak_id, event_type, trip_id, payload (JSONB), sent_at, channel,
status (`SENT`/`FAILED`).

### gRPC client

Calls `TripGrpcService.GetTripMembers(tripId)` on trip-service:9081 to resolve `display_name` and `email`
for each recipient at notification send time.

### RabbitMQ consumers

Consumes all events from exchange `plantogether.events` via routing key pattern `#`:

| Routing key | Source | Action |
|---|---|---|
| `trip.created` | trip-service | Notify trip creator (welcome) |
| `trip.member.joined` | trip-service | Notify organizer + new member |
| `poll.created` | poll-service | Notify all trip members |
| `poll.locked` | poll-service | Notify all trip members with locked dates |
| `vote.cast` | destination-service | Notify destination proposer |
| `expense.created` | expense-service | Notify all trip members |
| `expense.deleted` | expense-service | Notify trip members |
| `task.assigned` | task-service | Notify assignee |
| `task.deadline.reminder` | task-service | Notify assignee + organizer |
| `chat.message.sent` | chat-service | Notify offline trip members (push only) |

### Retry strategy

FCM/email failures are retried 3 times with exponential backoff: 5s → 60s → 300s. After exhaustion,
the notification is logged as `FAILED` in `notification_log`.

### REST API (`/api/v1/notifications/`)

| Method | Endpoint | Notes |
|---|---|---|
| GET | `/api/v1/notifications/preferences` | Get user preferences (global + per trip) |
| PUT | `/api/v1/notifications/preferences` | Update preferences |
| POST | `/api/v1/notifications/fcm-token` | Register device FCM token |
| DELETE | `/api/v1/notifications/fcm-token` | Unregister FCM token |
| GET | `/api/v1/notifications/history` | Delivery history (paginated) |

### Email templates

Thymeleaf HTML templates in `src/main/resources/email-templates/`. Naming: `{event-type}_{locale}.html`
(e.g. `poll-created_fr.html`, `expense-created_en.html`).

### Environment variables

| Variable | Description |
|---|---|
| `DB_USER` / `DB_PASSWORD` | PostgreSQL credentials |
| `RABBITMQ_HOST` | RabbitMQ host |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | RabbitMQ credentials |
| `MAIL_HOST` | SMTP host |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP credentials |
| `FIREBASE_CREDENTIALS_PATH` | Path to Firebase Admin JSON key (default: `classpath:firebase-service-account.json`) |
| `KEYCLOAK_URL` | Keycloak base URL (default: `http://localhost:8180`) |
| `TRIP_SERVICE_GRPC_HOST` | trip-service gRPC host (default: `localhost`) |
| `TRIP_SERVICE_GRPC_PORT` | trip-service gRPC port (default: `9081`) |

