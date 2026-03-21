# Notification Service

> Service d'orchestration des notifications push (FCM) et email

## Rôle dans l'architecture

Le Notification Service est le consommateur principal des événements RabbitMQ. Il orchestre l'envoi de
notifications push (Firebase Cloud Messaging) et d'emails SMTP vers les membres concernés. Pour chaque
événement, il appelle TripService.GetTripMembers via gRPC pour récupérer les emails et display_names, puis
filtre selon les préférences utilisateur avant d'envoyer.

## Fonctionnalités

- Consommation de tous les événements métier RabbitMQ (`*.events`)
- Envoi de notifications push via Firebase Cloud Messaging (FCM)
- Envoi d'emails via SMTP
- Retry automatique : 3 tentatives avec backoff exponentiel (5s → 60s → 300s)
- Gestion des préférences de notification par utilisateur et par trip
- Journal des notifications envoyées (`notification_log`)

## Événements consommés

| Routing Key | Action |
|-------------|--------|
| `trip.created` | Notifie les organisateurs |
| `trip.member.joined` | Notifie l'organisateur |
| `poll.created` | Notifie les membres du trip |
| `poll.locked` | Notifie les membres (dates confirmées) |
| `vote.cast` | Notifie les membres |
| `expense.created` | Notifie les membres concernés |
| `expense.deleted` | Notifie les membres concernés |
| `task.assigned` | Notifie l'assigné |
| `task.deadline.reminder` | Notifie l'assigné |
| `chat.message.sent` | Notifie les membres hors-ligne |

## gRPC Clients

- `TripService.GetTripMembers(tripId)` — récupère emails et display_names pour les notifications
- `TripService.CheckMembership(tripId, userId)` — (si besoin de vérification ponctuelle)

## Modèle de données (`db_notification`)

**notification_preference**

| Colonne | Type | Description |
|---------|------|-------------|
| `id` | UUID PK | |
| `keycloak_id` | UUID NOT NULL | Utilisateur |
| `trip_id` | UUID NULLABLE | NULL = préférence globale |
| `channel` | ENUM NOT NULL | PUSH / EMAIL |
| `event_type` | VARCHAR(100) NOT NULL | Ex. `expense.created`, `poll.created` |
| `enabled` | BOOLEAN NOT NULL | Préférence activée/désactivée |

**notification_log**

| Colonne | Type | Description |
|---------|------|-------------|
| `id` | UUID PK | |
| `keycloak_id` | UUID NOT NULL | Destinataire |
| `event_type` | VARCHAR(100) NOT NULL | Type d'événement source |
| `trip_id` | UUID NULLABLE | Trip concerné |
| `payload` | JSONB NOT NULL | Contenu de la notification |
| `channel` | ENUM NOT NULL | PUSH / EMAIL |
| `status` | ENUM NOT NULL | SENT / FAILED |
| `sent_at` | TIMESTAMP NOT NULL | |

## Stratégie de retry

Les messages en erreur sont rejoués automatiquement avec un backoff exponentiel :

1. 1ère tentative immédiate
2. Retry après 5 secondes
3. Retry après 60 secondes
4. Retry après 300 secondes (dernier essai)
5. Si toujours en échec → dead letter queue + log FAILED dans `notification_log`

## Endpoints REST

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/v1/notifications/preferences` | Récupérer ses préférences |
| PUT | `/api/v1/notifications/preferences` | Mettre à jour ses préférences |
| GET | `/api/v1/notifications/history` | Historique des notifications reçues |

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

smtp:
  host: ${SMTP_HOST}
  port: ${SMTP_PORT:587}
  username: ${SMTP_USERNAME}
  password: ${SMTP_PASSWORD}

grpc:
  client:
    trip-service:
      address: static://trip-service:9081
```

## Lancer en local

```bash
# Prérequis : docker compose --profile essential up -d
# + plantogether-proto et plantogether-common installés

mvn spring-boot:run
```

## Dépendances

- **Keycloak 24+** : validation JWT (endpoints préférences)
- **PostgreSQL 16** (`db_notification`) : préférences et journal
- **RabbitMQ** : consommation de tous les événements `*.events`
- **Firebase Cloud Messaging** : push mobile
- **SMTP** : notifications email
- **Trip Service** (gRPC 9081) : résolution des profils (emails, display_names)
- **plantogether-proto** : contrats gRPC (client)
- **plantogether-common** : DTOs events, CorsConfig

## Sécurité

- Les endpoints de préférences requièrent un token Bearer valide
- Aucune PII n'est stockée en dur — les profils sont résolus à la volée via gRPC
- Les FCM tokens sont gérés côté Keycloak / attribut custom utilisateur
