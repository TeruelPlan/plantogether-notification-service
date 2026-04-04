# Notification Service

> Push notification (FCM) and email orchestration service

## Role in the Architecture

The Notification Service is the primary consumer of RabbitMQ events. It orchestrates push notifications
(Firebase Cloud Messaging) and SMTP emails to relevant trip members. For each event, it calls
TripService.GetTripMembers via gRPC to retrieve display names, then filters based on user preferences
before sending.

## Features

- Consumption of all business events from RabbitMQ (`*.events`)
- Push notification delivery via Firebase Cloud Messaging (FCM)
- Retry mechanism: 3 attempts with exponential backoff (5s → 60s → 300s)
- Per-user, per-trip notification preference management
- Notification delivery log (`notification_log`)

## Events Consumed

| Routing Key | Action |
|-------------|--------|
| `trip.created` | Notify organizers |
| `trip.member.joined` | Notify the organizer |
| `poll.created` | Notify trip members |
| `poll.locked` | Notify members (dates confirmed) |
| `vote.cast` | Notify members |
| `expense.created` | Notify relevant members |
| `expense.deleted` | Notify relevant members |
| `task.assigned` | Notify the assignee |
| `task.deadline.reminder` | Notify the assignee |
| `chat.message.sent` | Notify offline members |

## gRPC Clients

- `TripService.GetTripMembers(tripId)` — retrieves display names for notifications
- `TripService.IsMember(tripId, deviceId)` — membership verification (if needed)

## Data Model (`db_notification`)

**notification_preference**

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID PK | |
| `device_id` | UUID NOT NULL | Device UUID |
| `trip_id` | UUID NULLABLE | NULL = global preference |
| `channel` | ENUM NOT NULL | PUSH |
| `event_type` | VARCHAR(100) NOT NULL | e.g. `expense.created`, `poll.created` |
| `enabled` | BOOLEAN NOT NULL | Preference enabled/disabled |

**notification_log**

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID PK | |
| `device_id` | UUID NOT NULL | Recipient device UUID |
| `event_type` | VARCHAR(100) NOT NULL | Source event type |
| `trip_id` | UUID NULLABLE | Related trip |
| `payload` | JSONB NOT NULL | Notification content |
| `channel` | ENUM NOT NULL | PUSH |
| `status` | ENUM NOT NULL | SENT / FAILED |
| `sent_at` | TIMESTAMP NOT NULL | |

## Retry Strategy

Failed messages are automatically retried with exponential backoff:

1. 1st attempt — immediate
2. Retry after 5 seconds
3. Retry after 60 seconds
4. Retry after 300 seconds (last attempt)
5. If still failing → dead letter queue + FAILED log in `notification_log`

## REST Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/notifications/preferences` | Get notification preferences |
| PUT | `/api/v1/notifications/preferences` | Update notification preferences |
| POST | `/api/v1/notifications/fcm-token` | Register device FCM token |
| DELETE | `/api/v1/notifications/fcm-token` | Unregister FCM token |
| GET | `/api/v1/notifications/history` | Notification delivery history |

## Configuration

```yaml
server:
  port: 8087

spring:
  application:
    name: plantogether-notification-service
  datasource:
    url: jdbc:postgresql://postgres:5432/db_notification
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
  rabbitmq:
    host: ${RABBITMQ_HOST:rabbitmq}

firebase:
  credentials: ${FIREBASE_CREDENTIALS_JSON}

grpc:
  client:
    trip-service:
      address: static://trip-service:9081
```

## Running Locally

```bash
# Prerequisites: docker compose up -d
# + plantogether-proto and plantogether-common installed

mvn spring-boot:run
```

## Dependencies

- **PostgreSQL 16** (`db_notification`): preferences and delivery log
- **RabbitMQ**: consumption of all `*.events` domain events
- **Firebase Cloud Messaging**: mobile push notifications
- **Trip Service** (gRPC 9081): profile resolution (display names)
- **plantogether-proto**: gRPC contracts (client)
- **plantogether-common**: event DTOs, DeviceIdFilter, SecurityAutoConfiguration, CorsConfig

## Security

- Anonymous device-based identity: `X-Device-Id` header on every request
- `DeviceIdFilter` (from plantogether-common, auto-configured via `SecurityAutoConfiguration`) extracts the device UUID and sets the SecurityContext principal
- No JWT, no Keycloak, no login, no sessions
- Zero PII stored — profiles are resolved on-the-fly via gRPC at send time
- FCM tokens are stored per device_id in this service's database
