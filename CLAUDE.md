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

**Prerequisites:**

Local Maven builds resolve shared libs (`plantogether-parent`, `plantogether-bom`, `plantogether-common`,
`plantogether-proto`) from GitHub Packages. Export a PAT with `read:packages` before running `mvn`:

```bash
export GITHUB_ACTOR=<your-github-username>
export PACKAGES_TOKEN=<your-PAT-with-read:packages>
mvn -s .settings.xml clean package
```

## Architecture

Spring Boot 3.5.9 microservice (Java 21). Orchestrates all user notifications: consumes domain events from
RabbitMQ, resolves user profiles via gRPC, sends FCM push notifications, **and owns the centralized STOMP hub
(`/ws`) that relays real-time trip updates to connected clients**.

**Port:** REST + WebSocket (STOMP) `8087` (no gRPC server).

**Package:** `com.plantogether.notification`

### Package structure

```
com.plantogether.notification/
├── config/          # RabbitConfig, WebSocketConfig (centralized STOMP hub)
├── controller/      # REST controllers (preferences, FCM tokens)
├── domain/          # JPA entities (NotificationPreference, NotificationLog)
├── repository/      # Spring Data JPA
├── security/        # StompDeviceIdInterceptor, StompMembershipInterceptor
├── service/         # NotificationService, FcmService
├── dto/             # Request/Response DTOs (Lombok @Data @Builder)
├── grpc/
│   └── client/      # TripGrpcClient (IsMember for STOMP membership check, GetTripMembers for FCM)
├── listener/        # RabbitMQ → STOMP bridge listeners (PollVoteStomp, PollLockedStomp)
└── event/
    └── listener/    # RabbitMQ consumers for FCM / email routing keys
```

### WebSocket / STOMP (centralized hub)

Serves STOMP at `/ws` (WebSocket + SockJS fallback). All services that need to push real-time updates to
clients publish RabbitMQ events; this service consumes them and broadcasts STOMP frames.

| Direction | Destination | Purpose |
|---|---|---|
| CONNECT | `/ws` (`X-Device-Id` STOMP header required) | Establish session |
| SUBSCRIBE | `/topic/trips/{tripId}/updates` | Membership-checked via `TripGrpcClient.IsMember`; receives per-trip update frames |

Membership check cached in-process for 60s per `(tripId, deviceId)` and invalidated on STOMP `DISCONNECT`.
`SimpleBroker` is used (in-process); multi-replica fan-out requires a STOMP relay — see deferred-work.

**Bridge listeners (RabbitMQ → STOMP):**

| Queue | Routing key | Frame type | Destination |
|---|---|---|---|
| `q.notification.stomp.poll.vote.cast` | `poll.vote.cast` | `POLL_VOTE_CAST` | `/topic/trips/{tripId}/updates` |
| `q.notification.stomp.poll.locked` | `poll.locked` | `POLL_LOCKED` | `/topic/trips/{tripId}/updates` |
| `q.notification.stomp.destination.vote.cast` | `vote.cast` | `DESTINATION_VOTE_CAST` | `/topic/trips/{tripId}/updates` |

### Infrastructure dependencies

| Dependency | Default (local) | Purpose |
|---|---|---|
| PostgreSQL 16 | `localhost:5432/plantogether_notification` | Preferences + delivery log (db_notification) |
| RabbitMQ | `localhost:5672` | Consuming all domain events |
| trip-service gRPC | `localhost:9081` | GetTripMembers (display_name resolution) |
| Firebase Admin SDK | — | FCM push notifications |


### Zero PII principle

No names stored in this service's database — only device UUIDs. User profiles (display_name)
are fetched at send time via `TripGrpcService.GetTripMembers(tripId)` and never persisted.

### Domain model (db_notification)

**`notification_preference`** — id (UUID), device_id, trip_id (nullable = global preference), channel
(`PUSH`), event_type, enabled (BOOLEAN).

**`notification_log`** — id (UUID), device_id, event_type, trip_id, payload (JSONB), sent_at, channel,
status (`SENT`/`FAILED`).

### gRPC client

Calls `TripGrpcService.GetTripMembers(tripId)` on trip-service:9081 to resolve `display_name`
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

FCM failures are retried 3 times with exponential backoff: 5s → 60s → 300s. After exhaustion,
the notification is logged as `FAILED` in `notification_log`.

### REST API (`/api/v1/notifications/`)

| Method | Endpoint | Notes |
|---|---|---|
| GET | `/api/v1/notifications/preferences` | Get user preferences (global + per trip) |
| PUT | `/api/v1/notifications/preferences` | Update preferences |
| POST | `/api/v1/notifications/fcm-token` | Register device FCM token |
| DELETE | `/api/v1/notifications/fcm-token` | Unregister FCM token |
| GET | `/api/v1/notifications/history` | Delivery history (paginated) |

### Security

- Anonymous device-based identity via `DeviceIdFilter` (from `plantogether-common`, auto-configured via `SecurityAutoConfiguration`)
- `X-Device-Id` header extracted and set as SecurityContext principal
- No JWT, no Keycloak, no login, no sessions
- No SecurityConfig.java needed — `SecurityAutoConfiguration` handles everything
- Zero PII stored — profiles resolved on-the-fly via gRPC

### Environment variables

| Variable | Description |
|---|---|
| `DB_USER` / `DB_PASSWORD` | PostgreSQL credentials |
| `RABBITMQ_HOST` | RabbitMQ host |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | RabbitMQ credentials |
| `FIREBASE_CREDENTIALS_PATH` | Path to Firebase Admin JSON key (default: `classpath:firebase-service-account.json`) |
| `TRIP_SERVICE_GRPC_HOST` | trip-service gRPC host (default: `localhost`) |
| `TRIP_SERVICE_GRPC_PORT` | trip-service gRPC port (default: `9081`) |
